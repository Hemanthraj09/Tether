package com.tether.app.domain

import com.tether.app.utils.DateKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class InterestsTest {

    @Test
    fun `default group goal follows the first focus area`() {
        assertEquals("Coding", Interests.defaultGoal(listOf("coding", "fitness")))
        assertEquals("Reading", Interests.defaultGoal(listOf("reading", "fitness")))
        assertEquals("Study", Interests.defaultGoal(listOf("studies")))
        assertEquals("Habits", Interests.defaultGoal(listOf("habits")))
        assertEquals("Study", Interests.defaultGoal(null))      // never asked
        assertEquals("Study", Interests.defaultGoal(emptyList())) // skipped
    }

    @Test
    fun `every focus area has exactly one group goal, and back`() {
        Interests.OPTIONS.forEach { option ->
            val goal = Interests.goalForArea(option.id)
            assertNotNull(goal)
            assertTrue(goal in Interests.GOALS)
            assertEquals(option.id, Interests.areaForGoal(goal))
        }
        assertEquals("fitness", Interests.areaForGoal("gym"))     // case-insensitive
        assertNull(Interests.areaForGoal("Other"))                 // counts toward nothing
        assertNull(Interests.areaForGoal(null))
    }

    @Test
    fun `targets are kept for chosen areas only and clamped`() {
        assertEquals(
            mapOf("coding" to 60, "fitness" to Interests.DEFAULT_TARGET_HOURS, "reading" to 1),
            Interests.sanitizeTargets(listOf("coding", "fitness", "reading"),
                mapOf("coding" to 500, "reading" to 0, "work" to 9))
        )
    }

    @Test
    fun `unknown ids are dropped and order follows the option list`() {
        assertEquals(listOf("coding", "fitness"), Interests.sanitize(listOf("fitness", "hacking", "coding")))
    }

    @Test
    fun `week numbering is the same regardless of the phone's region`() {
        // Thu 1 Jan 2026 and Sat 3 Jan are week 1; Sun 4 Jan starts week 2 (Sunday-first).
        fun keyFor(day: Int): String {
            val cal = DateKeys.weekCalendar().apply { clear(); set(2026, Calendar.JANUARY, day) }
            return DateKeys.weekKey(cal)
        }
        assertEquals("2026-W1", keyFor(1))
        assertEquals("2026-W1", keyFor(3))
        assertEquals("2026-W2", keyFor(4))
    }
}
