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
        //the screen is RTL: the right arrow goes to the previous item, the left arrow to the next one
        findViewById<ImageButton>(R.id.btnCarRight).setOnClickListener { step(-1) }
        findViewById<ImageButton>(R.id.btnCarLeft).setOnClickListener { step(1) }
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
