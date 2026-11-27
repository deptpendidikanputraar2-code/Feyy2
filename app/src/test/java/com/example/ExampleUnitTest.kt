package com.example

import com.example.data.AttendancePeriodHelper
import com.example.data.AttendanceTimeEvaluator
import com.example.data.DewanHarianItem
import com.example.data.FirqohFilterHelper
import com.example.data.FirqohItem
import com.example.data.JuknisDocumentItem
import com.example.data.JuknisRuleEngine
import com.example.data.MmqaConstants
import com.example.data.MmqaDefaults
import com.example.data.MuallimItem
import com.example.data.PersonaliaItem
import com.example.data.SantriItem
import com.example.ui.components.MmqaBarcodeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun predikat_and_sheet_id_match_spec() {
        assertEquals("17q_0Ms9D4kACfs7K5sqU7VP9jmXq8b0SQDNDC8zf6Z8", MmqaConstants.GOOGLE_SHEET_ID)
        assertTrue(MmqaConstants.DEFAULT_GAS_WEBAPP_URL.startsWith("https://script.google.com/macros/s/"))
        assertEquals(20, MmqaConstants.SHEET_NAMES.size)
        assertTrue(MmqaConstants.SHEET_NAMES.contains("juknisDocs"))
        assertTrue(MmqaConstants.SHEET_NAMES.contains("jadwal"))
        assertEquals("Mumtaz", MmqaConstants.getPredikatNilai(90.0))
        assertEquals("Jayyid Jiddan", MmqaConstants.getPredikatNilai(84.8))
        assertEquals("Jayyid", MmqaConstants.getPredikatNilai(75.0))
        assertEquals("Maqbul", MmqaConstants.getPredikatNilai(60.0))
        assertEquals("Rosib", MmqaConstants.getPredikatNilai(59.9))
        assertEquals("Kode A — Undangan", MmqaConstants.getKodeSuratLabel("A"))
    }

    @Test
    fun default_database_contains_complete_initial_records() {
        val db = MmqaDefaults.createDefaultDatabase()
        assertEquals(5, db.users.size)
        assertEquals(4, db.firqoh.size)
        assertEquals(12, db.santri.size)
        assertEquals(4, db.muallim.size)
        assertEquals(4, db.dewanHarian.size)
        assertTrue(db.personalia.isNotEmpty())
        assertTrue(db.juknisDocs.isNotEmpty())
        assertTrue(db.jadwal.isNotEmpty())
        assertEquals(13, db.adminConfig.menus.size)
        assertTrue(db.adminConfig.menus.any { it.id == "personalia" })
        assertTrue(db.adminConfig.menus.any { it.id == "jadwal" })
        assertTrue(db.adminConfig.menus.any { it.id == "juknis" })
        assertTrue(db.adminConfig.anakSub.any { it.menuId == "personalia" })
        assertTrue(db.adminConfig.anakSub.any { it.id == "dashboard_keuangan_view" })
        assertTrue(db.adminConfig.anakSub.any { it.id == "juknis_upload" })
        assertTrue(db.suratTemplates.all { it.tampilkanSekretaris && it.sekretarisDefault.isNotBlank() && it.pedomanFormat.isNotBlank() })
        assertEquals(150000L, db.adminConfig.permissions.syahriahNominal)
    }

    @Test
    fun firqoh_group_filter_supports_seleksi_and_jet_tempur() {
        val firqohs = listOf(
            FirqohItem("F1", "S1", "Firqoh Seleksi - Kelas 1A", "Seleksi", "Pagi", "R1"),
            FirqohItem("F2", "S2", "Firqoh Seleksi - Kelas 1B", "Seleksi", "Pagi", "R2"),
            FirqohItem("F3", "JT1", "Firqoh Jet Tempur 1", "Jet Tempur", "Malam", "R3"),
            FirqohItem("F4", "JT2", "Firqoh Jet Tempur 2", "Jet Tempur", "Malam", "R4")
        )
        val santris = listOf(
            SantriItem("S1", "01", "A", "F1", "2026", "W1", "081"),
            SantriItem("S2", "02", "B", "F3", "2026", "W2", "082")
        )
        val options = FirqohFilterHelper.buildFilterOptions(firqohs, santris, includeAll = true, countBySantri = false)
        assertTrue(options.any { it.first == "GROUP:Firqoh Seleksi" && it.second.contains("Semua Firqoh Seleksi") })
        assertTrue(options.any { it.first == "GROUP:Firqoh Jet Tempur" && it.second.contains("Semua Firqoh Jet Tempur") })
        assertTrue(FirqohFilterHelper.matchesFilter("F1", "GROUP:Firqoh Seleksi", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("F2", "GROUP:Firqoh Seleksi", firqohs))
        assertFalse(FirqohFilterHelper.matchesFilter("F3", "GROUP:Firqoh Seleksi", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("F3", "GROUP:Firqoh Jet Tempur", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("F1", "ALL", firqohs))
        assertEquals("Semua Firqoh Seleksi", FirqohFilterHelper.getDisplayLabel("GROUP:Firqoh Seleksi", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "ALL", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "GROUP:Firqoh Seleksi", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "F1", firqohs))
        assertFalse(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "F3", firqohs))
        assertFalse(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "GROUP:Firqoh Jet Tempur", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("ALL", "F3", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("ALL", "GROUP:Firqoh Jet Tempur", firqohs))
    }

    @Test
    fun juknis_rule_engine_syncs_consequences_dynamically() {
        val santri = SantriItem("S-1", "202601", "Ahmad Fulan", "F1", "2026", "Wali", "0812")
        val acuanAwal = JuknisDocumentItem(
            id = "JUK-1",
            judul = "Juknis Versi 1",
            isAcuanAturan = true,
            batasAlphaSp1 = 3,
            batasAlphaSp2 = 5,
            batasAlphaSp3 = 8,
            batasIzinPeringatan = 4,
            konsekuensiSp1 = "Teguran lisan versi 1",
            konsekuensiSp2 = "SP-1 versi 1",
            konsekuensiSp3 = "Sidang Dewan versi 1",
            konsekuensiIzin = "Pembatasan izin versi 1"
        )
        // Alpha 2 & Izin 2 belum melanggar batas awal
        assertNull(JuknisRuleEngine.evaluateSantri(santri, hadir = 10, izin = 2, sakit = 0, alpha = 2, acuan = acuanAwal))

        // Alpha 3 melanggar SP1
        val res1 = JuknisRuleEngine.evaluateSantri(santri, hadir = 10, izin = 1, sakit = 0, alpha = 3, acuan = acuanAwal)
        assertNotNull(res1)
        assertEquals(1, res1!!.severityRank)
        assertEquals("Teguran lisan versi 1", res1.konsekuensi)

        // Jika Juknis diubah (batas Alpha lebih ketat & konsekuensi baru), hasil otomatis berubah
        val acuanBaru = acuanAwal.copy(
            judul = "Juknis Revisi Rapat",
            batasAlphaSp1 = 2,
            konsekuensiSp1 = "Hafalan tambahan & pembinaan khusus"
        )
        val res2 = JuknisRuleEngine.evaluateSantri(santri, hadir = 10, izin = 1, sakit = 0, alpha = 2, acuan = acuanBaru)
        assertNotNull(res2)
        assertEquals("Hafalan tambahan & pembinaan khusus", res2!!.konsekuensi)
        assertEquals("Juknis Revisi Rapat", res2.acuanJudul)
    }

    @Test
    fun barcode_engine_auto_generates_and_updates_from_database() {
        val baseDb = MmqaDefaults.createDefaultDatabase().copy(
            firqoh = listOf(
                FirqohItem("F1", "F01", "Firqoh Ula A", "Ula", "Pagi", "Aula 1"),
                FirqohItem("F2", "F02", "Firqoh Wustho B", "Wustho", "Malam", "Aula 2")
            ),
            santri = listOf(
                SantriItem("SNT-1", "1001", "Zaid Bin Tsabit", "F1", "2026", "Abu Zaid", "0811")
            ),
            muallim = listOf(
                MuallimItem("MLM-1", "M01", "Ust. Hasan", "F1", "Nahwu", "0812"),
                MuallimItem("MLM-2", "M02", "Ust. Husain", "F2", "Fiqih", "0813")
            ),
            dewanHarian = listOf(
                DewanHarianItem("DWH-1", "DH01", "Ust. Ridwan", "Ketua Umum", "0814")
            ),
            personalia = listOf(
                PersonaliaItem("PRS-1", "DH01", "Ust. Ridwan", "Ketua Umum", "PJ Pendidikan & Kurikulum", "0814")
            )
        )

        val santriCode = MmqaBarcodeEngine.codeFor("santri", "SNT-1")
        val cardSantri1 = MmqaBarcodeEngine.resolveScannedCode(baseDb, santriCode)
        assertNotNull(cardSantri1)
        assertEquals("Zaid Bin Tsabit", cardSantri1!!.nama)
        assertEquals("Firqoh Ula A", cardSantri1.primaryDetailValue)
        assertEquals("Ust. Hasan", cardSantri1.secondaryDetailValue)

        // Simulasikan santri pindah firqoh di database: kode barcode tetap sama, tapi info otomatis sinkron!
        val updatedDb = baseDb.copy(
            santri = listOf(baseDb.santri.first().copy(firqohId = "F2"))
        )
        val cardSantri2 = MmqaBarcodeEngine.resolveScannedCode(updatedDb, santriCode)
        assertNotNull(cardSantri2)
        assertEquals("Firqoh Wustho B", cardSantri2!!.primaryDetailValue)
        assertEquals("Ust. Husain", cardSantri2.secondaryDetailValue)

        // Mu'allim barcode wajib menampilkan Firqoh
        val cardMuallim = MmqaBarcodeEngine.resolveScannedCode(baseDb, MmqaBarcodeEngine.codeFor("muallim", "MLM-1"))
        assertNotNull(cardMuallim)
        assertEquals("Firqoh Ula A", cardMuallim!!.primaryDetailValue)

        // Dewan Harian barcode wajib menampilkan PJ/Divisi dan Jabatan
        val cardDewan = MmqaBarcodeEngine.resolveScannedCode(baseDb, MmqaBarcodeEngine.codeFor("dewan_harian", "DWH-1"))
        assertNotNull(cardDewan)
        assertEquals("PJ Pendidikan & Kurikulum", cardDewan!!.primaryDetailValue)
        assertEquals("Ketua Umum", cardDewan.secondaryDetailValue)
    }

    @Test
    fun attendance_period_filter_matches_daily_weekly_monthly_semester_yearly() {
        val ref = "2026-11-26" // Thursday in week Mon 2026-11-23 .. Sun 2026-11-29, Semester Ganjil Jul-Dec 2026
        assertTrue(AttendancePeriodHelper.matchesPeriod("2026-11-26", "HARIAN", ref))
        assertFalse(AttendancePeriodHelper.matchesPeriod("2026-11-25", "HARIAN", ref))

        assertTrue(AttendancePeriodHelper.matchesPeriod("2026-11-23", "MINGGUAN", ref))
        assertTrue(AttendancePeriodHelper.matchesPeriod("2026-11-29", "MINGGUAN", ref))
        assertFalse(AttendancePeriodHelper.matchesPeriod("2026-11-22", "MINGGUAN", ref))

        assertTrue(AttendancePeriodHelper.matchesPeriod("2026-11-01", "BULANAN", ref))
        assertTrue(AttendancePeriodHelper.matchesPeriod("2026-11-30", "BULANAN", ref))
        assertFalse(AttendancePeriodHelper.matchesPeriod("2026-10-31", "BULANAN", ref))

        assertTrue(AttendancePeriodHelper.matchesPeriod("2026-07-01", "SEMESTER", ref))
        assertTrue(AttendancePeriodHelper.matchesPeriod("2026-12-31", "SEMESTER", ref))
        assertFalse(AttendancePeriodHelper.matchesPeriod("2026-06-30", "SEMESTER", ref))

        assertTrue(AttendancePeriodHelper.matchesPeriod("2026-01-01", "TAHUNAN", ref))
        assertFalse(AttendancePeriodHelper.matchesPeriod("2025-12-31", "TAHUNAN", ref))

        assertTrue(AttendancePeriodHelper.matchesPeriod("2024-01-01", "ALL", ref))
        assertEquals("2026-11-27", AttendancePeriodHelper.shiftReferenceDate("HARIAN", ref, 1))
    }

    @Test
    fun attendance_scan_timing_detects_on_time_vs_late_and_wa_phone_normalizes() {
        val onTime = AttendanceTimeEvaluator.evaluateScanTiming("05:30:45", "05:30", "Hadir")
        assertFalse(onTime.terlambat)
        assertEquals(0, onTime.menitTelat)

        val late = AttendanceTimeEvaluator.evaluateScanTiming("05:47:10", "05:30", "Hadir")
        assertTrue(late.terlambat)
        assertEquals(17, late.menitTelat)
        assertEquals("05:30", late.batasWaktu)

        val izin = AttendanceTimeEvaluator.evaluateScanTiming("06:10:00", "05:30", "Izin")
        assertFalse(izin.terlambat)
        assertEquals(0, izin.menitTelat)

        assertEquals("6281234567801", MmqaConstants.normalizeIndoPhoneForWa("0812-3456-7801"))
        assertEquals("6281234567801", MmqaConstants.normalizeIndoPhoneForWa("+62 812 3456 7801"))
        assertEquals("", MmqaConstants.normalizeIndoPhoneForWa("-"))
    }

    @Test
    fun role_access_restricts_dewan_and_muallim_recaps_from_wali_by_default() {
        assertTrue(MmqaConstants.matchesRoleAccess("pengurus", "dewan_harian"))
        assertTrue(MmqaConstants.matchesRoleAccess("pengurus", "muallim"))
        assertFalse(MmqaConstants.matchesRoleAccess("pengurus", "wali_santri"))

        assertTrue(MmqaConstants.matchesRoleAccess("all", "wali_santri"))
        assertTrue(MmqaConstants.matchesRoleAccess("wali_santri", "wali_santri"))
        assertFalse(MmqaConstants.matchesRoleAccess("wali_santri", "muallim"))

        val cfg = MmqaDefaults.defaultAdminConfig()
        assertEquals("pengurus", cfg.anakSub.first { it.id == "dashboard_muallim_rekap_view" }.roles)
        assertEquals("pengurus", cfg.anakSub.first { it.id == "dashboard_dewan_rekap_view" }.roles)
        assertEquals("all", cfg.anakSub.first { it.id == "dashboard_santri_rekap_view" }.roles)
        assertEquals("pengurus", cfg.submenus.absensi.first { it.id == "muallim" }.roles)
        assertEquals("pengurus", cfg.submenus.absensi.first { it.id == "dewan_harian" }.roles)
        assertEquals("all", cfg.submenus.absensi.first { it.id == "santri" }.roles)
    }

    @Test
    fun custom_admin_late_cutoff_applies_per_session() {
        val perm = com.example.data.AdminPermissions(
            batasTelatPagi = "05:45",
            batasTelatSore = "16:15",
            batasTelatMalam = "19:45",
            batasTelatEvent = "09:00"
        )
        assertEquals("05:45", AttendanceTimeEvaluator.defaultCutoffForSession("Pagi", emptyList(), perm))
        assertEquals("16:15", AttendanceTimeEvaluator.defaultCutoffForSession("Sore", emptyList(), perm))
        assertEquals("19:45", AttendanceTimeEvaluator.defaultCutoffForSession("Malam", emptyList(), perm))
        assertEquals("09:00", AttendanceTimeEvaluator.defaultCutoffForSession("Event: Istighotsah", emptyList(), perm))
    }

    @Test
    fun firqoh_mading_pdf_formats_public_info_without_private_wali_phone() {
        val db = MmqaDefaults.createDefaultDatabase().copy(
            firqoh = listOf(
                FirqohItem("F1", "F-01", "Firqoh Ula A", "Ula", "Senin-Sabtu 05:30", "Aula Barat"),
                FirqohItem("F2", "F-02", "Firqoh Wustho B", "Wustho", "Senin-Sabtu 19:30", "Ruang 2")
            ),
            santri = listOf(
                SantriItem("S1", "202601", "Ahmad Zaki", "F1", "2026", "Bpk. Hasyim", "089999111222"),
                SantriItem("S2", "202602", "Budi Santoso", "F2", "2025", "Ibu Aminah", "089999333444")
            ),
            muallim = listOf(
                MuallimItem("M1", "NIY-01", "Ust. Mansur", "F1", "Nahwu Shorof", "081111222")
            )
        )
        val singleHtml = com.example.ui.components.MmqaReportFormatters.buildFirqohMadingPdfHtml(
            db = db,
            filterLabel = "Firqoh Ula A",
            firqohList = listOf(db.firqoh.first())
        )
        assertTrue(singleHtml.contains("PAPAN INFORMASI FIRQOH (MADING)"))
        assertTrue(singleHtml.contains("Firqoh Ula A"))
        assertTrue(singleHtml.contains("Aula Barat"))
        assertTrue(singleHtml.contains("Ust. Mansur"))
        assertTrue(singleHtml.contains("Ahmad Zaki"))
        assertFalse(singleHtml.contains("Firqoh Wustho B"))
        assertFalse(singleHtml.contains("089999111222"))

        val allHtml = com.example.ui.components.MmqaReportFormatters.buildFirqohMadingPdfHtml(
            db = db,
            filterLabel = "Semua Firqoh",
            firqohList = db.firqoh
        )
        assertTrue(allHtml.contains("Firqoh Ula A"))
        assertTrue(allHtml.contains("Firqoh Wustho B"))
        assertTrue(allHtml.contains("Budi Santoso"))
        assertFalse(allHtml.contains("089999333444"))
    }

    @Test
    fun supabase_quota_failure_detector_identifies_storage_limits() {
        assertTrue(com.example.data.SupabaseSyncClient.isQuotaOrStorageFailure(IllegalStateException("Supabase UPSERT error 507: Insufficient Storage")))
        assertTrue(com.example.data.SupabaseSyncClient.isQuotaOrStorageFailure(IllegalStateException("Supabase GET error 402: database size quota exceeded")))
        assertTrue(com.example.data.SupabaseSyncClient.isQuotaOrStorageFailure(IllegalStateException("HTTP 540: Project is paused")))
        assertTrue(com.example.data.SupabaseSyncClient.isQuotaOrStorageFailure(IllegalStateException("Supabase UPSERT error 413: Payload too large")))
        assertFalse(com.example.data.SupabaseSyncClient.isQuotaOrStorageFailure(IllegalStateException("java.net.SocketTimeoutException: timeout")))
        assertFalse(com.example.data.SupabaseSyncClient.isQuotaOrStorageFailure(null))
    }

    @Test
    fun nfc_uid_formatting_and_dual_barcode_nfc_resolution() {
        val rawBytes = byteArrayOf(0xF7.toByte(), 0x22, 0xFF.toByte(), 0x90.toByte())
        assertEquals("F722FF90", com.example.ui.components.NfcCardReaderHelper.formatUidHex(rawBytes))
        assertEquals("F722FF90", com.example.ui.components.NfcCardReaderHelper.normalizeUidHex("f7:22:ff:90"))

        val db = MmqaDefaults.createDefaultDatabase().copy(
            firqoh = listOf(FirqohItem("F1", "F01", "Firqoh Ula", "Ula", "Pagi", "Aula")),
            santri = listOf(
                SantriItem(
                    id = "SNT-99",
                    nis = "202699",
                    nama = "Santri NFC",
                    firqohId = "F1",
                    angkatan = "2026",
                    wali = "Wali NFC",
                    hpWali = "0811",
                    idSantri = "BAR-SNT-9901",
                    uidNfc = "F722FF90"
                )
            ),
            muallim = listOf(
                MuallimItem(
                    id = "MLM-99",
                    niy = "M99",
                    nama = "Muallim NFC",
                    firqohId = "F1",
                    bidang = "Fiqih",
                    hp = "0812",
                    idSantri = "BAR-MLM-9901",
                    uidNfc = "A1B2C3D4"
                )
            ),
            dewanHarian = listOf(
                DewanHarianItem(
                    id = "DWH-99",
                    kode = "DH99",
                    nama = "Dewan NFC",
                    jabatan = "Ketua",
                    hp = "0813",
                    idSantri = "BAR-DWH-9901",
                    uidNfc = "11223344"
                )
            )
        )

        assertEquals("BAR-SNT-9901", MmqaBarcodeEngine.codeFor("santri", "SNT-99", "BAR-SNT-9901"))
        assertEquals("MMQA|SNT|SNT-99", MmqaBarcodeEngine.codeFor("santri", "SNT-99", ""))

        // Resolve via uid_nfc (hex uppercase or colon-separated)
        val byNfcSantri = MmqaBarcodeEngine.resolveScannedCode(db, "F722FF90")
        assertNotNull(byNfcSantri)
        assertEquals("SNT-99", byNfcSantri!!.targetId)

        val byNfcMuallim = MmqaBarcodeEngine.resolveScannedCode(db, "a1:b2:c3:d4")
        assertNotNull(byNfcMuallim)
        assertEquals("MLM-99", byNfcMuallim!!.targetId)

        val byNfcDewan = MmqaBarcodeEngine.resolveScannedCode(db, "11223344")
        assertNotNull(byNfcDewan)
        assertEquals("DWH-99", byNfcDewan!!.targetId)

        // Resolve via custom id_santri barcode
        val byCustomBarSantri = MmqaBarcodeEngine.resolveScannedCode(db, "BAR-SNT-9901")
        assertNotNull(byCustomBarSantri)
        assertEquals("SNT-99", byCustomBarSantri!!.targetId)
        assertEquals("BAR-SNT-9901", byCustomBarSantri.codeValue)

        val byCustomBarMuallim = MmqaBarcodeEngine.resolveScannedCode(db, "BAR-MLM-9901")
        assertNotNull(byCustomBarMuallim)
        assertEquals("MLM-99", byCustomBarMuallim!!.targetId)

        val byCustomBarDewan = MmqaBarcodeEngine.resolveScannedCode(db, "BAR-DWH-9901")
        assertNotNull(byCustomBarDewan)
        assertEquals("DWH-99", byCustomBarDewan!!.targetId)

        // Unregistered NFC returns null
        assertNull(MmqaBarcodeEngine.resolveScannedCode(db, "DEADBEEF"))
    }

    @Test
    fun app_update_version_check_and_url_validation() {
        // HTTPS public URL validation
        assertNull(com.example.update.AppUpdateManager.validateSecureHttpsUrl("https://releases.example.com/mmqa/version.json"))
        assertNotNull(com.example.update.AppUpdateManager.validateSecureHttpsUrl("http://releases.example.com/version.json"))
        assertNotNull(com.example.update.AppUpdateManager.validateSecureHttpsUrl("https://localhost/version.json"))
        assertNotNull(com.example.update.AppUpdateManager.validateSecureHttpsUrl("https://192.168.1.5/version.json"))
        assertNotNull(com.example.update.AppUpdateManager.validateSecureHttpsUrl("file:///sdcard/app.apk"))

        // versionCode comparison
        assertTrue(com.example.update.AppUpdateManager.isNewerVersion(currentVersionCode = 1L, remoteVersionCode = 2L))
        assertFalse(com.example.update.AppUpdateManager.isNewerVersion(currentVersionCode = 2L, remoteVersionCode = 2L))
        assertFalse(com.example.update.AppUpdateManager.isNewerVersion(currentVersionCode = 3L, remoteVersionCode = 2L))
    }
}
