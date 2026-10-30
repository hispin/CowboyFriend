package com.israel.cowboyfriend.UI.helper

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleOwner
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase

/**
 * Connects one label (a TextView) to the farm name of the logged in user, so every screen that shows the farm name
 * (the login screen, the new cow screen) uses the same code instead of its own copy:
 *
 * - [load] asks the view model to read the name from the database (the UsersInfo table),
 * - [bind] makes the label follow the name the view model holds (LiveData), also after a screen rotation,
 * - [show] writes the name in the label with the font that fits its letters.
 *
 * The view model stays the single owner of the data; this class only knows how to display it.
 */
class FarmNameLabelBinder(
    private val label: TextView,
    private val viewModel: MyViewModelSupbase
) {
    //the look the label has in the layout (Rye font), restored when the name has no Hebrew letters
    private val defaultTypeface: Typeface? = label.typeface
    private val defaultLetterSpacing: Float = label.letterSpacing
    private val defaultTextSizePx: Float = label.textSize

    /**
     * the label shows every farm name the view model publishes, until [owner] is destroyed
     */
    fun bind(owner: LifecycleOwner) {
        viewModel._farmName.observe(owner) { name -> show(name) }
    }

    /**
     * reads the farm name of the logged in user from the database; the result arrives through [bind].
     * nothing is requested when nobody is logged in
     */
    fun load() {
        if (!viewModel.isSessionGetSupabase()) return
        viewModel.dbGetFarmName(viewModel.getUserName()) { }
    }

    /**
     * writes the name in the label. an empty name is ignored, so the placeholder of the layout stays.
     * Rye has no Hebrew letters, so a Hebrew name uses the bold app font and an English one keeps the Rye look
     */
    fun show(name: String?) {
        if (name.isNullOrBlank()) return
        label.text = name
        val hasHebrew = name.any { it in HEBREW_LETTERS }
        if (hasHebrew) {
            label.typeface = ResourcesCompat.getFont(label.context, R.font.heebo_bold) ?: Typeface.DEFAULT_BOLD
            label.letterSpacing = 0f
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, HEBREW_TEXT_SIZE_SP)
        } else {
            label.typeface = defaultTypeface
            label.letterSpacing = defaultLetterSpacing
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSizePx)
        }
    }

    private companion object {
        val HEBREW_LETTERS = '֐'..'׿'
        const val HEBREW_TEXT_SIZE_SP = 18f
    }
}
