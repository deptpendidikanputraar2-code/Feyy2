package com.example

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
}
