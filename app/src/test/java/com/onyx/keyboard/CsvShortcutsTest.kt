package com.onyx.keyboard

import com.onyx.keyboard.data.CsvShortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F17 (PRD) — استيراد الاختصارات من CSV: pure parser behaviour.
 */
class CsvShortcutsTest {

    @Test
    fun `simple comma rows`() {
        val rows = CsvShortcuts.parse("brb,أعود حالاً\nomw,على طريقي")
        assertEquals(2, rows.size)
        assertEquals("brb", rows[0].trigger)
        assertEquals("أعود حالاً", rows[0].expansion)
    }

    @Test
    fun `quoted field with comma inside`() {
        val rows = CsvShortcuts.parse("addr,\"الرياض، السعودية\"")
        assertEquals(1, rows.size)
        assertEquals("الرياض، السعودية", rows[0].expansion)
    }

    @Test
    fun `semicolon delimiter is auto-detected`() {
        val rows = CsvShortcuts.parse("thx;شكرًا جزيلاً\nbrb;أعود حالاً")
        assertEquals(2, rows.size)
        assertEquals("شكرًا جزيلاً", rows[0].expansion)
    }

    @Test
    fun `tab delimiter (spreadsheet paste) is auto-detected`() {
        val rows = CsvShortcuts.parse("thx\tشكرًا\nomw\tعلى طريقي")
        assertEquals(2, rows.size)
    }

    @Test
    fun `header rows are skipped`() {
        val rows = CsvShortcuts.parse(
            "trigger,expansion\nbrb,أعود حالاً\nالمختصر,التوسعة\nomw,على طريقي"
        )
        assertEquals(2, rows.size)
        assertEquals("brb", rows[0].trigger)
    }

    @Test
    fun `malformed and empty lines are skipped`() {
        val rows = CsvShortcuts.parse("\n\nonlyonefield\n,,\nok,يشتغل\n")
        assertEquals(1, rows.size)
        assertEquals("ok", rows[0].trigger)
    }

    @Test
    fun `blank input yields nothing`() {
        assertTrue(CsvShortcuts.parse("").isEmpty())
        assertTrue(CsvShortcuts.parse("   \n\t ").isEmpty())
    }

    @Test
    fun `row cap protects the dictionary`() {
        val many = (1..2000).joinToString("\n") { "k$it,قيمة $it" }
        assertEquals(CsvShortcuts.MAX_ROWS, CsvShortcuts.parse(many).size)
    }
}
