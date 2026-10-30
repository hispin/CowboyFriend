package com.israel.cowboyfriend.UI.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.israel.cowboyfriend.R

/**
 * A small bar chart in the style of the app (the trend of the tour coverage on the ranch dashboard): one bar for every
 * value (0-100, in percent), the value on top of the bar and a short label under it. the bars are drawn from the oldest to
 * the newest, in the reading direction of the screen. the last bar (the newest) is brass, the others are turquoise.
 */
class TrendBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var values: List<Int> = emptyList()
    private var labels: List<String> = emptyList()

    private val density = resources.displayMetrics.density
    private val font: Typeface? = ResourcesCompat.getFont(context, R.font.heebo_bold)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = ContextCompat.getColor(context, R.color.ww_iron)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.ww_parchment) }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font
        textAlign = Paint.Align.CENTER
        textSize = 13 * density
        color = ContextCompat.getColor(context, R.color.ww_ink)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font
        textAlign = Paint.Align.CENTER
        textSize = 11 * density
        color = ContextCompat.getColor(context, R.color.ww_ink_soft)
    }
    private val rect = RectF()

    //the bars: [values] in percent and the [labels] under them (the same number of items)
    fun setData(values: List<Int>, labels: List<String>) {
        this.values = values
        this.labels = labels
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (150 * density).toInt()
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val count = values.size
        val labelHeight = 20 * density
        val valueHeight = 20 * density
        val top = valueHeight
        val bottom = height - labelHeight
        val slot = width.toFloat() / count
        val barWidth = minOf(slot * 0.62f, 44 * density)
        //the bars follow the reading direction: in a right to left screen the oldest bar is on the right
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in 0 until count) {
            val slotIndex = if (rtl) count - 1 - i else i
            val centerX = slotIndex * slot + slot / 2
            val percent = values[i].coerceIn(0, 100)
            val barHeight = (bottom - top) * percent / 100f
            //the track (the full height), then the bar on its bottom
            rect.set(centerX - barWidth / 2, top, centerX + barWidth / 2, bottom)
            canvas.drawRoundRect(rect, 6 * density, 6 * density, trackPaint)
            canvas.drawRoundRect(rect, 6 * density, 6 * density, borderPaint)
            if (barHeight > 0) {
                barPaint.color = ContextCompat.getColor(context, if (i == count - 1) R.color.ww_brass else R.color.ww_turquoise)
                rect.set(centerX - barWidth / 2 + 2 * density, bottom - barHeight + 2 * density, centerX + barWidth / 2 - 2 * density, bottom - 2 * density)
                canvas.drawRoundRect(rect, 4 * density, 4 * density, barPaint)
            }
            canvas.drawText("$percent%", centerX, top - 5 * density, valuePaint)
            canvas.drawText(labels.getOrElse(i) { "" }, centerX, height - 4 * density, labelPaint)
        }
    }
}
