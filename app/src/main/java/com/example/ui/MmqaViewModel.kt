package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

data class AttendanceStats(
    val hadir: Int,
    val izin: Int,
    val sakit: Int,
    val alpha: Int,
    val total: Int,
    val pct: Int
)

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isError: Boolean = false
)

data class DetailedAbsensiLogUi(
    val record: AbsensiRecord,
    val nama: String,
    val nomorIdentitas: String,
    val firqohName: String,
    val kontakWa: String,
    val waliOrBidang: String
)

data class MmqaUiState(
    val currentUser: UserAccount? = null,
    val activeMenu: String = "dashboard",
    val loginError: String? = null,
    val dashFirqohFilter: String = "ALL",
    val dashPeriodeFilter: String = "ALL",
    val dashPeriodeRefDate: String = MmqaConstants.todayStr(),
    val absensiTab: String = "santri",
    val absensiTanggal: String = MmqaConstants.todayStr(),
    val absensiSesi: String = "Pagi",
    val absensiFirqoh: String = "FRQ-1",
    val absensiRekapPeriode: String = "HARIAN",
    val absensiRekapRefDate: String = MmqaConstants.todayStr(),
    val materiFirqoh: String = "ALL",
    val ujianTab: String = "peserta",
    val ujianFirqoh: String = "ALL",
    val keuanganTab: String = "kas",
    val keuanganFilter: String = "ALL",
    val syahriahFirqoh: String = "ALL",
    val suratTab: String = "buat",
    val suratArsipKode: String = "ALL",
    val suratDraftKode: String = "A",
    val suratDraftTemplateId: String = "TPL-A1",
    val suratDraftNomor: String = "002/A/MMQA-AR2/XI/2026",
    val suratDraftTanggal: String = MmqaConstants.todayStr(),
    val suratDraftLampiran: String = "-",
    val suratDraftPerihal: String = "Undangan Rapat Koordinasi Evaluasi KBM & Persiapan Ujian",
    val suratDraftTujuan: String = "Yth. Seluruh Mu’allim Firqoh MMQA AR2",
    val suratDraftIsi: String = "Assalamu’alaikum Warahmatullahi Wabarakatuh.\n\nDengan memohon rahmat dan ridho Allah SWT, mengharap dengan hormat kehadiran Bapak/Ustadz pada rapat evaluasi KBM di Majelis Utama MMQA Al Rifai 02.\n\nWassalamu’alaikum Warahmatullahi Wabarakatuh.",
    val suratDraftPenandatangan: String = "Ust. H. Ahmad Fauzi, M.Pd",
    val suratDraftJabatan: String = "Ketua Dewan Harian MMQA",
    val suratDraftSekretaris: String = "Ust. Muhammad Ridwan",
    val suratDraftJabatanSekretaris: String = "Sekretaris Umum MMQA",
    val suratDraftTampilkanSekretaris: Boolean = true,
    val suratDraftTtdKetua: String = "",
    val suratDraftTtdSekretaris: String = "",
    val suratDraftKop1: String = "MAJELIS MUSYAWARAH QIRA'ATIL KUTUB & AL-QUR'AN",
    val suratDraftKop2: String = "MMQA AL RIFAI 02 (AR2)",
    val suratDraftKop3: String = "Sekretariat Dewan Harian Majelis • Sistem Administrasi Terpadu",
    val suratDraftKota: String = "Malang",
    val suratDraftFontStyle: String = "Times New Roman (Resmi)",
    val suratDraftUkuranKertas: String = "A4",
    val suratDraftMarginMm: Int = 20,
    val suratDraftPedoman: String = "Format resmi A4 margin 20mm, Times New Roman. Wajib mencantumkan nomor Kode A, rincian Hari/Tanggal/Waktu/Tempat/Acara, serta TTD Ketua & Sekretaris berdampingan.",
    val inventarisFilter: String = "ALL",
    val indukTab: String = "santri",
    val indukSearch: String = "",
    val santriIndukFirqoh: String = "ALL",
    val chatChannel: String = "CHN-UMUM",
    val chatSearch: String = "",
    val chatReplyTo: ChatReplyRef? = null,
    val adminTab: String = "menus"
)

class MmqaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MmqaRepository(application.applicationContext)

    val database: StateFlow<MmqaDatabase> = repository.database
    val syncState: StateFlow<SyncBannerState> = repository.syncState
    val isOnlineFlow: StateFlow<Boolean> = repository.isOnlineFlow
    val pendingMutationCount: StateFlow<Int> = repository.pendingMutationCount
    val incomingChatAlerts: SharedFlow<List<ChatMessageItem>> = repository.incomingChatAlerts
    val realtimeAlerts: SharedFlow<RealtimeAlertItem> = repository.realtimeAlerts

    private val _uiState = MutableStateFlow(MmqaUiState())
    val uiState: StateFlow<MmqaUiState> = _uiState.asStateFlow()

    private val _toastFlow = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 8)
    val toastFlow: SharedFlow<ToastMessage> = _toastFlow.asSharedFlow()

    private val _supabaseStatus = MutableStateFlow(repository.getSupabaseStatus())
    val supabaseStatus: StateFlow<SupabaseStatus> = _supabaseStatus.asStateFlow()

    private val _securitySettings = MutableStateFlow(repository.securityManager.loadSettings())
    val securitySettings: StateFlow<SecuritySettings> = _securitySettings.asStateFlow()

    private val _isAppLocked = MutableStateFlow(_securitySettings.value.biometricLockEnabled)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    init {
        // Keep currentUser synchronized whenever remote sync updates users (profile / role / access changes)
        viewModelScope.launch {
            repository.database.collect { db ->
                val cur = _uiState.value.currentUser ?: return@collect
                val fresh = db.users.firstOrNull { it.id == cur.id }
                if (fresh != null && fresh != cur) {
                    repository.setActiveActor(fresh.name)
                    _uiState.update { it.copy(currentUser = fresh) }
                }
            }
        }

        // Fast adaptive background auto-sync (4s in Chat, 8s in other menus)
        viewModelScope.launch {
            delay(350L)
            repository.syncWithGoogleSheets(forcePushEvenIfNoMutations = false)
            while (isActive) {
                val intervalMs = if (_uiState.value.activeMenu == "chat") 4_000L else 8_000L
                delay(intervalMs)
                if (repository.isNetworkAvailable()) {
                    repository.syncWithGoogleSheets(forcePushEvenIfNoMutations = false)
                }
            }
        }
    }

    fun showToast(text: String, isError: Boolean = false) {
        _toastFlow.tryEmit(ToastMessage(System.currentTimeMillis(), text, isError))
    }

    fun isOnline(): Boolean = repository.isNetworkAvailable()

    // =========================================================================
    // SYNC
    // =========================================================================
    fun syncNow(manual: Boolean = true, forcePush: Boolean = false) {
        viewModelScope.launch {
            if (manual) showToast("Menyinkronkan data...")
            val res = repository.syncWithGoogleSheets(forcePushEvenIfNoMutations = forcePush)
            if (manual) {
                res.onSuccess { msg -> showToast(msg) }
                    .onFailure { err -> showToast(err.message ?: "Gagal sinkronisasi", isError = true) }
            }
        }
    }

    fun isSupabaseConfigured(): Boolean = repository.isSupabaseConfigured()

    // =========================================================================
    // AUTHENTICATION & PASSWORD RULE
    // =========================================================================
    fun getPasswordRuleSummary(): String {
        val r = database.value.adminConfig.passwordRule
        val parts = mutableListOf("Min. ${r.minLength} karakter")
        if (r.requireUppercase) parts.add("Huruf Kapital (A-Z)")
        if (r.requireNumber) parts.add("Angka (0-9)")
        if (r.requireSpecial) parts.add("Simbol (!@#$...)")
        return parts.joinToString(" • ")
    }

    fun validatePasswordAgainstRule(pwd: String): String? {
        val r = database.value.adminConfig.passwordRule
        if (pwd.length < r.minLength) return "Password minimal harus ${r.minLength} karakter."
        if (r.requireUppercase && !pwd.any { it.isUpperCase() }) return "Password wajib mengandung minimal 1 huruf kapital (A-Z)."
        if (r.requireNumber && !pwd.any { it.isDigit() }) return "Password wajib mengandung minimal 1 angka (0-9)."
        if (r.requireSpecial && !pwd.any { !it.isLetterOrDigit() }) return "Password wajib mengandung minimal 1 karakter spesial (!@#$%^&*)."
        return null
    }

    fun login(usernameInput: String, passwordInput: String) {
        val username = usernameInput.trim()
        val db = database.value
        val user = db.users.firstOrNull { it.username.equals(username, ignoreCase = true) }
        if (user == null) {
            _uiState.update { it.copy(loginError = "Username tidak ditemukan. Periksa kembali akun Anda.") }
            return
        }

        val now = System.currentTimeMillis()
        if (user.lockedUntil != null && now < user.lockedUntil) {
            val sisaDetik = ((user.lockedUntil - now) / 1000L).coerceAtLeast(1L)
            _uiState.update {
                it.copy(loginError = "Akun terkunci sementara karena batas percobaan login gagal. Coba lagi dalam $sisaDetik detik.")
            }
            return
        }

        if (user.password != passwordInput) {
            val maxAttempts = db.adminConfig.passwordRule.maxLoginAttempts
            val nextAttempts = user.failedAttempts + 1
            if (maxAttempts > 0 && nextAttempts >= maxAttempts) {
                val lockedUser = user.copy(failedAttempts = 0, lockedUntil = now + 60_000L)
                repository.mutate(triggerRemoteSync = false) { cur ->
                    cur.copy(users = cur.users.map { if (it.id == user.id) lockedUser else it })
                }
                _uiState.update { it.copy(loginError = "Password salah ${maxAttempts}x. Akun dikunci selama 60 detik.") }
            } else {
                val updatedUser = user.copy(failedAttempts = nextAttempts)
                repository.mutate(triggerRemoteSync = false) { cur ->
                    cur.copy(users = cur.users.map { if (it.id == user.id) updatedUser else it })
                }
                val limitSuffix = if (maxAttempts > 0) " dari $maxAttempts" else ""
                _uiState.update { it.copy(loginError = "Password salah! (Percobaan ke-$nextAttempts$limitSuffix)") }
            }
            return
        }

        val cleanUser = user.copy(failedAttempts = 0, lockedUntil = null)
        repository.mutate(triggerRemoteSync = false) { cur ->
            cur.copy(users = cur.users.map { if (it.id == user.id) cleanUser else it })
        }
        enterApp(cleanUser)
    }

    private fun enterApp(user: UserAccount) {
        repository.setActiveActor(user.name)
        val curSec = _securitySettings.value
        if (curSec.savedUsername != user.username) {
            val updatedSec = curSec.copy(savedUsername = user.username)
            repository.securityManager.saveSettings(updatedSec)
            _securitySettings.value = updatedSec
        }
        _uiState.update { state ->
            val resolvedFirqoh = when (user.role) {
                "wali_santri" -> user.firqohId.ifBlank {
                    database.value.santri.firstOrNull { it.id == user.santriId }?.firqohId ?: "ALL"
                }
                "muallim" -> if (user.firqohId.isNotBlank()) user.firqohId else state.absensiFirqoh
                else -> "ALL"
            }
            state.copy(
                currentUser = user,
                loginError = null,
                activeMenu = "dashboard",
                dashFirqohFilter = if (user.role == "wali_santri") resolvedFirqoh else "ALL",
                absensiTab = if (user.role == "wali_santri") "santri" else state.absensiTab,
                absensiFirqoh = resolvedFirqoh
            )
        }
        showToast("Selamat datang, ${user.name}")
    }

    fun logout() {
        repository.setActiveActor("MMQA AR2 APK")
        _uiState.update { it.copy(currentUser = null, loginError = null) }
        showToast("Anda telah keluar (Logout).")
    }

    fun updateOwnProfile(newName: String, newAvatarBase64: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            showToast("Nama lengkap tidak boleh kosong!", isError = true)
            return false
        }
        val curUser = _uiState.value.currentUser ?: return false
        val userObj = database.value.users.firstOrNull { it.id == curUser.id } ?: curUser
        val updated = userObj.copy(name = cleanName, avatarBase64 = newAvatarBase64)
        repository.setActiveActor(cleanName)
        repository.mutate(listOf(PendingMutation("users", updated.id, "upsert"))) { db ->
            db.copy(users = db.users.map { if (it.id == updated.id) updated else it })
        }
        _uiState.update { it.copy(currentUser = updated) }
        showToast("Profil berhasil diperbarui & disinkronkan!")
        return true
    }

    fun changeOwnPassword(oldPwd: String, newPwd: String, confirmPwd: String): Boolean {
        val curUser = _uiState.value.currentUser ?: return false
        val userObj = database.value.users.firstOrNull { it.id == curUser.id } ?: return false
        if (userObj.password != oldPwd) {
            showToast("Password lama Anda tidak sesuai!", isError = true)
            return false
        }
        if (newPwd != confirmPwd) {
            showToast("Konfirmasi password baru tidak cocok!", isError = true)
            return false
        }
        val err = validatePasswordAgainstRule(newPwd)
        if (err != null) {
            showToast(err, isError = true)
            return false
        }
        val updated = userObj.copy(password = newPwd, passwordUpdatedAt = MmqaConstants.todayStr())
        repository.mutate(listOf(PendingMutation("users", updated.id, "upsert"))) { db ->
            db.copy(users = db.users.map { if (it.id == updated.id) updated else it })
        }
        _uiState.update { it.copy(currentUser = updated) }
        showToast("Password akun berhasil diperbarui & disinkronkan!")
        return true
    }

    // =========================================================================
    // 3-LEVEL ACCESS CONTROL (MENU -> SUBMENU -> ANAK SUB + PER-USER OVERRIDES)
    // =========================================================================
    private fun matchesRole(allowedRole: String, userRole: String): Boolean {
        return MmqaConstants.matchesRoleAccess(allowedRole, userRole)
    }

    fun getAccessibleMenus(): List<MenuConfigItem> {
        val user = _uiState.value.currentUser ?: return emptyList()
        return database.value.adminConfig.menus
            .filter { menu ->
                if (menu.id == "admin") {
                    user.role == "dewan_harian" && menu.id !in user.deniedMenus
                } else {
                    menu.enabled &&
                        matchesRole(menu.roles, user.role) &&
                        menu.id !in user.deniedMenus
                }
            }
            .sortedBy { it.order }
    }

    fun getAccessibleSubmenus(groupKey: String): List<SubmenuConfigItem> {
        val user = _uiState.value.currentUser ?: return emptyList()
        val sg = database.value.adminConfig.submenus
        val raw = when (groupKey) {
            "absensi" -> sg.absensi
            "ujian" -> sg.ujian
            "keuangan" -> sg.keuangan
            "surat" -> sg.surat
            "induk" -> sg.induk
            else -> emptyList()
        }
        return raw.filter { sub ->
            sub.enabled &&
                matchesRole(sub.roles, user.role) &&
                "$groupKey:${sub.id}" !in user.deniedSubmenus
        }
    }

    fun canAccessAnakSub(anakSubId: String): Boolean {
        val user = _uiState.value.currentUser ?: return false
        val item = database.value.adminConfig.anakSub.firstOrNull { it.id == anakSubId }
            ?: return user.role == "dewan_harian"
        return item.enabled &&
            matchesRole(item.roles, user.role) &&
            anakSubId !in user.deniedAnakSub
    }

    fun selectMenu(menuId: String) {
        _uiState.update { it.copy(activeMenu = menuId) }
    }

    fun updateState(transform: (MmqaUiState) -> MmqaUiState) {
        _uiState.update(transform)
    }

    fun getFirqohName(firqohId: String): String {
        if (firqohId.isBlank() || firqohId == "ALL") return "Semua Firqoh"
        if (firqohId.startsWith(FirqohFilterHelper.GROUP_PREFIX)) {
            return FirqohFilterHelper.getDisplayLabel(firqohId, database.value.firqoh)
        }
        return database.value.firqoh.firstOrNull { it.id == firqohId }?.nama ?: firqohId
    }

    fun buildFirqohFilterOptions(
        includeAll: Boolean = true,
        countBySantri: Boolean = false
    ): List<Pair<String, String>> {
        val db = database.value
        return FirqohFilterHelper.buildFilterOptions(
            firqohList = db.firqoh,
            santriList = db.santri,
            includeAll = includeAll,
            countBySantri = countBySantri
        )
    }

    fun matchesFirqohFilter(firqohId: String, filter: String): Boolean =
        FirqohFilterHelper.matchesFilter(firqohId, filter, database.value.firqoh)

    fun getFirqohFilterDisplayLabel(filter: String): String =
        FirqohFilterHelper.getDisplayLabel(filter, database.value.firqoh)

    // =========================================================================
    // DASHBOARD CALCULATIONS
    // =========================================================================
    fun calcAttendanceStats(tipe: String, targetId: String? = null, firqohId: String? = null): AttendanceStats {
        var logs = database.value.absensi.filter { it.tipe == tipe }
        if (targetId != null) logs = logs.filter { it.targetId == targetId }
        if (firqohId != null && firqohId != "ALL") {
            logs = logs.filter { matchesFirqohFilter(it.firqohId, firqohId) }
        }
        val hadir = logs.count { it.status == "Hadir" }
        val izin = logs.count { it.status == "Izin" }
        val sakit = logs.count { it.status == "Sakit" }
        val alpha = logs.count { it.status == "Alpha" }
        val total = logs.size
        val pct = if (total > 0) ((hadir.toDouble() / total) * 100.0).toInt().let {
            kotlin.math.round((hadir.toDouble() / total) * 100.0).toInt()
        } else 0
        return AttendanceStats(hadir, izin, sakit, alpha, total, pct)
    }

    fun calcAttendanceStatsByPeriod(
        tipe: String,
        targetId: String? = null,
        firqohId: String? = null,
        periodFilter: String = "ALL",
        referenceDate: String = MmqaConstants.todayStr()
    ): AttendanceStats {
        if (periodFilter == "ALL" || periodFilter.isBlank()) {
            return calcAttendanceStats(tipe, targetId, firqohId)
        }
        var logs = database.value.absensi.filter {
            it.tipe == tipe && AttendancePeriodHelper.matchesPeriod(it.tanggal, periodFilter, referenceDate)
        }
        if (targetId != null) logs = logs.filter { it.targetId == targetId }
        if (firqohId != null && firqohId != "ALL") {
            logs = logs.filter { matchesFirqohFilter(it.firqohId, firqohId) }
        }
        val hadir = logs.count { it.status == "Hadir" }
        val izin = logs.count { it.status == "Izin" }
        val sakit = logs.count { it.status == "Sakit" }
        val alpha = logs.count { it.status == "Alpha" }
        val total = logs.size
        val pct = if (total > 0) kotlin.math.round((hadir.toDouble() / total) * 100.0).toInt() else 0
        return AttendanceStats(hadir, izin, sakit, alpha, total, pct)
    }

    fun countLateAttendance(
        tipe: String,
        targetId: String? = null,
        firqohId: String? = null,
        periodFilter: String = "ALL",
        referenceDate: String = MmqaConstants.todayStr()
    ): Int {
        var logs = database.value.absensi.filter {
            it.tipe == tipe && it.terlambat && it.status == "Hadir"
        }
        if (periodFilter != "ALL" && periodFilter.isNotBlank()) {
            logs = logs.filter { AttendancePeriodHelper.matchesPeriod(it.tanggal, periodFilter, referenceDate) }
        }
        if (targetId != null) logs = logs.filter { it.targetId == targetId }
        if (firqohId != null && firqohId != "ALL") {
            logs = logs.filter { matchesFirqohFilter(it.firqohId, firqohId) }
        }
        return logs.size
    }

    fun getDetailedAbsensiLogs(
        tipe: String,
        firqohFilter: String = "ALL",
        periodFilter: String = "HARIAN",
        referenceDate: String = MmqaConstants.todayStr(),
        onlyLate: Boolean = false
    ): List<DetailedAbsensiLogUi> {
        val db = database.value
        val santriMap = db.santri.associateBy { it.id }
        val muallimMap = db.muallim.associateBy { it.id }
        val dewanMap = db.dewanHarian.associateBy { it.id }

        return db.absensi
            .asSequence()
            .filter { it.tipe == tipe }
            .filter { AttendancePeriodHelper.matchesPeriod(it.tanggal, periodFilter, referenceDate) }
            .filter { rec ->
                if (tipe == "santri" && firqohFilter != "ALL") {
                    matchesFirqohFilter(rec.firqohId, firqohFilter)
                } else {
                    true
                }
            }
            .filter { !onlyLate || (it.terlambat && it.status == "Hadir") }
            .map { rec ->
                when (tipe) {
                    "santri" -> {
                        val s = santriMap[rec.targetId]
                        DetailedAbsensiLogUi(
                            record = rec,
                            nama = s?.nama ?: rec.targetId,
                            nomorIdentitas = s?.nis?.let { "NIS $it" } ?: rec.targetId,
                            firqohName = getFirqohName(s?.firqohId ?: rec.firqohId),
                            kontakWa = s?.hpWali ?: "",
                            waliOrBidang = s?.wali?.let { "Wali: $it" } ?: ""
                        )
                    }
                    "muallim" -> {
                        val m = muallimMap[rec.targetId]
                        DetailedAbsensiLogUi(
                            record = rec,
                            nama = m?.nama ?: rec.targetId,
                            nomorIdentitas = m?.niy?.let { "NIY $it" } ?: rec.targetId,
                            firqohName = getFirqohName(m?.firqohId ?: rec.firqohId),
                            kontakWa = m?.hp ?: "",
                            waliOrBidang = m?.bidang ?: ""
                        )
                    }
                    else -> {
                        val d = dewanMap[rec.targetId]
                        DetailedAbsensiLogUi(
                            record = rec,
                            nama = d?.nama ?: rec.targetId,
                            nomorIdentitas = d?.kode?.let { "Kode $it" } ?: rec.targetId,
                            firqohName = "Dewan Harian MMQA",
                            kontakWa = d?.hp ?: "",
                            waliOrBidang = d?.jabatan ?: ""
                        )
                    }
                }
            }
            .sortedWith(
                compareByDescending<DetailedAbsensiLogUi> { it.record.tanggal }
                    .thenByDescending { it.record.waktuScan }
                    .thenBy { it.nama }
            )
            .toList()
    }

    // =========================================================================
    // ABSENSI ACTIONS
    // =========================================================================
    fun getAbsensiRecord(tipe: String, targetId: String): AbsensiRecord? {
        val st = _uiState.value
        return database.value.absensi.firstOrNull {
            it.tipe == tipe && it.targetId == targetId && it.tanggal == st.absensiTanggal && it.sesi == st.absensiSesi
        }
    }

    fun setAbsensiStatus(
        tipe: String,
        targetId: String,
        firqohId: String,
        status: String,
        customCutoffHm: String? = null
    ) {
        val st = _uiState.value
        val dbSnapshot = database.value
        val existing = getAbsensiRecord(tipe, targetId)
        val recId = existing?.id ?: "ABS-${System.currentTimeMillis()}-${(100..999).random()}"
        val actor = st.currentUser?.name ?: "Pengurus MMQA"
        val nowHms = MmqaConstants.nowTimeHms()
        val cutoffHm = customCutoffHm?.takeIf { AttendanceTimeEvaluator.parseMinutesOfDay(it) != null }
            ?.trim()?.replace(".", ":")
            ?: AttendanceTimeEvaluator.defaultCutoffForSession(st.absensiSesi, dbSnapshot.jadwal, dbSnapshot.adminConfig.permissions)
        val isToday = st.absensiTanggal == MmqaConstants.todayStr()
        val timing = if (status == "Hadir" && isToday) {
            AttendanceTimeEvaluator.evaluateScanTiming(nowHms, cutoffHm, "Hadir")
        } else {
            ScanTimingResult(waktuScan = nowHms, batasWaktu = cutoffHm, terlambat = false, menitTelat = 0)
        }

        val updatedRec = if (existing != null) {
            val useExistingTiming = status == "Hadir" && existing.status == "Hadir" && existing.waktuScan.isNotBlank()
            existing.copy(
                status = status,
                waktuScan = if (useExistingTiming) existing.waktuScan else timing.waktuScan,
                metode = existing.metode.ifBlank { "Input Manual" },
                terlambat = when {
                    status != "Hadir" -> false
                    useExistingTiming -> existing.terlambat
                    else -> timing.terlambat
                },
                menitTelat = when {
                    status != "Hadir" -> 0
                    useExistingTiming -> existing.menitTelat
                    else -> timing.menitTelat
                },
                batasWaktu = if (useExistingTiming && existing.batasWaktu.isNotBlank()) existing.batasWaktu else cutoffHm,
                petugasScan = existing.petugasScan.ifBlank { actor }
            )
        } else {
            AbsensiRecord(
                id = recId,
                tanggal = st.absensiTanggal,
                sesi = st.absensiSesi,
                tipe = tipe,
                targetId = targetId,
                firqohId = firqohId,
                status = status,
                catatan = "",
                waktuScan = timing.waktuScan,
                metode = "Input Manual",
                terlambat = timing.terlambat,
                menitTelat = timing.menitTelat,
                batasWaktu = timing.batasWaktu,
                petugasScan = actor
            )
        }
        repository.mutate(listOf(PendingMutation("absensi", recId, "upsert"))) { db ->
            val list = db.absensi.toMutableList()
            val idx = list.indexOfFirst { it.id == recId }
            if (idx >= 0) list[idx] = updatedRec else list.add(updatedRec)
            db.copy(absensi = list)
        }
    }

    fun saveSessionCutoff(sesi: String, cutoffHm: String): Boolean {
        val clean = cutoffHm.trim().replace(".", ":")
        if (AttendanceTimeEvaluator.parseMinutesOfDay(clean) == null) {
            showToast("Format jam batas telat harus HH:mm (contoh: 05:30)!", isError = true)
            return false
        }
        updateAdminPermissions { perm ->
            when (sesi.trim().lowercase()) {
                "pagi" -> perm.copy(batasTelatPagi = clean)
                "sore" -> perm.copy(batasTelatSore = clean)
                "malam" -> perm.copy(batasTelatMalam = clean)
                else -> perm.copy(batasTelatEvent = clean)
            }
        }
        showToast("Batas jam telat untuk \"$sesi\" disimpan ($clean WIB)!")
        return true
    }

    fun setAbsensiCatatan(tipe: String, targetId: String, firqohId: String, catatan: String) {
        val st = _uiState.value
        val existing = getAbsensiRecord(tipe, targetId)
        val recId = existing?.id ?: "ABS-${System.currentTimeMillis()}-${(100..999).random()}"
        val updatedRec = existing?.copy(catatan = catatan) ?: AbsensiRecord(
            id = recId,
            tanggal = st.absensiTanggal,
            sesi = st.absensiSesi,
            tipe = tipe,
            targetId = targetId,
            firqohId = firqohId,
            status = "Hadir",
            catatan = catatan
        )
        repository.mutate(listOf(PendingMutation("absensi", recId, "upsert"))) { db ->
            val list = db.absensi.toMutableList()
            val idx = list.indexOfFirst { it.id == recId }
            if (idx >= 0) list[idx] = updatedRec else list.add(updatedRec)
            db.copy(absensi = list)
        }
    }

    fun markAllPresent(tipe: String) {
        val st = _uiState.value
        val db = database.value
        val targets: List<Pair<String, String>> = when (tipe) {
            "santri" -> db.santri.filter { matchesFirqohFilter(it.firqohId, st.absensiFirqoh) }.map { it.id to it.firqohId }
            "muallim" -> db.muallim.map { it.id to it.firqohId }
            else -> db.dewanHarian.map { it.id to "" }
        }
        val mutations = mutableListOf<PendingMutation>()
        val newList = db.absensi.toMutableList()

        targets.forEachIndexed { i, (targetId, firqohId) ->
            val idx = newList.indexOfFirst {
                it.tipe == tipe && it.targetId == targetId && it.tanggal == st.absensiTanggal && it.sesi == st.absensiSesi
            }
            if (idx >= 0) {
                val updated = newList[idx].copy(status = "Hadir")
                newList[idx] = updated
                mutations.add(PendingMutation("absensi", updated.id, "upsert"))
            } else {
                val recId = "ABS-${System.currentTimeMillis()}-$i-${(10..99).random()}"
                newList.add(
                    AbsensiRecord(recId, st.absensiTanggal, st.absensiSesi, tipe, targetId, firqohId, "Hadir", "")
                )
                mutations.add(PendingMutation("absensi", recId, "upsert"))
            }
        }
        repository.mutate(mutations) { cur -> cur.copy(absensi = newList) }
        showToast("Semua peserta pada daftar ditandai HADIR!")
    }

    // =========================================================================
    // MATERI ACTIONS
    // =========================================================================
    fun saveMateri(id: String?, firqohId: String, kitab: String, bab: String, target: String, progress: Int) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "MTR-${System.currentTimeMillis()}"
        val item = MateriItem(
            id = itemId,
            firqohId = firqohId,
            kitab = kitab.trim(),
            bab = bab.trim(),
            target = target.trim(),
            progress = progress.coerceIn(0, 100),
            pengatur = _uiState.value.currentUser?.name ?: "Dewan Harian"
        )
        repository.mutate(listOf(PendingMutation("materi", itemId, "upsert"))) { db ->
            val list = db.materi.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(item)
            db.copy(materi = list)
        }
        showToast("Materi perfirqoh berhasil disimpan!")
    }

    fun deleteMateri(id: String) {
        repository.mutate(listOf(PendingMutation("materi", id, "delete"))) { db ->
            db.copy(materi = db.materi.filterNot { it.id == id })
        }
        showToast("Materi dihapus.")
    }

    // =========================================================================
    // UJIAN ACTIONS
    // =========================================================================
    fun getKkmForFirqoh(firqohId: String): Int {
        val db = database.value
        db.ujianMateri.firstOrNull { it.firqohId == firqohId }?.let { return it.kkm }
        db.ujianMateri.firstOrNull {
            it.firqohId.startsWith(FirqohFilterHelper.GROUP_PREFIX) &&
                FirqohFilterHelper.matchesFilter(firqohId, it.firqohId, db.firqoh)
        }?.let { return it.kkm }
        db.ujianMateri.firstOrNull { it.firqohId == "ALL" }?.let { return it.kkm }
        return 75
    }

    fun toggleSantriPesertaUjian(santriId: String) {
        val s = database.value.santri.firstOrNull { it.id == santriId } ?: return
        val updated = s.copy(pesertaUjian = !s.pesertaUjian)
        repository.mutate(listOf(PendingMutation("santri", santriId, "upsert"))) { db ->
            db.copy(santri = db.santri.map { if (it.id == santriId) updated else it })
        }
        showToast("Status peserta ujian ${s.nama} diperbarui.")
    }

    fun saveNilaiUjian(santriId: String, skorAspek: Map<String, Double>, catatan: String) {
        val santri = database.value.santri.firstOrNull { it.id == santriId } ?: return
        val formatList = database.value.ujianFormat
        var totalWeighted = 0.0
        var totalBobot = 0
        formatList.forEach { f ->
            val skor = skorAspek[f.id] ?: 0.0
            totalWeighted += skor * f.bobot
            totalBobot += f.bobot
        }
        val nilaiAkhir = if (totalBobot > 0) kotlin.math.round((totalWeighted / totalBobot) * 10.0) / 10.0 else 0.0
        val predikat = MmqaConstants.getPredikatNilai(nilaiAkhir)

        val existing = database.value.nilaiUjian.firstOrNull { it.santriId == santriId }
        val recordId = existing?.id ?: "NIL-${System.currentTimeMillis()}"
        val record = NilaiUjianItem(
            id = recordId,
            santriId = santriId,
            firqohId = santri.firqohId,
            skorAspek = skorAspek,
            nilaiAkhir = nilaiAkhir,
            predikat = predikat,
            catatan = catatan.trim()
        )
        repository.mutate(listOf(PendingMutation("nilaiUjian", recordId, "upsert"))) { db ->
            val list = db.nilaiUjian.toMutableList()
            val idx = list.indexOfFirst { it.santriId == santriId }
            if (idx >= 0) list[idx] = record else list.add(record)
            db.copy(nilaiUjian = list)
        }
        _uiState.update { it.copy(ujianTab = "rekapan") }
        showToast("Nilai ujian ${santri.nama} ($nilaiAkhir - $predikat) berhasil direkap!")
    }

    fun saveUjianMateri(id: String?, firqohId: String, namaUjian: String, kitab: String, kkm: Int, ket: String) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "UJM-${System.currentTimeMillis()}"
        val item = UjianMateriItem(itemId, firqohId, namaUjian.trim(), kitab.trim(), kkm, ket.trim())
        repository.mutate(listOf(PendingMutation("ujianMateri", itemId, "upsert"))) { db ->
            val list = db.ujianMateri.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(item)
            db.copy(ujianMateri = list)
        }
        showToast("Materi ujian perfirqoh disimpan!")
    }

    fun deleteUjianMateri(id: String) {
        repository.mutate(listOf(PendingMutation("ujianMateri", id, "delete"))) { db ->
            db.copy(ujianMateri = db.ujianMateri.filterNot { it.id == id })
        }
    }

    fun saveRubrik(id: String?, aspek: String, bobot: Int, deskripsi: String) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "FMT-${System.currentTimeMillis()}"
        val item = UjianFormatItem(itemId, aspek.trim(), bobot, deskripsi.trim())
        repository.mutate(listOf(PendingMutation("ujianFormat", itemId, "upsert"))) { db ->
            val list = db.ujianFormat.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(item)
            db.copy(ujianFormat = list)
        }
        showToast("Format penilaian diperbarui!")
    }

    fun deleteRubrik(id: String) {
        repository.mutate(listOf(PendingMutation("ujianFormat", id, "delete"))) { db ->
            db.copy(ujianFormat = db.ujianFormat.filterNot { it.id == id })
        }
    }

    fun shareRekapanUjianToChat() {
        val db = database.value
        val sudah = db.nilaiUjian.size
        val total = db.santri.count { it.pesertaUjian }
        val avg = if (sudah > 0) {
            String.format("%.1f", db.nilaiUjian.sumOf { it.nilaiAkhir } / sudah)
        } else "0"
        sendQuickReportToChat("📊 *REKAPAN HASIL UJIAN MMQA AR2*\n• Peserta Terdaftar: $total Santri\n• Sudah Dinilai: $sudah Santri\n• Rata-Rata Nilai Akhir: $avg")
    }

    // =========================================================================
    // KEUANGAN & SYAHRIAH ACTIONS
    // =========================================================================
    fun addKeuangan(tanggal: String, jenis: String, kategori: String, nominal: Long, keterangan: String) {
        val itemId = "KEU-${System.currentTimeMillis()}"
        val item = KeuanganItem(
            id = itemId,
            tanggal = tanggal,
            jenis = jenis,
            kategori = kategori.trim(),
            nominal = nominal,
            keterangan = keterangan.trim(),
            pencatat = _uiState.value.currentUser?.name ?: "Bendahara"
        )
        repository.mutate(listOf(PendingMutation("keuangan", itemId, "upsert"))) { db ->
            db.copy(keuangan = listOf(item) + db.keuangan)
        }
        showToast("Transaksi keuangan berhasil dicatat!")
    }

    fun deleteKeuangan(id: String) {
        repository.mutate(listOf(PendingMutation("keuangan", id, "delete"))) { db ->
            db.copy(keuangan = db.keuangan.filterNot { it.id == id })
        }
    }

    fun toggleSyahriahStatus(santriId: String) {
        val db = database.value
        val s = db.santri.firstOrNull { it.id == santriId } ?: return
        val wasLunas = s.statusSyahriah == "Lunas"
        val updatedSantri = s.copy(statusSyahriah = if (wasLunas) "Belum Lunas" else "Lunas")
        val mutations = mutableListOf(PendingMutation("santri", santriId, "upsert"))

        var newKeu: KeuanganItem? = null
        if (!wasLunas) {
            val nominal = db.adminConfig.permissions.syahriahNominal
            val keuId = "KEU-${System.currentTimeMillis()}"
            newKeu = KeuanganItem(
                id = keuId,
                tanggal = MmqaConstants.todayStr(),
                jenis = "pemasukan",
                kategori = "Syahriah Santri",
                nominal = nominal,
                keterangan = "Pembayaran Syahriah a.n. ${s.nama} (${getFirqohName(s.firqohId)})",
                pencatat = _uiState.value.currentUser?.name ?: "Bendahara"
            )
            mutations.add(PendingMutation("keuangan", keuId, "upsert"))
            showToast("Syahriah ${s.nama} Lunas & otomatis dicatat ke Kas (+${MmqaConstants.formatRupiah(nominal)})!")
        } else {
            showToast("Status Syahriah ${s.nama} diubah ke Belum Lunas.")
        }

        repository.mutate(mutations) { cur ->
            cur.copy(
                santri = cur.santri.map { if (it.id == santriId) updatedSantri else it },
                keuangan = if (newKeu != null) listOf(newKeu) + cur.keuangan else cur.keuangan
            )
        }
    }

    fun shareKeuanganToChat() {
        val list = database.value.keuangan
        val pemasukan = list.filter { it.jenis == "pemasukan" }.sumOf { it.nominal }
        val pengeluaran = list.filter { it.jenis == "pengeluaran" }.sumOf { it.nominal }
        sendQuickReportToChat(
            "💰 *LAPORAN KAS MAJELIS MMQA AR2*\n• Pemasukan: ${MmqaConstants.formatRupiah(pemasukan)}\n• Pengeluaran: ${MmqaConstants.formatRupiah(pengeluaran)}\n• Saldo Akhir: ${MmqaConstants.formatRupiah(pemasukan - pengeluaran)}"
        )
    }

    fun buildAbsensiSessionOptions(): List<Pair<String, String>> {
        val base = linkedMapOf(
            "Pagi" to "Sesi Pagi (Harian)",
            "Sore" to "Sesi Sore (Harian)",
            "Malam" to "Sesi Malam (Harian)"
        )
        database.value.jadwal.forEach { j ->
            val key = "Event: ${j.judul}"
            if (!base.containsKey(key)) {
                val catTag = if (j.kategori.isNotBlank()) " [${j.kategori}]" else ""
                base[key] = "🎯 $key$catTag"
            }
        }
        database.value.absensi.map { it.sesi.trim() }.filter { it.isNotBlank() }.distinct().forEach { s ->
            if (!base.containsKey(s)) {
                base[s] = "🎯 $s"
            }
        }
        val currentSesi = _uiState.value.absensiSesi.trim()
        if (currentSesi.isNotBlank() && !base.containsKey(currentSesi)) {
            base[currentSesi] = "🎯 $currentSesi"
        }
        return base.entries.map { it.key to it.value }
    }

    // =========================================================================
    // SURAT MENYURAT ACTIONS
    // =========================================================================
    fun suggestNextNomorSurat(kode: String): String {
        val countSameKode = database.value.suratArsip.count { it.kodeSurat == kode } + 1
        val numStr = countSameKode.toString().padStart(3, '0')
        val cal = Calendar.getInstance()
        val bulanRomawi = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")[cal.get(Calendar.MONTH)]
        val tahun = cal.get(Calendar.YEAR)
        return "$numStr/$kode/MMQA-AR2/$bulanRomawi/$tahun"
    }

    private fun applyTemplateToState(st: MmqaUiState, tpl: SuratTemplateItem): MmqaUiState {
        return st.copy(
            suratDraftTemplateId = tpl.id,
            suratDraftKode = tpl.kodeSurat,
            suratDraftNomor = suggestNextNomorSurat(tpl.kodeSurat),
            suratDraftLampiran = tpl.lampiranDefault.ifBlank { "-" },
            suratDraftPerihal = tpl.perihalDefault,
            suratDraftTujuan = tpl.tujuanDefault.ifBlank { st.suratDraftTujuan },
            suratDraftIsi = tpl.isiDefault,
            suratDraftPenandatangan = tpl.penandatanganDefault.ifBlank { st.suratDraftPenandatangan },
            suratDraftJabatan = tpl.jabatanDefault.ifBlank { st.suratDraftJabatan },
            suratDraftSekretaris = tpl.sekretarisDefault.ifBlank { st.suratDraftSekretaris },
            suratDraftJabatanSekretaris = tpl.jabatanSekretarisDefault.ifBlank { st.suratDraftJabatanSekretaris },
            suratDraftTampilkanSekretaris = tpl.tampilkanSekretaris,
            suratDraftTtdKetua = tpl.ttdKetuaDefault.ifBlank { st.suratDraftTtdKetua },
            suratDraftTtdSekretaris = tpl.ttdSekretarisDefault.ifBlank { st.suratDraftTtdSekretaris },
            suratDraftKop1 = tpl.kopBaris1.ifBlank { st.suratDraftKop1 },
            suratDraftKop2 = tpl.kopBaris2.ifBlank { st.suratDraftKop2 },
            suratDraftKop3 = tpl.kopBaris3.ifBlank { st.suratDraftKop3 },
            suratDraftKota = tpl.kotaDefault.ifBlank { st.suratDraftKota },
            suratDraftFontStyle = tpl.fontStyle.ifBlank { "Times New Roman (Resmi)" },
            suratDraftUkuranKertas = tpl.ukuranKertas.ifBlank { "A4" },
            suratDraftMarginMm = tpl.marginMm.coerceIn(10, 40),
            suratDraftPedoman = tpl.pedomanFormat
        )
    }

    fun onChangeSuratDraftKode(kode: String) {
        val tpl = database.value.suratTemplates.firstOrNull { it.kodeSurat == kode }
        _uiState.update { st ->
            if (tpl != null) {
                applyTemplateToState(st, tpl)
            } else {
                st.copy(
                    suratDraftKode = kode,
                    suratDraftNomor = suggestNextNomorSurat(kode)
                )
            }
        }
    }

    fun onSelectSuratTemplate(tplId: String) {
        val tpl = database.value.suratTemplates.firstOrNull { it.id == tplId }
        _uiState.update { st ->
            if (tpl != null) {
                applyTemplateToState(st, tpl)
            } else {
                st.copy(suratDraftTemplateId = "", suratDraftPedoman = "")
            }
        }
        if (tpl != null) {
            showToast("Pedoman format \"${tpl.nama}\" diterapkan ke draft surat!")
        }
    }

    fun publishAndArchiveSurat() {
        val st = _uiState.value
        val tpl = database.value.suratTemplates.firstOrNull { it.id == st.suratDraftTemplateId }
        val newId = "SRT-${System.currentTimeMillis()}"
        val newSurat = SuratArsipItem(
            id = newId,
            kodeSurat = st.suratDraftKode,
            nomorSurat = st.suratDraftNomor.trim(),
            tanggal = st.suratDraftTanggal,
            lampiran = st.suratDraftLampiran.trim().ifBlank { "-" },
            perihal = st.suratDraftPerihal.trim(),
            tujuan = st.suratDraftTujuan.trim(),
            isi = st.suratDraftIsi,
            penandatangan = st.suratDraftPenandatangan.trim(),
            jabatanPenandatangan = st.suratDraftJabatan.trim(),
            templateId = tpl?.id ?: "",
            pdfName = tpl?.pdfName ?: "",
            pdfDataUrl = tpl?.pdfDataUrl ?: "",
            kopBaris1 = st.suratDraftKop1.trim(),
            kopBaris2 = st.suratDraftKop2.trim(),
            kopBaris3 = st.suratDraftKop3.trim(),
            kotaTanggal = st.suratDraftKota.trim().ifBlank { "Malang" },
            sekretaris = st.suratDraftSekretaris.trim(),
            jabatanSekretaris = st.suratDraftJabatanSekretaris.trim(),
            tampilkanSekretaris = st.suratDraftTampilkanSekretaris,
            ttdKetuaDataUrl = st.suratDraftTtdKetua,
            ttdSekretarisDataUrl = st.suratDraftTtdSekretaris,
            fontStyle = st.suratDraftFontStyle,
            ukuranKertas = st.suratDraftUkuranKertas,
            marginMm = st.suratDraftMarginMm
        )
        repository.mutate(listOf(PendingMutation("suratArsip", newId, "upsert"))) { db ->
            db.copy(suratArsip = listOf(newSurat) + db.suratArsip)
        }
        _uiState.update {
            it.copy(
                suratDraftNomor = suggestNextNomorSurat(it.suratDraftKode),
                suratTab = "arsip",
                suratArsipKode = newSurat.kodeSurat
            )
        }
        showToast("Surat ${newSurat.nomorSurat} berhasil diterbitkan & diarsipkan ke ${MmqaConstants.getKodeSuratLabel(newSurat.kodeSurat)}!")
    }

    fun deleteArsipSurat(id: String) {
        repository.mutate(listOf(PendingMutation("suratArsip", id, "delete"))) { db ->
            db.copy(suratArsip = db.suratArsip.filterNot { it.id == id })
        }
    }

    fun saveTemplateSurat(
        id: String? = null,
        kode: String,
        nama: String,
        perihal: String,
        isi: String,
        pdfName: String,
        pdfDataUrl: String,
        kopBaris1: String = "MAJELIS MUSYAWARAH QIRA'ATIL KUTUB & AL-QUR'AN",
        kopBaris2: String = "MMQA AL RIFAI 02 (AR2)",
        kopBaris3: String = "Sekretariat Dewan Harian Majelis • Sistem Administrasi Terpadu",
        kotaDefault: String = "Malang",
        lampiranDefault: String = "-",
        tujuanDefault: String = "Yth. Seluruh Mu’allim Firqoh MMQA AR2",
        penandatanganDefault: String = "Ust. H. Ahmad Fauzi, M.Pd",
        jabatanDefault: String = "Ketua Dewan Harian MMQA",
        sekretarisDefault: String = "Ust. Muhammad Ridwan",
        jabatanSekretarisDefault: String = "Sekretaris Umum MMQA",
        tampilkanSekretaris: Boolean = true,
        ttdKetuaDefault: String = "",
        ttdSekretarisDefault: String = "",
        fontStyle: String = "Times New Roman (Resmi)",
        ukuranKertas: String = "A4",
        marginMm: Int = 20,
        pedomanFormat: String = ""
    ) {
        val tplId = id?.takeIf { it.isNotBlank() } ?: "TPL-${System.currentTimeMillis()}"
        val tpl = SuratTemplateItem(
            id = tplId,
            kodeSurat = kode,
            nama = nama.trim(),
            perihalDefault = perihal.trim(),
            isiDefault = isi,
            pdfName = pdfName,
            pdfDataUrl = pdfDataUrl,
            kopBaris1 = kopBaris1.trim(),
            kopBaris2 = kopBaris2.trim(),
            kopBaris3 = kopBaris3.trim(),
            kotaDefault = kotaDefault.trim().ifBlank { "Malang" },
            lampiranDefault = lampiranDefault.trim().ifBlank { "-" },
            tujuanDefault = tujuanDefault.trim(),
            penandatanganDefault = penandatanganDefault.trim(),
            jabatanDefault = jabatanDefault.trim(),
            sekretarisDefault = sekretarisDefault.trim(),
            jabatanSekretarisDefault = jabatanSekretarisDefault.trim(),
            tampilkanSekretaris = tampilkanSekretaris,
            ttdKetuaDefault = ttdKetuaDefault,
            ttdSekretarisDefault = ttdSekretarisDefault,
            fontStyle = fontStyle,
            ukuranKertas = ukuranKertas,
            marginMm = marginMm.coerceIn(10, 40),
            pedomanFormat = pedomanFormat.trim()
        )
        repository.mutate(listOf(PendingMutation("suratTemplates", tplId, "upsert"))) { db ->
            val exists = db.suratTemplates.any { it.id == tplId }
            val next = if (exists) db.suratTemplates.map { if (it.id == tplId) tpl else it } else db.suratTemplates + tpl
            db.copy(suratTemplates = next)
        }
        showToast("Template pedoman format surat berhasil disimpan!")
    }

    fun deleteTemplateSurat(id: String) {
        repository.mutate(listOf(PendingMutation("suratTemplates", id, "delete"))) { db ->
            db.copy(suratTemplates = db.suratTemplates.filterNot { it.id == id })
        }
        showToast("Template surat dihapus.")
    }

    // =========================================================================
    // INVENTARIS ACTIONS
    // =========================================================================
    fun saveInventaris(
        id: String?,
        kodeAset: String,
        kategori: String,
        nama: String,
        jumlah: Int,
        satuan: String,
        kondisi: String,
        lokasi: String,
        nilaiEstimasi: Long,
        pj: String,
        keterangan: String
    ) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "INV-${System.currentTimeMillis()}"
        val item = InventarisItem(
            id = itemId,
            kodeAset = kodeAset.trim(),
            nama = nama.trim(),
            kategori = kategori.trim(),
            jumlah = jumlah.coerceAtLeast(1),
            satuan = satuan.trim(),
            lokasi = lokasi.trim(),
            kondisi = kondisi,
            nilaiEstimasi = nilaiEstimasi,
            pj = pj.trim(),
            keterangan = keterangan.trim()
        )
        repository.mutate(listOf(PendingMutation("inventaris", itemId, "upsert"))) { db ->
            val list = db.inventaris.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(0, item)
            db.copy(inventaris = list)
        }
        showToast("Data inventaris berhasil disimpan!")
    }

    fun deleteInventaris(id: String) {
        repository.mutate(listOf(PendingMutation("inventaris", id, "delete"))) { db ->
            db.copy(inventaris = db.inventaris.filterNot { it.id == id })
        }
    }

    // =========================================================================
    // DATA INDUK ACTIONS
    // =========================================================================
    fun saveSantri(id: String?, nis: String, angkatan: String, nama: String, firqohId: String, wali: String, hpWali: String) {
        val existing = id?.let { sid -> database.value.santri.firstOrNull { it.id == sid } }
        val itemId = id?.takeIf { it.isNotBlank() } ?: "SNT-${System.currentTimeMillis()}"
        val item = SantriItem(
            id = itemId,
            nis = nis.trim(),
            nama = nama.trim(),
            firqohId = firqohId,
            angkatan = angkatan.trim(),
            wali = wali.trim(),
            hpWali = hpWali.trim(),
            statusSyahriah = existing?.statusSyahriah ?: "Belum Lunas",
            pesertaUjian = existing?.pesertaUjian ?: true
        )
        repository.mutate(listOf(PendingMutation("santri", itemId, "upsert"))) { db ->
            val list = db.santri.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(item)
            db.copy(santri = list)
        }
        showToast("Data Induk berhasil disimpan!")
    }

    fun saveMuallim(id: String?, niy: String, nama: String, firqohId: String, bidang: String, hp: String) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "MLM-${System.currentTimeMillis()}"
        val item = MuallimItem(itemId, niy.trim(), nama.trim(), firqohId, bidang.trim(), hp.trim())
        repository.mutate(listOf(PendingMutation("muallim", itemId, "upsert"))) { db ->
            val list = db.muallim.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(item)
            db.copy(muallim = list)
        }
        showToast("Data Induk berhasil disimpan!")
    }

    fun saveDewanHarian(id: String?, kode: String, nama: String, jabatan: String, hp: String) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "DWH-${System.currentTimeMillis()}"
        val item = DewanHarianItem(itemId, kode.trim(), nama.trim(), jabatan.trim(), hp.trim())
        repository.mutate(listOf(PendingMutation("dewanHarian", itemId, "upsert"))) { db ->
            val list = db.dewanHarian.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(item)
            db.copy(dewanHarian = list)
        }
        showToast("Data Induk berhasil disimpan!")
    }

    fun saveFirqoh(id: String?, kode: String, nama: String, tingkat: String, jadwal: String, ruangan: String) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "FRQ-${System.currentTimeMillis()}"
        val item = FirqohItem(itemId, kode.trim(), nama.trim(), tingkat.trim(), jadwal.trim(), ruangan.trim())
        repository.mutate(listOf(PendingMutation("firqoh", itemId, "upsert"))) { db ->
            val list = db.firqoh.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(item)
            db.copy(firqoh = list)
        }
        showToast("Data Induk berhasil disimpan!")
    }

    fun deleteIndukItem(tipe: String, id: String) {
        val col = when (tipe) {
            "santri" -> "santri"
            "muallim" -> "muallim"
            "dewan" -> "dewanHarian"
            else -> "firqoh"
        }
        repository.mutate(listOf(PendingMutation(col, id, "delete"))) { db ->
            when (tipe) {
                "santri" -> db.copy(santri = db.santri.filterNot { it.id == id })
                "muallim" -> db.copy(muallim = db.muallim.filterNot { it.id == id })
                "dewan" -> db.copy(dewanHarian = db.dewanHarian.filterNot { it.id == id })
                else -> db.copy(firqoh = db.firqoh.filterNot { it.id == id })
            }
        }
    }

    // =========================================================================
    // STRUKTUR PERSONALIA MMQA ACTIONS
    // =========================================================================
    fun savePersonalia(
        id: String?,
        kode: String,
        nama: String,
        jabatan: String,
        divisi: String,
        periode: String,
        kontak: String,
        urutan: Int
    ) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "PRS-${System.currentTimeMillis()}"
        val item = PersonaliaItem(
            id = itemId,
            kode = kode.trim(),
            nama = nama.trim(),
            jabatan = jabatan.trim(),
            divisi = divisi.trim().ifBlank { "Pengurus MMQA" },
            periode = periode.trim().ifBlank { "2026 - 2027" },
            kontak = kontak.trim(),
            urutan = urutan.coerceAtLeast(1)
        )
        repository.mutate(listOf(PendingMutation("personalia", itemId, "upsert"))) { db ->
            val list = db.personalia.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(item)
            db.copy(personalia = list.sortedWith(compareBy<PersonaliaItem> { it.urutan }.thenBy { it.nama }))
        }
        showToast("Struktur Personalia MMQA berhasil disimpan!")
    }

    fun deletePersonalia(id: String) {
        repository.mutate(listOf(PendingMutation("personalia", id, "delete"))) { db ->
            db.copy(personalia = db.personalia.filterNot { it.id == id })
        }
        showToast("Personalia dihapus dari struktur.")
    }

    fun syncPersonaliaFromDataInduk() {
        val db = database.value
        val existingNames = db.personalia.map { it.nama.trim().lowercase() }.toMutableSet()
        val added = mutableListOf<PersonaliaItem>()
        val mutations = mutableListOf<PendingMutation>()
        var nextOrder = (db.personalia.maxOfOrNull { it.urutan } ?: 0) + 1
        var idxCounter = db.personalia.size + 1

        db.dewanHarian.forEach { d ->
            val key = d.nama.trim().lowercase()
            if (key.isNotBlank() && key !in existingNames) {
                existingNames.add(key)
                val newId = "PRS-DH-${d.id}"
                val item = PersonaliaItem(
                    id = newId,
                    kode = d.kode.ifBlank { "STR-$idxCounter" },
                    nama = d.nama,
                    jabatan = d.jabatan.ifBlank { "Dewan Harian MMQA" },
                    divisi = "Pimpinan & Dewan Harian",
                    periode = "2026 - 2027",
                    kontak = d.hp,
                    urutan = nextOrder++
                )
                added.add(item)
                mutations.add(PendingMutation("personalia", newId, "upsert"))
                idxCounter++
            }
        }

        db.muallim.forEach { m ->
            val key = m.nama.trim().lowercase()
            if (key.isNotBlank() && key !in existingNames) {
                existingNames.add(key)
                val newId = "PRS-MLM-${m.id}"
                val item = PersonaliaItem(
                    id = newId,
                    kode = m.niy.ifBlank { "STR-$idxCounter" },
                    nama = m.nama,
                    jabatan = "Mu'allim ${getFirqohName(m.firqohId)}",
                    divisi = "Mu'allim Firqoh",
                    periode = "2026 - 2027",
                    kontak = m.hp,
                    urutan = nextOrder++
                )
                added.add(item)
                mutations.add(PendingMutation("personalia", newId, "upsert"))
                idxCounter++
            }
        }

        if (added.isEmpty()) {
            showToast("Semua Dewan Harian & Mu'allim sudah ada di Struktur Personalia.")
            return
        }
        repository.mutate(mutations) { cur ->
            cur.copy(personalia = (cur.personalia + added).sortedWith(compareBy<PersonaliaItem> { it.urutan }.thenBy { it.nama }))
        }
        showToast("${added.size} pengurus/mu'allim berhasil ditarik ke Struktur Personalia!")
    }

    // =========================================================================
    // GRUP CHAT MAJELIS ACTIONS
    // =========================================================================
    fun sendChatMessage(text: String, attachment: ChatAttachmentDraft? = null) {
        val clean = text.trim()
        if (clean.isBlank() && attachment == null) return
        val st = _uiState.value
        val curUser = st.currentUser
        val msgId = "MSG-${System.currentTimeMillis()}"
        val msg = ChatMessageItem(
            id = msgId,
            channelId = st.chatChannel,
            senderId = curUser?.id ?: "USR-01",
            senderName = curUser?.name ?: "Pengguna",
            senderRole = when (curUser?.role) {
                "dewan_harian" -> "Dewan Harian MMQA"
                "wali_santri" -> "Wali Santri"
                else -> curUser?.jabatan ?: "Mu'allim"
            },
            text = clean,
            time = MmqaConstants.nowTimeHm(),
            replyTo = st.chatReplyTo,
            reactions = emptyMap(),
            pinned = false,
            attachmentName = attachment?.fileName ?: "",
            attachmentMime = attachment?.mimeType ?: "",
            attachmentSize = attachment?.sizeBytes ?: 0L,
            attachmentDataUrl = attachment?.dataUrl ?: ""
        )
        repository.mutate(listOf(PendingMutation("chatMessages", msgId, "upsert"))) { db ->
            db.copy(chatMessages = db.chatMessages + msg)
        }
        _uiState.update { it.copy(chatReplyTo = null) }
    }

    fun sendQuickReportToChat(reportText: String) {
        _uiState.update { it.copy(activeMenu = "chat", chatChannel = "CHN-UMUM") }
        sendChatMessage(reportText, null)
        showToast("Ringkasan laporan berhasil dikirim ke Grup Chat Majelis!")
    }

    fun setReplyChat(msg: ChatMessageItem?) {
        _uiState.update {
            it.copy(chatReplyTo = msg?.let { m ->
                val preview = m.text.ifBlank { if (m.attachmentName.isNotBlank()) "📎 ${m.attachmentName}" else "" }
                ChatReplyRef(m.id, m.senderName, preview)
            })
        }
    }

    fun reactChatMessage(msgId: String, emoji: String) {
        val msg = database.value.chatMessages.firstOrNull { it.id == msgId } ?: return
        val newReactions = msg.reactions.toMutableMap()
        newReactions[emoji] = (newReactions[emoji] ?: 0) + 1
        val updated = msg.copy(reactions = newReactions)
        repository.mutate(listOf(PendingMutation("chatMessages", msgId, "upsert"))) { db ->
            db.copy(chatMessages = db.chatMessages.map { if (it.id == msgId) updated else it })
        }
    }

    fun togglePinChatMessage(msgId: String) {
        val msg = database.value.chatMessages.firstOrNull { it.id == msgId } ?: return
        val nextPinned = !msg.pinned
        val mutations = mutableListOf<PendingMutation>()
        val updatedList = database.value.chatMessages.map { m ->
            if (m.id == msgId) {
                mutations.add(PendingMutation("chatMessages", m.id, "upsert"))
                m.copy(pinned = nextPinned)
            } else if (m.channelId == msg.channelId && m.pinned) {
                mutations.add(PendingMutation("chatMessages", m.id, "upsert"))
                m.copy(pinned = false)
            } else {
                m
            }
        }
        repository.mutate(mutations) { db -> db.copy(chatMessages = updatedList) }
    }

    fun deleteChatMessage(msgId: String) {
        repository.mutate(listOf(PendingMutation("chatMessages", msgId, "delete"))) { db ->
            db.copy(chatMessages = db.chatMessages.filterNot { it.id == msgId })
        }
    }

    fun addChatChannel(name: String, desc: String) {
        val chId = "CHN-${System.currentTimeMillis()}"
        val ch = ChatChannelItem(chId, name.trim(), desc.trim())
        repository.mutate(listOf(PendingMutation("chatChannels", chId, "upsert"))) { db ->
            db.copy(chatChannels = db.chatChannels + ch)
        }
        _uiState.update { it.copy(chatChannel = chId, chatReplyTo = null) }
    }

    // =========================================================================
    // KONTROL ADMIN ACTIONS
    // =========================================================================
    fun updateAdminMenuLabel(id: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return
        repository.mutate(listOf(PendingMutation("adminConfig", "ADMIN_CFG_MAIN", "upsert"))) { db ->
            db.copy(
                adminConfig = db.adminConfig.copy(
                    menus = db.adminConfig.menus.map { if (it.id == id) it.copy(label = trimmed) else it }
                )
            )
        }
    }

    fun updateAdminMenuRole(id: String, role: String) {
        repository.mutate(listOf(PendingMutation("adminConfig", "ADMIN_CFG_MAIN", "upsert"))) { db ->
            db.copy(
                adminConfig = db.adminConfig.copy(
                    menus = db.adminConfig.menus.map { if (it.id == id) it.copy(roles = role) else it }
                )
            )
        }
    }

    fun toggleAdminMenuEnabled(id: String) {
        if (id == "admin") return
        repository.mutate(listOf(PendingMutation("adminConfig", "ADMIN_CFG_MAIN", "upsert"))) { db ->
            db.copy(
                adminConfig = db.adminConfig.copy(
                    menus = db.adminConfig.menus.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
                )
            )
        }
    }

    fun moveAdminMenuOrder(id: String, dir: Int) {
        val sorted = database.value.adminConfig.menus.sortedBy { it.order }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == id }
        val swapIdx = idx + dir
        if (idx < 0 || swapIdx < 0 || swapIdx >= sorted.size) return
        val tmp = sorted[idx]
        sorted[idx] = sorted[swapIdx]
        sorted[swapIdx] = tmp
        val reordered = sorted.mapIndexed { index, item -> item.copy(order = index + 1) }
        repository.mutate(listOf(PendingMutation("adminConfig", "ADMIN_CFG_MAIN", "upsert"))) { db ->
            db.copy(adminConfig = db.adminConfig.copy(menus = reordered))
        }
    }

    fun updateSubmenu(
        groupKey: String,
        subId: String,
        label: String? = null,
        role: String? = null,
        toggleEnabled: Boolean = false
    ) {
        repository.mutate(listOf(PendingMutation("adminConfig", "ADMIN_CFG_MAIN", "upsert"))) { db ->
            val sg = db.adminConfig.submenus
            fun patch(list: List<SubmenuConfigItem>) = list.map { sub ->
                if (sub.id == subId) {
                    sub.copy(
                        label = label?.trim()?.takeIf { it.isNotBlank() } ?: sub.label,
                        roles = role ?: sub.roles,
                        enabled = if (toggleEnabled) !sub.enabled else sub.enabled
                    )
                } else sub
            }
            val updatedSg = when (groupKey) {
                "absensi" -> sg.copy(absensi = patch(sg.absensi))
                "ujian" -> sg.copy(ujian = patch(sg.ujian))
                "keuangan" -> sg.copy(keuangan = patch(sg.keuangan))
                "surat" -> sg.copy(surat = patch(sg.surat))
                else -> sg.copy(induk = patch(sg.induk))
            }
            db.copy(adminConfig = db.adminConfig.copy(submenus = updatedSg))
        }
    }

    fun updateAnakSub(
        anakSubId: String,
        label: String? = null,
        role: String? = null,
        toggleEnabled: Boolean = false
    ) {
        repository.mutate(listOf(PendingMutation("adminConfig", "ADMIN_CFG_MAIN", "upsert"))) { db ->
            val updatedList = db.adminConfig.anakSub.map { item ->
                if (item.id == anakSubId) {
                    item.copy(
                        label = label?.trim()?.takeIf { it.isNotBlank() } ?: item.label,
                        roles = role ?: item.roles,
                        enabled = if (toggleEnabled) !item.enabled else item.enabled
                    )
                } else item
            }
            db.copy(adminConfig = db.adminConfig.copy(anakSub = updatedList))
        }
    }

    fun updateUserAccessOverrides(
        userId: String,
        deniedMenus: List<String>,
        deniedSubmenus: List<String>,
        deniedAnakSub: List<String>
    ) {
        repository.mutate(listOf(PendingMutation("users", userId, "upsert"))) { db ->
            db.copy(
                users = db.users.map { u ->
                    if (u.id == userId) {
                        u.copy(
                            deniedMenus = deniedMenus.distinct(),
                            deniedSubmenus = deniedSubmenus.distinct(),
                            deniedAnakSub = deniedAnakSub.distinct()
                        )
                    } else u
                }
            )
        }
        showToast("Hak akses khusus pengguna berhasil disimpan!")
    }

    fun updateAdminPermissions(transform: (AdminPermissions) -> AdminPermissions) {
        repository.mutate(listOf(PendingMutation("adminConfig", "ADMIN_CFG_MAIN", "upsert"))) { db ->
            val oldPerm = db.adminConfig.permissions
            val newPerm = transform(oldPerm)
            var updatedAnakSub = db.adminConfig.anakSub
            fun syncRole(ids: List<String>, allowAll: Boolean) {
                val targetRole = if (allowAll) "all" else "dewan_harian"
                updatedAnakSub = updatedAnakSub.map { item ->
                    if (item.id in ids) item.copy(roles = targetRole) else item
                }
            }
            if (oldPerm.muallimCanManageMateri != newPerm.muallimCanManageMateri) {
                syncRole(listOf("materi_manage"), newPerm.muallimCanManageMateri)
            }
            if (oldPerm.muallimCanManageUjianMateri != newPerm.muallimCanManageUjianMateri) {
                syncRole(listOf("ujian_materi_manage"), newPerm.muallimCanManageUjianMateri)
            }
            if (oldPerm.muallimCanManageRubrik != newPerm.muallimCanManageRubrik) {
                syncRole(listOf("ujian_rubrik_manage"), newPerm.muallimCanManageRubrik)
            }
            if (oldPerm.muallimCanInputKeuangan != newPerm.muallimCanInputKeuangan) {
                syncRole(listOf("keuangan_kas_tambah", "keuangan_syahriah_toggle"), newPerm.muallimCanInputKeuangan)
            }
            if (oldPerm.muallimCanManageSurat != newPerm.muallimCanManageSurat) {
                syncRole(listOf("surat_terbitkan", "surat_template_kelola"), newPerm.muallimCanManageSurat)
            }
            if (oldPerm.muallimCanManageInventaris != newPerm.muallimCanManageInventaris) {
                syncRole(listOf("inventaris_kelola"), newPerm.muallimCanManageInventaris)
            }
            if (oldPerm.muallimCanManageDataInduk != newPerm.muallimCanManageDataInduk) {
                syncRole(listOf("induk_kelola"), newPerm.muallimCanManageDataInduk)
            }
            db.copy(
                adminConfig = db.adminConfig.copy(
                    permissions = newPerm,
                    anakSub = updatedAnakSub
                )
            )
        }
    }

    fun savePasswordRule(rule: PasswordRule) {
        repository.mutate(listOf(PendingMutation("adminConfig", "ADMIN_CFG_MAIN", "upsert"))) { db ->
            db.copy(adminConfig = db.adminConfig.copy(passwordRule = rule))
        }
        showToast("Kebijakan Password Rule berhasil disimpan!")
    }

    fun saveAdminUser(
        userId: String?,
        username: String,
        password: String,
        name: String,
        jabatan: String,
        role: String,
        firqohId: String,
        santriId: String = ""
    ): Boolean {
        val ruleErr = validatePasswordAgainstRule(password)
        if (ruleErr != null) {
            showToast(ruleErr, isError = true)
            return false
        }
        val cleanUsername = username.trim()
        val duplicate = database.value.users.any {
            it.username.equals(cleanUsername, ignoreCase = true) && it.id != userId
        }
        if (duplicate) {
            showToast("Username sudah digunakan akun lain!", isError = true)
            return false
        }
        val itemId = userId?.takeIf { it.isNotBlank() } ?: "USR-${System.currentTimeMillis()}"
        val existing = database.value.users.firstOrNull { it.id == itemId }
        val resolvedSantriId = if (role == "wali_santri") santriId.trim() else ""
        val resolvedFirqohId = if (role == "wali_santri" && firqohId.isBlank() && resolvedSantriId.isNotBlank()) {
            database.value.santri.firstOrNull { it.id == resolvedSantriId }?.firqohId ?: ""
        } else {
            firqohId
        }
        val user = UserAccount(
            id = itemId,
            username = cleanUsername,
            password = password,
            name = name.trim(),
            role = role,
            jabatan = jabatan.trim(),
            firqohId = resolvedFirqohId,
            santriId = resolvedSantriId,
            failedAttempts = 0,
            lockedUntil = null,
            passwordUpdatedAt = MmqaConstants.todayStr(),
            avatarBase64 = existing?.avatarBase64 ?: "",
            deniedMenus = existing?.deniedMenus ?: emptyList(),
            deniedSubmenus = existing?.deniedSubmenus ?: emptyList(),
            deniedAnakSub = existing?.deniedAnakSub ?: emptyList()
        )
        repository.mutate(listOf(PendingMutation("users", itemId, "upsert"))) { db ->
            val list = db.users.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = user else list.add(user)
            db.copy(users = list)
        }
        showToast("Akun pengguna berhasil disimpan!")
        return true
    }

    fun deleteAdminUser(userId: String) {
        repository.mutate(listOf(PendingMutation("users", userId, "delete"))) { db ->
            db.copy(users = db.users.filterNot { it.id == userId })
        }
    }

    // =========================================================================
    // CSV EXPORT HELPERS
    // =========================================================================
    private fun toCsv(headers: List<String>, rows: List<List<String>>): String {
        fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""
        val lines = mutableListOf(headers.joinToString(",") { esc(it) })
        rows.forEach { r -> lines.add(r.joinToString(",") { esc(it) }) }
        return "\uFEFF" + lines.joinToString("\n")
    }

    fun buildRekapanUjianCsv(): String {
        val db = database.value
        val headers = listOf("No Peserta", "Nama Santri", "Firqoh", "Nilai Akhir", "Predikat", "Catatan")
        val rows = db.santri.filter { it.pesertaUjian }.map { s ->
            val n = db.nilaiUjian.firstOrNull { it.santriId == s.id }
            listOf(
                "UJN-${s.nis}",
                s.nama,
                getFirqohName(s.firqohId),
                n?.nilaiAkhir?.toString() ?: "Belum",
                n?.predikat ?: "-",
                n?.catatan ?: ""
            )
        }
        return toCsv(headers, rows)
    }

    fun buildKeuanganCsv(): String {
        val headers = listOf("Tanggal", "Jenis", "Kategori", "Nominal", "Keterangan", "Pencatat")
        val rows = database.value.keuangan.map { k ->
            listOf(k.tanggal, k.jenis, k.kategori, k.nominal.toString(), k.keterangan, k.pencatat)
        }
        return toCsv(headers, rows)
    }

    fun buildInventarisCsv(): String {
        val headers = listOf("Kode Aset", "Nama", "Kategori", "Jumlah", "Satuan", "Lokasi", "Kondisi", "Nilai Estimasi", "PJ", "Keterangan")
        val rows = database.value.inventaris.map { i ->
            listOf(i.kodeAset, i.nama, i.kategori, i.jumlah.toString(), i.satuan, i.lokasi, i.kondisi, i.nilaiEstimasi.toString(), i.pj, i.keterangan)
        }
        return toCsv(headers, rows)
    }

    // =========================================================================
    // KEAMANAN, ENKRIPSI & BIOMETRIK
    // =========================================================================
    fun getSecurityManager(): MmqaSecurityManager = repository.securityManager

    fun getBiometricCapability(): BiometricCapability = repository.securityManager.checkBiometricCapability()

    fun setBiometricLockEnabled(enabled: Boolean) {
        val next = _securitySettings.value.copy(biometricLockEnabled = enabled)
        repository.securityManager.saveSettings(next)
        _securitySettings.value = next
        if (!enabled) _isAppLocked.value = false
        showToast(if (enabled) "Kunci Biometrik Aplikasi diaktifkan!" else "Kunci Biometrik Aplikasi dinonaktifkan.")
    }

    fun setBiometricQuickLoginEnabled(enabled: Boolean) {
        val activeUsername = _uiState.value.currentUser?.username ?: _securitySettings.value.savedUsername
        val next = _securitySettings.value.copy(
            biometricQuickLoginEnabled = enabled,
            savedUsername = activeUsername
        )
        repository.securityManager.saveSettings(next)
        _securitySettings.value = next
        showToast(
            if (enabled) "Login Cepat Biometrik aktif untuk akun @$activeUsername!"
            else "Login Cepat Biometrik dinonaktifkan."
        )
    }

    fun unlockAppWithBiometric() {
        _isAppLocked.value = false
    }

    fun lockAppNow() {
        _isAppLocked.value = true
    }

    fun loginWithBiometricSavedUser(): Boolean {
        val saved = _securitySettings.value.savedUsername.trim()
        if (saved.isBlank()) {
            showToast("Belum ada akun tersimpan untuk login biometrik. Masuk sekali dengan password terlebih dahulu.", isError = true)
            return false
        }
        val user = database.value.users.firstOrNull { it.username.equals(saved, ignoreCase = true) }
        if (user == null) {
            showToast("Akun @$saved tidak ditemukan di database.", isError = true)
            return false
        }
        enterApp(user)
        return true
    }

    // =========================================================================
    // ABSENSI SCAN BARCODE (LIVE SYNCED WITH DATABASE)
    // =========================================================================
    fun recordBarcodeAttendance(
        tipe: String,
        targetId: String,
        firqohId: String,
        nama: String,
        status: String,
        customCutoffHm: String? = null
    ): ScanTimingResult {
        val st = _uiState.value
        val dbSnapshot = database.value
        val existing = getAbsensiRecord(tipe, targetId)
        val recId = existing?.id ?: "ABS-${System.currentTimeMillis()}-${(100..999).random()}"
        val nowHms = MmqaConstants.nowTimeHms()
        val cutoffHm = customCutoffHm?.takeIf { AttendanceTimeEvaluator.parseMinutesOfDay(it) != null }
            ?.trim()?.replace(".", ":")
            ?: AttendanceTimeEvaluator.defaultCutoffForSession(st.absensiSesi, dbSnapshot.jadwal, dbSnapshot.adminConfig.permissions)
        val timing = AttendanceTimeEvaluator.evaluateScanTiming(nowHms, cutoffHm, status)
        val actor = st.currentUser?.name ?: "Petugas Piket"
        val firqohDisplay = if (firqohId.isNotBlank()) getFirqohName(firqohId) else "Dewan Harian"
        val timingNote = if (timing.terlambat) {
            "Terlambat +${timing.menitTelat} mnt (Batas ${timing.batasWaktu})"
        } else if (status == "Hadir" && timing.batasWaktu.isNotBlank()) {
            "Tepat Waktu (Batas ${timing.batasWaktu})"
        } else {
            status
        }
        val scanNote = "Scan QR/Barcode pukul $nowHms WIB • $timingNote • Petugas: $actor"

        val updatedRec = existing?.copy(
            status = status,
            catatan = scanNote,
            waktuScan = timing.waktuScan,
            metode = "Scan QR/Barcode",
            terlambat = timing.terlambat,
            menitTelat = timing.menitTelat,
            batasWaktu = timing.batasWaktu,
            petugasScan = actor
        ) ?: AbsensiRecord(
            id = recId,
            tanggal = st.absensiTanggal,
            sesi = st.absensiSesi,
            tipe = tipe,
            targetId = targetId,
            firqohId = firqohId,
            status = status,
            catatan = scanNote,
            waktuScan = timing.waktuScan,
            metode = "Scan QR/Barcode",
            terlambat = timing.terlambat,
            menitTelat = timing.menitTelat,
            batasWaktu = timing.batasWaktu,
            petugasScan = actor
        )
        repository.mutate(listOf(PendingMutation("absensi", recId, "upsert"))) { db ->
            val list = db.absensi.toMutableList()
            val idx = list.indexOfFirst { it.id == recId }
            if (idx >= 0) list[idx] = updatedRec else list.add(updatedRec)
            db.copy(absensi = list)
        }

        val roleTag = when (tipe) {
            "santri" -> "Santri"
            "muallim" -> "Mu'allim"
            else -> "Dewan"
        }
        val notifTitle = if (timing.terlambat) {
            "⏰ [$roleTag] $nama TERLAMBAT (+${timing.menitTelat} mnt)"
        } else {
            "✅ [$roleTag] $nama ($status)"
        }
        val notifBody = "$firqohDisplay • Sesi ${st.absensiSesi} • Pukul $nowHms WIB" +
            if (timing.batasWaktu.isNotBlank()) " (Batas ${timing.batasWaktu})" else ""
        val perPersonNotifId = ("SCAN_${tipe}_${targetId}_${st.absensiTanggal}_${st.absensiSesi}".hashCode() and 0x7FFFFFFF) + 1000
        repository.emitRealtimeAlert(
            RealtimeAlertItem(
                title = notifTitle,
                body = notifBody,
                category = "absensi",
                notifId = perPersonNotifId
            )
        )

        val toastText = if (timing.terlambat) {
            "⏰ $nama ($firqohDisplay): $status TERLAMBAT +${timing.menitTelat} mnt ($nowHms WIB)"
        } else {
            "✅ $nama ($firqohDisplay): $status Tepat Waktu ($nowHms WIB)"
        }
        showToast(toastText, isError = timing.terlambat)
        return timing
    }

    // =========================================================================
    // JUKNIS / MATERI RAPAT PDF & REKAP SANTRI BERMASALAH (LIVE RULE ENGINE)
    // =========================================================================
    fun getActiveJuknisAcuan(): JuknisDocumentItem = JuknisRuleEngine.getActiveAcuan(database.value.juknisDocs)

    fun getProblematicSantri(
        firqohFilter: String,
        periodFilter: String = "ALL",
        referenceDate: String = MmqaConstants.todayStr()
    ): List<SantriViolationResult> {
        val db = database.value
        val ruleDoc = JuknisRuleEngine.getActiveAcuan(db.juknisDocs)
        return db.santri
            .filter { matchesFirqohFilter(it.firqohId, firqohFilter) }
            .mapNotNull { santri ->
                val stats = calcAttendanceStatsByPeriod("santri", santri.id, null, periodFilter, referenceDate)
                JuknisRuleEngine.evaluateSantri(
                    santri = santri,
                    hadir = stats.hadir,
                    izin = stats.izin,
                    sakit = stats.sakit,
                    alpha = stats.alpha,
                    acuan = ruleDoc,
                    firqohName = getFirqohName(santri.firqohId)
                )
            }
            .sortedWith(
                compareByDescending<SantriViolationResult> { it.severityLevel }
                    .thenByDescending { it.alphaCount }
                    .thenByDescending { it.izinCount }
                    .thenBy { it.santri.nama }
            )
    }

    fun saveJuknisDoc(
        id: String?,
        judul: String,
        kategori: String,
        nomorDokumen: String,
        tanggal: String,
        deskripsi: String,
        pdfName: String,
        pdfDataUrl: String,
        isPublicOnLogin: Boolean,
        isAcuanAturan: Boolean,
        batasAlphaSp1: Int,
        batasAlphaSp2: Int,
        batasAlphaSp3: Int,
        batasIzinPeringatan: Int,
        konsekuensiSp1: String,
        konsekuensiSp2: String,
        konsekuensiSp3: String,
        konsekuensiIzin: String
    ) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "JUK-${System.currentTimeMillis()}"
        val cleanSp1 = batasAlphaSp1.coerceAtLeast(1)
        val cleanSp2 = batasAlphaSp2.coerceAtLeast(cleanSp1 + 1)
        val cleanSp3 = batasAlphaSp3.coerceAtLeast(cleanSp2 + 1)
        val cleanIzin = batasIzinPeringatan.coerceAtLeast(1)
        val newItem = JuknisDocumentItem(
            id = itemId,
            judul = judul.trim(),
            kategori = kategori.trim().ifBlank { "Juknis" },
            nomorDokumen = nomorDokumen.trim(),
            tanggal = tanggal.ifBlank { MmqaConstants.todayStr() },
            deskripsi = deskripsi.trim(),
            pdfName = pdfName.trim(),
            pdfDataUrl = pdfDataUrl,
            uploadedBy = _uiState.value.currentUser?.name ?: "Dewan Harian",
            isPublicOnLogin = isPublicOnLogin,
            isAcuanAturan = isAcuanAturan,
            batasAlphaSp1 = cleanSp1,
            batasAlphaSp2 = cleanSp2,
            batasAlphaSp3 = cleanSp3,
            batasIzinPeringatan = cleanIzin,
            konsekuensiSp1 = konsekuensiSp1.trim().ifBlank { "Teguran Lisan & Pembinaan Mu'allim" },
            konsekuensiSp2 = konsekuensiSp2.trim().ifBlank { "Surat Peringatan (SP1) & Panggilan Wali" },
            konsekuensiSp3 = konsekuensiSp3.trim().ifBlank { "Sidang Dewan Harian & Takzir Berat" },
            konsekuensiIzin = konsekuensiIzin.trim().ifBlank { "Evaluasi Kehadiran & Konfirmasi Wali Santri" }
        )

        val mutations = mutableListOf(PendingMutation("juknisDocs", itemId, "upsert"))
        repository.mutate(mutations) { db ->
            val updatedExisting = db.juknisDocs.map { doc ->
                if (isAcuanAturan && doc.id != itemId && doc.isAcuanAturan) {
                    mutations.add(PendingMutation("juknisDocs", doc.id, "upsert"))
                    doc.copy(isAcuanAturan = false)
                } else doc
            }.toMutableList()
            val idx = updatedExisting.indexOfFirst { it.id == itemId }
            if (idx >= 0) updatedExisting[idx] = newItem else updatedExisting.add(0, newItem)
            db.copy(juknisDocs = updatedExisting)
        }
        showToast(
            if (isAcuanAturan) "Juknis disimpan & aturan Dashboard otomatis disinkronkan!"
            else "Dokumen Juknis/Materi Rapat berhasil disimpan!"
        )
    }

    fun setActiveJuknisAcuan(docId: String) {
        val dbSnapshot = database.value
        if (dbSnapshot.juknisDocs.none { it.id == docId }) return
        val mutations = dbSnapshot.juknisDocs
            .filter { it.id == docId || it.isAcuanAturan }
            .map { PendingMutation("juknisDocs", it.id, "upsert") }
        repository.mutate(mutations) { db ->
            db.copy(
                juknisDocs = db.juknisDocs.map { doc ->
                    doc.copy(isAcuanAturan = doc.id == docId)
                }
            )
        }
        showToast("Acuan peraturan Dashboard berhasil diperbarui!")
    }

    fun deleteJuknisDoc(id: String) {
        repository.mutate(listOf(PendingMutation("juknisDocs", id, "delete"))) { db ->
            db.copy(juknisDocs = db.juknisDocs.filterNot { it.id == id })
        }
        showToast("Dokumen Juknis dihapus.")
    }

    // =========================================================================
    // MENU JADWAL KEGIATAN (MMQA HARIAN, UJIAN, ABSENSI, EVENT)
    // =========================================================================
    fun saveJadwal(
        id: String?,
        kategori: String,
        judul: String,
        hari: String,
        tanggal: String,
        jamMulai: String,
        jamSelesai: String,
        tempat: String,
        firqohId: String,
        penanggungJawab: String,
        keterangan: String,
        isPublicOnLogin: Boolean
    ) {
        val itemId = id?.takeIf { it.isNotBlank() } ?: "JDW-${System.currentTimeMillis()}"
        val item = JadwalItem(
            id = itemId,
            kategori = kategori.trim().ifBlank { "MMQA Harian" },
            judul = judul.trim(),
            hari = hari.trim().ifBlank { "Setiap Hari" },
            tanggal = tanggal.trim(),
            jamMulai = jamMulai.trim().ifBlank { "07:00" },
            jamSelesai = jamSelesai.trim().ifBlank { "08:30" },
            tempat = tempat.trim().ifBlank { "Aula Utama MMQA" },
            firqohId = firqohId.ifBlank { "ALL" },
            penanggungJawab = penanggungJawab.trim().ifBlank { _uiState.value.currentUser?.name ?: "Dewan Harian" },
            keterangan = keterangan.trim(),
            isPublicOnLogin = isPublicOnLogin
        )
        repository.mutate(listOf(PendingMutation("jadwal", itemId, "upsert"))) { db ->
            val list = db.jadwal.toMutableList()
            val idx = list.indexOfFirst { it.id == itemId }
            if (idx >= 0) list[idx] = item else list.add(0, item)
            db.copy(jadwal = list)
        }
        showToast("Jadwal kegiatan berhasil disimpan!")
    }

    fun deleteJadwal(id: String) {
        repository.mutate(listOf(PendingMutation("jadwal", id, "delete"))) { db ->
            db.copy(jadwal = db.jadwal.filterNot { it.id == id })
        }
        showToast("Jadwal dihapus.")
    }
}
