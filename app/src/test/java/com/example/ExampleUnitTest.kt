package com.example

import com.example.data.FirqohFilterHelper
import com.example.data.FirqohItem
import com.example.data.MmqaConstants
import com.example.data.MmqaDefaults
import com.example.data.SantriItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun predikat_and_sheet_id_match_spec() {
        assertEquals("17q_0Ms9D4kACfs7K5sqU7VP9jmXq8b0SQDNDC8zf6Z8", MmqaConstants.GOOGLE_SHEET_ID)
        assertTrue(MmqaConstants.DEFAULT_GAS_WEBAPP_URL.startsWith("https://script.google.com/macros/s/"))
        assertEquals(18, MmqaConstants.SHEET_NAMES.size)
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
        assertEquals(11, db.adminConfig.menus.size)
        assertTrue(db.adminConfig.menus.any { it.id == "personalia" })
        assertTrue(db.adminConfig.anakSub.any { it.menuId == "personalia" })
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
        // Item yang disimpan langsung untuk grup atau semua firqoh
        assertTrue(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "ALL", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "GROUP:Firqoh Seleksi", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "F1", firqohs))
        assertFalse(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "F3", firqohs))
        assertFalse(FirqohFilterHelper.matchesFilter("GROUP:Firqoh Seleksi", "GROUP:Firqoh Jet Tempur", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("ALL", "F3", firqohs))
        assertTrue(FirqohFilterHelper.matchesFilter("ALL", "GROUP:Firqoh Jet Tempur", firqohs))
    }
}
