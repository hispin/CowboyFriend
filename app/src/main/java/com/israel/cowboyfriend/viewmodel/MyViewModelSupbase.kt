package com.israel.cowboyfriend.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.israel.cowboyfriend.DB.CowDto
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
import kotlinx.coroutines.CoroutineScope
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

    private lateinit var supabase: SupabaseClient
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


    /**
     * set supabase
     */
    fun setSupabase() {
        if (::supabase.isInitialized) return
        supabase=createSupabaseClient(
            supabaseUrl="https://ymgsasxfgfyagppltvdy.supabase.co",
            supabaseKey="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InltZ3Nhc3hmZ2Z5YWdwcGx0dmR5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQzNjg1MTIsImV4cCI6MjA4OTk0NDUxMn0.FcH0j_Ec83MzcE73rV_EfH5BA5xxpNQyGBY-4Wxm1yk"
        ) {

            install(Postgrest) // For database interactions
            install(Auth)
            install(Storage)
            install(Realtime) {
                heartbeatInterval = 30.seconds
                reconnectDelay = 5.seconds
            }
//        install(SessionSource.Storage)   // For file storage
        }
    }

    fun getSupabase(): SupabaseClient {
        return supabase
    }

    /**
     * get user name
     */
    fun getUserName():String{
        return supabase.auth.currentUserOrNull()?.email.toString()
    }


    /**
     * read the farm name (column "name") of the user from the table UsersInfo, by the email column.
     * the name is published in [_farmName] (the screens follow it) and also given to the callback;
     * the callback gets null when there is no such row or the request failed
     */
    fun dbGetFarmName(email: String, callback: (String?) -> Unit) {
        runBlocking {
            runCatching {
                supabase.from("UsersInfo").select {
                    filter {
                        eq("email", email)
                    }
                }.decodeList<UsersInfoDto>()
            }.onSuccess { rows ->
                //a user without a row in the table has no farm name: nothing is published
                val name = rows.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                if (name != null) _farmName.postValue(name)
                callback(name)
            }.onFailure {
                callback(null)
            }
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
        val email = supabase.auth.currentUserOrNull()?.email ?: return
        viewModelScope.launch {
            runCatching { readMapElements(email) }
                .onSuccess { _mapElements.postValue(it) }
                .onFailure { Log.e("MapElements", "read failed", it) }
        }
    }

    /**
     * changes the list of the map elements: the fresh list is read from the database, [change] makes the new list from
     * it, and the new list is written back to the column map_element. so an element added on another phone is not lost.
     * the update asks for the changed row back, so a row that was not really updated (no row for the email, or a missing
     * UPDATE permission) is reported as a failure. [callback] gets true on success; the new list is published in [_mapElements]
     */
    fun dbChangeMapElements(change: (List<MapElement>) -> List<MapElement>, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = runCatching {
                val email = supabase.auth.currentUserOrNull()?.email ?: error("no session")
                val updated = change(readMapElements(email))
                val rows = supabase.from("UsersInfo").update({
                    set("map_element", updated)
                }) {
                    filter {
                        eq("email", email)
                    }
                    select(Columns.list("map_element"))
                }.decodeList<UsersInfoMapElementsDto>()
                if (rows.isEmpty()) error("no row was updated")
                _mapElements.postValue(rows.first().mapElement.orEmpty())
            }.onFailure {
                error -> Log.e("MapElements", "save failed", error)
            }.isSuccess
                callback(saved)
        }
    }

    //the heat map of the map screen: the mode asked by the ranch dashboard (HEAT_OFF / HEAT_ALL / HEAT_ATTENTION), read by the
    //map screen when it is shown
    var pendingHeatMode = HEAT_OFF

    companion object {
        const val HEAT_OFF = 0
        const val HEAT_ALL = 1
        const val HEAT_ATTENTION = 2
    }

    //the tour of the logged in user: no open tour, or the open one with its progress; the cattle tour screen follows it
    val _tourState = MutableLiveData<TourState>()

    /**
     * loads the state of the tour: the open tour of the user (if any) and how many of the living cows were seen since it
     * started. a failure is only logged (the tour bar then shows "no tour"; the screen stays usable)
     */
    fun dbLoadTourState() {
        val email = supabase.auth.currentUserOrNull()?.email?.lowercase() ?: return
        viewModelScope.launch {
            runCatching {
                val open = supabase.from("tours").select(Columns.list("id", "started_at")) {
                    filter {
                        eq("status", "open")
                        eq("started_by", email)
                    }
                }.decodeList<TourRowDto>().firstOrNull()
                if (open == null) {
                    TourState()
                } else {
                    //the progress of the tour comes from the database function that makes the summary
                    val summary = supabase.postgrest.rpc("tour_summary", buildJsonObject { put("p_tour", open.id) })
                        .data.let { TourSummary.fromJson(it) }
                    TourState(open.id, parseTimestampMillis(open.started_at), summary?.seen ?: 0, summary?.alive ?: 0)
                }
            }.onSuccess { _tourState.postValue(it) }
                .onFailure { Log.e("Tour", "load state failed", it) }
        }
    }

    /**
     * starts a tour (or continues the open one) with the database function start_tour. [callback] gets true on success;
     * the new state is loaded
     */
    fun dbStartTour(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val started = runCatching {
                supabase.postgrest.rpc("start_tour", buildJsonObject { })
            }.onFailure { Log.e("Tour", "start failed", it) }.isSuccess
            if (started) dbLoadTourState()
            callback(started)
        }
    }

    /**
     * ends the tour with the database function end_tour: it closes the tour and returns the summary. [callback] gets the
     * summary, or null when the tour could not be ended
     */
    fun dbEndTour(tourId: String, callback: (TourSummary?) -> Unit) {
        viewModelScope.launch {
            val summary = runCatching {
                val result = supabase.postgrest.rpc("end_tour", buildJsonObject { put("p_tour", tourId) })
                TourSummary.fromJson(result.data) ?: error("the summary is not a json object")
            }.onFailure { Log.e("Tour", "end failed", it) }.getOrNull()
            //the tour may be one of another cowboy (ended by a coordinator): the state of MY tour is loaded again
            if (summary != null) dbLoadTourState()
            callback(summary)
        }
    }

    /**
     * the summary of a tour (also of an open one, "so far"), for the history list. [callback] gets null on a failure
     */
    fun dbTourSummary(tourId: String, callback: (TourSummary?) -> Unit) {
        viewModelScope.launch {
            val summary = runCatching {
                TourSummary.fromJson(supabase.postgrest.rpc("tour_summary", buildJsonObject { put("p_tour", tourId) }).data)
            }.onFailure { Log.e("Tour", "summary failed", it) }.getOrNull()
            callback(summary)
        }
    }

    /**
     * loads the tour history of the ranch (the last [limit] tours, newest first) and the role of the user. a coordinator
     * sees the tours of all the cowboys; a cowboy sees the tours of his ranch as the database allows. [callback] gets null
     * on a failure
     */
    fun dbLoadTourHistory(limit: Int = 60, callback: (TourHistory?) -> Unit) {
        val email = supabase.auth.currentUserOrNull()?.email?.lowercase()
        if (email == null) {
            callback(null)
            return
        }
        viewModelScope.launch {
            val history = runCatching {
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
            }.onFailure { Log.e("Tour", "history failed", it) }.getOrNull()
            callback(history)
        }
    }

    /**
     * waits until the saved session was loaded from the device storage, then tells whether the user is logged in
     * (checking before that would wrongly say "not logged in" right after the app starts)
     */
    fun checkSession(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { supabase.auth.awaitInitialization() }
            callback(isSessionGetSupabase())
        }
    }
    /**
     * check if session is get
     */
    fun isSessionGetSupabase(): Boolean {
        return supabase.auth.currentSessionOrNull()!=null
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
     * get cow details by id
     */
    fun dbGetCowDetailsById(id:Int) {

            runBlocking {
                var result: PostgrestResult?=null
                runCatching {
                    //cowsDetails=ArrayList()
                       result=supabase.from("CowDetails").select( ) {
                           filter {
                               eq("id", id)
                           }
                       }

                    }.onSuccess {
                    val item=result?.decodeList<CowDto>()[0]
                    if(item!=null){
                        updateCowsListForUi(item,id)
                    }
                    // use session / user here
                }.onFailure { error ->
                    // show error.message to the user
                }
            }
        }


                /**
     * get details of all cows
     */
    fun dbGetCowsDetails() {

        runBlocking {
            if(supabase.auth.currentUserOrNull()?.email==null) return@runBlocking

            try {
                cowsDetails=ArrayList()
                val result=supabase.from("CowDetails").select(){
                    filter {
                        eq("user_id", supabase.auth.currentUserOrNull()?.email!!)
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
                for (i in 0 until jsonArray.length()){
                    val jsonObject = jsonArray.getJSONObject(i)
                    val id = jsonObject.getString("id")
                    strArr.add(id)
                    createdArr.add(if (jsonObject.isNull("created_at")) null else jsonObject.getString("created_at"))
                    val health = jsonObject.optJSONArray("health_issues")
                    healthArr.add(if (health == null) emptyList() else (0 until health.length()).map { health.getString(it) })
                }

                val cowsDto=result.decodeList<CowDto>()

                val iterator=cowsDto.iterator()

                var idx = 0
                while (iterator.hasNext()) {
                    val item=iterator.next()
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
                    Log.d("testId",cow.id.toString()+" "+item.number)
                    if(item.corpse!=null) {
                        cow.isCorpse=item.corpse
                    }
                    if(item.marked_tag!=null) {
                        cow.isMarkedTag=item.marked_tag
                    }
                    cow.num_of_calvings=item.num_of_calvings
                    cow.created_at = com.israel.cowboyfriend.global.parseTimestampMillis(createdArr.getOrNull(idx - 1))
                    cow.healthIssues = healthArr.getOrNull(idx - 1).orEmpty()

                    cowsDetails?.add(cow)
                }

                //sort by calf number
                cowsDetails = sortByCalfNumber(cowsDetails)?.let { ArrayList(it) }

                _cowsDetails.postValue(cowsDetails?.toList())
            } catch (ex: Exception) {
                print(ex)
                //cowRepositoryCBselect.onRequestResult(ArrayList(emptyList()))
            }
        }
    }

    /**
     * sort by number of calf
     */
    private fun sortByCalfNumber(cows: java.util.ArrayList<CowDetails>?): List<CowDetails>? {
        return cows?.sortedBy { it.number }

    }


    /**
     * update a new cow
     */
    fun dbUpdateCowDetails(
        cow: CowDetails, type: Int,
        //optional: told when the update finished (1) or failed (0), so the screen can show the result
        cowRepositoryCallback: CowRepositoryCB? = null
    ) {
        runBlocking {
            runCatching {
                if (cow.id != null) {
                    when (type) {
                        CORPSE_TYPE -> {
                            val result=supabase.postgrest.from("CowDetails")
                                .update({ set("corpse", cow.isCorpse) }) {
                                    filter {
                                        // Target rows where column 'id' equals 554
                                        eq("id", cow.id!!)
                                        //eq("id", 7)
                                    }
                                    //dbGetCowsDetailsById(cow.id!!)
                                }
                        }

                        LAST_SEEN_AT_TYPE -> {
                            val result=supabase.postgrest.from("CowDetails")
                                .update({ set("last_seen_at", cow.last_seen_at);set("with_mom", cow.with_mom)}) {
                                    filter {
                                        // Target rows where column 'id' equals 554
                                        eq("id", cow.id!!)
                                        //eq("id", 7)
                                    }
                                    //dbGetCowsDetailsById(cow.id!!)
                                }

                        }
                        //the edit window of the cow card: all its editable fields are saved in one request
                        ALL_FIELDS_TYPE -> {
                            val result=supabase.postgrest.from("CowDetails")
                                .update({
                                    set("corpse", cow.isCorpse)
                                    set("last_seen_at", cow.last_seen_at)
                                    set("with_mom", cow.with_mom)
                                    set("marked_tag", cow.isMarkedTag)
                                    set("cow_type", cow.cowType)
                                    set("comment", cow.comment)
                                    //the health problems are sent only when they were changed in the window (so saving the
                                    //other fields works even before the column health_issues was added to the table)
                                    if (cow.healthIssuesChanged) {
                                        set("health_issues", cow.healthIssues)
                                    }
                                    //the location is sent only when it was updated in the window
                                    if (cow.latitude != null && cow.longitude != null) {
                                        set("latitude", cow.latitude)
                                        set("longitude", cow.longitude)
                                        set("location_updated_at", cow.location_updated_at)
                                    }
                                }) {
                                    filter {
                                        eq("id", cow.id!!)
                                    }
                                }
                        }
                        //the comment edited in the comment window of the cow card: save the comment column
                        COMMENT_TYPE -> {
                            val result=supabase.postgrest.from("CowDetails")
                                .update({ set("comment", cow.comment) }) {
                                    filter {
                                        eq("id", cow.id!!)
                                    }
                                }
                        }
                        //the ear tag checkbox of the cow card: save the marked_tag column
                        MARKED_TAG_TYPE -> {
                            val result=supabase.postgrest.from("CowDetails")
                                .update({ set("marked_tag", cow.isMarkedTag) }) {
                                    filter {
                                        eq("id", cow.id!!)
                                    }
                                }
                        }
                        TYPE_COW -> {
                            val result=supabase.postgrest.from("CowDetails")
                                .update({ set("cow_type", cow.cowType) }) {
                                    filter {
                                        // Target rows where column 'id' equals 554
                                        eq("id", cow.id!!)
                                        //eq("id", 7)
                                    }
                                    //dbGetCowsDetailsById(cow.id!!)
                                }

                        }
                    }

                }
                //dbGetCowsDetails()
            }.onSuccess {
                //the health problems were saved (or were not changed)
                cow.healthIssuesChanged = false
                dbGetCowDetailsById(cow.id!!)
                cowRepositoryCallback?.onRequestResult(1)
                // use session / user here
            }.onFailure { error ->
                // show error.message to the user
                cowRepositoryCallback?.onRequestResult(0)
            }
        }
    }
//    /**
//     * update a new cow
//     */
//    fun dbUpdateCowDetails1(
//        cow: CowDetails, type: Int
//    ) {
//        runBlocking {
//            try {
//
//                if(cow.id!=null){
//                    when(type){
//                        CORPSE_TYPE->{
//                            val result=supabase.postgrest.from("CowDetails").update({ set("corpse", cow.isCorpse)} ) {
//                                filter {
//                                    // Target rows where column 'id' equals 554
//                                    eq("id", cow.id!!)
//                                    //eq("id", 7)
//                                }
//                                //dbGetCowsDetailsById(cow.id!!)
//                            }
//                        }
//
//                        LAST_SEEN_AT_TYPE->{
//                            val result=supabase.postgrest.from("CowDetails").update({ set("last_seen_at", cow.last_seen_at)} ) {
//                                filter {
//                                    // Target rows where column 'id' equals 554
//                                    eq("id", cow.id!!)
//                                    //eq("id", 7)
//                                }
//                                //dbGetCowsDetailsById(cow.id!!)
//                            }
//
//                        }
//                    }
//
//                }
//                //dbGetCowsDetails()
//            }catch (ex: Exception) {
//                print(ex)
//                //cowRepositoryCallback.onRequestResult(0)
//            }
//        }
//    }

    /**
     * insert a new cow
     */
    fun dbInsertCowDetails(
        cow: CowDetails,
        cowRepositoryCallback: CowRepositoryCB
    ) {
        runBlocking {
            runCatching {
                val cowDto =CowDto(
                    number =  cow.number,
                    number_mom = cow.number_mom,
                    gender = cow.gender,
                    image_url=cow.image_url,
                    user_id =cow.user_id,
                    comment = cow.comment,
                    latitude = cow.latitude,
                    longitude = cow.longitude,
                    location_updated_at = cow.location_updated_at,
                    corpse = cow.isCorpse,
                    last_seen_at = cow.last_seen_at,
                    cow_type = cow.cowType,
                    with_mom = cow.with_mom,
                    marked_tag = cow.isMarkedTag,
                    num_of_calvings = cow.num_of_calvings,
                    //the birth date chosen on the new cow screen, as an ISO timestamp with the time zone (null = the database default, now)
                    created_at = cow.created_at?.let {
                        java.time.OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    }
                )
                val result=supabase.postgrest.from("CowDetails").insert(cowDto)
                //cowRepositoryCallback.onRequestResult(1)

            }.onSuccess {
                dbGetCowsDetails()
                cowRepositoryCallback.onRequestResult(1)
                // use session / user here
            }.onFailure { error ->
                // show error.message to the user
                cowRepositoryCallback.onRequestResult(0)
            }
//            catch (ex: Exception) {
//                print(ex)
//                cowRepositoryCallback.onRequestResult(0)
//            }
        }
    }

    /**
     * upload image to storage
     */
    fun uploadCowImage(src: Uri?, context: Context, cowStorageRespose: CowStorageRespose) {
        runBlocking {
            var trgUrl=""

            try {
                trgUrl=UUID.randomUUID().toString()+"/image"
                val result=supabase.storage.from("CowsAndCalf").createSignedUploadUrl("$trgUrl.jpg")

                lateinit var outputFile: File
                val inputStream = context.contentResolver.openInputStream(src!!)
                inputStream?.use { input ->
                    // Option 1: Read all bytes (best for small files)
//                    val bytes = input.readBytes()
//
//                    val url2=supabase.storage.from("CowsAndCalf").uploadToSignedUrl(
//                        path="$trgUrl.jpg", token=url1.token, data = bytes
//                        )

                    // Option 2: Copy to a local file
                    outputFile = File(context.cacheDir, "temp_file")
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                //val options = cnewUrlontext.UpdateOptions().upsert(true)
//                val url2=supabase.storage.from("CowsAndCalf").uploadToSignedUrl(
//                    path="$trgUrl.jpg", token=url1.token, file = outputFile
//                )
                val newUrl=supabase.storage.from("CowsAndCalf").upload(
                    path="$trgUrl.jpg", file = outputFile
                )
                cowStorageRespose.onRequestResult(newUrl.key.toString())

            }catch (ex: Exception){
                print(ex.message.toString())
                //let the caller know, otherwise its loading indicator would never stop
                cowStorageRespose.onError()
            }

        }
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
                if(cow.marked_tag!=null) {
                    item.isMarkedTag=cow.marked_tag
                }
            }
        }
        //_cowsDetails= MutableLiveData<List<CowDetails>>()
        _cowsDetails.postValue(cowsDetails?.toList())
    }

}