package com.israel.cowboyfriend.DB

import android.content.Context
import android.net.Uri
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
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.FileNotFoundException
import java.io.File
import java.util.UUID

class DBService {
    private constructor ()


    private lateinit var supabase: SupabaseClient

    companion object {
        @Volatile
        private var instance: DBService?=null

        fun getInstance(): DBService {
            return instance ?: synchronized(this) {
                instance ?: DBService().also { instance=it }
            }
        }


    }

    fun setSupabase() {
        supabase=createSupabaseClient(
            supabaseUrl="https://ymgsasxfgfyagppltvdy.supabase.co",
            supabaseKey="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InltZ3Nhc3hmZ2Z5YWdwcGx0dmR5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQzNjg1MTIsImV4cCI6MjA4OTk0NDUxMn0.FcH0j_Ec83MzcE73rV_EfH5BA5xxpNQyGBY-4Wxm1yk"
        ) {

            install(Postgrest) // For database interactions
            install(Auth)
            install(Storage)
//        install(SessionSource.Storage)   // For file storage
        }
    }

    fun login(cowRepositoryCallback: CowRepositoryCB) {
        runBlocking {
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
                val cows=supabase.from("CowDetails").select().decodeList<CowDetails>()
                cowRepositoryCBselect.onRequestResult(ArrayList(cows))
            }catch (ex: Exception){
                print(ex)
                cowRepositoryCBselect.onRequestResult(ArrayList(emptyList()))
            }
        }
    }

    /**
     * insert a new cow
     */
    fun insert(
        number: Int?,
        number_mom: Int?,
        gender: String,
        image_url: String,
        cowRepositoryCallback: CowRepositoryCB
    ) {
        runBlocking {
            try {

                if ( number_mom==null || number==null || gender.isEmpty() ||number_mom <= 0|| number <= 0 ) return@runBlocking

                val cow =CowDetails(
                    number=number,
                    number_mom=number_mom,
                    gender=gender,
                    image_url=image_url
                )
                val cowDto =CowDto(
                    number =  cow.number,
                    number_mom = cow.number_mom,
                    gender = cow.gender,
                    image_url=cow.image_url
                )
                var result=supabase.postgrest.from("CowDetails").insert(cowDto)
                val n=0
                cowRepositoryCallback.onRequestResult(1)

            }catch (ex: Exception) {
                print(ex)
                cowRepositoryCallback.onRequestResult(0)
            }
        }
    }

    fun uploadCowImage(src: Uri?, context: Context, cowStorageRespose: CowStorageRespose) {
        runBlocking {
            var trgUrl=""
            //trgUrl.replace('/',':')
           // supabase.storage.from("avatars").uploadToSignedUrl(path = "avatar.jpg", token = "token-from-createSignedUploadUrl", data = bytes)
//or on JVM:
            try {
                val user1=supabase.auth.currentSessionOrNull()?.user?.email
                trgUrl=UUID.randomUUID().toString()+"/image"
                val url1=supabase.storage.from("CowsAndCalf").createSignedUploadUrl("$trgUrl.jpg")

                ////////////////////////
                lateinit var outputFile: File
                val inputStream = context.contentResolver.openInputStream(src!!)
                inputStream?.use { input ->
                    // Option 1: Read all bytes (best for small files)
//                    val bytes = input.readBytes()
//
//                    val url2=supabase.storage.from("CowsAndCalf").uploadToSignedUrl(
//                        path="$trgUrl.jpg", token=url1.token, data = bytes
//                        )
//                    var n =0

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
                //var n =0

                ////////////////////////




            }catch (ex: Exception){
                print(ex.message.toString())
            }

        }
    }

    fun getByteFromUri(uri: Uri,context: Context){
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                // Option 1: Read all bytes (best for small files)
                val bytes = input.readBytes()

                // Option 2: Copy to a local file
//                val outputFile = File(cacheDir, "temp_file")
//                outputFile.outputStream().use { output ->
//                    input.copyTo(output)
//                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace() // Handle case where URI is invalid or inaccessible
        }
    }
}