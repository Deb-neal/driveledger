package com.iancompany.voiceledger

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Non-interactive chart; the adjacent text legend carries all values for accessibility. */
@SuppressLint("ViewConstructor") // Built programmatically with immutable monthly data, never inflated from XML.
class ExpenseDonutView(context: Context, val shares: List<CategoryShare>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = RectF()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = min(width, height).toFloat()
        val stroke = diameter * .15f
        paint.strokeWidth = stroke
        val radius = (diameter - stroke) / 2f - resources.displayMetrics.density
        ring.set(width / 2f - radius, height / 2f - radius, width / 2f + radius, height / 2f + radius)
        paint.color = context.getColor(R.color.divider)
        canvas.drawOval(ring, paint)
        var start = -90f
        shares.forEachIndexed { index, share ->
            // Close the final arc exactly; do not enlarge tiny expenses for visual effect.
            val sweep = if (index == shares.lastIndex) (270f - start).coerceAtLeast(0f) else (share.fraction * 360).toFloat()
            paint.color = context.getColor(categoryColor(share.category))
            canvas.drawArc(ring, start, sweep, false, paint)
            start += sweep
        }
    }
}
