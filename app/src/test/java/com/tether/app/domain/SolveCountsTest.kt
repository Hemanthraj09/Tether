package com.tether.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class SolveCountsTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 24 * 60 * 60 * 1000L
    private val weekStart = 1_780_000_000_000L - (1_780_000_000_000L % day) // a UTC midnight
    private val todayStart = weekStart + 3 * day

    private fun lc(at: Long) = CompletionEntry(CompletionSource.LEETCODE, at)

    @Test
    fun `counts verified solves today and this week`() {
        val index = mapOf(
            "two-sum" to lc(todayStart + 1_000),
            "lru-cache" to lc(todayStart + 5_000),
            "valid-anagram" to lc(weekStart + 1_000),      // earlier this week
            "old-one" to lc(weekStart - 1_000)              // last week
        )
        assertEquals(SolveCounts.Counts(today = 2, week = 3),
            SolveCounts.count(index, emptySet(), todayStart, weekStart))
    }

    @Test
    fun `self-reported and contradicted claims never count`() {
        val index = mapOf(
            "two-sum" to lc(todayStart + 1_000),
            "made-up" to lc(todayStart + 2_000),
            "ticked" to CompletionEntry(CompletionSource.SELF, todayStart + 3_000)
        )
        assertEquals(SolveCounts.Counts(today = 1, week = 1),
            SolveCounts.count(index, setOf("made-up"), todayStart, weekStart))
    }

    @Test
    fun `groups verified solves by local day for the heatmap`() {
        val index = mapOf(
            "a" to lc(todayStart + 1_000),
            "b" to lc(todayStart + 2_000),
            "c" to lc(todayStart - 1_000),
            "d" to CompletionEntry(CompletionSource.SELF, todayStart + 3_000)
        )
        val byDay = SolveCounts.byDay(index, timeZone = utc)
        assertEquals(2, byDay.size)
        assertEquals(listOf(1, 2), byDay.toSortedMap().values.toList())
    }

    @Test
    fun `empty index gives zeros`() {
        assertEquals(SolveCounts.Counts(0, 0), SolveCounts.count(emptyMap(), emptySet(), todayStart, weekStart))
        assertEquals(emptyMap<String, Int>(), SolveCounts.byDay(emptyMap(), timeZone = utc))
    }
}
