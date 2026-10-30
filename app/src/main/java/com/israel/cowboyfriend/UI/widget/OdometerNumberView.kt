package com.israel.cowboyfriend.UI.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.israel.cowboyfriend.R

/**
 * 3-digit odometer style number input. Tap the upper half of a digit to raise it, the lower half to
 * lower it, or drag a digit up / down. [setNumber] updates the digits without firing the callback.
 */
class OdometerNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onNumberChanged: ((Int) -> Unit)? = null

    //number of digit tiles, read from app:digitCount (3 for the cow number, 2 for the number of calvings)
    private val digitCount: Int = context.obtainStyledAttributes(attrs, R.styleable.OdometerNumberView).let {
        val count = it.getInt(R.styleable.OdometerNumberView_digitCount, 3)
        it.recycle()
        count.coerceIn(1, 6)
    }
    //highest value the tiles can show: 999 for 3 digits, 99 for 2
    private val maxNumber: Int = Math.pow(10.0, digitCount.toDouble()).toInt() - 1
    private val digits = IntArray(digitCount)
    //false until the user (or voice input) sets a value, the digits are drawn faded before that
    private var userTouched = false

    private val density = resources.displayMetrics.density
    private val tileW = 44 * density
    private val tileH = 60 * density
    private val gap = 6 * density
    private val pad = 6 * density
    private val tileRadius = 8 * density
    private val frameRadius = 14 * density
    private val dragStep = 24 * density

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ww_iron)
    }
    private val frameEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFF5A3E1F.toInt()
    }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tileEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = 0xFF8F7230.toInt()
    }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFFFFF
    }
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 36 * resources.displayMetrics.scaledDensity
        typeface = ResourcesCompat.getFont(context, R.font.frank_ruhl_bold)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ww_saddle)
        alpha = 130
    }

    private val tileRect = RectF()
    private val arrowPath = Path()

    //tile that received the touch down and the dragged distance not yet converted to steps
    private var activeTile = -1
    private var dragAccum = 0f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            activeTile = tileAt(e.x)
            dragAccum = 0f
            return activeTile >= 0
        }

        //tap on the upper half of a tile raises the digit, on the lower half lowers it
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (activeTile < 0) return false
            step(activeTile, if (e.y < height / 2f) 1 else -1)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (activeTile < 0) return false
            parent?.requestDisallowInterceptTouchEvent(true)
            //dragging up raises the digit, one step per dragStep; the parent is not allowed to scroll meanwhile
            dragAccum += distanceY
            while (dragAccum >= dragStep) {
                step(activeTile, 1)
                dragAccum -= dragStep
            }
            while (dragAccum <= -dragStep) {
                step(activeTile, -1)
                dragAccum += dragStep
            }
            return true
        }
    })

    init {
        isClickable = true
        //rounded outline so the elevation shadow follows the frame shape
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, frameRadius)
            }
        }
    }

    val number: Int
        get() = digits.fold(0) { acc, d -> acc * 10 + d }

    fun setNumber(value: Int?) {
        val v = (value ?: 0).coerceIn(0, maxNumber)
        var rest = v
        for (i in digitCount - 1 downTo 0) {
            digits[i] = rest % 10
            rest /= 10
        }
        userTouched = value != null
        invalidate()
    }

    //change one digit (wraps 9 -> 0 and 0 -> 9) and report the new full number
    private fun step(tile: Int, delta: Int) {
        digits[tile] = (digits[tile] + delta + 10) % 10
        userTouched = true
        invalidate()
        onNumberChanged?.invoke(number)
    }

    //index of the tile under x, or -1 when the touch is on the frame border
    private fun tileAt(x: Float): Int {
        for (i in 0 until digitCount) {
            val left = pad + i * (tileW + gap)
            if (x >= left - gap / 2 && x <= left + tileW + gap / 2) return i
        }
        return -1
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (pad * 2 + tileW * digitCount + gap * (digitCount - 1)).toInt()
        val h = (pad * 2 + tileH).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            //keep the ScrollView from taking over while a digit is touched, so dragging changes the digit and not the page
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return handled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        tileRect.set(0f, 0f, width.toFloat(), height.toFloat())
        tileRect.inset(frameEdgePaint.strokeWidth / 2, frameEdgePaint.strokeWidth / 2)
        canvas.drawRoundRect(tileRect, frameRadius, frameRadius, framePaint)
        canvas.drawRoundRect(tileRect, frameRadius, frameRadius, frameEdgePaint)

        digitPaint.color = ContextCompat.getColor(context, R.color.ww_iron)
        digitPaint.alpha = if (userTouched) 255 else 110

        for (i in 0 until digitCount) {
            val left = pad + i * (tileW + gap)
            tileRect.set(left, pad, left + tileW, pad + tileH)

            //vertical gradient of the tile face
            tilePaint.shader = LinearGradient(
                0f, tileRect.top, 0f, tileRect.bottom,
                intArrayOf(0xFFF8EBC0.toInt(), 0xFFE8D08A.toInt(), 0xFFCDAE5E.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(tileRect, tileRadius, tileRadius, tilePaint)

            //lighter gloss on the upper part of the tile for the 3D look
            canvas.save()
            canvas.clipRect(tileRect.left, tileRect.top, tileRect.right, tileRect.top + tileH * 0.42f)
            canvas.drawRoundRect(tileRect, tileRadius, tileRadius, glossPaint)
            canvas.restore()

            canvas.drawRoundRect(tileRect, tileRadius, tileRadius, tileEdgePaint)

            val baseline = tileRect.centerY() - (digitPaint.descent() + digitPaint.ascent()) / 2
            canvas.drawText(digits[i].toString(), tileRect.centerX(), baseline, digitPaint)

            drawArrow(canvas, tileRect.centerX(), tileRect.top + 5 * density, up = true)
            drawArrow(canvas, tileRect.centerX(), tileRect.bottom - 5 * density, up = false)
        }
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, up: Boolean) {
        val half = 4 * density
        val h = 3.2f * density
        arrowPath.reset()
        if (up) {
            arrowPath.moveTo(cx - half, cy + h / 2)
            arrowPath.lineTo(cx + half, cy + h / 2)
            arrowPath.lineTo(cx, cy - h / 2)
        } else {
            arrowPath.moveTo(cx - half, cy - h / 2)
            arrowPath.lineTo(cx + half, cy - h / 2)
            arrowPath.lineTo(cx, cy + h / 2)
        }
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
    }

}
