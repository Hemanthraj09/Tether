package com.tether.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatmapGridTest {

    @Test
    fun `levels are one per hour, clamped to 0 to 12`() {
        assertEquals(0, HeatmapGrid.levelFor(0.0))
        assertEquals(1, HeatmapGrid.levelFor(0.25))  // any activity shows
        assertEquals(3, HeatmapGrid.levelFor(3.9))
        assertEquals(12, HeatmapGrid.levelFor(12.0))
        assertEquals(12, HeatmapGrid.levelFor(16.0))
    }

    @Test
    fun `grid covers the whole year in whole Monday-first weeks`() {
        // Jan 1, 2026 is a Thursday → grid starts Mon Dec 29, 2025.
        val grid = HeatmapGrid.build(2026, emptyMap(), today = "")
        assertEquals(0, grid.levels.size % 7)
        assertEquals(53, grid.columns)
        // 3 padding days (Mon–Wed) before Jan 1 are real cells; trailing padding is -1.
        assertTrue(grid.levels.last() == -1 || grid.levels.last() == 0)
    }

    @Test
    fun `hours land on the right cell`() {
        // Jan 1, 2026 is Thursday = row 3 of column 0.
        val grid = HeatmapGrid.build(2026, mapOf("2026-01-01" to 5.0), today = "2026-01-01")
        assertEquals(5, grid.levels[3])
        assertEquals(3, grid.todayIndex)
    }

    @Test
    fun `month labels skip padding days from the previous year`() {
        val grid = HeatmapGrid.build(2026, emptyMap(), today = "")
        // Column 0 starts on Dec 29, 2025 → no "Dec" label; Jan appears at column 1.
        assertTrue(0 !in grid.monthLabels)
        assertEquals("Jan", grid.monthLabels[1])
        assertEquals(12, grid.monthLabels.size)
    }

    @Test
    fun `today outside the year is not marked`() {
        assertEquals(-1, HeatmapGrid.build(2026, emptyMap(), today = "2027-03-01").todayIndex)
    }
}
