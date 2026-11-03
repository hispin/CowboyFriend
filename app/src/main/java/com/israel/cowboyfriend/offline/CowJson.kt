package com.israel.cowboyfriend.offline

import com.israel.cowboyfriend.classes.CowDetails
import org.json.JSONArray
import org.json.JSONObject

/**
 * A cow as a json text: this is how the cows are kept on the phone (see [LocalDb]) and how a new cow waits in the list of the
 * changes to send. only the data of the cow is written, the name of the mother (CowDetails.momLabel) is worked out again when
 * the list is loaded
 */
object CowJson {

    fun toJson(cow: CowDetails): JSONObject = JSONObject().apply {
        put("id", cow.id ?: JSONObject.NULL)
        put("number", cow.number ?: JSONObject.NULL)
        put("number_mom", cow.number_mom ?: JSONObject.NULL)
        put("gender", cow.gender ?: JSONObject.NULL)
        put("image_url", cow.image_url ?: JSONObject.NULL)
        put("user_id", cow.user_id ?: JSONObject.NULL)
        put("comment", cow.comment ?: JSONObject.NULL)
        put("latitude", cow.latitude ?: JSONObject.NULL)
        put("longitude", cow.longitude ?: JSONObject.NULL)
        put("location_updated_at", cow.location_updated_at ?: JSONObject.NULL)
        put("last_seen_at", cow.last_seen_at ?: JSONObject.NULL)
        put("cow_type", cow.cowType ?: JSONObject.NULL)
        put("with_mom", cow.with_mom ?: JSONObject.NULL)
        put("marked_tag", cow.isMarkedTag ?: JSONObject.NULL)
        put("corpse", cow.isCorpse)
        put("num_of_calvings", cow.num_of_calvings ?: JSONObject.NULL)
        put("created_at", cow.created_at ?: JSONObject.NULL)
        put("health_issues", JSONArray(cow.healthIssues))
        put("mom_id", cow.momId ?: JSONObject.NULL)
        put("calving_difficulty", cow.calvingDifficulty)
        put("last_calving_at", cow.lastCalvingAt ?: JSONObject.NULL)
        put("last_calving_difficulty", cow.lastCalvingDifficulty ?: JSONObject.NULL)
        put("hard_calvings", cow.hardCalvings)
        put("pending_sync", cow.pendingSync)
        put("created_locally_at", cow.createdLocallyAt ?: JSONObject.NULL)
    }

    fun fromJson(json: JSONObject): CowDetails {
        val cow = CowDetails(
            number = json.intOrNull("number"),
            number_mom = json.intOrNull("number_mom"),
            gender = json.stringOrNull("gender"),
            image_url = json.stringOrNull("image_url"),
            user_id = json.stringOrNull("user_id"),
            comment = json.stringOrNull("comment"),
            latitude = json.doubleOrNull("latitude"),
            longitude = json.doubleOrNull("longitude"),
            location_updated_at = json.longOrNull("location_updated_at"),
            last_seen_at = json.longOrNull("last_seen_at"),
            cowType = json.stringOrNull("cow_type"),
            with_mom = json.longOrNull("with_mom"),
            isMarkedTag = if (json.isNull("marked_tag")) null else json.optBoolean("marked_tag")
        )
        cow.id = json.intOrNull("id")
        cow.isCorpse = json.optBoolean("corpse", false)
        cow.num_of_calvings = json.intOrNull("num_of_calvings")
        cow.created_at = json.longOrNull("created_at")
        val health = json.optJSONArray("health_issues")
        cow.healthIssues = if (health == null) emptyList() else (0 until health.length()).map { health.getString(it) }
        cow.momId = json.intOrNull("mom_id")
        cow.calvingDifficulty = json.optInt("calving_difficulty", 0)
        cow.lastCalvingAt = json.longOrNull("last_calving_at")
        cow.lastCalvingDifficulty = json.intOrNull("last_calving_difficulty")
        cow.hardCalvings = json.optInt("hard_calvings", 0)
        cow.pendingSync = json.optBoolean("pending_sync", false)
        cow.createdLocallyAt = json.longOrNull("created_locally_at")
        return cow
    }
}

//the readers of a json object that give null for a missing or null value (org.json gives 0 / "null" instead)
fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else optDouble(key)
fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
