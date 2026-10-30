package com.israel.cowboyfriend.global

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The language of the app, chosen inside the app (not by the language of the phone). AppCompat applies it to every
 * screen: the texts (values / values-iw), the direction of the screens (RTL for Hebrew, LTR for English), and it also
 * remembers the choice. the voice (speech recognition and text to speech) follows the same language.
 *
 * to add a language: add an [Option] to [SUPPORTED], a values-xx/strings.xml folder, and the language to res/xml/locales_config.xml
 */
object AppLanguage {

    /**
     * one language of the app: [tag] is the language tag of the resources, [nativeName] the name shown in the list (in its
     * own language), [shortName] the short text on the language button, [speechTag] the language of the voice
     */
    data class Option(val tag: String, val nativeName: String, val shortName: String, val speechTag: String)

    val SUPPORTED = listOf(
        Option("he", "עברית", "עב", "he-IL"),
        Option("en", "English", "EN", "en-US")
    )

    //the language of the app until the user chooses another one
    private val DEFAULT = SUPPORTED[0]

    /**
     * the language in use now: the chosen language of the app, or the default one when nothing was chosen yet or the
     * chosen language is not one of [SUPPORTED]
     */
    fun current(): Option {
        val chosen = AppCompatDelegate.getApplicationLocales()
        val language = if (chosen.isEmpty) null else chosen[0]?.language
        //Android may call Hebrew "iw" (the old code) or "he"
        val normalized = if (language == "iw") "he" else language
        return SUPPORTED.firstOrNull { it.tag == normalized } ?: DEFAULT
    }

    /**
     * changes the language of the whole app: the open screen is recreated in the new language (and direction) by AppCompat
     */
    fun set(option: Option) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag))
    }

    /**
     * the first start of the app: no language was chosen yet, so the default language is chosen (and saved), whatever the
     * language of the phone is. must be called from an activity after super.onCreate (AppCompat applies the language only
     * through the open activities). returns true when the language was just chosen: the screen is being recreated in it,
     * so the caller should stop opening the screen
     */
    fun ensureChosen(): Boolean {
        if (!AppCompatDelegate.getApplicationLocales().isEmpty) return false
        set(DEFAULT)
        return true
    }

    //the language of the speech recognizer, for example "he-IL"
    fun speechTag(): String = current().speechTag

    //the language of the text to speech
    fun speechLocale(): Locale = Locale.forLanguageTag(current().speechTag)
}
