package com.example

import com.example.data.MmqaConstants
import com.example.data.MmqaDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun predikat_and_sheet_id_match_spec() {
        assertEquals("17q_0Ms9D4kACfs7K5sqU7VP9jmXq8b0SQDNDC8zf6Z8", MmqaConstants.GOOGLE_SHEET_ID)
        assertTrue(MmqaConstants.DEFAULT_GAS_WEBAPP_URL.startsWith("https://script.google.com/macros/s/"))
        assertEquals(17, MmqaConstants.SHEET_NAMES.size)
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
        assertEquals(10, db.adminConfig.menus.size)
        assertEquals(150000L, db.adminConfig.permissions.syahriahNominal)
    }
}
