package com.tether.app.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private val sec = 1_000L
    private val min = 60_000L
    private val t0 = 1_000_000L // arbitrary monotonic start

    private fun advanced(state: TimerState, now: Long) = TimerEngine.advance(state, now).state

    // ── Stopwatch ────────────────────────────────────────────────

    @Test
    fun `stopwatch counts focus time from timestamps`() {
        val s = TimerEngine.start(TimerMode.STOPWATCH, t0)
        val now = t0 + 25 * min + 30 * sec
        assertEquals(25 * min + 30 * sec, TimerEngine.focusMillis(s, now))
        assertEquals(1530, TimerEngine.displaySeconds(s, now))
    }

    @Test
    fun `stopwatch break pauses focus and resumes exactly when the break ends`() {
        var s = TimerEngine.start(TimerMode.STOPWATCH, t0)
        s = TimerEngine.startBreak(s, minutes = 5, now = t0 + 20 * min)!!
        assertEquals(TimerPhase.BREAK, s.phase)

        // During the break, focus is frozen at 20 min and the face counts down.
        assertEquals(20 * min, TimerEngine.focusMillis(s, t0 + 22 * min))
        assertEquals(180, TimerEngine.displaySeconds(s, t0 + 22 * min))

        // Noticed late (screen off): focus resumes from the break's end, not from "now".
        val now = t0 + 40 * min
        s = advanced(s, now)
        assertEquals(TimerPhase.FOCUSING, s.phase)
        assertEquals(20 * min + 15 * min, TimerEngine.focusMillis(s, now))
    }

    @Test
    fun `breaks are only allowed while focusing in stopwatch mode`() {
        val pomodoro = TimerEngine.start(TimerMode.POMODORO, t0)
        assertNull(TimerEngine.startBreak(pomodoro, 5, t0 + min))

        val onBreak = TimerEngine.startBreak(TimerEngine.start(TimerMode.STOPWATCH, t0), 5, t0 + min)!!
        assertNull(TimerEngine.startBreak(onBreak, 10, t0 + 2 * min))
    }

    // ── Pomodoro ─────────────────────────────────────────────────

    @Test
    fun `pomodoro switches from focus to break at the boundary`() {
        val s = TimerEngine.start(TimerMode.POMODORO, t0, focusMinutes = 25, breakMinutes = 5)
        assertEquals(TimerPhase.FOCUSING, advanced(s, t0 + 25 * min - 1).phase)

        val result = TimerEngine.advance(s, t0 + 25 * min)
        assertEquals(TimerPhase.BREAK, result.state.phase)
        assertTrue(result.phaseChanged)
        assertEquals(300, TimerEngine.displaySeconds(result.state, t0 + 25 * min))
    }

    @Test
    fun `pomodoro catches up over many cycles and counts focus only`() {
        val s = TimerEngine.start(TimerMode.POMODORO, t0, focusMinutes = 25, breakMinutes = 5)
        // 2h later: 4 full cycles of 25+5 have passed; now at the start of focus #5.
        val now = t0 + 120 * min
        val after = advanced(s, now)
        assertEquals(TimerPhase.FOCUSING, after.phase)
        assertEquals(100 * min, TimerEngine.focusMillis(after, now))

        // 10 min into the 5th focus block.
        assertEquals(110 * min, TimerEngine.focusMillis(advanced(s, now + 10 * min), now + 10 * min))
    }

    @Test
    fun `pomodoro focus is not counted during breaks`() {
        val s = TimerEngine.start(TimerMode.POMODORO, t0, focusMinutes = 50, breakMinutes = 10)
        val midBreak = t0 + 55 * min
        assertEquals(50 * min, TimerEngine.focusMillis(advanced(s, midBreak), midBreak))
    }

    // ── Break warning ────────────────────────────────────────────

    @Test
    fun `break warning fires once at 2 minutes left`() {
        var s = TimerEngine.startBreak(TimerEngine.start(TimerMode.STOPWATCH, t0), 5, t0)!!

        assertFalse(TimerEngine.advance(s, t0 + 2 * min).warnBreakEnding) // 3 min left

        val warn = TimerEngine.advance(s, t0 + 3 * min)                  // 2 min left
        assertTrue(warn.warnBreakEnding)
        s = warn.state

        assertFalse(TimerEngine.advance(s, t0 + 4 * min).warnBreakEnding)  // already warned
    }

    @Test
    fun `no warning for breaks of 2 minutes or less`() {
        val s = TimerEngine.startBreak(TimerEngine.start(TimerMode.STOPWATCH, t0), 2, t0)!!
        assertFalse(TimerEngine.advance(s, t0 + 30 * sec).warnBreakEnding)
    }

    // ── Persistence across reboot ────────────────────────────────

    @Test
    fun `state is unchanged when there was no reboot`() {
        val s = TimerEngine.start(TimerMode.STOPWATCH, t0)
        assertEquals(s, TimerEngine.restoreAfterSave(s, t0 + min, 5_000_000L, t0 + 2 * min, 5_060_000L))
    }

    @Test
    fun `timestamps are shifted after a reboot so elapsed time is preserved`() {
        // Started at elapsed t0, saved at t0+10min. Phone reboots; now elapsed is
        // only 30s, but the wall clock says 15 min passed since the save.
        val s = TimerEngine.start(TimerMode.STOPWATCH, t0)
        val savedElapsed = t0 + 10 * min
        val savedWall = 50_000_000L
        val nowElapsed = 30 * sec
        val nowWall = savedWall + 15 * min

        val restored = TimerEngine.restoreAfterSave(s, savedElapsed, savedWall, nowElapsed, nowWall)
        assertEquals(25 * min, TimerEngine.focusMillis(restored, nowElapsed))
    }
}
