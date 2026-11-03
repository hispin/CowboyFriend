package com.israel.cowboyfriend.offline

import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.TourState
import com.israel.cowboyfriend.global.CowIssues
import com.israel.cowboyfriend.global.CowLabels
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The tour as the phone knows it. a tour is started and ended on the phone, also without a network: the phone keeps the tour
 * (this record), counts the cows that were seen from its own list, and makes a temporary summary when the tour ends. the
 * start and the end of the tour wait in the outbox, and when they are sent the server makes the real summary (the tour
 * history shows it). the record lives in the small values of [LocalDb] (key "tour").
 */
class LocalTour(
    //the number of the tour on the phone (the number on the server when the tour was started on another device)
    val localId: String,
    //the number of the tour on the server, empty until the start of the tour was sent
    var serverId: String?,
    var startedAt: Long,
    val startedBy: String,
    //set when the cowboy ended the tour; the record stays until the end was sent to the server
    var endedAt: Long?,
    //what happened in the tour on this phone, for the temporary summary
    val touched: MutableSet<Int> = LinkedHashSet(),
    var commentsAdded: Int = 0,
    var newCorpses: Int = 0,
    var newCalves: Int = 0
) {
    //the number the screens use for the tour: the one of the server when there is one
    val shownId: String get() = serverId ?: localId

    val isOpen: Boolean get() = endedAt == null

    fun toJson(): JSONObject = JSONObject().apply {
        put("localId", localId)
        put("serverId", serverId ?: JSONObject.NULL)
        put("startedAt", startedAt)
        put("startedBy", startedBy)
        put("endedAt", endedAt ?: JSONObject.NULL)
        put("touched", JSONArray(touched.toList()))
        put("commentsAdded", commentsAdded)
        put("newCorpses", newCorpses)
        put("newCalves", newCalves)
    }

    /** the state for the tour bar: how many of the living cows were seen since the tour started, counted from the list on the phone */
    fun state(cows: List<CowDetails>): TourState {
        val alive = cows.filter { !it.isCorpse }
        val seen = alive.count { (it.last_seen_at ?: 0L) >= startedAt }
        return TourState(shownId, startedAt, seen, alive.size)
    }

    companion object {
        private const val KEY = "tour"

        fun fromJson(json: JSONObject): LocalTour {
            val touched = LinkedHashSet<Int>()
            json.optJSONArray("touched")?.let { for (i in 0 until it.length()) touched.add(it.getInt(i)) }
            return LocalTour(
                localId = json.getString("localId"),
                serverId = json.stringOrNull("serverId")?.takeIf { it.isNotBlank() },
                startedAt = json.getLong("startedAt"),
                startedBy = json.optString("startedBy"),
                endedAt = json.longOrNull("endedAt"),
                touched = touched,
                commentsAdded = json.optInt("commentsAdded"),
                newCorpses = json.optInt("newCorpses"),
                newCalves = json.optInt("newCalves")
            )
        }

        fun get(db: LocalDb): LocalTour? = db.kvGet(KEY)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }

        fun save(db: LocalDb, tour: LocalTour) = db.kvSet(KEY, tour.toJson().toString())

        fun clear(db: LocalDb) = db.kvSet(KEY, null)

        //the temporary summary of an ended tour is kept under the number of the tour until the history shows the real one
        fun saveSummary(db: LocalDb, tourId: String, summary: JSONObject) = db.kvSet("tour_summary:$tourId", summary.toString())

        fun getSummary(db: LocalDb, tourId: String): String? = db.kvGet("tour_summary:$tourId")

        fun clearSummary(db: LocalDb, tourId: String) = db.kvSet("tour_summary:$tourId", null)
    }
}

/**
 * The temporary summary of a tour, made on the phone from its list of cows, in the same json form as the one the server
 * makes (see supabase/tours_schema.sql, the function tour_summary), so the summary screen shows it as it is. it has no
 * comparison with the previous tour. when the tour is sent, the history shows the real summary of the server
 */
object LocalSummary {

    fun isoOf(millis: Long): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun build(tour: LocalTour, cows: List<CowDetails>, endedAt: Long): JSONObject {
        val alive = cows.filter { !it.isCorpse }
        val seen = alive.filter { (it.last_seen_at ?: 0L) >= tour.startedAt }.sortedBy { it.number }
        val notSeen = alive.filter { (it.last_seen_at ?: 0L) < tour.startedAt }.sortedBy { it.number }

        fun kind(index: Int) = alive.count { CowLabels.typeIndex(it.cowType) == index }

        //the open problems of the herd, counted with the same rules as the list of the app (CowIssues)
        val problemCounts = LinkedHashMap<String, Int>()
        for (cow in alive) {
            for (issue in CowIssues.of(cow, endedAt)) problemCounts[issue] = (problemCounts[issue] ?: 0) + 1
        }
        val problems = JSONObject().apply {
            for (key in listOf("calf_without_mom", "calf_without_tag", "no_location", "not_seen_14d", "never_seen", "health_issue", "hard_calving")) {
                put(key, problemCounts[key] ?: 0)
            }
            put("calf_without_mom_numbers", JSONArray(
                alive.filter { "calf_without_mom" in CowIssues.of(it, endedAt) }.mapNotNull { it.number }.sorted()
            ))
        }

        return JSONObject().apply {
            put("tour_id", tour.shownId)
            put("status", "closed")
            put("started_by", tour.startedBy)
            put("started_at", isoOf(tour.startedAt))
            put("ended_at", isoOf(endedAt))
            put("duration_minutes", Math.round((endedAt - tour.startedAt) / 60000.0).toInt())
            put("herd", JSONObject().apply {
                put("alive", alive.size)
                put("calves", kind(CowLabels.TYPE_CALF))
                put("first_cows", kind(CowLabels.TYPE_FIRST_COW))
                put("cows", kind(CowLabels.TYPE_COW))
                put("bulls", kind(CowLabels.TYPE_BULL))
                put("dead", cows.size - alive.size)
            })
            put("seen", seen.size)
            put("seen_numbers", JSONArray(seen.mapNotNull { it.number }))
            put("not_seen", JSONObject().apply {
                put("count", notSeen.size)
                put("numbers", JSONArray(notSeen.mapNotNull { it.number }))
            })
            put("changed", tour.touched.size)
            put("new_calves", JSONObject().apply { put("count", tour.newCalves) })
            put("new_corpses", JSONObject().apply { put("count", tour.newCorpses) })
            put("comments_added", tour.commentsAdded)
            put("problems", problems)
            //a temporary summary: the screen can tell that the numbers are of the phone
            put("provisional", true)
        }
    }
}
