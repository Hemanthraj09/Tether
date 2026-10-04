package com.tether.app.domain

import com.tether.app.domain.StreakCalculator.Streak
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    @Test
    fun `first ever log starts a streak of 1`() {
        assertEquals(Streak(1, 1, "2026-05-10"), StreakCalculator.afterLog(null, "2026-05-10"))
    }

    @Test
    fun `second log on the same day does not change the streak`() {
        val previous = Streak(current = 4, longest = 9, lastLogDate = "2026-05-10")
        assertEquals(previous, StreakCalculator.afterLog(previous, "2026-05-10"))
    }

    @Test
    fun `logging the day after continues the streak`() {
        val previous = Streak(current = 4, longest = 9, lastLogDate = "2026-05-09")
        assertEquals(Streak(5, 9, "2026-05-10"), StreakCalculator.afterLog(previous, "2026-05-10"))
    }

    @Test
    fun `a missed day resets the streak but keeps the longest`() {
        val previous = Streak(current = 12, longest = 12, lastLogDate = "2026-05-07")
        assertEquals(Streak(1, 12, "2026-05-10"), StreakCalculator.afterLog(previous, "2026-05-10"))
    }

    @Test
    fun `continuing past the longest streak raises the record`() {
        val previous = Streak(current = 9, longest = 9, lastLogDate = "2026-05-09")
        assertEquals(10, StreakCalculator.afterLog(previous, "2026-05-10").longest)
    }

    @Test
    fun `streak continues across month and year boundaries`() {
        assertEquals(3, StreakCalculator.afterLog(Streak(2, 2, "2026-02-28"), "2026-03-01").current)
        assertEquals(3, StreakCalculator.afterLog(Streak(2, 2, "2026-12-31"), "2027-01-01").current)
    }

    @Test
    fun `leap day is handled`() {
        assertEquals(3, StreakCalculator.afterLog(Streak(2, 2, "2028-02-28"), "2028-02-29").current)
        assertEquals(4, StreakCalculator.afterLog(Streak(3, 3, "2028-02-29"), "2028-03-01").current)
        // 2027 is not a leap year: Feb 28 → Mar 1 is consecutive.
        assertEquals(3, StreakCalculator.afterLog(Streak(2, 2, "2027-02-28"), "2027-03-01").current)
    }

    @Test
    fun `displayed streak is kept while the last log is today or yesterday`() {
        assertEquals(7, StreakCalculator.displayed(7, "2026-05-10", "2026-05-10"))
        assertEquals(7, StreakCalculator.displayed(7, "2026-05-09", "2026-05-10"))
    }

    @Test
    fun `displayed streak is 0 once a day has been missed`() {
        assertEquals(0, StreakCalculator.displayed(7, "2026-05-08", "2026-05-10"))
        assertEquals(0, StreakCalculator.displayed(7, "", "2026-05-10"))
    }
}
