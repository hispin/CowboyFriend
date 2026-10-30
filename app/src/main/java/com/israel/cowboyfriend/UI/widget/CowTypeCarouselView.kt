package com.israel.cowboyfriend.UI.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.israel.cowboyfriend.R

/**
 * Center-locked type picker: shows the current item large on a highlighted pill,
 * with the previous/next items faded on either side. Tapping an arrow steps the
 * selection by one and wraps around. There is no drag/scroll gesture - this mirrors
 * a small fixed list (cow types), not a general-purpose carousel.
 */
class CowTypeCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var items: List<String> = emptyList()
    private var index = 0

    /** called with (position, value) whenever the selection changes via the arrows */
    var onSelectionChanged: ((position: Int, value: String) -> Unit)? = null

    private val tvPrev: TextView
    private val tvCenter: TextView
    private val tvNext: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = (12 * resources.displayMetrics.density).toInt()
        val padV = (10 * resources.displayMetrics.density).toInt()
        setPadding(padH, padV, padH, padV)
        background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.shape_ww_carousel_bg)

        LayoutInflater.from(context).inflate(R.layout.widget_cow_type_carousel, this, true)
        tvPrev = findViewById(R.id.tvCarPrev)
        tvCenter = findViewById(R.id.tvCarCenter)
        tvNext = findViewById(R.id.tvCarNext)
        applyDirection()
    }

    //the arrows follow the direction of the screen (Hebrew is RTL, English is LTR); also when the direction is resolved later
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyDirection()
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        applyDirection()
    }

    /**
     * the first button of the layout (btnCarRight) is at the start of the row and the last one (btnCarLeft) at its end.
     * the start button always goes to the previous item (shown next to it) and the end button to the next one; the
     * arrows point away from the center: RTL start = right side, LTR start = left side
     */
    private fun applyDirection() {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val start = findViewById<ImageButton>(R.id.btnCarRight)
        val end = findViewById<ImageButton>(R.id.btnCarLeft)
        start.setImageResource(if (rtl) R.drawable.ic_chevron_right else R.drawable.ic_chevron_left)
        end.setImageResource(if (rtl) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right)
        start.contentDescription = context.getString(R.string.prev_cow_type)
        end.contentDescription = context.getString(R.string.next_cow_type)
        start.setOnClickListener { step(-1) }
        end.setOnClickListener { step(1) }
    }

    /**
     * set the fixed list of items to cycle through, and which one starts centered
     */
    fun setItems(newItems: List<String>, initialIndex: Int = 0) {
        items = newItems
        index = if (items.isEmpty()) 0 else initialIndex.coerceIn(0, items.size - 1)
        render()
    }

    val selectedItem: String?
        get() = items.getOrNull(index)

    val selectedIndex: Int
        get() = index

    /** move the centered item to [position] without firing onSelectionChanged (e.g. initial state) */
    fun setSelectionSilently(position: Int) {
        if (items.isEmpty()) return
        index = ((position % items.size) + items.size) % items.size
        render()
    }

    private fun step(delta: Int) {
        if (items.isEmpty()) return
        index = ((index + delta) % items.size + items.size) % items.size
        render()
        onSelectionChanged?.invoke(index, items[index])
    }

    private fun render() {
        if (items.isEmpty()) return
        val prevIdx = ((index - 1) % items.size + items.size) % items.size
        val nextIdx = (index + 1) % items.size
        tvPrev.text = items[prevIdx]
        tvCenter.text = items[index]
        tvNext.text = items[nextIdx]
    }
}
