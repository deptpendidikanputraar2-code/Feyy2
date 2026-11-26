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

data class MmqaUiState(
    val currentUser: UserAccount? = null,
    val activeMenu: String = "dashboard",
    val loginError: String? = null,
    val dashFirqohFilter: String = "ALL",
    val absensiTab: String = "santri",
    val absensiTanggal: String = MmqaConstants.todayStr(),
    val absensiSesi: String = "Pagi",
    val absensiFirqoh: String = "FRQ-1",
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
    val incomingChatAlerts: SharedFlow<List<ChatMessageItem>> = repository.incomingChatAlerts

    private val _uiState = MutableStateFlow(MmqaUiState())
    val uiState: StateFlow<MmqaUiState> = _uiState.asStateFlow()

    private val _toastFlow = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 8)
    val toastFlow: SharedFlow<ToastMessage> = _toastFlow.asSharedFlow()

    private val _supabaseStatus = MutableStateFlow(repository.getSupabaseStatus())
    val supabaseStatus: StateFlow<SupabaseStatus> = _supabaseStatus.asStateFlow()

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
        _uiState.update { state ->
            state.copy(
                currentUser = user,
                loginError = null,
                activeMenu = "dashboard",
                absensiFirqoh = if (user.role == "muallim" && user.firqohId.isNotBlank()) user.firqohId else state.absensiFirqoh
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
        return allowedRole == "all" || allowedRole == userRole
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

    // =========================================================================
    // ABSENSI ACTIONS
    // =========================================================================
    fun getAbsensiRecord(tipe: String, targetId: String): AbsensiRecord? {
        val st = _uiState.value
        return database.value.absensi.firstOrNull {
            it.tipe == tipe && it.targetId == targetId && it.tanggal == st.absensiTanggal && it.sesi == st.absensiSesi
        }
    }

    fun setAbsensiStatus(tipe: String, targetId: String, firqohId: String, status: String) {
        val st = _uiState.value
        val existing = getAbsensiRecord(tipe, targetId)
        val recId = existing?.id ?: "ABS-${System.currentTimeMillis()}-${(100..999).random()}"
        val updatedRec = existing?.copy(status = status) ?: AbsensiRecord(
            id = recId,
            tanggal = st.absensiTanggal,
            sesi = st.absensiSesi,
            tipe = tipe,
            targetId = targetId,
            firqohId = firqohId,
            status = status,
            catatan = ""
        )
        repository.mutate(listOf(PendingMutation("absensi", recId, "upsert"))) { db ->
            val list = db.absensi.toMutableList()
            val idx = list.indexOfFirst { it.id == recId }
            if (idx >= 0) list[idx] = updatedRec else list.add(updatedRec)
            db.copy(absensi = list)
        }
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
    fun getKkmForFirqoh(firqohId: String): Int =
        database.value.ujianMateri.firstOrNull { it.firqohId == firqohId }?.kkm ?: 75

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

    fun onChangeSuratDraftKode(kode: String) {
        val tpl = database.value.suratTemplates.firstOrNull { it.kodeSurat == kode }
        _uiState.update { st ->
            st.copy(
                suratDraftKode = kode,
                suratDraftNomor = suggestNextNomorSurat(kode),
                suratDraftTemplateId = tpl?.id ?: st.suratDraftTemplateId,
                suratDraftPerihal = tpl?.perihalDefault ?: st.suratDraftPerihal,
                suratDraftIsi = tpl?.isiDefault ?: st.suratDraftIsi
            )
        }
    }

    fun onSelectSuratTemplate(tplId: String) {
        val tpl = database.value.suratTemplates.firstOrNull { it.id == tplId }
        _uiState.update { st ->
            if (tpl != null) {
                st.copy(
                    suratDraftTemplateId = tpl.id,
                    suratDraftKode = tpl.kodeSurat,
                    suratDraftNomor = suggestNextNomorSurat(tpl.kodeSurat),
                    suratDraftPerihal = tpl.perihalDefault,
                    suratDraftIsi = tpl.isiDefault
                )
            } else {
                st.copy(suratDraftTemplateId = "")
            }
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
            pdfDataUrl = tpl?.pdfDataUrl ?: ""
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

    fun saveTemplateSurat(kode: String, nama: String, perihal: String, isi: String, pdfName: String, pdfDataUrl: String) {
        val tplId = "TPL-${System.currentTimeMillis()}"
        val tpl = SuratTemplateItem(tplId, kode, nama.trim(), perihal.trim(), isi, pdfName, pdfDataUrl)
        repository.mutate(listOf(PendingMutation("suratTemplates", tplId, "upsert"))) { db ->
            db.copy(suratTemplates = db.suratTemplates + tpl)
        }
        showToast("Template format surat berhasil disimpan!")
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
            senderRole = if (curUser?.role == "dewan_harian") "Dewan Harian MMQA" else (curUser?.jabatan ?: "Mu'allim"),
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
        firqohId: String
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
        val user = UserAccount(
            id = itemId,
            username = cleanUsername,
            password = password,
            name = name.trim(),
            role = role,
            jabatan = jabatan.trim(),
            firqohId = firqohId,
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
}
