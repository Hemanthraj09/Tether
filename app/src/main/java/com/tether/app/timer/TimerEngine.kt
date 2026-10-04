package com.tether.app.timer

enum class TimerMode { STOPWATCH, POMODORO }
enum class TimerPhase { FOCUSING, BREAK }

/**
 * Immutable snapshot of a focus session. All timestamps are on a monotonic
 * clock (SystemClock.elapsedRealtime() in the app), in milliseconds.
 */
data class TimerState(
    val mode: TimerMode,
    val phase: TimerPhase,
    /** Focus time completed in earlier phases. */
    val focusAccumMs: Long,
    /** When the current phase started. */
    val phaseStartAt: Long,
    /** When the current countdown ends (Pomodoro phases, stopwatch breaks); 0 otherwise. */
    val phaseEndAt: Long,
    val breakLengthMs: Long = 0L,
    val breakWarned: Boolean = false,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5
)

/**
 * Pure timer state machine: no Android, no clocks, no threads.
 * Time is derived from timestamps rather than counted per tick, so the result
 * is exact even if ticks are delayed or the CPU sleeps for hours.
 */
object TimerEngine {

    const val BREAK_WARNING_MS = 120_000L
    private const val MINUTE_MS = 60_000L

    data class Advance(
        val state: TimerState,
        val phaseChanged: Boolean,
        val warnBreakEnding: Boolean
    )

    fun start(mode: TimerMode, now: Long, focusMinutes: Int = 25, breakMinutes: Int = 5): TimerState =
        TimerState(
            mode = mode,
            phase = TimerPhase.FOCUSING,
            focusAccumMs = 0L,
            phaseStartAt = now,
            phaseEndAt = if (mode == TimerMode.POMODORO) now + focusMinutes * MINUTE_MS else 0L,
            pomodoroFocusMinutes = focusMinutes,
            pomodoroBreakMinutes = breakMinutes
        )

    /** Moves through every phase boundary that has passed by [now]. */
    fun advance(state: TimerState, now: Long): Advance {
        var s = state
        var changed = false

        if (s.mode == TimerMode.STOPWATCH) {
            if (s.phase == TimerPhase.BREAK && now >= s.phaseEndAt) {
                // Focus resumes exactly when the break ended, even if we're late noticing.
                s = s.copy(phase = TimerPhase.FOCUSING, phaseStartAt = s.phaseEndAt, phaseEndAt = 0L)
                changed = true
            }
        } else {
            val focusMs = s.pomodoroFocusMinutes * MINUTE_MS
            val breakMs = s.pomodoroBreakMinutes * MINUTE_MS
            while (now >= s.phaseEndAt) {
                s = if (s.phase == TimerPhase.FOCUSING) {
                    s.copy(
                        phase = TimerPhase.BREAK,
                        focusAccumMs = s.focusAccumMs + focusMs,
                        phaseStartAt = s.phaseEndAt,
                        phaseEndAt = s.phaseEndAt + breakMs,
                        breakLengthMs = breakMs,
                        breakWarned = false
                    )
                } else {
                    s.copy(
                        phase = TimerPhase.FOCUSING,
                        phaseStartAt = s.phaseEndAt,
                        phaseEndAt = s.phaseEndAt + focusMs,
                        breakWarned = false
                    )
                }
                changed = true
            }
        }

        var warn = false
        if (s.phase == TimerPhase.BREAK && !s.breakWarned &&
            s.breakLengthMs > BREAK_WARNING_MS && s.phaseEndAt - now <= BREAK_WARNING_MS
        ) {
            s = s.copy(breakWarned = true)
            warn = true
        }
        return Advance(s, changed || warn, warn)
    }

    /** Starts a manual break (stopwatch only, while focusing); null if not allowed. */
    fun startBreak(state: TimerState, minutes: Int, now: Long): TimerState? {
        if (state.mode != TimerMode.STOPWATCH || state.phase != TimerPhase.FOCUSING) return null
        val length = minutes * MINUTE_MS
        return state.copy(
            phase = TimerPhase.BREAK,
            focusAccumMs = state.focusAccumMs + (now - state.phaseStartAt),
            phaseStartAt = now,
            phaseEndAt = now + length,
            breakLengthMs = length,
            breakWarned = false
        )
    }

    /** Focus time only — breaks are never counted. Call on an advanced state. */
    fun focusMillis(state: TimerState, now: Long): Long =
        state.focusAccumMs + if (state.phase == TimerPhase.FOCUSING) now - state.phaseStartAt else 0L

    /** What the timer face shows: elapsed focus (stopwatch) or time remaining (countdowns). */
    fun displaySeconds(state: TimerState, now: Long): Long =
        if (state.mode == TimerMode.STOPWATCH && state.phase == TimerPhase.FOCUSING) {
            focusMillis(state, now) / 1000
        } else {
            ((state.phaseEndAt - now).coerceAtLeast(0L) + 999) / 1000
        }

    /**
     * The monotonic clock restarts at 0 after a reboot. If that happened since
     * the state was saved, shift its timestamps using the wall clock so the
     * session continues where it should.
     */
    fun restoreAfterSave(
        state: TimerState,
        savedElapsed: Long,
        savedWall: Long,
        nowElapsed: Long,
        nowWall: Long
    ): TimerState {
        if (nowElapsed >= savedElapsed) return state // no reboot
        val shift = nowElapsed - (savedElapsed + (nowWall - savedWall))
        return state.copy(
            phaseStartAt = state.phaseStartAt + shift,
            phaseEndAt = if (state.phaseEndAt != 0L) state.phaseEndAt + shift else 0L
        )
    }
}
