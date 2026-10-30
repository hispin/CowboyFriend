package com.israel.cowboyfriend.classes

import android.content.Context
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.global.getStringFromCalendar
import com.israel.cowboyfriend.global.parseTimestampMillis
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * The state of the tour of the logged in user: no open tour ([openTourId] is null), or an open one with its progress
 * (how many of the living cows were seen since it started).
 */
data class TourState(
    val openTourId: String? = null,
    val startedAtMillis: Long? = null,
    val seen: Int = 0,
    val alive: Int = 0
)

/**
 * The summary of a tour, as the database function tour_summary / end_tour returns it (a json object, see
 * supabase/tours_schema.sql). it is parsed here once, so the summary screen only shows the numbers
 */
class TourSummary private constructor(private val json: JSONObject) {

    val tourId: String = json.optString("tour_id")
    val startedBy: String = json.optString("started_by")
    val startedAtMillis: Long? = parseTimestampMillis(json.optString("started_at", null))
    val durationMinutes: Int = json.optInt("duration_minutes")

    //the living cows at the end of the tour, and how many of them were seen in it
    val alive: Int = json.optJSONObject("herd")?.optInt("alive") ?: 0
    val seen: Int = json.optInt("seen")
    //the numbers of the cows that were seen (empty in a summary saved before the database function returned them)
    val seenNumbers: List<Int> = numbers(json.optJSONArray("seen_numbers"))
    val notSeenCount: Int = json.optJSONObject("not_seen")?.optInt("count") ?: 0
    val notSeenNumbers: List<Int> = numbers(json.optJSONObject("not_seen")?.optJSONArray("numbers"))

    //what changed in the tour
    val changed: Int = json.optInt("changed")
    val newCalves: Int = json.optJSONObject("new_calves")?.optInt("count") ?: 0
    val newCorpses: Int = json.optJSONObject("new_corpses")?.optInt("count") ?: 0
    val commentsAdded: Int = json.optInt("comments_added")

    //the open problems of the herd (see the view cow_status)
    private val problems: JSONObject? = json.optJSONObject("problems")
    fun problem(key: String): Int = problems?.optInt(key) ?: 0
    val calvesWithoutMomNumbers: List<Int> = numbers(problems?.optJSONArray("calf_without_mom_numbers"))

    //the comparison with the previous closed tour of the ranch, null when there is none: signed differences
    private val vsPrevious: JSONObject? = json.optJSONObject("vs_previous")
    val hasPrevious: Boolean get() = vsPrevious != null
    fun vsPrevious(key: String): Int? = vsPrevious?.takeIf { it.has(key) && !it.isNull(key) }?.optInt(key)

    //the problem rows of the summary screen: the key of the problem, with its count (only the problems that exist)
    fun attentionRows(): List<Pair<String, Int>> =
        ATTENTION_KEYS.map { it to problem(it) }.filter { it.second > 0 }

    //the whole summary as a text to share (a message, WhatsApp)
    fun toShareText(context: Context): String {
        val lines = ArrayList<String>()
        lines.add(context.getString(R.string.summary_title))
        startedAtMillis?.let {
            val date = Calendar.getInstance().apply { timeInMillis = it }
            lines.add(getStringFromCalendar(date, "dd/MM/yyyy HH:mm", context) + " · " + durationText(context))
        }
        lines.add(context.getString(R.string.summary_seen_of, seen, alive))
        if (notSeenCount > 0) {
            lines.add(context.getString(R.string.summary_not_seen, notSeenCount) + ": " + notSeenNumbers.joinToString(", "))
        }
        lines.add(context.getString(R.string.summary_new_calves) + ": " + newCalves)
        lines.add(context.getString(R.string.summary_new_corpses) + ": " + newCorpses)
        lines.add(context.getString(R.string.summary_changed) + ": " + changed)
        val rows = attentionRows()
        if (rows.isNotEmpty()) {
            lines.add(context.getString(R.string.summary_attention) + ":")
            rows.forEach { (key, count) -> lines.add("- " + context.getString(attentionLabelRes(key)) + ": " + count) }
        }
        return lines.joinToString("\n")
    }

    //"2 h 10 min" or "35 min"
    fun durationText(context: Context): String =
        if (durationMinutes < 1) context.getString(R.string.summary_duration_lt1)
        else if (durationMinutes >= 60) context.getString(R.string.summary_duration_hm, durationMinutes / 60, durationMinutes % 60)
        else context.getString(R.string.summary_duration_m, durationMinutes)

    companion object {
        //the problems of the summary, in the order they are shown (the keys of the object "problems")
        val ATTENTION_KEYS = listOf(
            "calf_without_mom", "calf_without_tag", "health_issue", "not_seen_14d", "never_seen", "no_location"
        )

        //the name of a problem in the language of the app
        fun attentionLabelRes(key: String): Int = when (key) {
            "calf_without_mom" -> R.string.attention_calf_without_mom
            "calf_without_tag" -> R.string.attention_calf_without_tag
            "health_issue" -> R.string.attention_health
            "not_seen_14d" -> R.string.attention_not_seen_14d
            "never_seen" -> R.string.attention_never_seen
            else -> R.string.attention_no_location
        }

        fun fromJson(text: String): TourSummary? = try {
            TourSummary(JSONObject(text))
        } catch (e: Exception) {
            null
        }

        private fun numbers(array: JSONArray?): List<Int> =
            if (array == null) emptyList() else (0 until array.length()).map { array.optInt(it) }
    }

    //the original json text (to keep the summary when the screen is recreated)
    override fun toString(): String = json.toString()
}

/**
 * One tour in the history list: who did it, when, its status (open / closed / cancelled) and its saved summary
 * ([summaryJson], null while the tour is open)
 */
data class TourHistoryItem(
    val id: String,
    val startedBy: String,
    val startedAtMillis: Long?,
    val endedAtMillis: Long?,
    val status: String,
    val summaryJson: String?
) {
    val isOpen: Boolean get() = status == "open"
}

/**
 * The tour history of the ranch: the tours (newest first), who is asking, and whether he may end the tours of the other
 * cowboys (a coordinator or an owner)
 */
data class TourHistory(val items: List<TourHistoryItem>, val myEmail: String, val canEndOthers: Boolean) {
    //a tour can be ended by the cowboy who started it, or by a coordinator / owner
    fun canEnd(item: TourHistoryItem): Boolean =
        item.isOpen && (canEndOthers || item.startedBy.equals(myEmail, ignoreCase = true))
}
