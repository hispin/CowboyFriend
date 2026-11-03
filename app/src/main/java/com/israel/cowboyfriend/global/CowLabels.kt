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

    //the sign in front of the year of birth, like in "class of '24": it tells the year from the number
    private const val YEAR_SIGN = '’'

    /**
     * how a cow is named when it is chosen or shown as a mother: its number, a space and the last two digits of its birth year
     * after an apostrophe, for example "12 ’24" is cow 12 born in 2024 (the same number is used again in other years). only
     * the number when the birth date is unknown, null when there is no number
     */
    fun momLabel(number: Int?, createdAt: Long?, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String? {
        if (number == null) return null
        if (createdAt == null) return number.toString()
        val year = java.util.Calendar.getInstance(zone).apply { timeInMillis = createdAt }.get(java.util.Calendar.YEAR)
        return String.format(java.util.Locale.US, "%d %c%02d", number, YEAR_SIGN, year % 100)
    }

    /**
     * the label of a mother ([momLabel]) made safe for a right to left screen: it is always shown left to right, so the number
     * stays first and the year after it, whatever the language of the text around it
     */
    fun momLabelForScreen(label: String): String =
        androidx.core.text.BidiFormatter.getInstance().unicodeWrap(label, androidx.core.text.TextDirectionHeuristicsCompat.LTR)

    /**
     * styles the year of birth of a mother inside [text] (a text that holds a label of [momLabel]): the year after the
     * apostrophe is smaller and has the color [yearColor], like the year next to the number in the head of the cow card.
     * the same text when there is no year in it
     */
    fun styleMomYear(text: CharSequence, yearColor: Int): CharSequence {
        val start = text.indexOf(YEAR_SIGN)
        if (start < 0) return text
        var end = start + 1
        while (end < text.length && text[end].isDigit()) end++
        return android.text.SpannableString(text).apply {
            setSpan(android.text.style.RelativeSizeSpan(0.8f), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.ForegroundColorSpan(yearColor), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * the text to save for the position selected in the gender spinner (0 is the "choose" placeholder, which is saved
     * as the Hebrew placeholder text, like before)
     */
    fun storedGenderFromSpinner(position: Int): String =
        STORED_GENDERS.getOrNull(position - GENDER_OPTIONS_OFFSET) ?: "בחר"
}
