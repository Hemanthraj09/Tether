package com.tether.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class DateKeysAndFormattersTest {

    private fun usCalendar(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance(Locale.US).apply {
            clear()
            set(year, month, day)
        }

    @Test
    fun `previous day across month, year and leap boundaries`() {
        assertEquals("2026-05-09", DateKeys.previousDay("2026-05-10"))
        assertEquals("2026-02-28", DateKeys.previousDay("2026-03-01"))
        assertEquals("2028-02-29", DateKeys.previousDay("2028-03-01"))
        assertEquals("2025-12-31", DateKeys.previousDay("2026-01-01"))
    }

    @Test
    fun `week key uses the week-year so late December doesn't collide with January`() {
        // Dec 29, 2026 (Tue) is in week 1 of 2027 in the US calendar.
        // Old code produced "2026-W1", the same key as the first week of Jan 2026.
        assertEquals("2027-W1", DateKeys.weekKey(usCalendar(2026, Calendar.DECEMBER, 29)))
        assertEquals("2026-W1", DateKeys.weekKey(usCalendar(2026, Calendar.JANUARY, 1)))
    }

    @Test
    fun `week key mid-year is unchanged from the previous format`() {
        val cal = usCalendar(2026, Calendar.MAY, 3)
        assertEquals("2026-W${cal.get(Calendar.WEEK_OF_YEAR)}", DateKeys.weekKey(cal))
    }

    @Test
    fun `today key is ASCII yyyy-MM-dd`() {
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(DateKeys.today()))
    }

    @Test
    fun `hours are formatted as hours and minutes`() {
        assertEquals("30m", Formatters.formatHours(0.5))
        assertEquals("1h", Formatters.formatHours(1.0))
        assertEquals("1h 30m", Formatters.formatHours(1.5))
        assertEquals("5h 55m", Formatters.formatHours(5.9167))
        assertEquals("0m", Formatters.formatHours(0.0))
    }

    @Test
    fun `initials take the first letter of up to two words`() {
        assertEquals("HR", Formatters.initials("hemanth raj"))
        assertEquals("A", Formatters.initials("Asha"))
        assertEquals("AB", Formatters.initials("Asha Bharat Chitra"))
    }

    @Test
    fun `avatar colour is stable per user`() {
        assertEquals(Formatters.avatarColor("uid-123"), Formatters.avatarColor("uid-123"))
        assertTrue(Formatters.avatarColor("x").startsWith("#"))
    }
}
