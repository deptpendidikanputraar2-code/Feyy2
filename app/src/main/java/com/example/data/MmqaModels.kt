package com.example.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MmqaConstants {
    const val GOOGLE_SHEET_ID = "17q_0Ms9D4kACfs7K5sqU7VP9jmXq8b0SQDNDC8zf6Z8"
    const val DEFAULT_GAS_WEBAPP_URL =
        "https://script.google.com/macros/s/AKfycbxzzhSwMzRw53zqnk_z9xjAuWf8G-3QsZR9qcV-m_rHENz8bZuNiTrhkE_LV8MTgtsxsg/exec"

    val SHEET_NAMES = listOf(
        "users", "santri", "muallim", "dewanHarian", "firqoh",
        "absensi", "materi", "ujianMateri", "ujianFormat",
        "nilaiUjian", "keuangan", "suratTemplates", "suratArsip",
        "inventaris", "personalia", "chatChannels", "chatMessages", "adminConfig"
    )

    fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun nowTimeHm(): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date())

    fun nowTimeHms(): String =
        SimpleDateFormat("HH:mm:ss", Locale("id", "ID")).format(Date())

    fun formatRupiah(amount: Long): String {
        val formatter = NumberFormat.getInstance(Locale("id", "ID"))
        return "Rp ${formatter.format(amount)}"
    }

    fun getPredikatNilai(nilai: Double): String = when {
        nilai >= 90.0 -> "Mumtaz"
        nilai >= 80.0 -> "Jayyid Jiddan"
        nilai >= 70.0 -> "Jayyid"
        nilai >= 60.0 -> "Maqbul"
        else -> "Rosib"
    }

    fun getKodeSuratLabel(kode: String): String = when (kode) {
        "A" -> "Kode A — Undangan"
        "B" -> "Kode B — Permohonan & Pemberitahuan"
        "C" -> "Kode C — Instruksi"
        else -> "Kode $kode"
    }
}

data class UserAccount(
    val id: String,
    val username: String,
    val password: String,
    val name: String,
    val role: String, // "dewan_harian" | "muallim"
    val jabatan: String,
    val firqohId: String = "",
    val failedAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val passwordUpdatedAt: String = "",
    val avatarBase64: String = "",
    val deniedMenus: List<String> = emptyList(),
    val deniedSubmenus: List<String> = emptyList(),
    val deniedAnakSub: List<String> = emptyList()
)

data class FirqohItem(
    val id: String,
    val kode: String,
    val nama: String,
    val tingkat: String,
    val jadwal: String,
    val ruangan: String
)

data class SantriItem(
    val id: String,
    val nis: String,
    val nama: String,
    val firqohId: String,
    val angkatan: String,
    val wali: String,
    val hpWali: String,
    val statusSyahriah: String = "Belum Lunas",
    val pesertaUjian: Boolean = true
)

data class MuallimItem(
    val id: String,
    val niy: String,
    val nama: String,
    val firqohId: String,
    val bidang: String,
    val hp: String
)

data class DewanHarianItem(
    val id: String,
    val kode: String,
    val nama: String,
    val jabatan: String,
    val hp: String
)

data class PersonaliaItem(
    val id: String,
    val kode: String,
    val nama: String,
    val jabatan: String,
    val divisi: String,
    val periode: String = "2026 - 2027",
    val kontak: String = "",
    val urutan: Int = 1
)

object FirqohFilterHelper {
    const val GROUP_PREFIX = "GROUP:"

    private val separators = listOf(" - ", " — ", " – ", ": ")
    private val trailingTokenRegex = Regex(
        """\s+(?:kelas\s+[\w.-]+|\d+[a-zA-Z]?|[IVX]+|[A-Z])$""",
        RegexOption.IGNORE_CASE
    )

    private fun extractCandidateGroup(f: FirqohItem): Pair<String, Boolean>? {
        val rawName = f.nama.trim()
        if (rawName.isBlank()) return null

        for (sep in separators) {
            val idx = rawName.indexOf(sep)
            if (idx > 0) {
                val prefix = rawName.substring(0, idx).trim()
                if (prefix.isNotBlank() && !prefix.equals("firqoh", ignoreCase = true) && !prefix.last().isDigit()) {
                    val wordCount = prefix.split(Regex("\\s+")).count { it.isNotBlank() }
                    return prefix to (wordCount >= 2)
                }
                // Prefix ended with a digit (e.g. "Firqoh 1") -> not a multi-firqoh category unless stripped works below
                return null
            }
        }

        val stripped = rawName.replace(trailingTokenRegex, "").trim()
        if (stripped != rawName && stripped.isNotBlank() && !stripped.equals("firqoh", ignoreCase = true)) {
            val wordCount = stripped.split(Regex("\\s+")).count { it.isNotBlank() }
            if (wordCount >= 2) {
                return stripped to false
            }
        }
        return null
    }

    fun buildGroupMap(firqohList: List<FirqohItem>): Map<String, List<FirqohItem>> {
        val candidateGroups = linkedMapOf<String, Pair<String, MutableList<FirqohItem>>>()
        val explicitSeparatorFlags = mutableSetOf<String>()

        // 1. Group by Firqoh name prefix
        firqohList.forEach { f ->
            val cand = extractCandidateGroup(f) ?: return@forEach
            val (label, hasExplicitSep) = cand
            val key = label.lowercase()
            val entry = candidateGroups.getOrPut(key) { label to mutableListOf() }
            entry.second.add(f)
            if (hasExplicitSep) explicitSeparatorFlags.add(key)
        }

        // 2. Also allow grouping by tingkat when >= 2 firqohs share exact non-blank tingkat not yet grouped
        val groupedFirqohIds = candidateGroups.values
            .filter { it.second.size >= 2 || it.first.lowercase() in explicitSeparatorFlags }
            .flatMap { it.second.map { f -> f.id } }
            .toSet()

        firqohList
            .filter { it.id !in groupedFirqohIds && it.tingkat.isNotBlank() }
            .groupBy { it.tingkat.trim().lowercase() }
            .forEach { (_, items) ->
                if (items.size >= 2) {
                    val rawTingkat = items.first().tingkat.trim()
                    val groupLabel = if (rawTingkat.startsWith("firqoh", ignoreCase = true)) rawTingkat else "Firqoh $rawTingkat"
                    val key = groupLabel.lowercase()
                    if (key !in candidateGroups) {
                        candidateGroups[key] = groupLabel to items.toMutableList()
                    }
                }
            }

        val result = linkedMapOf<String, List<FirqohItem>>()
        candidateGroups.forEach { (key, pair) ->
            val (displayName, items) = pair
            if (items.size >= 2 || key in explicitSeparatorFlags) {
                result[displayName] = items
            }
        }
        return result
    }

    fun formatGroupFilterLabel(groupName: String, suffix: String? = null): String {
        val clean = groupName.trim()
        val base = if (clean.startsWith("firqoh", ignoreCase = true)) {
            "Semua $clean"
        } else {
            "Semua Firqoh $clean"
        }
        return if (suffix.isNullOrBlank()) base else "$base ($suffix)"
    }

    fun buildFilterOptions(
        firqohList: List<FirqohItem>,
        santriList: List<SantriItem>,
        includeAll: Boolean = true,
        countBySantri: Boolean = false
    ): List<Pair<String, String>> {
        val options = mutableListOf<Pair<String, String>>()
        if (includeAll) {
            val allLabel = if (countBySantri) {
                "Semua Firqoh (${santriList.size} Santri)"
            } else {
                "Semua Firqoh (${firqohList.size} Firqoh)"
            }
            options.add("ALL" to allLabel)
        }

        val groups = buildGroupMap(firqohList)
        groups.forEach { (groupName, members) ->
            val memberIds = members.map { it.id }.toSet()
            val suffix = if (countBySantri) {
                val sc = santriList.count { it.firqohId in memberIds }
                "$sc Santri"
            } else {
                "${members.size} Firqoh"
            }
            options.add("$GROUP_PREFIX$groupName" to formatGroupFilterLabel(groupName, suffix))
        }

        firqohList.forEach { f ->
            val label = if (countBySantri) {
                val sc = santriList.count { it.firqohId == f.id }
                "${f.nama} ($sc Santri)"
            } else {
                f.nama
            }
            options.add(f.id to label)
        }
        return options
    }

    fun matchesFilter(firqohId: String, filter: String, firqohList: List<FirqohItem>): Boolean {
        if (filter.isBlank() || filter == "ALL") return true
        if (firqohId == filter) return true
        if (firqohId == "ALL") return true
        if (firqohId.startsWith(GROUP_PREFIX)) {
            if (filter.startsWith(GROUP_PREFIX)) {
                return firqohId.removePrefix(GROUP_PREFIX).trim()
                    .equals(filter.removePrefix(GROUP_PREFIX).trim(), ignoreCase = true)
            }
            return matchesFilter(filter, firqohId, firqohList)
        }
        if (filter.startsWith(GROUP_PREFIX)) {
            val targetGroup = filter.removePrefix(GROUP_PREFIX).trim()
            val groups = buildGroupMap(firqohList)
            val members = groups.entries.firstOrNull { it.key.equals(targetGroup, ignoreCase = true) }?.value
            if (members != null) {
                return members.any { it.id == firqohId }
            }
            val firqoh = firqohList.firstOrNull { it.id == firqohId } ?: return false
            return firqoh.nama.contains(targetGroup, ignoreCase = true) ||
                firqoh.tingkat.equals(targetGroup, ignoreCase = true)
        }
        return false
    }

    fun getDisplayLabel(filter: String, firqohList: List<FirqohItem>): String {
        if (filter.isBlank() || filter == "ALL") return "Semua Firqoh"
        if (filter.startsWith(GROUP_PREFIX)) {
            return formatGroupFilterLabel(filter.removePrefix(GROUP_PREFIX))
        }
        return firqohList.firstOrNull { it.id == filter }?.nama ?: filter
    }
}

data class AbsensiRecord(
    val id: String,
    val tanggal: String,
    val sesi: String,
    val tipe: String, // "santri" | "muallim" | "dewan_harian"
    val targetId: String,
    val firqohId: String = "",
    val status: String, // "Hadir" | "Izin" | "Sakit" | "Alpha"
    val catatan: String = ""
)

data class MateriItem(
    val id: String,
    val firqohId: String,
    val kitab: String,
    val bab: String,
    val target: String,
    val progress: Int = 0,
    val pengatur: String = "Dewan Harian"
)

data class UjianMateriItem(
    val id: String,
    val firqohId: String,
    val namaUjian: String,
    val kitab: String,
    val kkm: Int = 75,
    val keterangan: String = ""
)

data class UjianFormatItem(
    val id: String,
    val aspek: String,
    val bobot: Int,
    val deskripsi: String
)

data class NilaiUjianItem(
    val id: String,
    val santriId: String,
    val firqohId: String,
    val skorAspek: Map<String, Double>,
    val nilaiAkhir: Double,
    val predikat: String,
    val catatan: String = ""
)

data class KeuanganItem(
    val id: String,
    val tanggal: String,
    val jenis: String, // "pemasukan" | "pengeluaran"
    val kategori: String,
    val nominal: Long,
    val keterangan: String,
    val pencatat: String
)

data class SuratTemplateItem(
    val id: String,
    val kodeSurat: String,
    val nama: String,
    val perihalDefault: String,
    val isiDefault: String,
    val pdfName: String = "",
    val pdfDataUrl: String = ""
)

data class SuratArsipItem(
    val id: String,
    val kodeSurat: String,
    val nomorSurat: String,
    val tanggal: String,
    val lampiran: String = "-",
    val perihal: String,
    val tujuan: String,
    val isi: String,
    val penandatangan: String,
    val jabatanPenandatangan: String,
    val templateId: String = "",
    val pdfName: String = "",
    val pdfDataUrl: String = ""
)

data class InventarisItem(
    val id: String,
    val kodeAset: String,
    val nama: String,
    val kategori: String,
    val jumlah: Int,
    val satuan: String,
    val lokasi: String,
    val kondisi: String, // "Baik" | "Perlu Perawatan" | "Rusak"
    val nilaiEstimasi: Long,
    val pj: String,
    val keterangan: String = ""
)

data class ChatChannelItem(
    val id: String,
    val name: String,
    val desc: String
)

data class ChatReplyRef(
    val id: String,
    val senderName: String,
    val text: String
)

data class ChatAttachmentDraft(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dataUrl: String
)

data class ChatMessageItem(
    val id: String,
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val text: String,
    val time: String,
    val replyTo: ChatReplyRef? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val pinned: Boolean = false,
    val attachmentName: String = "",
    val attachmentMime: String = "",
    val attachmentSize: Long = 0L,
    val attachmentDataUrl: String = ""
)

data class MenuConfigItem(
    val id: String,
    val label: String,
    val icon: String,
    val roles: String, // "all" | "dewan_harian" | "muallim"
    val enabled: Boolean,
    val order: Int
)

data class SubmenuConfigItem(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val roles: String = "all" // "all" | "dewan_harian" | "muallim"
)

data class AnakSubConfigItem(
    val id: String,
    val menuId: String,
    val submenuId: String,
    val label: String,
    val roles: String = "all", // "all" | "dewan_harian" | "muallim"
    val enabled: Boolean = true
)

data class SubmenuGroups(
    val absensi: List<SubmenuConfigItem>,
    val ujian: List<SubmenuConfigItem>,
    val keuangan: List<SubmenuConfigItem>,
    val surat: List<SubmenuConfigItem>,
    val induk: List<SubmenuConfigItem>
)

data class AdminPermissions(
    val muallimCanManageMateri: Boolean = true,
    val muallimCanManageUjianMateri: Boolean = false,
    val muallimCanManageRubrik: Boolean = false,
    val muallimCanInputKeuangan: Boolean = false,
    val muallimCanManageSurat: Boolean = false,
    val muallimCanManageInventaris: Boolean = false,
    val muallimCanManageDataInduk: Boolean = false,
    val allowDeleteRecords: Boolean = true,
    val syahriahNominal: Long = 150000L
)

data class PasswordRule(
    val minLength: Int = 3,
    val requireUppercase: Boolean = false,
    val requireNumber: Boolean = false,
    val requireSpecial: Boolean = false,
    val maxLoginAttempts: Int = 5,
    val expireDays: Int = 0
)

data class AdminConfig(
    val menus: List<MenuConfigItem>,
    val submenus: SubmenuGroups,
    val anakSub: List<AnakSubConfigItem> = MmqaDefaults.defaultAnakSub(),
    val permissions: AdminPermissions,
    val passwordRule: PasswordRule
)

data class MmqaDatabase(
    val gasUrl: String = MmqaConstants.DEFAULT_GAS_WEBAPP_URL,
    val sheetId: String = MmqaConstants.GOOGLE_SHEET_ID,
    val lastSync: String? = null,
    val pendingSync: Boolean = false,
    val users: List<UserAccount> = emptyList(),
    val firqoh: List<FirqohItem> = emptyList(),
    val santri: List<SantriItem> = emptyList(),
    val muallim: List<MuallimItem> = emptyList(),
    val dewanHarian: List<DewanHarianItem> = emptyList(),
    val absensi: List<AbsensiRecord> = emptyList(),
    val materi: List<MateriItem> = emptyList(),
    val ujianMateri: List<UjianMateriItem> = emptyList(),
    val ujianFormat: List<UjianFormatItem> = emptyList(),
    val nilaiUjian: List<NilaiUjianItem> = emptyList(),
    val keuangan: List<KeuanganItem> = emptyList(),
    val suratTemplates: List<SuratTemplateItem> = emptyList(),
    val suratArsip: List<SuratArsipItem> = emptyList(),
    val inventaris: List<InventarisItem> = emptyList(),
    val personalia: List<PersonaliaItem> = emptyList(),
    val chatChannels: List<ChatChannelItem> = emptyList(),
    val chatMessages: List<ChatMessageItem> = emptyList(),
    val adminConfig: AdminConfig = MmqaDefaults.defaultAdminConfig()
)

data class PendingMutation(
    val collection: String,
    val itemId: String,
    val op: String, // "upsert" | "delete"
    val timestamp: Long = System.currentTimeMillis()
)

object MmqaDefaults {
    fun defaultAnakSub(): List<AnakSubConfigItem> = listOf(
        // Absensi
        AnakSubConfigItem("absensi_santri_edit", "absensi", "santri", "Input & Ubah Status Presensi Santri", "all", true),
        AnakSubConfigItem("absensi_santri_mark_all", "absensi", "santri", "Tombol Semua Hadir Santri", "all", true),
        AnakSubConfigItem("absensi_pengurus_edit", "absensi", "muallim", "Input Presensi Mu'allim & Dewan Harian", "dewan_harian", true),
        // Materi
        AnakSubConfigItem("materi_manage", "materi", "utama", "Tambah & Edit Materi Perfirqoh", "all", true),
        AnakSubConfigItem("materi_delete", "materi", "utama", "Hapus Materi Perfirqoh", "dewan_harian", true),
        // Ujian
        AnakSubConfigItem("ujian_peserta_toggle", "ujian", "peserta", "Ubah Status Peserta Ujian Santri", "all", true),
        AnakSubConfigItem("ujian_input_nilai", "ujian", "rekapan", "Input & Edit Nilai Rubrik Ujian", "all", true),
        AnakSubConfigItem("ujian_export_csv", "ujian", "rekapan", "Ekspor CSV Rekapan Ujian", "all", true),
        AnakSubConfigItem("ujian_share_chat", "ujian", "rekapan", "Kirim Ringkasan Ujian ke Grup Chat", "all", true),
        AnakSubConfigItem("ujian_materi_manage", "ujian", "materi_ujian", "Tambah/Edit/Hapus Materi Ujian", "dewan_harian", true),
        AnakSubConfigItem("ujian_rubrik_manage", "ujian", "format", "Tambah/Edit/Hapus Format & Bobot Rubrik", "dewan_harian", true),
        // Keuangan
        AnakSubConfigItem("keuangan_kas_tambah", "keuangan", "kas", "Catat Transaksi Kas Baru", "dewan_harian", true),
        AnakSubConfigItem("keuangan_kas_hapus", "keuangan", "kas", "Hapus Riwayat Transaksi Kas", "dewan_harian", true),
        AnakSubConfigItem("keuangan_export_share", "keuangan", "kas", "Ekspor CSV & Kirim Laporan Kas", "all", true),
        AnakSubConfigItem("keuangan_syahriah_toggle", "keuangan", "syahriah", "Tandai Lunas / Batalkan Syahriah Santri", "dewan_harian", true),
        // Surat
        AnakSubConfigItem("surat_terbitkan", "surat", "buat", "Terbitkan & Arsipkan Surat Resmi", "dewan_harian", true),
        AnakSubConfigItem("surat_cetak_pdf", "surat", "buat", "Cetak & Simpan PDF Surat", "all", true),
        AnakSubConfigItem("surat_arsip_hapus", "surat", "arsip", "Hapus Dokumen Arsip Surat", "dewan_harian", true),
        AnakSubConfigItem("surat_template_kelola", "surat", "template", "Tambah & Upload Template Surat PDF", "dewan_harian", true),
        // Inventaris
        AnakSubConfigItem("inventaris_kelola", "inventaris", "utama", "Tambah & Edit Aset Inventaris", "dewan_harian", true),
        AnakSubConfigItem("inventaris_hapus", "inventaris", "utama", "Hapus Aset Inventaris", "dewan_harian", true),
        AnakSubConfigItem("inventaris_export", "inventaris", "utama", "Ekspor CSV Inventaris", "all", true),
        // Data Induk
        AnakSubConfigItem("induk_kelola", "induk", "santri", "Tambah & Edit Data Induk (Santri/Mu'allim/Dewan/Firqoh)", "dewan_harian", true),
        AnakSubConfigItem("induk_hapus", "induk", "santri", "Hapus Record Data Induk", "dewan_harian", true),
        // Struktur Personalia MMQA
        AnakSubConfigItem("personalia_manage", "personalia", "utama", "Tambah & Edit Struktur Personalia MMQA", "dewan_harian", true),
        AnakSubConfigItem("personalia_delete", "personalia", "utama", "Hapus Pengurus Struktur Personalia", "dewan_harian", true),
        AnakSubConfigItem("personalia_export", "personalia", "utama", "Kirim WA & Unduh PDF Struktur Personalia", "all", true),
        // Grup Chat
        AnakSubConfigItem("chat_send", "chat", "pesan", "Kirim Pesan Teks di Grup Chat", "all", true),
        AnakSubConfigItem("chat_attach", "chat", "pesan", "Kirim Lampiran PDF, Dokumen & Foto", "all", true),
        AnakSubConfigItem("chat_add_channel", "chat", "channel", "Buat Ruang / Channel Diskusi Baru", "dewan_harian", true),
        AnakSubConfigItem("chat_pin", "chat", "pesan", "Sematkan (Pin) Pesan Penting", "dewan_harian", true),
        AnakSubConfigItem("chat_delete", "chat", "pesan", "Hapus Pesan Chat", "all", true)
    )

    fun defaultAdminConfig(): AdminConfig = AdminConfig(
        menus = listOf(
            MenuConfigItem("dashboard", "Dashboard Kehadiran", "📊", "all", true, 1),
            MenuConfigItem("absensi", "Absensi Bertingkat", "📝", "all", true, 2),
            MenuConfigItem("materi", "Materi Perfirqoh", "📚", "all", true, 3),
            MenuConfigItem("ujian", "Ujian & Penilaian", "🎓", "all", true, 4),
            MenuConfigItem("keuangan", "Keuangan & Syahriah", "💰", "all", true, 5),
            MenuConfigItem("surat", "Surat Menyurat", "✉️", "all", true, 6),
            MenuConfigItem("inventaris", "Inventaris Modern", "🏛️", "all", true, 7),
            MenuConfigItem("induk", "Data Induk", "🗂️", "all", true, 8),
            MenuConfigItem("personalia", "Struktur Personalia MMQA", "🧑‍💼", "all", true, 9),
            MenuConfigItem("chat", "Grup Chat Majelis", "💬", "all", true, 10),
            MenuConfigItem("admin", "Kontrol Admin", "🛡️", "dewan_harian", true, 11)
        ),
        submenus = SubmenuGroups(
            absensi = listOf(
                SubmenuConfigItem("santri", "Absensi Santri Perfirqoh", true, "all"),
                SubmenuConfigItem("muallim", "Absensi Mu'allim", true, "all"),
                SubmenuConfigItem("dewan_harian", "Absensi Dewan Harian", true, "all")
            ),
            ujian = listOf(
                SubmenuConfigItem("peserta", "1. Peserta Ujian (Data Induk)", true, "all"),
                SubmenuConfigItem("rekapan", "2. Rekapan Hasil & Input Nilai", true, "all"),
                SubmenuConfigItem("materi_ujian", "3. Materi Ujian Perfirqoh", true, "all"),
                SubmenuConfigItem("format", "4. Format & Bobot Penilaian", true, "all")
            ),
            keuangan = listOf(
                SubmenuConfigItem("kas", "Buku Kas & Transaksi", true, "all"),
                SubmenuConfigItem("syahriah", "Kontrol Syahriah Santri", true, "all")
            ),
            surat = listOf(
                SubmenuConfigItem("buat", "Buat Surat Baru", true, "all"),
                SubmenuConfigItem("arsip", "Arsip Surat (Kode A/B/C)", true, "all"),
                SubmenuConfigItem("template", "Template & Format PDF", true, "all")
            ),
            induk = listOf(
                SubmenuConfigItem("santri", "Data Santri", true, "all"),
                SubmenuConfigItem("muallim", "Data Mu'allim", true, "all"),
                SubmenuConfigItem("dewan", "Data Dewan Harian", true, "all"),
                SubmenuConfigItem("firqoh", "Data Firqoh", true, "all")
            )
        ),
        anakSub = defaultAnakSub(),
        permissions = AdminPermissions(),
        passwordRule = PasswordRule()
    )

    fun createDefaultDatabase(): MmqaDatabase {
        val today = MmqaConstants.todayStr()
        return MmqaDatabase(
            gasUrl = MmqaConstants.DEFAULT_GAS_WEBAPP_URL,
            sheetId = MmqaConstants.GOOGLE_SHEET_ID,
            lastSync = null,
            pendingSync = false,
            users = listOf(
                UserAccount("USR-01", "admin", "123", "Ust. H. Ahmad Fauzi, M.Pd", "dewan_harian", "Ketua Dewan Harian MMQA", "", 0, null, today),
                UserAccount("USR-02", "sekretaris", "123", "Ust. Muhammad Ridwan", "dewan_harian", "Sekretaris Dewan Harian", "", 0, null, today),
                UserAccount("USR-03", "bendahara", "123", "Ust. Abdul Karim", "dewan_harian", "Bendahara Dewan Harian", "", 0, null, today),
                UserAccount("USR-04", "muallim1", "123", "Ust. Zulkifli Hasan", "muallim", "Mu'allim Firqoh 1 (I'dadi)", "FRQ-1", 0, null, today),
                UserAccount("USR-05", "muallim2", "123", "Ust. Syamsul Arifin", "muallim", "Mu'allim Firqoh 2 (Ula)", "FRQ-2", 0, null, today)
            ),
            firqoh = listOf(
                FirqohItem("FRQ-1", "F-01", "Firqoh 1 — I'dadiyah", "Dasar / Pemula", "Ba’da Subuh & Ba’da Maghrib", "Serambi Masjid Utama"),
                FirqohItem("FRQ-2", "F-02", "Firqoh 2 — Ula (Tajwid & Jurumiyah)", "Menengah Awal", "Ba’da Subuh & Ba’da Isya", "Aula Al-Rifai Lt. 1"),
                FirqohItem("FRQ-3", "F-03", "Firqoh 3 — Wustho (Fathul Qorib)", "Menengah Lanjut", "Ba’da Ashar & Ba’da Isya", "Aula Al-Rifai Lt. 2"),
                FirqohItem("FRQ-4", "F-04", "Firqoh 4 — Ulya (Alfiyah & Tafsir)", "Tinggi / Takhassus", "Ba’da Subuh & Ba’da Isya", "Majelis Utama AR2")
            ),
            santri = listOf(
                SantriItem("SNT-01", "2026001", "Ahmad Zaki Mubarok", "FRQ-1", "2026", "Bpk. H. Sulaiman", "081234567801", "Lunas", true),
                SantriItem("SNT-02", "2026002", "Muhammad Farhan Al-Hafidz", "FRQ-1", "2026", "Bpk. Drs. Rahmat", "081234567802", "Lunas", true),
                SantriItem("SNT-03", "2026003", "Fathur Rahman Hakim", "FRQ-1", "2026", "Bpk. Nurdin", "081234567803", "Belum Lunas", true),
                SantriItem("SNT-04", "2025004", "Abdullah Azzam", "FRQ-2", "2025", "Bpk. H. Mansur", "081234567804", "Lunas", true),
                SantriItem("SNT-05", "2025005", "Luqmanul Hakim", "FRQ-2", "2025", "Bpk. Wahyudi", "081234567805", "Lunas", true),
                SantriItem("SNT-06", "2025006", "Salman Al-Farisi", "FRQ-2", "2025", "Bpk. Hendra", "081234567806", "Belum Lunas", true),
                SantriItem("SNT-07", "2024007", "Ibrahim Khalilullah", "FRQ-3", "2024", "Bpk. H. Mahfudz", "081234567807", "Lunas", true),
                SantriItem("SNT-08", "2024008", "Yusuf Maulana", "FRQ-3", "2024", "Bpk. Bambang", "081234567808", "Lunas", true),
                SantriItem("SNT-09", "2024009", "Hamzah Asadullah", "FRQ-3", "2024", "Bpk. Kusnadi", "081234567809", "Lunas", true),
                SantriItem("SNT-10", "2023010", "Umar Mukhtar", "FRQ-4", "2023", "Bpk. KH. Syafi’i", "081234567810", "Lunas", true),
                SantriItem("SNT-11", "2023011", "Zaid Bin Tsabit", "FRQ-4", "2023", "Bpk. H. Burhan", "081234567811", "Lunas", true),
                SantriItem("SNT-12", "2023012", "Bilal Bin Rabah", "FRQ-4", "2023", "Bpk. Supriyadi", "081234567812", "Belum Lunas", true)
            ),
            muallim = listOf(
                MuallimItem("MLM-01", "M-202101", "Ust. Zulkifli Hasan", "FRQ-1", "Tahsin & Mabadi Fiqih", "085211002201"),
                MuallimItem("MLM-02", "M-202102", "Ust. Syamsul Arifin", "FRQ-2", "Nahwu Shorof Dasar", "085211002202"),
                MuallimItem("MLM-03", "M-202003", "Ust. Miftahul Huda", "FRQ-3", "Fiqih Syafi’iyah", "085211002203"),
                MuallimItem("MLM-04", "M-201904", "Ust. Badruddin Al-Hafidz", "FRQ-4", "Tafsir & Ushul Fiqih", "085211002204")
            ),
            dewanHarian = listOf(
                DewanHarianItem("DWH-01", "DH-01", "Ust. H. Ahmad Fauzi, M.Pd", "Ketua Dewan Harian MMQA", "081122334401"),
                DewanHarianItem("DWH-02", "DH-02", "Ust. Muhammad Ridwan", "Sekretaris Umum", "081122334402"),
                DewanHarianItem("DWH-03", "DH-03", "Ust. Abdul Karim", "Bendahara Majelis", "081122334403"),
                DewanHarianItem("DWH-04", "DH-04", "Ust. Khoirul Anam", "Koordinator Kurikulum & Ujian", "081122334404")
            ),
            absensi = listOf(
                AbsensiRecord("ABS-01", today, "Pagi", "santri", "SNT-01", "FRQ-1", "Hadir", ""),
                AbsensiRecord("ABS-02", today, "Pagi", "santri", "SNT-02", "FRQ-1", "Hadir", ""),
                AbsensiRecord("ABS-03", today, "Pagi", "santri", "SNT-03", "FRQ-1", "Izin", "Izin acara keluarga"),
                AbsensiRecord("ABS-04", today, "Pagi", "santri", "SNT-04", "FRQ-2", "Hadir", ""),
                AbsensiRecord("ABS-05", today, "Pagi", "santri", "SNT-05", "FRQ-2", "Hadir", ""),
                AbsensiRecord("ABS-06", today, "Pagi", "santri", "SNT-06", "FRQ-2", "Sakit", "Istirahat di asrama"),
                AbsensiRecord("ABS-07", today, "Pagi", "santri", "SNT-07", "FRQ-3", "Hadir", ""),
                AbsensiRecord("ABS-08", today, "Pagi", "santri", "SNT-08", "FRQ-3", "Hadir", ""),
                AbsensiRecord("ABS-09", today, "Pagi", "santri", "SNT-09", "FRQ-3", "Hadir", ""),
                AbsensiRecord("ABS-10", today, "Pagi", "santri", "SNT-10", "FRQ-4", "Hadir", ""),
                AbsensiRecord("ABS-11", today, "Pagi", "santri", "SNT-11", "FRQ-4", "Hadir", ""),
                AbsensiRecord("ABS-12", today, "Pagi", "santri", "SNT-12", "FRQ-4", "Alpha", ""),
                AbsensiRecord("ABS-13", today, "Pagi", "muallim", "MLM-01", "FRQ-1", "Hadir", "Tepat waktu"),
                AbsensiRecord("ABS-14", today, "Pagi", "muallim", "MLM-02", "FRQ-2", "Hadir", ""),
                AbsensiRecord("ABS-15", today, "Pagi", "muallim", "MLM-03", "FRQ-3", "Hadir", ""),
                AbsensiRecord("ABS-16", today, "Pagi", "muallim", "MLM-04", "FRQ-4", "Izin", "Tugas luar pesantren"),
                AbsensiRecord("ABS-17", today, "Pagi", "dewan_harian", "DWH-01", "", "Hadir", "Piket Harian"),
                AbsensiRecord("ABS-18", today, "Pagi", "dewan_harian", "DWH-02", "", "Hadir", ""),
                AbsensiRecord("ABS-19", today, "Pagi", "dewan_harian", "DWH-03", "", "Hadir", ""),
                AbsensiRecord("ABS-20", today, "Pagi", "dewan_harian", "DWH-04", "", "Hadir", "")
            ),
            materi = listOf(
                MateriItem("MTR-01", "FRQ-1", "Tuhfatul Athfal & Safinatun Najah", "Bab Hukum Nun Mati & Thaharah", "Hafal 30 bait nadhom & praktik wudhu", 80, "Dewan Harian"),
                MateriItem("MTR-02", "FRQ-2", "Matan Al-Ajurumiyah & Amtsilah Tashrifiyah", "Bab Kalam, I’rab & Tashrif Istilahi", "Mampu meng-i’rab kalimat sederhana", 70, "Dewan Harian"),
                MateriItem("MTR-03", "FRQ-3", "Fathul Qorib Al-Mujib", "Kitab Sholat & Zakat", "Membaca kitab gundul & memahami syarah", 65, "Dewan Harian"),
                MateriItem("MTR-04", "FRQ-4", "Alfiyah Ibnu Malik & Tafsir Jalalain", "Bab Mubtada Khobar & Juz Amma", "Hafal 250 bait Alfiyah & murad tafsir", 75, "Dewan Harian")
            ),
            ujianMateri = listOf(
                UjianMateriItem("UJM-01", "FRQ-1", "Ujian Muhafadzoh Tuhfatul Athfal & Fiqih Dasar", "Tuhfatul Athfal & Safinah", 75, "Setoran hafalan & tanya jawab hukum thaharah"),
                UjianMateriItem("UJM-02", "FRQ-2", "Ujian Qira’atul Kutub Al-Ajurumiyah", "Matan Al-Ajurumiyah", 75, "Baca teks tanpa harakat & kedudukan i’rab"),
                UjianMateriItem("UJM-03", "FRQ-3", "Ujian Fathul Qorib Bab Ibadah", "Fathul Qorib", 78, "Membaca, menerjemah, dan istinbath hukum"),
                UjianMateriItem("UJM-04", "FRQ-4", "Ujian Munaqosyah Alfiyah & Tafsir", "Alfiyah Ibnu Malik", 80, "Ujian lisan di hadapan Dewan Penguji")
            ),
            ujianFormat = listOf(
                UjianFormatItem("FMT-01", "Kelancaran Membaca Kitab / Hafalan", 35, "Fashohah, kelancaran bacaan, dan ketepatan harakat akhir"),
                UjianFormatItem("FMT-02", "Kaidah Nahwu, Shorof & Tajwid", 25, "Ketepatan tarkib, i’rab, dan hukum tajwid"),
                UjianFormatItem("FMT-03", "Pemahaman Makna & Murad Kitab", 25, "Kemampuan menjelaskan maksud ibarah dan dalil"),
                UjianFormatItem("FMT-04", "Adab Majelis & Kedisiplinan", 15, "Sikap tawadhu, kerapian, dan kehadiran selama ujian")
            ),
            nilaiUjian = listOf(
                NilaiUjianItem("NIL-01", "SNT-01", "FRQ-1", mapOf("FMT-01" to 92.0, "FMT-02" to 88.0, "FMT-03" to 90.0, "FMT-04" to 95.0), 91.0, "Mumtaz", "Sangat lancar dan fashih"),
                NilaiUjianItem("NIL-02", "SNT-04", "FRQ-2", mapOf("FMT-01" to 85.0, "FMT-02" to 82.0, "FMT-03" to 84.0, "FMT-04" to 90.0), 84.8, "Jayyid Jiddan", "Penguasaan i’rab baik"),
                NilaiUjianItem("NIL-03", "SNT-07", "FRQ-3", mapOf("FMT-01" to 88.0, "FMT-02" to 85.0, "FMT-03" to 86.0, "FMT-04" to 92.0), 87.3, "Jayyid Jiddan", "Mampu menjelaskan syarah dengan runtut")
            ),
            keuangan = listOf(
                KeuanganItem("KEU-01", today, "pemasukan", "Syahriah Santri", 1350000L, "Rekap Syahriah 9 Santri Lunas Bulan Ini", "Ust. Abdul Karim"),
                KeuanganItem("KEU-02", today, "pemasukan", "Infaq & Donatur", 2500000L, "Infaq Wali Santri untuk Pengadaan Kitab & ATK Ujian", "Ust. Abdul Karim"),
                KeuanganItem("KEU-03", today, "pengeluaran", "Operasional Ujian", 650000L, "Cetak Lembar Soal, Kartu Peserta & Konsumsi Penguji", "Ust. Abdul Karim"),
                KeuanganItem("KEU-04", today, "pengeluaran", "Sarana & Kitab", 450000L, "Pembelian Spidol Papan Tulis, Penghapus & Kertas HVS", "Ust. Muhammad Ridwan")
            ),
            suratTemplates = listOf(
                SuratTemplateItem(
                    "TPL-A1", "A", "Template Undangan Rapat Dewan & Mu’allim",
                    "Undangan Rapat Koordinasi Evaluasi KBM & Persiapan Ujian",
                    "Assalamu’alaikum Warahmatullahi Wabarakatuh.\n\nDengan memohon rahmat dan ridho Allah SWT, mengharap dengan hormat kehadiran Bapak/Ustadz pada:\n\nHari / Tanggal : Ahad, 29 November 2026\nWaktu          : Pukul 20.00 WIB (Ba’da Isya)\nTempat         : Majelis Utama MMQA Al Rifai 02\nAcara          : Koordinasi Evaluasi Kehadiran & Teknis Ujian Perfirqoh\n\nDemikian undangan ini kami sampaikan. Atas perhatian dan kehadirannya kami haturkan Jazakumullahu Khairan Katsiran.\n\nWassalamu’alaikum Warahmatullahi Wabarakatuh."
                ),
                SuratTemplateItem(
                    "TPL-B1", "B", "Template Pemberitahuan Wali Santri",
                    "Pemberitahuan Pelaksanaan Ujian Semester & Administrasi Syahriah",
                    "Assalamu’alaikum Warahmatullahi Wabarakatuh.\n\nTeriring doa semoga Bapak/Ibu Wali Santri senantiasa dalam lindungan Allah SWT. Bersama surat ini kami memberitahukan bahwa pelaksanaan Ujian Akhir Semester MMQA Al Rifai 02 akan dilaksanakan mulai pekan depan.\n\nDimohon kepada Bapak/Ibu untuk senantiasa memotivasi putra-putrinya dalam muraja’ah materi perfirqoh.\n\nWassalamu’alaikum Warahmatullahi Wabarakatuh."
                ),
                SuratTemplateItem(
                    "TPL-C1", "C", "Template Instruksi Harian Majelis",
                    "Instruksi Penertiban Absensi Harian & Input Nilai Ujian",
                    "Assalamu’alaikum Warahmatullahi Wabarakatuh.\n\nBerdasarkan hasil musyawarah Dewan Harian MMQA Al Rifai 02, diinstruksikan kepada seluruh Mu’allim Firqoh untuk:\n1. Mengisi presensi santri setiap sesi melalui aplikasi MMQA AR2.\n2. Menyelesaikan input nilai rubrik ujian paling lambat H+3 setelah pelaksanaan ujian.\n\nDemikian instruksi ini disampaikan untuk dilaksanakan dengan penuh tanggung jawab.\n\nWassalamu’alaikum Warahmatullahi Wabarakatuh."
                )
            ),
            suratArsip = listOf(
                SuratArsipItem(
                    "SRT-01", "A", "001/A/MMQA-AR2/XI/2026", today, "1 Berkas",
                    "Undangan Rapat Koordinasi Dewan Harian & Mu’allim",
                    "Seluruh Mu’allim Firqoh 1 - 4 MMQA AR2",
                    "Assalamu’alaikum Warahmatullahi Wabarakatuh.\n\nMengharap dengan hormat kehadiran seluruh Mu’allim Firqoh pada rapat evaluasi pembelajaran dan persiapan ujian perfirqoh di Majelis Utama MMQA Al Rifai 02.\n\nWassalamu’alaikum Warahmatullahi Wabarakatuh.",
                    "Ust. H. Ahmad Fauzi, M.Pd", "Ketua Dewan Harian MMQA", "TPL-A1"
                )
            ),
            inventaris = listOf(
                InventarisItem("INV-01", "INV-AR2-001", "Papan Tulis Whiteboard Besar (240x120)", "Sarana Belajar", 4, "Unit", "Ruang Firqoh 1 - 4", "Baik", 3200000L, "Ust. Muhammad Ridwan", "Terpasang permanen di masing-masing ruang firqoh lengkap dengan rak spidol."),
                InventarisItem("INV-02", "INV-AR2-002", "Kitab Referensi Perpustakaan Majelis", "Kitab & Literatur", 85, "Eksemplar", "Lemari Utama Majelis", "Baik", 6500000L, "Ust. Khoirul Anam", "Meliputi Fathul Qorib, Alfiyah, Tafsir Jalalain, dan kamus Al-Munawwir."),
                InventarisItem("INV-03", "INV-AR2-003", "Sound System & Wireless Mic Majelis", "Elektronik", 2, "Set", "Aula Al-Rifai Lt. 1", "Perlu Perawatan", 4800000L, "Ust. Abdul Karim", "Satu unit mikrofon cadangan perlu penggantian baterai isi ulang.")
            ),
            personalia = listOf(
                PersonaliaItem("PRS-01", "STR-01", "Ust. H. Ahmad Fauzi, M.Pd", "Ketua Dewan Harian MMQA", "Pimpinan & Dewan Harian", "2026 - 2027", "081122334401", 1),
                PersonaliaItem("PRS-02", "STR-02", "Ust. Muhammad Ridwan", "Sekretaris Umum", "Kesekretariatan & Administrasi", "2026 - 2027", "081122334402", 2),
                PersonaliaItem("PRS-03", "STR-03", "Ust. Abdul Karim", "Bendahara Majelis", "Keuangan & Syahriah", "2026 - 2027", "081122334403", 3),
                PersonaliaItem("PRS-04", "STR-04", "Ust. Khoirul Anam", "Koordinator Kurikulum & Ujian", "Kurikulum & Pendidikan", "2026 - 2027", "081122334404", 4),
                PersonaliaItem("PRS-05", "STR-05", "Ust. Zulkifli Hasan", "Koordinator Mu'allim Firqoh", "Koordinator Firqoh", "2026 - 2027", "085211002201", 5)
            ),
            chatChannels = listOf(
                ChatChannelItem("CHN-UMUM", "Majelis Umum MMQA AR2", "Koordinasi seluruh Dewan Harian & Mu’allim"),
                ChatChannelItem("CHN-MUALLIM", "Koordinasi Mu’allim Firqoh", "Diskusi kurikulum, absensi & perkembangan santri"),
                ChatChannelItem("CHN-DEWAN", "Dewan Harian & Kesekretariatan", "Rapat internal, surat menyurat & keuangan")
            ),
            chatMessages = listOf(
                ChatMessageItem(
                    "MSG-01", "CHN-UMUM", "USR-01", "Ust. H. Ahmad Fauzi, M.Pd", "Dewan Harian MMQA",
                    "Assalamu’alaikum Warahmatullahi Wabarakatuh. Selamat datang di ruang koordinasi resmi MMQA Al Rifai 02. Mohon seluruh Mu’allim memastikan presensi harian terinput tepat waktu.",
                    "07:30", null, mapOf("🤲" to 3, "✅" to 4), true
                ),
                ChatMessageItem(
                    "MSG-02", "CHN-UMUM", "USR-04", "Ust. Zulkifli Hasan", "Mu'allim Firqoh 1",
                    "Wa’alaikumussalam Wr. Wb. Siap Ustadz, presensi Firqoh 1 sesi pagi sudah kami input ke sistem.",
                    "07:42", ChatReplyRef("MSG-01", "Ust. H. Ahmad Fauzi, M.Pd", "Assalamu’alaikum Warahmatullahi Wabarakatuh. Selamat datang..."),
                    mapOf("👍" to 2), false
                )
            ),
            adminConfig = defaultAdminConfig()
        )
    }
}
