package com.tether.app.ui.profile

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Layout model for the yearly heatmap (pure, unit-tested).
 * Cells are column-major: index = column * 7 + row, row 0 = Monday.
 */
class HeatmapGrid(
    /** Intensity 0..12 per cell; -1 marks padding cells after Dec 31 (not drawn). */
    val levels: IntArray,
    /** Column index → month label, on the first column whose Monday starts a new month. */
    val monthLabels: Map<Int, String>,
    /** Cell index of today, or -1 if today isn't in this year's grid. */
    val todayIndex: Int
) {
    val columns: Int get() = levels.size / 7

    companion object {
        const val MAX_LEVEL = 12
        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        /** One level per hour: any activity ≥ 1, 12h or more = brightest. */
        fun levelFor(hours: Double): Int = when {
            hours <= 0.0 -> 0
            hours >= MAX_LEVEL -> MAX_LEVEL
            else -> hours.toInt().coerceIn(1, MAX_LEVEL)
        }

        /** Full calendar [year]: Jan 1 aligned back to Monday … Dec 31, padded to whole weeks. */
        fun build(year: Int, hoursByDate: Map<String, Double>, today: String): HeatmapGrid {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cursor = Calendar.getInstance().apply {
                clear()
                set(year, Calendar.JANUARY, 1)
                while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1)
            }
            val end = Calendar.getInstance().apply {
                clear()
                set(year, Calendar.DECEMBER, 31)
            }

            val levels = ArrayList<Int>(378)
            val labels = HashMap<Int, String>()
            var lastMonth = -1
            var todayIndex = -1
            while (!cursor.after(end)) {
                val index = levels.size
                val date = sdf.format(cursor.time)
                levels.add(levelFor(hoursByDate[date] ?: 0.0))
                if (date == today) todayIndex = index

                if (index % 7 == 0 && cursor.get(Calendar.YEAR) == year) {
                    val month = cursor.get(Calendar.MONTH)
                    if (month != lastMonth) {
                        labels[index / 7] = MONTHS[month]
                        lastMonth = month
                    }
                }
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
            while (levels.size % 7 != 0) levels.add(-1)

            return HeatmapGrid(levels.toIntArray(), labels, todayIndex)
        }
    }
}
