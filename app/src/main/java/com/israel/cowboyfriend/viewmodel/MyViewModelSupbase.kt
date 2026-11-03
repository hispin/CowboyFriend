package com.israel.cowboyfriend.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.israel.cowboyfriend.DB.CowDto
import com.israel.cowboyfriend.DB.CalvingInsertDto
import com.israel.cowboyfriend.DB.CalvingRowDto
import com.israel.cowboyfriend.DB.CowCalvingsDto
import com.israel.cowboyfriend.classes.CalvingDifficulty
import com.israel.cowboyfriend.DB.CowImageUrlDto
import com.israel.cowboyfriend.global.CowLabels
import com.israel.cowboyfriend.classes.CowImageReplaceResult
import com.israel.cowboyfriend.global.COW_IMAGES_BUCKET
import com.israel.cowboyfriend.global.CowImageStorage
import com.israel.cowboyfriend.DB.UsersInfoDto
import com.israel.cowboyfriend.DB.RanchRoleDto
import com.israel.cowboyfriend.DB.TourFullDto
import com.israel.cowboyfriend.DB.TourRowDto
import com.israel.cowboyfriend.classes.TourHistory
import com.israel.cowboyfriend.classes.TourHistoryItem
import com.israel.cowboyfriend.DB.UsersInfoMapElementsDto
import com.israel.cowboyfriend.classes.TourState
import com.israel.cowboyfriend.classes.TourSummary
import com.israel.cowboyfriend.global.parseTimestampMillis
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.MapElement
import com.israel.cowboyfriend.global.CORPSE_TYPE
import com.israel.cowboyfriend.global.ALL_FIELDS_TYPE
import com.israel.cowboyfriend.global.COMMENT_TYPE
import com.israel.cowboyfriend.global.LAST_SEEN_AT_TYPE
import com.israel.cowboyfriend.global.MARKED_TAG_TYPE
import com.israel.cowboyfriend.global.TYPE_COW
import com.israel.cowboyfriend.interfaces.CowRepositoryCB
import com.israel.cowboyfriend.interfaces.CowStorageRespose
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import com.israel.cowboyfriend.offline.CowJson
import com.israel.cowboyfriend.offline.LocalDb
import com.israel.cowboyfriend.offline.LocalSummary
import com.israel.cowboyfriend.offline.LocalTour
import com.israel.cowboyfriend.offline.OfflineHub
import com.israel.cowboyfriend.offline.SupabaseProvider
import com.israel.cowboyfriend.offline.SyncEngine
import com.israel.cowboyfriend.offline.SyncStatus
import com.israel.cowboyfriend.offline.longOrNull
import com.israel.cowboyfriend.offline.stringOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.json.JSONArray
import java.io.File
import java.util.ArrayList
import java.util.UUID
import kotlin.collections.sortedBy
import kotlin.runCatching
import kotlin.text.toInt
import kotlin.time.Duration.Companion.seconds


class MyViewModelSupbase (application: Application) : AndroidViewModel(application) {

    //the one connection of the app (also used by the sync that runs in the background); it is safe to use before setSupabase()
    @get:JvmName("supabaseClient")
    private val supabase: SupabaseClient get() = SupabaseProvider.get()
    //the listeners of setSupabase() are started once
    private var listenersStarted = false
    private var realtimeJob: Job? = null
    //private var cowsDto:ArrayList<CowDto> ?=null
    var _cowsDetails = MutableLiveData<List<CowDetails>>()
    var cowsDetails:ArrayList<CowDetails>?=null
    var pageNum = MutableLiveData(0)
    var _farmName = MutableLiveData("")
    /**
     * set page number
     */
    fun setPageNum(num:Int){
        pageNum.postValue(num)
    }


    //the database of the phone, the sync and the network monitor (started once for the whole app)
    private val db: LocalDb get() = OfflineHub.db

    //the status of the work without a network (online, how many changes wait, ...) for the screens
    val syncStatus: LiveData<SyncStatus> = OfflineHub.status.asLiveData()

    //true when the saved login is not valid any more (for example the password was changed): the login screen has to be shown
    val needLogin = MutableLiveData<Boolean>()

    init {
        OfflineHub.init(application)
    }

    /**
     * set supabase
     */
    fun setSupabase() {
        if (listenersStarted) return
        listenersStarted = true

        //the user that is logged in is kept on the phone: after a long time without a network the saved login can be expired for
        //a while (it is refreshed when the network comes back) and the app must still work for the same user
        viewModelScope.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> status.session.user?.email?.let { onUserKnown(it) }
                    is SessionStatus.NotAuthenticated -> {
                        //the login is not valid any more (the saved login was refused): the user has to log in again
                        if (db.kvGet("user_email") != null) {
                            db.kvSet("user_email", null)
                            needLogin.postValue(true)
                        }
                    }
                    else -> {}
                }
            }
        }
        //the sync changed the data on the phone: the list is read from the phone again; when everything was sent the server is read
        viewModelScope.launch {
            OfflineHub.dataChanged.collect {
                withContext(Dispatchers.IO) { loadCowsFromLocal() }
                refreshLocalTourState()
            }
        }
        viewModelScope.launch {
            OfflineHub.syncFinished.collect {
                withContext(Dispatchers.IO) { refreshFromServer() }
            }
        }
    }

    /** the user asked to send the waiting changes now (also the ones that gave up) */
    fun syncNow() {
        db.retryFailed()
        OfflineHub.requestSync()
    }

    //a user is logged in: another user than the one of the data on the phone starts from nothing, so nobody sees (or sends) the data of someone else
    private fun onUserKnown(email: String) {
        val owner = db.kvGet("owner")
        if (owner != null && !owner.equals(email, ignoreCase = true)) {
            db.wipe()
            cowsDetails = null
            _cowsDetails.postValue(emptyList())
        }
        db.kvSet("owner", email)
        db.kvSet("user_email", email)
    }

    fun getSupabase(): SupabaseClient {
        return supabase
    }

    /**
     * the e-mail of the logged in user. when the saved login is not usable for the moment (no network for a long time) it is the
     * last user that was logged in on this phone, so the app keeps working
     */
    fun currentEmail(): String? = supabase.auth.currentUserOrNull()?.email ?: db.kvGet("user_email")

    /**
     * get user name
     */
    fun getUserName():String{
        return currentEmail().toString()
    }


    /**
     * read the farm name (column "name") of the user from the table UsersInfo, by the email column.
     * the name is published in [_farmName] (the screens follow it) and also given to the callback;
     * the callback gets null when there is no such row or the request failed
     */
    fun dbGetFarmName(email: String, callback: (String?) -> Unit) {
        //the name that was read the last time is shown at once, so it is there also without a network
        val saved = db.kvGet("farm_name")?.takeIf { it.isNotBlank() }
        if (saved != null) _farmName.postValue(saved)
        if (!OfflineHub.network.online.value) {
            callback(saved)
            return
        }
        viewModelScope.launch {
            val name = runCatching {
                if (!SupabaseProvider.awaitLogin()) error("no login")
                supabase.from("UsersInfo").select {
                    filter {
                        eq("email", email)
                    }
                }.decodeList<UsersInfoDto>().firstOrNull()?.name?.takeIf { it.isNotBlank() }
            }.getOrNull()
            //a user without a row in the table has no farm name: nothing is published
            if (name != null) {
                _farmName.postValue(name)
                db.kvSet("farm_name", name)
            }
            callback(name ?: saved)
        }
    }

    //the elements the user added to the map (column map_element of UsersInfo); the map screen follows it
    val _mapElements = MutableLiveData<List<MapElement>>()

    //reads the list of the map elements of the user from the database (suspend: the caller decides where to wait)
    private suspend fun readMapElements(email: String): List<MapElement> {
        return supabase.from("UsersInfo").select(Columns.list("map_element")) {
            filter {
                eq("email", email)
            }
        }.decodeList<UsersInfoMapElementsDto>().firstOrNull()?.mapElement.orEmpty()
    }

    /**
     * loads the map elements of the user and publishes them in [_mapElements]; a failure is only logged
     * (the map is still usable without the elements)
     */
    fun dbGetMapElements() {
        val email = currentEmail() ?: return
        //the elements that are saved on the phone are shown at once
        savedMapElements()?.let { _mapElements.postValue(it) }
        //the list of the server replaces them only when no change of the elements waits to be sent
        if (!OfflineHub.network.online.value || db.ops().any { it.type == SyncEngine.OP_MAP_ELEMENTS }) return
        viewModelScope.launch {
            runCatching {
                if (!SupabaseProvider.awaitLogin()) error("no login")
                readMapElements(email)
            }
                .onSuccess {
                    if (db.ops().none { op -> op.type == SyncEngine.OP_MAP_ELEMENTS }) {
                        db.kvSet("map_elements", Json.encodeToString(it))
                        db.kvSet("map_elements_loaded", "1")
                        _mapElements.postValue(it)
                    }
                }
                .onFailure { Log.e("MapElements", "read failed", it) }
        }
    }

    //the map elements that were saved on the phone (null when they were never saved)
    private fun savedMapElements(): List<MapElement>? =
        db.kvGet("map_elements")?.let { runCatching { Json.decodeFromString<List<MapElement>>(it) }.getOrNull() }

    /**
     * changes the list of the map elements: [change] makes the new list from the list on the phone, the new list is saved on the
     * phone and shown at once, and it waits in the outbox to be written to the column map_element of the server (a newer change
     * replaces a waiting one). [callback] gets true when the new list was saved on the phone
     */
    fun dbChangeMapElements(change: (List<MapElement>) -> List<MapElement>, callback: (Boolean) -> Unit) {
        val email = currentEmail()
        if (email == null) {
            callback(false)
            return
        }
        val updated = change(_mapElements.value ?: savedMapElements().orEmpty())
        val text = Json.encodeToString(updated)
        db.kvSet("map_elements", text)
        _mapElements.postValue(updated)
        db.deleteOpsOfType(SyncEngine.OP_MAP_ELEMENTS)
        db.enqueue(
            SyncEngine.OP_MAP_ELEMENTS,
            //"merge": the list of the server was never read on this phone, so the sync adds the elements that the server holds
            JSONObject().put("email", email).put("elements", text).put("merge", db.kvGet("map_elements_loaded") == null)
        )
        callback(true)
        OfflineHub.requestSync()
    }

    //the heat map of the map screen: the mode asked by the ranch dashboard (HEAT_OFF / HEAT_ALL / HEAT_ATTENTION), read by the
    //map screen when it is shown
    var pendingHeatMode = HEAT_OFF

    companion object {
        const val HEAT_OFF = 0
        const val HEAT_ALL = 1
        const val HEAT_ATTENTION = 2
        //how long the app waits before it asks the server again whether a tour of the user is open there
        private const val TOUR_CHECK_MILLIS = 60_000L
    }

    //the tour of the logged in user: no open tour, or the open one with its progress; the cattle tour screen follows it
    val _tourState = MutableLiveData<TourState>()

    /**
     * the tour bar follows the tour that is kept on the phone (see [LocalTour]): no tour, or the open one with the number of
     * the cows that were seen since it started, counted from the list on the phone. so it also works without a network
     */
    private fun refreshLocalTourState() {
        val tour = LocalTour.get(db)
        val cows = cowsDetails ?: emptyList()
        _tourState.postValue(if (tour != null && tour.isOpen) tour.state(cows) else TourState())
    }

    //the last time the server was asked if a tour of this user is open (it is not asked after every change)
    private var lastTourCheckAt = 0L

    /**
     * loads the state of the tour from the phone. when there is a network and the phone has no tour, the server is asked once in
     * a while whether a tour of this user is open there (started on another device, or before the phone kept the tours): that
     * tour is taken over
     */
    fun dbLoadTourState() {
        refreshLocalTourState()
        val now = System.currentTimeMillis()
        if (!OfflineHub.network.online.value || LocalTour.get(db) != null || now - lastTourCheckAt < TOUR_CHECK_MILLIS) return
        lastTourCheckAt = now
        viewModelScope.launch(Dispatchers.IO) {
            if (SupabaseProvider.awaitLogin()) adoptServerTour()
        }
    }

    //a tour that is open on the server and not known to the phone becomes the tour of the phone
    private suspend fun adoptServerTour() {
        if (LocalTour.get(db) != null) return
        val email = currentEmail()?.lowercase() ?: return
        val open = runCatching {
            supabase.from("tours").select(Columns.list("id", "started_at")) {
                filter {
                    eq("status", "open")
                    eq("started_by", email)
                }
            }.decodeList<TourRowDto>().firstOrNull()
        }.onFailure { Log.w("Tour", "the open tour was not read", it) }.getOrNull() ?: return
        if (LocalTour.get(db) != null) return
        LocalTour.save(db, LocalTour(open.id, open.id, parseTimestampMillis(open.started_at) ?: System.currentTimeMillis(), email, null))
        refreshLocalTourState()
    }

    /**
     * starts a tour (or continues the open one). the tour is kept on the phone at once and the start waits in the outbox to be
     * sent with the database function start_tour. [callback] gets true when the tour started. a tour that ended on the phone and
     * was not sent yet must be sent first, so a new tour needs a network in that case
     */
    fun dbStartTour(callback: (Boolean) -> Unit) {
        val email = currentEmail()?.lowercase()
        if (email == null) {
            callback(false)
            return
        }
        val existing = LocalTour.get(db)
        if (existing != null && existing.isOpen) {
            refreshLocalTourState()
            callback(true)
            return
        }
        fun startNow() {
            val tour = LocalTour(UUID.randomUUID().toString(), null, System.currentTimeMillis(), email, null)
            LocalTour.save(db, tour)
            db.enqueue(SyncEngine.OP_TOUR_START, JSONObject().put("localId", tour.localId))
            refreshLocalTourState()
            callback(true)
            OfflineHub.requestSync()
        }
        if (existing == null) {
            startNow()
            return
        }
        //the previous tour was ended on the phone and was not sent yet
        viewModelScope.launch(Dispatchers.IO) {
            if (OfflineHub.network.online.value) OfflineHub.engine.sync(announce = false)
            withContext(Dispatchers.Main) {
                if (LocalTour.get(db) == null) startNow() else callback(false)
            }
        }
    }

    /**
     * ends the tour. the tour of the phone ends at once: a temporary summary is made from the cows of the phone and the end waits
     * in the outbox to be sent with the database function end_tour (the server makes the real summary, which the tour history
     * shows). [callback] gets the summary, or null when the tour could not be ended. a tour of another cowboy (ended by a
     * coordinator) is ended on the server, so that needs a network
     */
    fun dbEndTour(tourId: String, callback: (TourSummary?) -> Unit) {
        val tour = LocalTour.get(db)
        if (tour != null && tour.isOpen && (tourId == tour.localId || tourId == tour.serverId)) {
            val endedAt = System.currentTimeMillis()
            val summary = LocalSummary.build(tour, cowsDetails ?: db.allCows(), endedAt)
            tour.endedAt = endedAt
            LocalTour.save(db, tour)
            LocalTour.saveSummary(db, tour.localId, summary)
            tour.serverId?.let { LocalTour.saveSummary(db, it, summary) }
            db.enqueue(
                SyncEngine.OP_TOUR_END,
                JSONObject().put("localId", tour.localId).put("endedAt", endedAt).put("serverId", tour.serverId ?: "")
            )
            refreshLocalTourState()
            callback(TourSummary.fromJson(summary.toString()))
            OfflineHub.requestSync()
            return
        }
        viewModelScope.launch {
            val summary = runCatching {
                if (!OfflineHub.network.online.value || !SupabaseProvider.awaitLogin()) error("no network")
                val result = supabase.postgrest.rpc("end_tour", buildJsonObject { put("p_tour", tourId) })
                TourSummary.fromJson(result.data) ?: error("the summary is not a json object")
            }.onFailure { Log.e("Tour", "end failed", it) }.getOrNull()
            callback(summary)
        }
    }

    /**
     * the summary of a tour (also of an open one, "so far"), for the history list. the temporary summary of a tour that was ended
     * on the phone is returned from the phone; the others are read from the server. [callback] gets null on a failure
     */
    fun dbTourSummary(tourId: String, callback: (TourSummary?) -> Unit) {
        LocalTour.getSummary(db, tourId)?.let { TourSummary.fromJson(it) }?.let {
            callback(it)
            return
        }
        viewModelScope.launch {
            val summary = runCatching {
                if (!OfflineHub.network.online.value || !SupabaseProvider.awaitLogin()) error("no network")
                TourSummary.fromJson(supabase.postgrest.rpc("tour_summary", buildJsonObject { put("p_tour", tourId) }).data)
            }.onFailure { Log.e("Tour", "summary failed", it) }.getOrNull()
            callback(summary)
        }
    }

    /**
     * loads the tour history of the ranch (the last [limit] tours, newest first) and the role of the user. a coordinator
     * sees the tours of all the cowboys; a cowboy sees the tours of his ranch as the database allows. without a network the
     * history that was read the last time is shown. the tour of the phone that the server does not know yet is shown first.
     * [callback] gets null when there is nothing to show
     */
    fun dbLoadTourHistory(limit: Int = 60, callback: (TourHistory?) -> Unit) {
        val email = currentEmail()?.lowercase()
        if (email == null) {
            callback(null)
            return
        }
        viewModelScope.launch {
            //the server is read only when no start or end of a tour waits (it would not know that tour yet)
            val canRead = OfflineHub.network.online.value && db.ops().none { it.type == SyncEngine.OP_TOUR_START || it.type == SyncEngine.OP_TOUR_END }
            var history = if (canRead) runCatching {
                if (!SupabaseProvider.awaitLogin()) error("no login")
                val rows = supabase.from("tours").select {
                    order("started_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(limit.toLong())
                }.decodeList<TourFullDto>()
                val items = rows.map { row ->
                    TourHistoryItem(
                        id = row.id,
                        startedBy = row.started_by.orEmpty(),
                        startedAtMillis = parseTimestampMillis(row.started_at),
                        endedAtMillis = parseTimestampMillis(row.ended_at),
                        status = row.status ?: "closed",
                        summaryJson = (row.summary as? kotlinx.serialization.json.JsonObject)?.toString()
                    )
                }
                //my role: only a coordinator or an owner may end the tours of other cowboys
                val role = runCatching {
                    supabase.from("ranch_members").select(Columns.list("role")) {
                        filter { eq("user_email", email) }
                    }.decodeList<RanchRoleDto>().mapNotNull { it.role }
                }.getOrDefault(emptyList())
                TourHistory(items, email, role.any { it == "coordinator" || it == "owner" })
            }.onFailure { Log.e("Tour", "history failed", it) }.getOrNull()?.also { cacheHistory(it) } else null
            if (history == null) history = cachedHistory(email)
            callback(withLocalTour(history, email))
        }
    }

    //the history that was read the last time is kept on the phone (also the summaries of the tours)
    private fun cacheHistory(history: TourHistory) {
        val items = org.json.JSONArray()
        for (item in history.items) {
            items.put(
                JSONObject().put("id", item.id).put("startedBy", item.startedBy).put("startedAt", item.startedAtMillis ?: JSONObject.NULL)
                    .put("endedAt", item.endedAtMillis ?: JSONObject.NULL).put("status", item.status)
                    .put("summary", item.summaryJson ?: JSONObject.NULL)
            )
        }
        db.kvSet("history_cache", JSONObject().put("items", items).put("canEndOthers", history.canEndOthers).toString())
    }

    private fun cachedHistory(email: String): TourHistory? = runCatching {
        val json = JSONObject(db.kvGet("history_cache") ?: return null)
        val array = json.getJSONArray("items")
        val items = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            TourHistoryItem(o.getString("id"), o.optString("startedBy"), o.longOrNull("startedAt"), o.longOrNull("endedAt"),
                o.optString("status", "closed"), o.stringOrNull("summary"))
        }
        TourHistory(items, email, json.optBoolean("canEndOthers", false))
    }.getOrNull()

    //the tour of the phone (open, or ended and not sent yet) is shown first, in place of the tour of the server with the same number
    private fun withLocalTour(history: TourHistory?, email: String): TourHistory? {
        val tour = LocalTour.get(db) ?: return history
        val local = TourHistoryItem(
            id = tour.shownId, startedBy = tour.startedBy, startedAtMillis = tour.startedAt, endedAtMillis = tour.endedAt,
            status = if (tour.isOpen) "open" else "closed", summaryJson = LocalTour.getSummary(db, tour.localId)
        )
        val items = ArrayList<TourHistoryItem>(history?.items.orEmpty().filter { it.id != local.id })
        items.add(0, local)
        return TourHistory(items, email, history?.canEndOthers ?: false)
    }

    /**
     * checks whether the user is logged in. a user that was logged in on this phone stays logged in without a network (the saved
     * login is refreshed when the network comes back), so that is answered at once; otherwise it waits until the saved session
     * was loaded (checking before that would wrongly say "not logged in" right after the app starts)
     */
    fun checkSession(callback: (Boolean) -> Unit) {
        if (db.kvGet("user_email") != null) {
            callback(true)
            return
        }
        viewModelScope.launch {
            runCatching { supabase.auth.awaitInitialization() }
            callback(isSessionGetSupabase())
        }
    }
    /**
     * check if session is get (the user of this phone counts also while the saved login is being refreshed)
     */
    fun isSessionGetSupabase(): Boolean {
        return supabase.auth.currentSessionOrNull()!=null || db.kvGet("user_email") != null
    }


    /**
     * login with email and password
     */
    fun login(email: String, password: String,cowRepositoryCallback: CowRepositoryCB) {
        viewModelScope.launch {
            runCatching {
                supabase.auth.signInWith(Email) {
                    this.email=email
                    this.password=password
                }
            }.onSuccess {
                val session=supabase.auth.currentSessionOrNull()
                val user=supabase.auth.currentUserOrNull()
                cowRepositoryCallback.onRequestResult(1)
                // use session / user here
            }.onFailure { error ->
                // show error.message to the user
                cowRepositoryCallback.onRequestResult(0)
            }
        }
    }


//    /**
//     * log in
//     */
//    fun login1(email: String, password: String,cowRepositoryCallback: CowRepositoryCB) {
//
//        viewModelScope.launch {
//            try {
//
//                var user=supabase.auth.currentSessionOrNull()
//
//                supabase.auth.signInWith(Email) {
//                    _email = email
//                    _password=password
//                }.
//
//                user=supabase.auth.currentSessionOrNull()
//                if(user!=null){
//                    cowRepositoryCallback.onRequestResult(1)
//                }else {
//                    cowRepositoryCallback.onRequestResult(0)
//                }
//                //configTabs()
//
//                //val cows=supabase.from("CowDetails").select().decodeList<CowDetails>()
//
//                //val users=supabase.postgrest["CowDetails"].select().decodeList<CowDetails>()
//                //val num=10
//            } catch (ex: Exception) {
//                print(ex)
//                cowRepositoryCallback.onRequestResult(0)
//            }
//        }
//    }

//    /**
//     * log in
//     */
//    fun login(cowRepositoryCallback: CowRepositoryCB) {
//        viewModelScope.launch {
//            try {
//
//                var user=supabase.auth.currentSessionOrNull()
//
//                supabase.auth.signInWith(Email) {
//                    email="hag.swead@gmail.com"
//                    password="ringo1234"
//                }
//
//                user=supabase.auth.currentSessionOrNull()
//                if(user!=null){
//                    cowRepositoryCallback.onRequestResult(1)
//                }else {
//                    cowRepositoryCallback.onRequestResult(0)
//                }
//                //configTabs()
//
//                //val cows=supabase.from("CowDetails").select().decodeList<CowDetails>()
//
//                //val users=supabase.postgrest["CowDetails"].select().decodeList<CowDetails>()
//                //val num=10
//            } catch (ex: Exception) {
//                print(ex)
//                cowRepositoryCallback.onRequestResult(0)
//            }
//        }
//    }

//    /**
//     * get details of all cows
//     */
//    fun getCowsDetails(cowRepositoryCBselect: CowRepositoryCBselect) {
//        runBlocking {
//            try {
//                val cows=supabase.from("CowDetails").select().decodeList<CowDto>()
//                cowRepositoryCBselect.onRequestResult(ArrayList(cows))
//            }catch (ex: Exception){
//                print(ex)
//                cowRepositoryCBselect.onRequestResult(ArrayList(emptyList()))
//            }
//        }
//    }

    /**
     * the list of the cows is published again (after a change on the phone, or after the sync changed the data); the tour bar
     * follows it, because the cows that were seen are counted from the list
     */
    private fun publishCows() {
        _cowsDetails.postValue(cowsDetails?.toList())
        refreshLocalTourState()
    }

    /**
     * the list on the phone is the truth about the cows, so there is nothing to read from the server for one cow: the list is
     * published again (the row of the cow shows what it holds now)
     */
    fun dbGetCowDetailsById(id:Int) {
        publishCows()
    }

    /**
     * get details of all cows: the cows that are saved on the phone are shown at once (also without a network), then the waiting
     * changes are sent and, when nothing waits any more, the list is read from the server and saved on the phone
     */
    fun dbGetCowsDetails() {
        if (cowsDetails.isNullOrEmpty()) loadCowsFromLocal()
        viewModelScope.launch(Dispatchers.IO) { refreshFromServer() }
    }

    //publishes the cows that are saved on the phone (nothing when the app was never online with this user)
    private fun loadCowsFromLocal() {
        val saved = ArrayList(db.allCows())
        if (saved.isEmpty()) return
        val sorted = ArrayList(sortByCalfNumber(saved) ?: saved)
        nameTheMothers(sorted)
        cowsDetails = sorted
        _cowsDetails.postValue(sorted.toList())
        refreshLocalTourState()
    }

    //the list is read from the server by one request at a time
    private val pullMutex = Mutex()

    /**
     * with a network: the waiting changes are sent first (the list of the server must not replace a change that was not sent),
     * and when they are all sent the list is read from the server
     */
    private suspend fun refreshFromServer() = pullMutex.withLock {
        if (!OfflineHub.network.online.value) return@withLock
        if (!OfflineHub.engine.sync(announce = false)) return@withLock
        pullCows()
    }

    //reads the cows of the logged in user from the table, saves them on the phone and publishes them in _cowsDetails
    private suspend fun pullCows() {
        if (!SupabaseProvider.awaitLogin()) return
        val email = currentEmail() ?: return
        try {
            val result = supabase.from("CowDetails").select(){
                filter {
                    eq("user_id", email)
                }
            }

            //get the identity ids for each row and the add it to main array
            val jsonArray =JSONArray(result.data)
            val strArr =ArrayList<String>()
            //the created_at column (the birth date) of each row, in the same order
            val createdArr = ArrayList<String?>()
            //the health problems (column health_issues, a json array of codes) of each row, in the same order; read from the
            //raw json so the app also works before the column exists
            val healthArr = ArrayList<List<String>>()
            val momIdArr = ArrayList<Int?>()
            for (i in 0 until jsonArray.length()){
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.getString("id")
                strArr.add(id)
                createdArr.add(if (jsonObject.isNull("created_at")) null else jsonObject.getString("created_at"))
                val health = jsonObject.optJSONArray("health_issues")
                healthArr.add(if (health == null) emptyList() else (0 until health.length()).map { health.getString(it) })
                //the id of the mother of a calf (column mom_id), read from the raw json so the app also works before the column exists
                momIdArr.add(if (jsonObject.isNull("mom_id")) null else jsonObject.optInt("mom_id", -1).takeIf { it >= 0 })
            }

            val cowsDto=result.decodeList<CowDto>()
            val fresh = ArrayList<CowDetails>()

            var idx = 0
            for (item in cowsDto) {
                val cow=CowDetails(
                    number=item.number,
                    number_mom=item.number_mom,
                    gender=item.gender,
                    image_url=item.image_url,
                    user_id=item.user_id,
                    comment = item.comment,
                    latitude = item.latitude,
                    longitude = item.longitude,
                    location_updated_at = item.location_updated_at,
                    last_seen_at = item.last_seen_at,
                    cowType=item.cow_type,
                    with_mom=item.with_mom,
                    isMarkedTag=item.marked_tag
                )
                val id=strArr[idx++]
                if(id.isDigitsOnly()) {
                    cow.id=id.toInt()
                }
                if(item.corpse!=null) {
                    cow.isCorpse=item.corpse
                }
                if(item.marked_tag!=null) {
                    cow.isMarkedTag=item.marked_tag
                }
                cow.num_of_calvings=item.num_of_calvings
                cow.created_at = com.israel.cowboyfriend.global.parseTimestampMillis(createdArr.getOrNull(idx - 1))
                cow.healthIssues = healthArr.getOrNull(idx - 1).orEmpty()
                cow.momId = momIdArr.getOrNull(idx - 1)
                fresh.add(cow)
            }

            //sort by calf number
            val sorted = ArrayList(sortByCalfNumber(fresh) ?: fresh)
            nameTheMothers(sorted)
            //how the last calving of every mother went (the table calvings may not exist yet: then nothing is shown)
            runCatching { loadCalvings(sorted) }.onFailure { Log.w("Calvings", "the calvings were not read", it) }

            //a change was made while the list was read: the list of the phone is the truth, the list of the server is dropped
            if (db.pendingCount() > 0) return
            db.replaceAllCows(sorted)
            cowsDetails = sorted
            _cowsDetails.postValue(sorted.toList())
            db.kvSet("last_pull", System.currentTimeMillis().toString())
            refreshLocalTourState()
            adoptServerTour()
        } catch (e: CancellationException) {
            throw e
        } catch (ex: Exception) {
            Log.w("Offline", "the list of the cows was not read", ex)
        }
    }

    /**
     * reads the calvings of the ranch and gives every mother in [cows] the day and the difficulty of her last calving and the
     * number of her hard calvings (see [CalvingDifficulty.isHard]); a cow without a calving is left as it is
     */
    private suspend fun loadCalvings(cows: List<CowDetails>) {
        val rows = supabase.from("calvings").select(Columns.list("mother_id", "calved_at", "difficulty"))
            .decodeList<CalvingRowDto>()
        val byMother = rows.groupBy { it.mother_id }
        for (cow in cows) {
            val own = byMother[cow.id] ?: continue
            val last = own.maxByOrNull { parseTimestampMillis(it.calved_at) ?: 0L } ?: continue
            cow.lastCalvingAt = parseTimestampMillis(last.calved_at)
            cow.lastCalvingDifficulty = last.difficulty
            cow.hardCalvings = own.count { CalvingDifficulty.isHard(it.difficulty) }
        }
    }

    /**
     * the tour of the phone counts what changed in the cows (for its temporary summary): the cow that was changed, a comment that
     * was added, a cow that died. [before] is the cow as it was saved before this change
     */
    private fun recordTourEdit(cow: CowDetails, before: CowDetails?) {
        val tour = LocalTour.get(db)?.takeIf { it.isOpen } ?: return
        cow.id?.let { tour.touched.add(it) }
        if (!cow.comment.isNullOrBlank() && cow.comment != before?.comment) tour.commentsAdded++
        if (cow.isCorpse && before?.isCorpse != true) tour.newCorpses++
        LocalTour.save(db, tour)
    }

    /**
     * gives every calf the name of its mother in the format of the mother list of the new cow screen ("12-24", number and the
     * last two digits of the birth year). the mother is found by her id (column mom_id). a calf saved before that column existed
     * has only the mother number: its mother is the cow with that number that was born before the calf, when there is exactly
     * one such cow, otherwise only the number is shown
     */
    private fun nameTheMothers(cows: List<CowDetails>) {
        val byId = cows.filter { it.id != null }.associateBy { it.id }
        for (calf in cows) {
            if (CowLabels.typeIndex(calf.cowType) != CowLabels.TYPE_CALF) {
                calf.momLabel = null
                continue
            }
            var mom = calf.momId?.let { byId[it] }
            if (mom == null && calf.number_mom != null) {
                val calfBorn = calf.created_at
                val candidates = cows.filter {
                    it.number == calf.number_mom &&
                        (CowLabels.typeIndex(it.cowType) == CowLabels.TYPE_COW || CowLabels.typeIndex(it.cowType) == CowLabels.TYPE_FIRST_COW) &&
                        (calfBorn == null || it.created_at == null || it.created_at!! <= calfBorn)
                }
                mom = candidates.singleOrNull()
            }
            calf.momLabel = if (mom != null) CowLabels.momLabel(mom.number, mom.created_at) else calf.number_mom?.toString()
        }
    }

    /**
     * sort by number of calf
     */
    private fun sortByCalfNumber(cows: java.util.ArrayList<CowDetails>?): List<CowDetails>? {
        return cows?.sortedBy { it.number }

    }


    /**
     * a cow was edited: the change is saved on the phone at once (the list shows it) and waits in the outbox to be sent to the
     * server, which happens now when there is a network, or when it comes back. [type] says which fields of [cow] were changed
     * (the constants of Consts.kt). [cowRepositoryCallback] gets 1 when the change was saved on the phone (0 when the cow has no id)
     */
    fun dbUpdateCowDetails(
        cow: CowDetails, type: Int,
        cowRepositoryCallback: CowRepositoryCB? = null
    ) {
        val id = cow.id
        if (id == null) {
            cowRepositoryCallback?.onRequestResult(0)
            return
        }
        //the fields of the cow that go to the server, as the old code sent them
        val fields = JSONObject()
        when (type) {
            CORPSE_TYPE -> fields.put("corpse", cow.isCorpse)
            LAST_SEEN_AT_TYPE -> {
                fields.put("last_seen_at", cow.last_seen_at ?: JSONObject.NULL)
                fields.put("with_mom", cow.with_mom ?: JSONObject.NULL)
            }
            //the edit window of the cow card: all its editable fields are saved in one change
            ALL_FIELDS_TYPE -> {
                fields.put("corpse", cow.isCorpse)
                fields.put("last_seen_at", cow.last_seen_at ?: JSONObject.NULL)
                fields.put("with_mom", cow.with_mom ?: JSONObject.NULL)
                fields.put("marked_tag", cow.isMarkedTag ?: JSONObject.NULL)
                fields.put("cow_type", cow.cowType ?: JSONObject.NULL)
                fields.put("comment", cow.comment ?: JSONObject.NULL)
                //the health problems and the number of calvings are sent only when they were changed in the window
                if (cow.healthIssuesChanged) fields.put("health_issues", org.json.JSONArray(cow.healthIssues))
                if (cow.calvingsChanged) fields.put("num_of_calvings", cow.num_of_calvings ?: JSONObject.NULL)
                //the location is sent only when it was updated in the window
                if (cow.latitude != null && cow.longitude != null) {
                    fields.put("latitude", cow.latitude)
                    fields.put("longitude", cow.longitude)
                    fields.put("location_updated_at", cow.location_updated_at ?: JSONObject.NULL)
                }
            }
            COMMENT_TYPE -> fields.put("comment", cow.comment ?: JSONObject.NULL)
            MARKED_TAG_TYPE -> fields.put("marked_tag", cow.isMarkedTag ?: JSONObject.NULL)
            TYPE_COW -> fields.put("cow_type", cow.cowType ?: JSONObject.NULL)
        }

        //the tour of the phone counts what changed (the cow as it was saved before is compared with the new one)
        recordTourEdit(cow, db.getCow(id))

        cow.healthIssuesChanged = false
        cow.calvingsChanged = false
        cow.pendingSync = true
        db.saveCow(cow)
        db.enqueue(SyncEngine.OP_COW_UPDATE, JSONObject().put("cowId", id).put("fields", fields))
        publishCows()
        cowRepositoryCallback?.onRequestResult(1)
        OfflineHub.requestSync()
    }

    /**
     * a new cow (a calf): it is added to the list on the phone at once, with a number that only the phone knows, and waits in the
     * outbox. when it is sent the picture goes to the storage, the row is made on the server, the mother gets one more calving
     * and the calving is recorded (see [SyncEngine]). a picture on the phone ([CowDetails.image_url] is a path of a file) is
     * shown until then. [cowRepositoryCallback] gets 1 when the cow was saved on the phone
     */
    fun dbInsertCowDetails(
        cow: CowDetails,
        cowRepositoryCallback: CowRepositoryCB
    ) {
        val localImage = cow.image_url?.takeIf { it.isNotBlank() && !it.startsWith("http") }
        val now = System.currentTimeMillis()
        cow.id = db.nextLocalId()
        cow.pendingSync = true
        cow.createdLocallyAt = now

        //the mother (if she was chosen) has one more calving, the list shows it before it is sent
        val list = cowsDetails ?: ArrayList<CowDetails>().also { cowsDetails = it }
        val mom = cow.momId?.let { momId -> list.firstOrNull { it.id == momId } }
        if (mom != null) {
            mom.num_of_calvings = (mom.num_of_calvings ?: 0) + 1
            mom.lastCalvingAt = cow.created_at ?: now
            mom.lastCalvingDifficulty = cow.calvingDifficulty
            if (CalvingDifficulty.isHard(cow.calvingDifficulty)) mom.hardCalvings++
            db.saveCow(mom)
        }

        //a calf added in the tour of the phone is counted in its summary
        LocalTour.get(db)?.takeIf { it.isOpen }?.let { tour ->
            tour.newCalves++
            tour.touched.add(cow.id ?: 0)
            LocalTour.save(db, tour)
        }

        list.add(cow)
        cowsDetails = ArrayList(list.sortedBy { it.number })
        nameTheMothers(cowsDetails!!)
        db.saveCow(cow)
        db.enqueue(
            SyncEngine.OP_COW_INSERT, JSONObject()
                .put("localId", cow.id)
                .put("cow", CowJson.toJson(cow))
                .put("imagePath", localImage ?: "")
                //the name of the picture in the storage is made now, so a second try of the sending writes the same file
                .put("uploadPath", UUID.randomUUID().toString() + "/image.jpg")
                .put("difficulty", cow.calvingDifficulty)
                .put("stage", 0)
        )
        publishCows()
        cowRepositoryCallback.onRequestResult(1)
        OfflineHub.requestSync()
    }

    /**
     * the picture of a new cow: it is copied to a file of the app and [cowStorageRespose] gets the path of the copy at once. the
     * picture goes to the storage later, with the cow, when there is a network (the name stays from the time the picture went to
     * the storage directly)
     */
    fun uploadCowImage(src: Uri?, context: Context, cowStorageRespose: CowStorageRespose) {
        val path = copyImageToLocal(src, context)
        if (path != null) cowStorageRespose.onRequestResult(path) else cowStorageRespose.onError()
    }

    /**
     * copies the picture that the camera took to a file of the app, so it is kept until it is sent (the camera file can be removed
     * by the phone). gives back the path of the copy, or null when it could not be copied
     */
    fun copyImageToLocal(src: Uri?, context: Context): String? = runCatching {
        val folder = File(context.filesDir, "offline_images").apply { mkdirs() }
        val file = File(folder, UUID.randomUUID().toString() + ".jpg")
        context.contentResolver.openInputStream(src!!)!!.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.absolutePath
    }.onFailure { Log.e("Offline", "the picture was not copied", it) }.getOrNull()

    /**
     * records a calving whose calf was born dead (the calf is not registered in the app): the mother has one more calving on the
     * phone at once, and the calving (without a calf, with the difficulty) waits in the outbox. [callback] gets the new number of
     * calvings of the cow, or null when the cow is not known
     */
    fun dbRecordStillbirth(cowId: Int, difficulty: Int, callback: (Int?) -> Unit) {
        val cow = cowsDetails?.firstOrNull { it.id == cowId } ?: db.getCow(cowId)
        if (cow == null) {
            callback(null)
            return
        }
        val now = System.currentTimeMillis()
        val newCount = (cow.num_of_calvings ?: 0) + 1
        cow.num_of_calvings = newCount
        cow.lastCalvingAt = now
        cow.lastCalvingDifficulty = difficulty
        if (CalvingDifficulty.isHard(difficulty)) cow.hardCalvings++
        cow.pendingSync = true
        db.saveCow(cow)
        db.enqueue(
            SyncEngine.OP_STILLBIRTH,
            JSONObject().put("cowId", cowId).put("difficulty", difficulty).put("calvedAt", now).put("stage", 0)
        )
        publishCows()
        callback(newCount)
        OfflineHub.requestSync()
    }

    /**
     * replaces the picture of the cow [cowId] with [file] (a jpg): the new picture is copied to the app and shown at once, and
     * the replace waits in the outbox: when it is sent the new picture goes to the storage, the cow points to it and the old
     * picture is deleted from the storage. [callback] gets the result on the phone (the answer of the server comes later)
     */
    fun dbReplaceCowImage(cowId: Int, file: File, callback: (CowImageReplaceResult) -> Unit) {
        val cow = cowsDetails?.firstOrNull { it.id == cowId } ?: db.getCow(cowId)
        val copy = if (cow == null) null else runCatching {
            val folder = File(getApplication<Application>().filesDir, "offline_images").apply { mkdirs() }
            File(folder, UUID.randomUUID().toString() + ".jpg").also { file.copyTo(it, overwrite = true) }
        }.onFailure { Log.e("Offline", "the picture was not copied", it) }.getOrNull()
        if (cow == null || copy == null) {
            callback(CowImageReplaceResult(ok = false, oldDeleted = false))
            return
        }
        cow.image_url = copy.absolutePath
        cow.pendingSync = true
        db.saveCow(cow)
        db.enqueue(
            SyncEngine.OP_IMAGE_REPLACE,
            JSONObject().put("cowId", cowId).put("imagePath", copy.absolutePath)
                .put("uploadPath", UUID.randomUUID().toString() + "/image.jpg").put("stage", 0)
        )
        publishCows()
        callback(CowImageReplaceResult(ok = true, oldDeleted = true))
        OfflineHub.requestSync()
    }

    /**
     * set listener realtime to table CowDetails
     */
    fun setListenerRealtimeCowDetails() {
        if (realtimeJob?.isActive == true) return
        realtimeJob = viewModelScope.launch {
            try {
                val channel=supabase.channel(channelId="table-changes")
                val dataFlow=channel.postgresChangeFlow<PostgresAction>(schema="public"){
                    table="CowDetails"
                }
                dataFlow.onEach {
                    when(it){
                        is PostgresAction.Insert->{
                            //val cow = it.decodeRecord<CowDto>()
                            //insertToCowsListUI(cow)
                            //dbGetCowsDetails()
                            Log.d("chatInfo", "insert list")
                        }
                        is PostgresAction.Delete->{
                            //dbGetCowsDetails()
                            Log.d("chatInfo", "delete list")
                        }
                        is PostgresAction.Update->{
//                            val id =it.record["id"].toString()
//
//                            val cow = it.decodeRecord<CowDto>()
//
//                            if(id.isDigitsOnly()) {
//                                updateCowsList(cow, id.toInt())
//                            }

                            Log.d("chatInfo", "updated list")
                        }
                        is PostgresAction.Select->{
                            Log.d("chatInfo", "select list")
                        }

                        else -> {}
                    }
                }.launchIn(this)
                channel.subscribe()
            } catch (ex: Exception) {
                 ex.printStackTrace()
            }
        }
    }


//    /**
//     * insert to list of cows UI
//     */
//    fun insertToCowsListUI(cow: CowDto) {
//        cowsDetails?.add(
//            CowDetails(
//                number=cow.number, number_mom=cow.number_mom
//                ,gender=cow.gender, image_url=cow.image_url, user_id=cow.user_id, comment = cow.comment,latitude = cow.latitude, longitude = cow.longitude
//            )
//        )
//        _cowsDetails.value=cowsDetails?.toList()
//    }

    /**
     * update list of cows for UI
     */
    fun updateCowsListForUi(cow: CowDto, id: Int) {
        val iterator=cowsDetails?.iterator()

        while (iterator?.hasNext() == true) {
            val item=iterator.next()
            if(item.id==id) {
                item.isCorpse=cow.corpse!!
                item.last_seen_at=cow.last_seen_at
                item.location_updated_at=cow.location_updated_at
                //the picture may have been replaced (the camera of the cow card)
                item.image_url=cow.image_url
                if(cow.marked_tag!=null) {
                    item.isMarkedTag=cow.marked_tag
                }
            }
        }
        //_cowsDetails= MutableLiveData<List<CowDetails>>()
        _cowsDetails.postValue(cowsDetails?.toList())
    }

}