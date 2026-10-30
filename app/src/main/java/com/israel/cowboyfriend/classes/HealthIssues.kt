package com.israel.cowboyfriend.classes

import android.content.Context
import com.israel.cowboyfriend.R

/**
 * The health problems that can be marked on a cow (edit window of the cattle tour). a cow has a list of them, saved in
 * the column health_issues of the table CowDetails as a json array of codes, for example ["lameness","swollen_udder"].
 * the codes are the same in every language of the app (only the shown names are translated), so reports do not depend on
 * the language. "other" is a free text: it is saved as "other:<the text>" (or just "other" when no text was typed).
 *
 * to add a problem: add a line to [CATALOG] and its name to values/strings.xml and values-iw/strings.xml
 */
object HealthIssues {

    //one problem of the list: its code in the database and the name that is shown
    data class Issue(val code: String, val labelRes: Int)

    //the code of the free text problem, and the start of its saved text
    const val OTHER_CODE = "other"
    private const val OTHER_PREFIX = "other:"

    //the common problems found in a cattle tour, in the order they are shown
    val CATALOG = listOf(
        Issue("lameness", R.string.health_lameness),
        Issue("swollen_udder", R.string.health_swollen_udder),
        Issue("eye", R.string.health_eye),
        Issue("respiratory", R.string.health_respiratory),
        Issue("diarrhea", R.string.health_diarrhea),
        Issue("bloat", R.string.health_bloat),
        Issue("wound_skin", R.string.health_wound_skin),
        Issue("thin", R.string.health_thin),
        Issue("lethargic", R.string.health_lethargic),
        Issue("parasites", R.string.health_parasites),
        Issue("calving", R.string.health_calving),
        Issue("weak_calf", R.string.health_weak_calf)
    )

    private val codesInCatalog = CATALOG.map { it.code }.toSet()

    //the free text of the "other" problem in the saved list, or null when there is none (or it has no text)
    fun otherText(saved: List<String>): String? =
        saved.firstOrNull { it.startsWith(OTHER_PREFIX) }?.removePrefix(OTHER_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }

    //the codes of the saved list that are chosen in the window: the known problems, and OTHER_CODE when "other" is there
    fun selectedCodes(saved: List<String>): Set<String> {
        val result = LinkedHashSet<String>()
        for (entry in saved) {
            if (entry in codesInCatalog) result.add(entry)
            else if (entry == OTHER_CODE || entry.startsWith(OTHER_PREFIX)) result.add(OTHER_CODE)
        }
        return result
    }

    /**
     * the list to save from the chosen codes and the typed text of "other": the known problems in the order of the
     * catalog, then "other:<text>". unknown codes are dropped
     */
    fun build(selected: Collection<String>, otherText: String?): List<String> {
        val result = ArrayList<String>()
        for (issue in CATALOG) if (issue.code in selected) result.add(issue.code)
        if (OTHER_CODE in selected) {
            val text = otherText?.trim().orEmpty()
            result.add(if (text.isEmpty()) OTHER_CODE else OTHER_PREFIX + text)
        }
        return result
    }

    //the names of the saved problems in the language of the app (an unknown code from a newer version is shown as it is)
    fun labels(context: Context, saved: List<String>): List<String> {
        return saved.map { entry ->
            val issue = CATALOG.firstOrNull { it.code == entry }
            when {
                issue != null -> context.getString(issue.labelRes)
                entry == OTHER_CODE -> context.getString(R.string.health_other)
                entry.startsWith(OTHER_PREFIX) -> entry.removePrefix(OTHER_PREFIX).trim().ifEmpty { context.getString(R.string.health_other) }
                else -> entry
            }
        }
    }
}
