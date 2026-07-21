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
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.global.CORPSE_TYPE
import com.israel.cowboyfriend.global.LAST_SEEN_AT_TYPE
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
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.File
import java.util.ArrayList
import java.util.UUID
import kotlin.collections.sortedBy
import kotlin.runCatching
import kotlin.text.toInt


class MyViewModelSupbase (application: Application) : AndroidViewModel(application) {

    private lateinit var supabase: SupabaseClient
    //private var cowsDto:ArrayList<CowDto> ?=null
    var _cowsDetails = MutableLiveData<List<CowDetails>>()
    var cowsDetails:ArrayList<CowDetails>?=null
    var pageNum = MutableLiveData(0)
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
        supabase=createSupabaseClient(
            supabaseUrl="https://ymgsasxfgfyagppltvdy.supabase.co",
            supabaseKey="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InltZ3Nhc3hmZ2Z5YWdwcGx0dmR5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQzNjg1MTIsImV4cCI6MjA4OTk0NDUxMn0.FcH0j_Ec83MzcE73rV_EfH5BA5xxpNQyGBY-4Wxm1yk"
        ) {

            install(Postgrest) // For database interactions
            install(Auth)
            install(Storage)
            install(Realtime)
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
            try {
                cowsDetails=ArrayList()
                val result=supabase.from("CowDetails").select()


                //get the identity ids for each row and the add it to main array
                val jsonArray =JSONArray(result.data)
                val strArr =ArrayList<String>()
                for (i in 0 until jsonArray.length()){
                    val jsonObject = jsonArray.getJSONObject(i)
                    val id = jsonObject.getString("id")
                    strArr.add(id)
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
                        last_seen_at = item.last_seen_at
                    )
                    val id=strArr[idx++]
                    if(id.isDigitsOnly()) {
                        cow.id=id.toInt()
                    }
                    Log.d("testId",cow.id.toString()+" "+item.number)
                    if(item.corpse!=null) {
                        cow.isCorpse=item.corpse
                    }

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
        cow: CowDetails, type: Int
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
                                .update({ set("last_seen_at", cow.last_seen_at) }) {
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
                dbGetCowDetailsById(cow.id!!)
                // use session / user here
            }.onFailure { error ->
                // show error.message to the user
                //cowRepositoryCallback.onRequestResult(0)
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
                    last_seen_at = cow.last_seen_at
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
            }

        }
    }

    /**
     * set listener realtime to table CowDetails
     */
    fun setListenerRealtimeCowDetails(){//scope: CoroutineScope) {

        viewModelScope.launch {
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
                }.launchIn(CoroutineScope(coroutineContext))
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
            }
        }
        //_cowsDetails= MutableLiveData<List<CowDetails>>()
        _cowsDetails.postValue(cowsDetails?.toList())
    }

}