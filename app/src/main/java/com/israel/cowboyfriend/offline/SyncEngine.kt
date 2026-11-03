package com.israel.cowboyfriend.offline

import android.util.Log
import com.israel.cowboyfriend.DB.CalvingInsertDto
import com.israel.cowboyfriend.DB.CowCalvingsDto
import com.israel.cowboyfriend.DB.CowDto
import com.israel.cowboyfriend.DB.CowImageUrlDto
import com.israel.cowboyfriend.DB.TourRowDto
import com.israel.cowboyfriend.DB.UsersInfoMapElementsDto
import com.israel.cowboyfriend.classes.CalvingDifficulty
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.MapElement
import com.israel.cowboyfriend.global.COW_IMAGES_BUCKET
import com.israel.cowboyfriend.global.CowImageStorage
import com.israel.cowboyfriend.global.CowLabels
import com.israel.cowboyfriend.global.parseTimestampMillis
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Sends the changes that were made on the phone (the outbox of [LocalDb]) to the server, one after the other in the order they
 * were made. a change that needs several steps (a new cow: the picture, the row, the mother, the calving) remembers the step it
 * reached in its own record, so a connection that drops in the middle never makes a second cow or a second calving.
 *
 * - no network, or a weak one: the sync stops and tries again later (the work manager and the network monitor start it again);
 * - a change the server refuses is counted, and after [MAX_ATTEMPTS] attempts it gives up and stays in the list as failed.
 */
class SyncEngine(private val db: LocalDb) {

    private val mutex = Mutex()

    /**
     * sends the waiting changes. returns true when nothing waits any more. [announce]: tell the screens when everything was sent
     * (the view model that asked for the sync reads the server itself, so it passes false)
     */
    suspend fun sync(announce: Boolean = true): Boolean = mutex.withLock {
        if (db.pendingCount() == 0) return@withLock true
        val client = SupabaseProvider.get()

        //the login has to be ready (after a long time without a network the saved login is refreshed first)
        if (!SupabaseProvider.awaitLogin(LOGIN_WAIT_MILLIS)) {
            OfflineHub.publishStatus(syncing = false)
            return@withLock false
        }

        OfflineHub.publishStatus(syncing = true)
        var sent = 0
        while (true) {
            val op = db.ops().firstOrNull() ?: break
            try {
                execute(client, op)
                db.deleteOp(op.seq)
                sent++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isTransient(e)) {
                    Log.w(TAG, "no connection for ${op.type}, will try again", e)
                } else {
                    Log.e(TAG, "the server refused ${op.type}", e)
                    db.markAttempt(op.seq, e.message, MAX_ATTEMPTS)
                }
                break
            }
        }

        refreshPendingFlags()
        val allSent = db.pendingCount() == 0
        OfflineHub.publishStatus(syncing = false)
        if (sent > 0) OfflineHub.dataChanged.tryEmit(Unit)
        if (announce && sent > 0 && allSent) OfflineHub.syncFinished.tryEmit(true)
        allSent
    }

    //an error that is only the connection (or the login that is being refreshed): the change is sent later, it is not wrong
    private fun isTransient(e: Throwable): Boolean = when (e) {
        is RestException -> e.statusCode in TRANSIENT_CODES || e.statusCode >= 500
        //an error of our own (a cow that was not sent yet, a row that was not changed) or of the data: trying again does not help
        is IllegalStateException, is IllegalArgumentException -> false
        else -> true
    }

    /** the cows that still have a change waiting are marked, the others are not */
    private fun refreshPendingFlags() {
        val waiting = HashSet<Int>()
        for (op in db.ops()) {
            for (key in listOf("cowId", "localId")) {
                val id = op.payload.intOrNull(key) ?: continue
                waiting.add(id)
                db.serverIdOf(id)?.let { waiting.add(it) }
            }
        }
        for (cow in db.allCows()) {
            val should = cow.id in waiting
            if (cow.pendingSync != should) {
                cow.pendingSync = should
                db.saveCow(cow)
            }
        }
    }

    private suspend fun execute(client: SupabaseClient, op: LocalDb.Op) {
        when (op.type) {
            OP_COW_UPDATE -> cowUpdate(client, op)
            OP_COW_INSERT -> cowInsert(client, op)
            OP_IMAGE_REPLACE -> imageReplace(client, op)
            OP_STILLBIRTH -> stillbirth(client, op)
            OP_MAP_ELEMENTS -> mapElements(client, op)
            OP_TOUR_START -> tourStart(client, op)
            OP_TOUR_END -> tourEnd(client, op)
            else -> Log.w(TAG, "unknown change ${op.type} is dropped")
        }
    }

    // ----- one cow was edited -----

    private suspend fun cowUpdate(client: SupabaseClient, op: LocalDb.Op) {
        val id = db.resolveId(op.payload.getInt("cowId")) ?: error("the cow was not sent yet")
        val fields = toJsonObject(op.payload.getJSONObject("fields"))
        client.from("CowDetails").update(fields) {
            filter { eq("id", id) }
        }
    }

    // ----- a new cow (a calf): picture, row, mother, calving -----

    private suspend fun cowInsert(client: SupabaseClient, op: LocalDb.Op) {
        val p = op.payload
        val cow = CowJson.fromJson(p.getJSONObject("cow"))
        val localId = p.getInt("localId")
        val bucket = client.storage.from(COW_IMAGES_BUCKET)
        fun stage() = p.optInt("stage", 0)
        fun advance(to: Int) {
            p.put("stage", to)
            db.updatePayload(op.seq, p)
        }

        //1: the picture goes to the storage under a name that was made when the cow was added (a second try writes the same file)
        if (stage() < 1) {
            val imagePath = p.optString("imagePath", "")
            if (imagePath.isNotEmpty() && File(imagePath).exists()) {
                val uploadPath = p.getString("uploadPath")
                uploadOnce(bucket, uploadPath, File(imagePath))
                p.put("imageUrl", CowImageStorage.publicUrl(uploadPath))
            }
            advance(1)
        }
        val imageUrl = p.stringOrNull("imageUrl")

        //2: the row. a row with the same picture is the same cow (the first try may have reached the server)
        if (stage() < 2) {
            var serverId = imageUrl?.let { findIdByImageUrl(client, it) }
            if (serverId == null) {
                val dto = CowDto(
                    number = cow.number, number_mom = cow.number_mom, gender = cow.gender, image_url = imageUrl,
                    user_id = cow.user_id, comment = cow.comment, latitude = cow.latitude, longitude = cow.longitude,
                    location_updated_at = cow.location_updated_at, corpse = cow.isCorpse, last_seen_at = cow.last_seen_at,
                    cow_type = cow.cowType, with_mom = cow.with_mom, marked_tag = cow.isMarkedTag,
                    num_of_calvings = cow.num_of_calvings, created_at = cow.created_at?.let { LocalSummary.isoOf(it) }
                )
                serverId = if (imageUrl != null) {
                    client.from("CowDetails").insert(dto)
                    findIdByImageUrl(client, imageUrl)
                } else {
                    client.from("CowDetails").insert(dto) { select(Columns.list("id")) }
                        .decodeList<CowCalvingsDto>().firstOrNull()?.id
                }
            }
            if (serverId == null) error("the new cow was not found after it was saved")
            db.replaceCowId(localId, serverId)
            db.getCow(serverId)?.let { saved ->
                if (imageUrl != null) saved.image_url = imageUrl
                db.saveCow(saved)
            }
            p.put("serverId", serverId)
            advance(2)
        }
        val serverId = p.getInt("serverId")

        //3: the mother has one more calving (a failure of the data, like a missing mother, does not stop the cow)
        if (stage() < 3) {
            val momId = optionalStep("the calvings of the mother") { incrementMom(client, cow) }
            p.put("momServerId", momId ?: JSONObject.NULL)
            advance(3)
        }
        val momServerId = p.intOrNull("momServerId")

        //4: the calf remembers its mother (the column mom_id may not exist yet)
        if (stage() < 4) {
            if (momServerId != null) {
                optionalStep("the mother of the calf") {
                    client.from("CowDetails").update({ set("mom_id", momServerId) }) { filter { eq("id", serverId) } }
                }
            }
            advance(4)
        }

        //5: the calving and how it went (the table calvings may not exist yet)
        if (stage() < 5) {
            if (momServerId != null) {
                val born = LocalSummary.isoOf(cow.created_at ?: System.currentTimeMillis())
                val difficulty = p.optInt("difficulty", CalvingDifficulty.NONE)
                optionalStep("the calving") {
                    client.from("calvings").insert(CalvingInsertDto(momServerId, serverId, born, difficulty))
                }
            }
            advance(5)
        }

        //the picture on the phone is not needed any more
        p.optString("imagePath", "").takeIf { it.isNotEmpty() }?.let { runCatching { File(it).delete() } }
    }

    /**
     * puts the file in the storage under [path]. a file that is already there (the first try reached the storage and only the
     * answer was lost) is the same picture, so that is not an error
     */
    private suspend fun uploadOnce(bucket: io.github.jan.supabase.storage.BucketApi, path: String, file: File) {
        try {
            bucket.upload(path = path, file = file)
        } catch (e: RestException) {
            val duplicate = e.statusCode == 409 || e.message?.contains("already exists", ignoreCase = true) == true ||
                e.message?.contains("Duplicate", ignoreCase = true) == true
            if (!duplicate) throw e
        }
    }

    /** the id of the cow whose picture is [url], or null */
    private suspend fun findIdByImageUrl(client: SupabaseClient, url: String): Int? =
        client.from("CowDetails").select(Columns.list("id")) {
            filter { eq("image_url", url) }
        }.decodeList<CowCalvingsDto>().firstOrNull()?.id

    /**
     * raises by one the number of calvings of the mother of the new [calf] and gives back her id (null when she is not found or
     * not clear). the mother is the cow that was chosen in the list; without her id it is the living cow of the user that has the
     * mother number, and when that number matches more than one cow it is not known which one is the mother
     */
    private suspend fun incrementMom(client: SupabaseClient, calf: CowDetails): Int? {
        val momNumber = calf.number_mom ?: return null
        if (CowLabels.typeIndex(calf.cowType) != CowLabels.TYPE_CALF) return null
        val email = calf.user_id ?: return null

        val momId = calf.momId?.let { db.resolveId(it) }
        val columns = Columns.list("id", "cow_type", "corpse", "num_of_calvings")
        val mom = if (momId != null) {
            client.from("CowDetails").select(columns) { filter { eq("id", momId) } }.decodeList<CowCalvingsDto>().singleOrNull()
        } else {
            val sameNumber = client.from("CowDetails").select(columns) {
                filter {
                    eq("user_id", email)
                    eq("number", momNumber)
                }
            }.decodeList<CowCalvingsDto>().filter { CowLabels.typeIndex(it.cow_type) == CowLabels.TYPE_COW }
            sameNumber.filter { it.corpse != true }.ifEmpty { sameNumber }.singleOrNull()
        } ?: return null
        client.from("CowDetails").update({ set("num_of_calvings", (mom.num_of_calvings ?: 0) + 1) }) {
            filter { eq("id", mom.id) }
        }
        return mom.id
    }

    /** runs a step that may fail because of the data (a missing column or table): that is only logged. a lost connection is not hidden */
    private suspend fun <T> optionalStep(name: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (isTransient(e)) throw e
        Log.w(TAG, "$name was not saved", e)
        null
    }

    // ----- the picture of a cow was replaced -----

    private suspend fun imageReplace(client: SupabaseClient, op: LocalDb.Op) {
        val p = op.payload
        val cowId = db.resolveId(p.getInt("cowId")) ?: error("the cow was not sent yet")
        val bucket = client.storage.from(COW_IMAGES_BUCKET)
        fun stage() = p.optInt("stage", 0)
        fun advance(to: Int) {
            p.put("stage", to)
            db.updatePayload(op.seq, p)
        }
        val imagePath = p.getString("imagePath")

        //1: the new picture goes to the storage
        if (stage() < 1) {
            val uploadPath = p.getString("uploadPath")
            uploadOnce(bucket, uploadPath, File(imagePath))
            p.put("newUrl", CowImageStorage.publicUrl(uploadPath))
            advance(1)
        }
        val newUrl = p.getString("newUrl")

        //2: the cow points to the new picture (the old one is read first, to be deleted in the next step)
        if (stage() < 2) {
            val oldUrl = client.from("CowDetails").select(Columns.list("image_url")) {
                filter { eq("id", cowId) }
            }.decodeList<CowImageUrlDto>().firstOrNull()?.image_url
            p.put("oldPath", CowImageStorage.pathInBucket(oldUrl) ?: JSONObject.NULL)
            val changed = client.from("CowDetails").update({ set("image_url", newUrl) }) {
                select(Columns.list("image_url"))
                filter { eq("id", cowId) }
            }.decodeList<CowImageUrlDto>()
            check(changed.isNotEmpty()) { "no cow row was updated" }
            advance(2)
        }

        //3: the old picture is deleted from the storage (a refusal of the storage is only logged)
        if (stage() < 3) {
            val oldPath = p.stringOrNull("oldPath")
            if (oldPath != null && oldPath != CowImageStorage.pathInBucket(newUrl)) {
                optionalStep("the delete of the old picture") { bucket.delete(oldPath) }
            }
            advance(3)
        }

        //the cow on the phone shows the picture of the server, and the file of the phone is not needed
        db.getCow(cowId)?.let { saved ->
            saved.image_url = newUrl
            db.saveCow(saved)
        }
        runCatching { File(imagePath).delete() }
    }

    // ----- a calf that was born dead -----

    private suspend fun stillbirth(client: SupabaseClient, op: LocalDb.Op) {
        val p = op.payload
        val cowId = db.resolveId(p.getInt("cowId")) ?: error("the cow was not sent yet")
        fun advance(to: Int) {
            p.put("stage", to)
            db.updatePayload(op.seq, p)
        }
        if (p.optInt("stage", 0) < 1) {
            val born = LocalSummary.isoOf(p.getLong("calvedAt"))
            client.from("calvings").insert(CalvingInsertDto(cowId, null, born, p.optInt("difficulty"), "dead"))
            advance(1)
        }
        if (p.optInt("stage", 0) < 2) {
            val mother = client.from("CowDetails").select(Columns.list("id", "cow_type", "corpse", "num_of_calvings")) {
                filter { eq("id", cowId) }
            }.decodeList<CowCalvingsDto>().single()
            client.from("CowDetails").update({ set("num_of_calvings", (mother.num_of_calvings ?: 0) + 1) }) {
                filter { eq("id", cowId) }
            }
            advance(2)
        }
    }

    // ----- the elements the user added to the map -----

    private suspend fun mapElements(client: SupabaseClient, op: LocalDb.Op) {
        val email = op.payload.getString("email")
        var elements = Json.decodeFromString<List<MapElement>>(op.payload.getString("elements"))
        //the list of the server was never read on this phone: the elements that the server holds are kept
        if (op.payload.optBoolean("merge", false)) {
            val onServer = client.from("UsersInfo").select(Columns.list("map_element")) {
                filter { eq("email", email) }
            }.decodeList<UsersInfoMapElementsDto>().firstOrNull()?.mapElement.orEmpty()
            elements = elements + onServer.filter { server -> elements.none { it.id == server.id } }
        }
        val rows = client.from("UsersInfo").update({ set("map_element", elements) }) {
            filter { eq("email", email) }
            select(Columns.list("map_element"))
        }.decodeList<UsersInfoMapElementsDto>()
        check(rows.isNotEmpty()) { "no row was updated" }
    }

    // ----- the tour -----

    private suspend fun tourStart(client: SupabaseClient, op: LocalDb.Op) {
        val tour = LocalTour.get(db)
        if (tour == null || tour.serverId != null) return
        val serverId = client.postgrest.rpc("start_tour", buildJsonObject { }).data.trim().trim('"')

        //a tour that was just made gets the time the cowboy started it on the phone; a tour that was already open (started on
        //another device) keeps its own time, and the phone follows it
        val row = client.from("tours").select(Columns.list("id", "started_at")) {
            filter { eq("id", serverId) }
        }.decodeList<TourRowDto>().firstOrNull()
        val serverStart = parseTimestampMillis(row?.started_at)
        if (serverStart != null && Math.abs(serverStart - System.currentTimeMillis()) < NEW_TOUR_MILLIS) {
            optionalStep("the start time of the tour") {
                client.from("tours").update({ set("started_at", LocalSummary.isoOf(tour.startedAt)) }) {
                    filter { eq("id", serverId) }
                }
            }
        } else if (serverStart != null) {
            tour.startedAt = serverStart
        }
        tour.serverId = serverId
        LocalTour.save(db, tour)
    }

    private suspend fun tourEnd(client: SupabaseClient, op: LocalDb.Op) {
        val p = op.payload
        val tour = LocalTour.get(db)
        val serverId = tour?.serverId ?: p.stringOrNull("serverId")?.takeIf { it.isNotBlank() }
            ?: error("the tour was not sent yet")
        val endedAt = p.optLong("endedAt", System.currentTimeMillis())

        var summary = try {
            client.postgrest.rpc("end_tour", buildJsonObject { put("p_tour", serverId) }).data
        } catch (e: RestException) {
            //the end of the tour reached the server in an earlier try
            if (e.message?.contains("already closed") == true) {
                client.postgrest.rpc("tour_summary", buildJsonObject { put("p_tour", serverId) }).data
            } else {
                throw e
            }
        }

        //the tour ended on the phone some time ago: the server saved the time of the sync, so the real end time is written back and
        //the summary is made again with it (it holds the length of the tour)
        if (Math.abs(System.currentTimeMillis() - endedAt) > NEW_TOUR_MILLIS / 2) {
            optionalStep("the end time of the tour") {
                client.from("tours").update({ set("ended_at", LocalSummary.isoOf(endedAt)) }) { filter { eq("id", serverId) } }
                summary = client.postgrest.rpc("tour_summary", buildJsonObject { put("p_tour", serverId) }).data
                client.from("tours").update({ set("summary", Json.parseToJsonElement(summary)) }) { filter { eq("id", serverId) } }
            }
        }

        //the real summary of the server replaces the temporary one
        tour?.let {
            LocalTour.clearSummary(db, it.localId)
            LocalTour.clear(db)
        }
        LocalTour.clearSummary(db, serverId)
        db.kvSet("history_stale", "1")
    }

    /** a json object of the phone (org.json) as the json object of the library that sends it */
    private fun toJsonObject(json: JSONObject): JsonObject = buildJsonObject {
        for (key in json.keys()) put(key, toElement(json.get(key)))
    }

    private fun toElement(value: Any?): kotlinx.serialization.json.JsonElement = when (value) {
        null, JSONObject.NULL -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is JSONArray -> JsonArray((0 until value.length()).map { toElement(value.get(it)) })
        is JSONObject -> toJsonObject(value)
        else -> JsonPrimitive(value.toString())
    }

    companion object {
        private const val TAG = "OfflineSync"
        private const val MAX_ATTEMPTS = 5
        private const val LOGIN_WAIT_MILLIS = 25_000L
        //a tour that the server made less than two minutes ago is the one that was just started from the phone
        private const val NEW_TOUR_MILLIS = 120_000L
        private val TRANSIENT_CODES = setOf(401, 408, 425, 429)

        const val OP_COW_UPDATE = "cow_update"
        const val OP_COW_INSERT = "cow_insert"
        const val OP_IMAGE_REPLACE = "image_replace"
        const val OP_STILLBIRTH = "stillbirth"
        const val OP_MAP_ELEMENTS = "map_elements"
        const val OP_TOUR_START = "tour_start"
        const val OP_TOUR_END = "tour_end"
    }
}
