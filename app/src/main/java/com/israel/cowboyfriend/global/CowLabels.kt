package com.israel.cowboyfriend.global

import android.content.Context
import com.israel.cowboyfriend.R

/**
 * The cow type and the gender are saved in the database as text. the text in the table is the Hebrew one (the app was
 * only in Hebrew), and it must not depend on the language of the app, so a cow saved in one language is understood in
 * the other. this object turns the saved text into a position (type: 0 calf, 1 first-cow, 2 cow, 3 bull; gender: 0 male,
 * 1 female), the position into the text of the current language to show, and the position back into the saved text.
 * a saved text in English (if any) is understood too.
 */
object CowLabels {

    //the texts saved in the database (do not change them: they are in the existing rows)
    private val STORED_TYPES = listOf("עגל", "מבכירה", "פרה", "שור")
    private val STORED_GENDERS = listOf("זכר", "נקבה")
    //the first item of the gender options is the "choose" placeholder, so the options start from this position
    private const val GENDER_OPTIONS_OFFSET = 1

    private val ENGLISH_TYPES = listOf("calf", "first-cow", "cow", "bull")
    private val ENGLISH_GENDERS = listOf("male", "female")

    const val TYPE_CALF = 0
    const val TYPE_FIRST_COW = 1
    const val TYPE_COW = 2
    const val TYPE_BULL = 3

    //the position of a saved type, or -1 when it is empty or unknown
    fun typeIndex(stored: String?): Int {
        val text = stored?.trim().orEmpty()
        val index = STORED_TYPES.indexOf(text)
        return if (index >= 0) index else ENGLISH_TYPES.indexOf(text.lowercase())
    }

    //the saved text of a type position
    fun storedType(index: Int): String? = STORED_TYPES.getOrNull(index)

    //the type in the current language (the saved text itself when it is not a known type)
    fun typeLabel(context: Context, stored: String?): String {
        val index = typeIndex(stored)
        val names = context.resources.getStringArray(R.array.cow_type)
        return if (index in names.indices) names[index] else stored.orEmpty()
    }

    //the position of a saved gender, or -1 when it is empty or unknown
    fun genderIndex(stored: String?): Int {
        val text = stored?.trim().orEmpty()
        val index = STORED_GENDERS.indexOf(text)
        return if (index >= 0) index else ENGLISH_GENDERS.indexOf(text.lowercase())
    }

    //the gender in the current language (the saved text itself when it is not a known gender)
    fun genderLabel(context: Context, stored: String?): String {
        val index = genderIndex(stored)
        val options = context.resources.getStringArray(R.array.gender_options)
        return if (index >= 0 && index + GENDER_OPTIONS_OFFSET in options.indices) options[index + GENDER_OPTIONS_OFFSET] else stored.orEmpty()
    }

    /**
     * the text to save for the position selected in the gender spinner (0 is the "choose" placeholder, which is saved
     * as the Hebrew placeholder text, like before)
     */
    fun storedGenderFromSpinner(position: Int): String =
        STORED_GENDERS.getOrNull(position - GENDER_OPTIONS_OFFSET) ?: "בחר"
}
