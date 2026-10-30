package com.israel.cowboyfriend.global

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.view.ViewGroup

/**
 * The size of the windows (dialogs) of the app on every screen. a window is a part of the screen width, but never wider
 * than [MAX_WIDTH_DP], so in landscape (a very wide screen) the windows stay a comfortable size and are centered.
 */
object DialogSizing {

    //the widest a window of the app gets
    private const val MAX_WIDTH_DP = 560

    //true when the phone is in landscape
    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    //the width in pixels of a window that takes [fraction] of the screen width, at most [maxWidthDp]
    fun width(context: Context, fraction: Float, maxWidthDp: Int = MAX_WIDTH_DP): Int {
        val metrics = context.resources.displayMetrics
        return minOf((metrics.widthPixels * fraction).toInt(), (maxWidthDp * metrics.density).toInt())
    }

    /**
     * sets the size of the window of [dialog]: the width as above, and the height as a part of the screen height
     * ([heightFraction]) or as the height of its content when it is null
     */
    fun apply(dialog: Dialog, widthFraction: Float, heightFraction: Float? = null, maxWidthDp: Int = MAX_WIDTH_DP) {
        val context = dialog.context
        val height = if (heightFraction != null) {
            (context.resources.displayMetrics.heightPixels * heightFraction).toInt()
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        dialog.window?.setLayout(width(context, widthFraction, maxWidthDp), height)
    }
}
