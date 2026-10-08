package com.tether.app.domain

import com.tether.app.domain.SaidVsDid.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class SaidVsDidTest {

    private val groups = mapOf("g1" to "Coding", "g2" to "Coding", "g3" to "Gym", "g4" to "Other")

    private fun compute(
        interests: List<String>,
        targets: Map<String, Int> = emptyMap(),
        hours: Map<String, Double> = emptyMap(),
        day: Int = 4
    ) = SaidVsDid.compute(interests, targets, groups, hours, day).associateBy { it.area }

    @Test
    fun `hours from every group with the area's goal add up`() {
        val rows = compute(listOf("coding"), mapOf("coding" to 10), mapOf("g1" to 2.0, "g2" to 3.5, "g3" to 4.0))
        assertEquals(5.5, rows.getValue("coding").doneHours, 1e-9)
    }

    @Test
    fun `other groups count toward no focus area`() {
        val rows = compute(listOf("coding", "fitness"), hours = mapOf("g4" to 6.0))
        assertEquals(0.0, rows.getValue("coding").doneHours, 1e-9)
        assertEquals(0.0, rows.getValue("fitness").doneHours, 1e-9)
    }

    @Test
    fun `status follows an even pace over the week`() {
        // Day 4 of 7 with a 7h target → 4h expected; within 80% (3.2h) is on track.
        val t = mapOf("coding" to 7)
        assertEquals(Status.ON_TRACK, compute(listOf("coding"), t, mapOf("g1" to 3.5)).getValue("coding").status)
        val behind = compute(listOf("coding"), t, mapOf("g1" to 1.0)).getValue("coding")
        assertEquals(Status.BEHIND, behind.status)
        assertEquals(3.0, behind.behindBy, 1e-9)
        assertEquals(Status.DONE, compute(listOf("coding"), t, mapOf("g1" to 7.0)).getValue("coding").status)
        assertEquals(Status.NOT_STARTED, compute(listOf("coding"), t).getValue("coding").status)
    }

    @Test
    fun `an area with no matching group says so`() {
        val rows = compute(listOf("reading"))
        assertEquals(Status.NO_GROUP, rows.getValue("reading").status)
        assertEquals(0.0, rows.getValue("reading").behindBy, 1e-9)
    }

    @Test
    fun `rows keep the user's order, default target, and cap the bar at full`() {
        val rows = SaidVsDid.compute(listOf("fitness", "coding"), emptyMap(), groups, mapOf("g1" to 20.0), 7)
        assertEquals(listOf("fitness", "coding"), rows.map { it.area })
        assertEquals(Interests.DEFAULT_TARGET_HOURS.toDouble(), rows[0].targetHours, 1e-9)
        assertEquals(1.0, rows[1].fraction, 1e-9)
    }
}
