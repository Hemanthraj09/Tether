package com.tether.app.domain

/**
 * Accountability rules for a log: a short "what did you do?" note on every
 * log, and an optional/required photo depending on the group's proof mode.
 * The security rules enforce the same limits server-side.
 */
object Proof {

    const val MODE_OFF = "off"
    const val MODE_OPTIONAL = "optional"
    const val MODE_REQUIRED = "required"
    val MODES = listOf(MODE_OFF, MODE_OPTIONAL, MODE_REQUIRED)

    const val SOURCE_MANUAL = "manual"
    const val SOURCE_TIMER = "timer"

    const val NOTE_MIN = 3
    const val NOTE_MAX = 200

    /** Longest side of a stored proof photo, in pixels. */
    const val MAX_SIDE_PX = 720
    /** Upper bound of a stored photo (the rules allow up to 150 KB). */
    const val MAX_BYTES = 120 * 1024

    fun normalizeMode(mode: String?): String = if (mode in MODES) mode!! else MODE_OPTIONAL

    fun isNoteValid(note: String): Boolean = note.trim().length in NOTE_MIN..NOTE_MAX

    /** Whether this log needs a photo before it can be saved (timer sessions measure themselves). */
    fun photoRequired(mode: String?, source: String): Boolean =
        normalizeMode(mode) == MODE_REQUIRED && source == SOURCE_MANUAL

    fun photoAllowed(mode: String?): Boolean = normalizeMode(mode) != MODE_OFF

    /**
     * Power-of-two decode subsample so the decoded bitmap's longest side is
     * still at least [target] px (BitmapFactory.Options.inSampleSize).
     */
    fun sampleSize(width: Int, height: Int, target: Int = MAX_SIDE_PX): Int {
        var size = 1
        val longest = maxOf(width, height)
        while (longest / (size * 2) >= target) size *= 2
        return size
    }

    /** Final scaled size keeping the aspect ratio, longest side ≤ [target]. */
    fun scaledSize(width: Int, height: Int, target: Int = MAX_SIDE_PX): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= target) return width to height
        val scale = target.toDouble() / longest
        return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
    }
}
