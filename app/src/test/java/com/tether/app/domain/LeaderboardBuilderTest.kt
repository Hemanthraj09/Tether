package com.tether.app.domain

import com.tether.app.domain.LeaderboardBuilder.StreakInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardBuilderTest {

    private val today = "2026-05-10"

    private fun build(
        members: List<String> = listOf("a", "b", "c"),
        names: Map<String, String> = mapOf("a" to "Asha Rao", "b" to "Bharat", "c" to "Chitra K"),
        todayHours: Map<String, Double> = emptyMap(),
        yesterdayHours: Map<String, Double> = emptyMap(),
        weeklyHours: Map<String, Double> = emptyMap(),
        streaks: Map<String, StreakInfo> = emptyMap(),
        nudged: Set<String> = emptySet(),
        me: String = "a"
    ) = LeaderboardBuilder.build(members, names, todayHours, yesterdayHours, weeklyHours,
        streaks, nudged, me, today)

    @Test
    fun `ranks members by today's hours, descending`() {
        val entries = build(todayHours = mapOf("a" to 1.0, "b" to 3.5, "c" to 2.0))
        assertEquals(listOf("b", "c", "a"), entries.map { it.uid })
    }

    @Test
    fun `ties keep the group's member order`() {
        val entries = build(todayHours = mapOf("a" to 2.0, "b" to 2.0, "c" to 2.0))
        assertEquals(listOf("a", "b", "c"), entries.map { it.uid })
    }

    @Test
    fun `members without stats get zero hours and no streak`() {
        val entry = build().first { it.uid == "c" }
        assertEquals(0.0, entry.todayHours, 0.0)
        assertEquals(0.0, entry.hours, 0.0)
        assertEquals(0, entry.streak)
    }

    @Test
    fun `missing user document shows as Unknown`() {
        val entry = build(names = emptyMap()).first()
        assertEquals("Unknown", entry.name)
        assertEquals("U", entry.initials)
    }

    @Test
    fun `weekly hours, initials and current user are mapped`() {
        val entries = build(weeklyHours = mapOf("a" to 10.5))
        val me = entries.first { it.uid == "a" }
        assertEquals(10.5, me.hours, 0.0)
        assertEquals("AR", me.initials)
        assertTrue(me.isCurrentUser)
        assertFalse(entries.first { it.uid == "b" }.isCurrentUser)
    }

    @Test
    fun `broken streaks display as 0, active ones as stored`() {
        val entries = build(streaks = mapOf(
            "a" to StreakInfo(5, "2026-05-09"),   // logged yesterday → active
            "b" to StreakInfo(8, "2026-05-01")    // stale → broken
        ))
        assertEquals(5, entries.first { it.uid == "a" }.streak)
        assertEquals(0, entries.first { it.uid == "b" }.streak)
    }

    @Test
    fun `nudged flag comes from today's nudges by the current user`() {
        val entries = build(nudged = setOf("b"))
        assertTrue(entries.first { it.uid == "b" }.hasNudgedToday)
        assertFalse(entries.first { it.uid == "c" }.hasNudgedToday)
    }

    @Test
    fun `pace label only when behind a meaningful yesterday`() {
        assertEquals("1h 30m behind yesterday", LeaderboardBuilder.paceLabel(0.5, 2.0))
        assertEquals("45m behind yesterday", LeaderboardBuilder.paceLabel(1.25, 2.0))
        assertEquals("", LeaderboardBuilder.paceLabel(2.0, 2.0))   // caught up
        assertEquals("", LeaderboardBuilder.paceLabel(3.0, 2.0))   // ahead
        assertEquals("", LeaderboardBuilder.paceLabel(0.0, 0.5))   // yesterday too small
    }

    @Test
    fun `pace label is attached to entries`() {
        val entries = build(todayHours = mapOf("a" to 1.0), yesterdayHours = mapOf("a" to 3.0))
        assertEquals("2h 0m behind yesterday", entries.first { it.uid == "a" }.paceLabel)
    }
}
