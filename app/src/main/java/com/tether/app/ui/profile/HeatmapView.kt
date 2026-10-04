package com.tether.app.ui.profile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * GitHub-style yearly heatmap drawn directly on a Canvas.
 *
 * Replaces a TableLayout of ~420 child views (slow to inflate, measure and
 * scroll) with a single view. Same layout and colours as before:
 * full calendar year, Monday-first columns, month labels on top,
 * 13-level green scale (one level per hour, 12h+ = brightest).
 */
class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val cellSize = 14 * density
    private val cellMargin = 2 * density
    private val pitch = cellSize + cellMargin * 2
    private val cornerRadius = 3 * density

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 9f, resources.displayMetrics)
    }
    private val labelHeight = labelPaint.fontSpacing + 2 * density

    private val levelPaints = intArrayOf(
        0xFF2A2A2A.toInt(),  // 0 - no activity
        0xFF0E4429.toInt(),  // 1h
        0xFF0F542E.toInt(),  // 2h
        0xFF136535.toInt(),  // 3h
        0xFF1A7A3C.toInt(),  // 4h
        0xFF1E8F42.toInt(),  // 5h
        0xFF22A348.toInt(),  // 6h
        0xFF26B84E.toInt(),  // 7h
        0xFF2DC653.toInt(),  // 8h
        0xFF39D35A.toInt(),  // 9h
        0xFF45E061.toInt(),  // 10h
        0xFF56E870.toInt(),  // 11h
        0xFF6BF080.toInt()   // 12h
    ).map { c -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c } }

    private var grid = HeatmapGrid.build(
        Calendar.getInstance().get(Calendar.YEAR), emptyMap(), ""
    )

    private val rect = RectF()

    /** [hoursByDate] is keyed by "yyyy-MM-dd". */
    fun setData(year: Int, hoursByDate: Map<String, Double>) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        grid = HeatmapGrid.build(year, hoursByDate, today)
        requestLayout()
        invalidate()
    }

    /** X position (px) of the column containing today, or -1. */
    fun todayColumnX(): Int =
        if (grid.todayIndex < 0) -1 else ((grid.todayIndex / 7) * pitch).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (grid.columns * pitch).toInt()
        val height = (labelHeight + 7 * pitch).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val baseline = -labelPaint.ascent()
        for ((col, label) in grid.monthLabels) {
            canvas.drawText(label, col * pitch + cellMargin, baseline, labelPaint)
        }
        val levels = grid.levels
        for (i in levels.indices) {
            val level = levels[i]
            if (level < 0) continue
            val col = i / 7
            val row = i % 7
            val left = col * pitch + cellMargin
            val top = labelHeight + row * pitch + cellMargin
            rect.set(left, top, left + cellSize, top + cellSize)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, levelPaints[level])
        }
    }
}
