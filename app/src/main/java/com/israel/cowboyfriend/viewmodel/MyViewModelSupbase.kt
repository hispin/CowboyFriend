package com.israel.cowboyfriend.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.israel.cowboyfriend.DB.CowDto
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.interfaces.CowRepositoryCB
import com.israel.cowboyfriend.interfaces.CowRepositoryCBselect
import com.israel.cowboyfriend.interfaces.CowStorageRespose
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID


class MyViewModelSupbase (application: Application) : AndroidViewModel(application) {

    private lateinit var supabase: SupabaseClient
    //private var cowsDto:ArrayList<CowDto> ?=null
    var _cowsDetails = MutableLiveData<List<CowDetails>>()
    var cowsDetails:ArrayList<CowDetails>?=null


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
     * log in
     */
    fun login(cowRepositoryCallback: CowRepositoryCB) {
        viewModelScope.launch {
            try {

                var user=supabase.auth.currentSessionOrNull()

                supabase.auth.signInWith(Email) {
                    email="hag.swead@gmail.com"
                    password="ringo1234"
                }

                user=supabase.auth.currentSessionOrNull()
                if(user!=null){
                    cowRepositoryCallback.onRequestResult(1)
                }else {
                    cowRepositoryCallback.onRequestResult(0)
                }
                //configTabs()

                //val cows=supabase.from("CowDetails").select().decodeList<CowDetails>()

                //val users=supabase.postgrest["CowDetails"].select().decodeList<CowDetails>()
                //val num=10
            } catch (ex: Exception) {
                print(ex)
                cowRepositoryCallback.onRequestResult(0)
            }
        }
    }

    /**
     * get details of all cows
     */
    fun getCowsDetails(cowRepositoryCBselect: CowRepositoryCBselect) {
        runBlocking {
            try {
                val cows=supabase.from("CowDetails").select().decodeList<CowDto>()
                cowRepositoryCBselect.onRequestResult(ArrayList(cows))
            }catch (ex: Exception){
                print(ex)
                cowRepositoryCBselect.onRequestResult(ArrayList(emptyList()))
            }
        }
    }

    /**
     * get details of all cows
     */
    fun getCowsDetails() {

        runBlocking {
            try {
                cowsDetails=ArrayList()

                val cowsDto=supabase.from("CowDetails").select().decodeList<CowDto>()

                val iterator=cowsDto.iterator()

                while (iterator.hasNext()) {
                    val item=iterator.next()
                    val cow=CowDetails(
                        number=item.number,
                        number_mom=item.number_mom,
                        gender=item.gender,
                        image_url=item.image_url,
                        user_id=item.user_id,
                        comment = item.comment
                    )
                    cowsDetails?.add(cow)
                }
                _cowsDetails.value=cowsDetails?.toList()
            } catch (ex: Exception) {
                print(ex)
                //cowRepositoryCBselect.onRequestResult(ArrayList(emptyList()))
            }
        }
    }

    /**
     * insert a new cow
     */
    fun insertCowDetails(
        cow: CowDetails,
        cowRepositoryCallback: CowRepositoryCB
    ) {
        runBlocking {
            try {
                val cowDto =CowDto(
                    number =  cow.number,
                    number_mom = cow.number_mom,
                    gender = cow.gender,
                    image_url=cow.image_url,
                    user_id =cow.user_id,
                    comment = cow.comment
                )
                val result=supabase.postgrest.from("CowDetails").insert(cowDto)
                cowRepositoryCallback.onRequestResult(1)

            }catch (ex: Exception) {
                print(ex)
                cowRepositoryCallback.onRequestResult(0)
            }
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
                            val cow = it.decodeRecord<CowDto>()
                            insertToCowsListUI(cow)
                            Log.d("chatInfo", "insert list")
                        }
                        is PostgresAction.Delete->{

                            Log.d("chatInfo", "delete list")
                        }
                        is PostgresAction.Update->{
                            val cow = it.decodeRecord<CowDto>()
                            updateCowsList(cow)
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


    /**
     * insert to list of cows UI
     */
    fun insertToCowsListUI(cow: CowDto) {
        cowsDetails?.add(
            CowDetails(
                number=cow.number, number_mom=cow.number_mom
                ,gender=cow.gender, image_url=cow.image_url, user_id=cow.user_id, comment = cow.comment
            )
        )
        _cowsDetails.value=cowsDetails?.toList()
    }

    /**
     * update list of cows
     */
    fun updateCowsList(cow: CowDto) {
        val iterator=cowsDetails?.iterator()

        while (iterator?.hasNext() == true) {
            val item=iterator.next()
            if(item.number==cow.number && item.user_id.equals(cow.user_id)) {
                item.image_url=cow.image_url
                item.number_mom=cow.number_mom
                item.gender=cow.gender
                item.comment=cow.comment
            }
        }
        _cowsDetails.value=cowsDetails?.toList()
    }

}