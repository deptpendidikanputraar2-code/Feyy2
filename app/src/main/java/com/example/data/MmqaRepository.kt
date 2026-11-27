package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface SyncBannerState {
    data object Idle : SyncBannerState
    data object Syncing : SyncBannerState
    data class Synced(val timeLabel: String, val source: String) : SyncBannerState
    data object Offline : SyncBannerState
    data class Error(val message: String) : SyncBannerState
}

data class RealtimeAlertItem(
    val title: String,
    val body: String,
    val category: String,
    val notifId: Int = (System.currentTimeMillis() % 100000).toInt()
)

class MmqaRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncClient by lazy { GoogleSheetsSyncClient() }
    private val supabaseClient by lazy { SupabaseSyncClient() }
    val securityManager: MmqaSecurityManager by lazy { MmqaSecurityManager(context) }
    private val syncMutex = Mutex()
    private val dbFile = File(context.filesDir, "mmqa_db_v3.json")
    private val mutationsFile = File(context.filesDir, "mmqa_mutations_v3.json")

    private val _database = MutableStateFlow(MmqaDefaults.createDefaultDatabase())
    val database: StateFlow<MmqaDatabase> = _database.asStateFlow()

    private val _syncState = MutableStateFlow<SyncBannerState>(SyncBannerState.Idle)
    val syncState: StateFlow<SyncBannerState> = _syncState.asStateFlow()

    private val _isOnlineFlow = MutableStateFlow(true)
    val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    private val _pendingMutationCount = MutableStateFlow(0)
    val pendingMutationCount: StateFlow<Int> = _pendingMutationCount.asStateFlow()

    private val _incomingChatAlerts = MutableSharedFlow<List<ChatMessageItem>>(extraBufferCapacity = 8)
    val incomingChatAlerts: SharedFlow<List<ChatMessageItem>> = _incomingChatAlerts.asSharedFlow()

    private val _realtimeAlerts = MutableSharedFlow<RealtimeAlertItem>(extraBufferCapacity = 12)
    val realtimeAlerts: SharedFlow<RealtimeAlertItem> = _realtimeAlerts.asSharedFlow()

    private val syncPrefs by lazy {
        context.getSharedPreferences("mmqa_sync_prefs", Context.MODE_PRIVATE)
    }

    private val _syncMode = MutableStateFlow(loadSyncMode())
    val syncMode: StateFlow<String> = _syncMode.asStateFlow()

    private val _activeBackendLabel = MutableStateFlow(
        when (loadSyncMode()) {
            SYNC_MODE_LOCAL_ONLY -> "Lokal AES-256"
            SYNC_MODE_SHEETS_ONLY -> "Google Sheets"
            else -> if (supabaseClient.isConfigured()) "Supabase" else "Google Sheets"
        }
    )
    val activeBackendLabel: StateFlow<String> = _activeBackendLabel.asStateFlow()

    private val _supabaseQuotaWarning = MutableStateFlow<String?>(null)
    val supabaseQuotaWarning: StateFlow<String?> = _supabaseQuotaWarning.asStateFlow()

    private var pendingMutations: MutableList<PendingMutation> = mutableListOf()
    @Volatile
    private var localDataLoaded = false
    @Volatile
    private var hasMutatedBeforeLoad = false
    private var lastKnownSupabaseRevision: String? = null
    private var supabaseBypassUntilMs: Long = 0L
    private var quotaAlertEmittedThisSession = false
    private var debounceSyncJob: Job? = null
    private var reconnectSyncJob: Job? = null
    private var sheetsMirrorJob: Job? = null
    private var activeActorName: String = "MMQA AR2 APK"
    private var networkCallbackRegistered = false

    private fun loadSyncMode(): String {
        val raw = runCatching { syncPrefs.getString("sync_mode", SYNC_MODE_AUTO) }.getOrNull()
        return when (raw) {
            SYNC_MODE_SHEETS_ONLY, SYNC_MODE_LOCAL_ONLY -> raw
            else -> SYNC_MODE_AUTO
        }
    }

    fun setSyncMode(mode: String) {
        val normalized = when (mode) {
            SYNC_MODE_SHEETS_ONLY, SYNC_MODE_LOCAL_ONLY -> mode
            else -> SYNC_MODE_AUTO
        }
        runCatching { syncPrefs.edit().putString("sync_mode", normalized).apply() }
        _syncMode.value = normalized
        if (normalized == SYNC_MODE_AUTO) {
            supabaseBypassUntilMs = 0L
            _supabaseQuotaWarning.value = null
            _activeBackendLabel.value = if (supabaseClient.isConfigured()) "Supabase" else "Google Sheets"
        } else if (normalized == SYNC_MODE_SHEETS_ONLY) {
            _activeBackendLabel.value = "Google Sheets"
        } else {
            _activeBackendLabel.value = "Lokal AES-256"
            _syncState.value = SyncBannerState.Synced(MmqaConstants.nowTimeHms(), "Lokal")
        }
    }

    fun resetSupabaseBypass() {
        supabaseBypassUntilMs = 0L
        _supabaseQuotaWarning.value = null
        quotaAlertEmittedThisSession = false
    }

    init {
        _isOnlineFlow.value = isNetworkAvailable()
        if (!_isOnlineFlow.value) {
            _syncState.value = SyncBannerState.Offline
        }
        registerNetworkCallback()
        scope.launch {
            ensureLocalDataLoaded()
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val wasOffline = !_isOnlineFlow.value
                    _isOnlineFlow.value = true
                    if (wasOffline || pendingMutations.isNotEmpty() || _database.value.pendingSync) {
                        reconnectSyncJob?.cancel()
                        reconnectSyncJob = scope.launch {
                            delay(1200L)
                            val hadPending = pendingMutations.isNotEmpty() || _database.value.pendingSync
                            val res = syncWithGoogleSheets(forcePushEvenIfNoMutations = false)
                            if (hadPending && res.isSuccess) {
                                _realtimeAlerts.tryEmit(
                                    RealtimeAlertItem(
                                        title = "✅ Sinkronisasi Offline Selesai",
                                        body = "Koneksi kembali stabil. Data lokal yang tersimpan saat offline berhasil disinkronkan ke Supabase.",
                                        category = "sync"
                                    )
                                )
                            }
                        }
                    }
                }

                override fun onLost(network: Network) {
                    val stillConnected = isNetworkAvailable()
                    _isOnlineFlow.value = stillConnected
                    if (!stillConnected) {
                        _syncState.value = SyncBannerState.Offline
                    }
                }
            })
            networkCallbackRegistered = true
        }
    }

    private suspend fun ensureLocalDataLoaded() {
        if (localDataLoaded) return
        syncMutex.withLock {
            if (localDataLoaded) return
            val loadedDb = loadLocalDatabaseFast()
            val loadedMutations = loadLocalMutations()
            pendingMutations = loadedMutations.toMutableList()
            _pendingMutationCount.value = pendingMutations.size
            if (!hasMutatedBeforeLoad) {
                _database.value = loadedDb
            }
            localDataLoaded = true
        }
    }

    fun setActiveActor(name: String) {
        activeActorName = name.ifBlank { "MMQA AR2 APK" }
    }

    fun emitRealtimeAlert(alert: RealtimeAlertItem) {
        _realtimeAlerts.tryEmit(alert)
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadLocalDatabaseFast(): MmqaDatabase {
        return try {
            if (dbFile.exists()) {
                val raw = securityManager.decrypt(dbFile.readText())
                if (raw.isNotBlank()) MmqaJsonCodec.decodeDatabase(raw) else MmqaDefaults.createDefaultDatabase()
            } else {
                MmqaDefaults.createDefaultDatabase()
            }
        } catch (_: Exception) {
            MmqaDefaults.createDefaultDatabase()
        }
    }

    private fun loadLocalMutations(): List<PendingMutation> {
        return try {
            if (mutationsFile.exists()) {
                val raw = securityManager.decrypt(mutationsFile.readText())
                if (raw.isNotBlank()) MmqaJsonCodec.decodeMutations(raw) else emptyList()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun persistLocalState(db: MmqaDatabase, mutations: List<PendingMutation>) = withContext(Dispatchers.IO) {
        _pendingMutationCount.value = mutations.size
        runCatching {
            dbFile.writeText(securityManager.encrypt(MmqaJsonCodec.encodeDatabase(db)))
            mutationsFile.writeText(securityManager.encrypt(MmqaJsonCodec.encodeMutations(mutations)))
        }
    }

    /**
     * Memperbarui state lokal secara instan (0ms), mencatat mutasi atomik per itemId
     * untuk 3-way merge multi-user, lalu menyinkronkan secara delta & cepat di background.
     */
    fun mutate(
        mutations: List<PendingMutation> = emptyList(),
        triggerRemoteSync: Boolean = true,
        transform: (MmqaDatabase) -> MmqaDatabase
    ) {
        hasMutatedBeforeLoad = true
        val updated = transform(_database.value).let {
            if (triggerRemoteSync) it.copy(pendingSync = true) else it
        }
        _database.value = updated

        scope.launch {
            ensureLocalDataLoaded()
            syncMutex.withLock {
                if (mutations.isNotEmpty()) {
                    mutations.forEach { m ->
                        pendingMutations.removeAll { it.collection == m.collection && it.itemId == m.itemId }
                        pendingMutations.add(m)
                    }
                }
                persistLocalState(updated, pendingMutations)
            }
            if (triggerRemoteSync) {
                scheduleDebouncedSync()
            }
        }
    }

    private fun scheduleDebouncedSync() {
        debounceSyncJob?.cancel()
        debounceSyncJob = scope.launch {
            delay(250L)
            syncWithGoogleSheets(forcePushEvenIfNoMutations = false)
        }
    }

    /**
     * Mirror database ke Google Sheets secara asinkron di background (non-blocking)
     * agar sinkronisasi utama Supabase selesai dalam <300ms.
     */
    private fun scheduleSheetsMirror(dbSnapshot: MmqaDatabase, gasUrl: String, sheetId: String, actor: String) {
        sheetsMirrorJob?.cancel()
        sheetsMirrorJob = scope.launch(Dispatchers.IO) {
            delay(1500L)
            runCatching {
                syncClient.pushSyncAll(gasUrl, sheetId, actor, MmqaJsonCodec.encodePayload(dbSnapshot))
            }
        }
    }

    fun isSupabaseConfigured(): Boolean = supabaseClient.isConfigured()

    fun getSupabaseStatus(): SupabaseStatus = supabaseClient.getStatusSummary()

    suspend fun syncWithGoogleSheets(forcePushEvenIfNoMutations: Boolean = false): Result<String> {
        ensureLocalDataLoaded()
        val mode = _syncMode.value
        if (mode == SYNC_MODE_LOCAL_ONLY) {
            val syncTime = MmqaConstants.nowTimeHms()
            val cur = _database.value
            if (cur.pendingSync || pendingMutations.isNotEmpty()) {
                pendingMutations.clear()
                val saved = cur.copy(lastSync = syncTime, pendingSync = false)
                _database.value = saved
                persistLocalState(saved, pendingMutations)
            }
            _activeBackendLabel.value = "Lokal AES-256"
            _syncState.value = SyncBannerState.Synced(syncTime, "Lokal")
            return Result.success("Tersimpan lokal terenkripsi pukul $syncTime")
        }

        if (!isNetworkAvailable()) {
            _syncState.value = SyncBannerState.Offline
            return Result.failure(IllegalStateException("Perangkat sedang offline. Data tersimpan lokal."))
        }

        return syncMutex.withLock {
            val currentLocal = _database.value
            val gasUrl = currentLocal.gasUrl.ifBlank { MmqaConstants.DEFAULT_GAS_WEBAPP_URL }
            val sheetId = currentLocal.sheetId.ifBlank { MmqaConstants.GOOGLE_SHEET_ID }
            val hasLocalChanges = pendingMutations.isNotEmpty() || currentLocal.pendingSync || forcePushEvenIfNoMutations
            val nowMs = System.currentTimeMillis()
            val supabaseBypassed = nowMs < supabaseBypassUntilMs && !forcePushEvenIfNoMutations
            var workingLocal = currentLocal
            var supabaseFallbackReason: String? = null

            // 0. Fast Multi-User Cloud Sync via Supabase PostgreSQL (only in AUTO mode & when not bypassed)
            if (mode == SYNC_MODE_AUTO && supabaseClient.isConfigured() && !supabaseBypassed) {
                // Fast revision check when idle & already synced once (~40 bytes)
                if (!hasLocalChanges && lastKnownSupabaseRevision != null) {
                    val revRes = supabaseClient.fetchRevision()
                    if (revRes.isSuccess) {
                        val remoteRev = revRes.getOrNull()
                        if (remoteRev != null && remoteRev == lastKnownSupabaseRevision) {
                            val syncTime = MmqaConstants.nowTimeHms()
                            _activeBackendLabel.value = "Supabase"
                            _supabaseQuotaWarning.value = null
                            _syncState.value = SyncBannerState.Synced(syncTime, "Cloud")
                            return@withLock Result.success("Tersinkron otomatis pukul $syncTime")
                        }
                    } else if (SupabaseSyncClient.isQuotaOrStorageFailure(revRes.exceptionOrNull())) {
                        supabaseFallbackReason = handleSupabaseQuotaFailure(revRes.exceptionOrNull())
                    }
                }

                // Fast delta push when we already have a synced baseline and remote hasn't changed
                if (supabaseFallbackReason == null && pendingMutations.isNotEmpty() && !forcePushEvenIfNoMutations && lastKnownSupabaseRevision != null) {
                    val revRes = supabaseClient.fetchRevision()
                    if (revRes.isSuccess && revRes.getOrNull() == lastKnownSupabaseRevision) {
                        _syncState.value = SyncBannerState.Syncing
                        val deltaPush = supabaseClient.pushMutations(workingLocal, pendingMutations.toList(), activeActorName)
                        if (deltaPush.isSuccess) {
                            pendingMutations.clear()
                            lastKnownSupabaseRevision = supabaseClient.fetchRevision().getOrNull() ?: lastKnownSupabaseRevision
                            val syncTime = MmqaConstants.nowTimeHms()
                            val finalDb = workingLocal.copy(lastSync = syncTime, pendingSync = false)
                            _database.value = finalDb
                            persistLocalState(finalDb, pendingMutations)
                            _activeBackendLabel.value = "Supabase"
                            _supabaseQuotaWarning.value = null
                            _syncState.value = SyncBannerState.Synced(syncTime, "Cloud")
                            scheduleSheetsMirror(finalDb, gasUrl, sheetId, activeActorName)
                            return@withLock Result.success("Tersinkron cepat pukul $syncTime")
                        } else {
                            supabaseFallbackReason = handleSupabaseWriteOrQuotaFailure(deltaPush.exceptionOrNull())
                        }
                    } else if (revRes.isFailure && SupabaseSyncClient.isQuotaOrStorageFailure(revRes.exceptionOrNull())) {
                        supabaseFallbackReason = handleSupabaseQuotaFailure(revRes.exceptionOrNull())
                    }
                }

                // Full pull + 3-way merge + delta push (or initial seed if remote empty)
                if (supabaseFallbackReason == null) {
                    _syncState.value = SyncBannerState.Syncing
                    val sbFetch = supabaseClient.fetchAll(
                        MmqaDefaults.createDefaultDatabase().copy(
                            gasUrl = gasUrl,
                            sheetId = sheetId,
                            lastSync = workingLocal.lastSync
                        )
                    )
                    if (sbFetch.isSuccess) {
                        val payload = sbFetch.getOrThrow()
                        val remoteDb = payload.database
                        val remoteWasEmpty = payload.recordCount == 0
                        val mergedDb = if (remoteWasEmpty) {
                            workingLocal
                        } else if (pendingMutations.isEmpty() && !forcePushEvenIfNoMutations) {
                            remoteDb
                        } else {
                            applyPendingMutationsOntoRemote(remoteDb, workingLocal, pendingMutations)
                        }

                        var newRevision = payload.revision
                        var writeFailure: Throwable? = null
                        val writeSucceeded: Boolean = if (remoteWasEmpty || forcePushEvenIfNoMutations) {
                            val pushSb = supabaseClient.pushAll(mergedDb, activeActorName)
                            if (pushSb.isSuccess) {
                                pendingMutations.clear()
                                newRevision = supabaseClient.fetchRevision().getOrNull() ?: newRevision
                                scheduleSheetsMirror(mergedDb, gasUrl, sheetId, activeActorName)
                                true
                            } else {
                                writeFailure = pushSb.exceptionOrNull()
                                false
                            }
                        } else if (pendingMutations.isNotEmpty()) {
                            val deltaPush = supabaseClient.pushMutations(mergedDb, pendingMutations.toList(), activeActorName)
                            if (deltaPush.isSuccess) {
                                pendingMutations.clear()
                                newRevision = supabaseClient.fetchRevision().getOrNull() ?: newRevision
                                scheduleSheetsMirror(mergedDb, gasUrl, sheetId, activeActorName)
                                true
                            } else {
                                writeFailure = deltaPush.exceptionOrNull()
                                false
                            }
                        } else if (workingLocal.pendingSync) {
                            val pushSb = supabaseClient.pushAll(mergedDb, activeActorName)
                            if (pushSb.isSuccess) {
                                newRevision = supabaseClient.fetchRevision().getOrNull() ?: newRevision
                                scheduleSheetsMirror(mergedDb, gasUrl, sheetId, activeActorName)
                                true
                            } else {
                                writeFailure = pushSb.exceptionOrNull()
                                false
                            }
                        } else {
                            true
                        }

                        if (writeSucceeded) {
                            lastKnownSupabaseRevision = newRevision
                            val syncTime = MmqaConstants.nowTimeHms()
                            val finalDb = mergedDb.copy(lastSync = syncTime, pendingSync = pendingMutations.isNotEmpty())
                            emitRemoteDiffNotifications(workingLocal, finalDb)
                            _database.value = finalDb
                            persistLocalState(finalDb, pendingMutations)
                            _activeBackendLabel.value = "Supabase"
                            _supabaseQuotaWarning.value = null
                            _syncState.value = SyncBannerState.Synced(syncTime, "Cloud")
                            return@withLock Result.success("Tersinkron pukul $syncTime")
                        } else {
                            // Keep merged pull safely in local database before falling back to Google Sheets
                            workingLocal = mergedDb.copy(pendingSync = true)
                            _database.value = workingLocal
                            persistLocalState(workingLocal, pendingMutations)
                            supabaseFallbackReason = handleSupabaseWriteOrQuotaFailure(writeFailure)
                        }
                    } else {
                        supabaseFallbackReason = handleSupabaseWriteOrQuotaFailure(sbFetch.exceptionOrNull())
                    }
                }
            }

            // 1. Fallback to Google Sheets GAS / Direct Read if Supabase unavailable, bypassed, or over quota
            _syncState.value = SyncBannerState.Syncing
            val fetchRes = when (val gasRes = syncClient.fetchAllFromGas(gasUrl, sheetId)) {
                is RemoteFetchResult.Success -> gasRes
                is RemoteFetchResult.Error -> {
                    val directRes = syncClient.fetchAllFromGoogleSheetDirect(sheetId)
                    if (directRes is RemoteFetchResult.Success) directRes else gasRes
                }
            }

            if (fetchRes is RemoteFetchResult.Error && !forcePushEvenIfNoMutations) {
                _syncState.value = SyncBannerState.Error(fetchRes.message)
                return@withLock Result.failure(IllegalStateException(fetchRes.message))
            }

            val remoteJson = (fetchRes as? RemoteFetchResult.Success)?.data
            val sourceLabel = (fetchRes as? RemoteFetchResult.Success)?.source ?: "Cloud"
            val remoteIsEmpty = remoteJson == null || MmqaJsonCodec.isRemoteDataEmpty(remoteJson)

            val mergedDb: MmqaDatabase = if (remoteIsEmpty) {
                workingLocal
            } else {
                val remoteDb = MmqaJsonCodec.mergeRemoteDataJson(
                    MmqaDefaults.createDefaultDatabase().copy(
                        gasUrl = gasUrl,
                        sheetId = sheetId,
                        lastSync = workingLocal.lastSync
                    ),
                    remoteJson!!
                )
                if (pendingMutations.isEmpty() && !forcePushEvenIfNoMutations) {
                    remoteDb
                } else {
                    applyPendingMutationsOntoRemote(remoteDb, workingLocal, pendingMutations)
                }
            }

            val shouldPush = remoteIsEmpty || pendingMutations.isNotEmpty() || workingLocal.pendingSync || forcePushEvenIfNoMutations
            if (shouldPush) {
                val payload = MmqaJsonCodec.encodePayload(mergedDb)
                val pushRes = syncClient.pushSyncAll(gasUrl, sheetId, activeActorName, payload)
                if (pushRes.isFailure) {
                    val kept = mergedDb.copy(pendingSync = true)
                    _database.value = kept
                    persistLocalState(kept, pendingMutations)
                    val errMsg = pushRes.exceptionOrNull()?.message ?: "Gagal menyinkronkan data"
                    _syncState.value = SyncBannerState.Error(errMsg)
                    return@withLock Result.failure(IllegalStateException(errMsg))
                }
                pendingMutations.clear()
            }

            val syncTime = MmqaConstants.nowTimeHms()
            val finalDb = mergedDb.copy(lastSync = syncTime, pendingSync = false)
            emitRemoteDiffNotifications(currentLocal, finalDb)

            _database.value = finalDb
            persistLocalState(finalDb, pendingMutations)
            val backendDisplay = if (mode == SYNC_MODE_AUTO && supabaseClient.isConfigured()) {
                "Google Sheets (Cadangan)"
            } else {
                "Google Sheets"
            }
            _activeBackendLabel.value = backendDisplay
            _syncState.value = SyncBannerState.Synced(syncTime, sourceLabel)
            val suffix = if (supabaseFallbackReason != null) " via Google Sheets ($supabaseFallbackReason)" else " via $sourceLabel"
            Result.success("Tersinkron pukul $syncTime$suffix")
        }
    }

    private fun handleSupabaseQuotaFailure(error: Throwable?): String {
        supabaseBypassUntilMs = System.currentTimeMillis() + 10 * 60_000L
        val detail = error?.message?.take(80).orEmpty()
        val warningMsg = "Penyimpanan/kuota Supabase penuh atau dijeda ($detail). APK otomatis beralih ke Google Sheets & Lokal AES-256."
        _supabaseQuotaWarning.value = warningMsg
        _activeBackendLabel.value = "Google Sheets (Cadangan)"
        if (!quotaAlertEmittedThisSession) {
            quotaAlertEmittedThisSession = true
            _realtimeAlerts.tryEmit(
                RealtimeAlertItem(
                    title = "⚠️ Kuota Supabase Penuh — Beralih ke Cadangan",
                    body = "Aplikasi tetap berjalan normal. Data tersimpan lokal AES-256 dan dialihkan ke Google Sheets.",
                    category = "sync"
                )
            )
        }
        return "Kuota Supabase Penuh"
    }

    private fun handleSupabaseWriteOrQuotaFailure(error: Throwable?): String {
        return if (SupabaseSyncClient.isQuotaOrStorageFailure(error)) {
            handleSupabaseQuotaFailure(error)
        } else {
            _activeBackendLabel.value = "Google Sheets (Cadangan)"
            "Supabase tidak terjangkau"
        }
    }

    private fun emitRemoteDiffNotifications(previous: MmqaDatabase, updated: MmqaDatabase) {
        val existingIds = previous.chatMessages.map { it.id }.toSet()
        val newChats = updated.chatMessages.filter { it.id !in existingIds }
        if (newChats.isNotEmpty()) _incomingChatAlerts.tryEmit(newChats)

        val existingJuknisIds = previous.juknisDocs.map { it.id }.toSet()
        updated.juknisDocs.filter { it.id !in existingJuknisIds }.takeLast(2).forEach { doc ->
            _realtimeAlerts.tryEmit(
                RealtimeAlertItem(
                    title = "📘 Juknis / Rapat Baru: ${doc.kategori}",
                    body = doc.judul,
                    category = "juknis"
                )
            )
        }
        val existingJadwalIds = previous.jadwal.map { it.id }.toSet()
        updated.jadwal.filter { it.id !in existingJadwalIds }.takeLast(2).forEach { jdw ->
            _realtimeAlerts.tryEmit(
                RealtimeAlertItem(
                    title = "🗓️ Jadwal Baru: ${jdw.kategori}",
                    body = "${jdw.judul} (${jdw.hari}, ${jdw.jamMulai}-${jdw.jamSelesai} di ${jdw.tempat})",
                    category = "jadwal"
                )
            )
        }
    }

    fun getStorageFootprint(): StorageFootprintSummary =
        SupabaseSyncClient.estimateStorageFootprint(_database.value)

    fun exportDatabaseJson(): String = MmqaJsonCodec.encodeDatabase(_database.value)

    suspend fun importDatabaseJson(rawJson: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = MmqaJsonCodec.decodeDatabase(rawJson)
            if (decoded.users.isEmpty() && decoded.firqoh.isEmpty() && decoded.santri.isEmpty()) {
                throw IllegalArgumentException("File JSON tidak berisi data MMQA yang valid.")
            }
            val nowTime = MmqaConstants.nowTimeHms()
            val prepared = decoded.copy(
                lastSync = nowTime,
                pendingSync = _syncMode.value != SYNC_MODE_LOCAL_ONLY
            )
            syncMutex.withLock {
                pendingMutations.clear()
                _database.value = prepared
                persistLocalState(prepared, pendingMutations)
            }
            prepared.santri.size + prepared.absensi.size + prepared.firqoh.size
        }
    }

    fun clearChatAttachments(): Int {
        var clearedCount = 0
        val targetIds = _database.value.chatMessages
            .filter { it.attachmentDataUrl.isNotBlank() }
            .map { it.id }
        if (targetIds.isEmpty()) return 0
        val mutations = targetIds.map { PendingMutation("chatMessages", it, "upsert") }
        mutate(mutations) { db ->
            val updated = db.chatMessages.map { msg ->
                if (msg.attachmentDataUrl.isNotBlank()) {
                    clearedCount++
                    val note = if (msg.attachmentName.isNotBlank()) {
                        if (msg.text.isBlank()) "[Lampiran dibersihkan: ${msg.attachmentName}]" else msg.text
                    } else msg.text
                    msg.copy(
                        text = note,
                        attachmentDataUrl = "",
                        attachmentSize = 0L
                    )
                } else msg
            }
            db.copy(chatMessages = updated)
        }
        return clearedCount
    }

    companion object {
        const val SYNC_MODE_AUTO = "AUTO"
        const val SYNC_MODE_SHEETS_ONLY = "SHEETS_ONLY"
        const val SYNC_MODE_LOCAL_ONLY = "LOCAL_ONLY"
    }

    private fun applyPendingMutationsOntoRemote(
        remote: MmqaDatabase,
        local: MmqaDatabase,
        mutations: List<PendingMutation>
    ): MmqaDatabase {
        var acc = remote
        for (m in mutations) {
            acc = when (m.collection) {
                "users" -> acc.copy(users = mergeCollection(acc.users, local.users, m) { it.id })
                "firqoh" -> acc.copy(firqoh = mergeCollection(acc.firqoh, local.firqoh, m) { it.id })
                "santri" -> acc.copy(santri = mergeCollection(acc.santri, local.santri, m) { it.id })
                "muallim" -> acc.copy(muallim = mergeCollection(acc.muallim, local.muallim, m) { it.id })
                "dewanHarian" -> acc.copy(dewanHarian = mergeCollection(acc.dewanHarian, local.dewanHarian, m) { it.id })
                "absensi" -> acc.copy(absensi = mergeCollection(acc.absensi, local.absensi, m) { it.id })
                "materi" -> acc.copy(materi = mergeCollection(acc.materi, local.materi, m) { it.id })
                "ujianMateri" -> acc.copy(ujianMateri = mergeCollection(acc.ujianMateri, local.ujianMateri, m) { it.id })
                "ujianFormat" -> acc.copy(ujianFormat = mergeCollection(acc.ujianFormat, local.ujianFormat, m) { it.id })
                "nilaiUjian" -> acc.copy(nilaiUjian = mergeCollection(acc.nilaiUjian, local.nilaiUjian, m) { it.id })
                "keuangan" -> acc.copy(keuangan = mergeCollection(acc.keuangan, local.keuangan, m) { it.id })
                "suratTemplates" -> acc.copy(suratTemplates = mergeCollection(acc.suratTemplates, local.suratTemplates, m) { it.id })
                "suratArsip" -> acc.copy(suratArsip = mergeCollection(acc.suratArsip, local.suratArsip, m) { it.id })
                "inventaris" -> acc.copy(inventaris = mergeCollection(acc.inventaris, local.inventaris, m) { it.id })
                "personalia" -> acc.copy(personalia = mergeCollection(acc.personalia, local.personalia, m) { it.id })
                "juknisDocs" -> acc.copy(juknisDocs = mergeCollection(acc.juknisDocs, local.juknisDocs, m) { it.id })
                "jadwal" -> acc.copy(jadwal = mergeCollection(acc.jadwal, local.jadwal, m) { it.id })
                "chatChannels" -> acc.copy(chatChannels = mergeCollection(acc.chatChannels, local.chatChannels, m) { it.id })
                "chatMessages" -> acc.copy(chatMessages = mergeCollection(acc.chatMessages, local.chatMessages, m) { it.id })
                "adminConfig" -> acc.copy(adminConfig = local.adminConfig)
                else -> acc
            }
        }
        return acc
    }

    private inline fun <T> mergeCollection(
        remoteList: List<T>,
        localList: List<T>,
        mutation: PendingMutation,
        crossinline idOf: (T) -> String
    ): List<T> {
        val mutable = remoteList.toMutableList()
        if (mutation.op == "delete") {
            mutable.removeAll { idOf(it) == mutation.itemId }
        } else {
            val localItem = localList.firstOrNull { idOf(it) == mutation.itemId }
            if (localItem != null) {
                val idx = mutable.indexOfFirst { idOf(it) == mutation.itemId }
                if (idx >= 0) mutable[idx] = localItem else mutable.add(localItem)
            }
        }
        return mutable
    }
}
