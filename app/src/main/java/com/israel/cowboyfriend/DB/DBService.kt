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

    fun getSupabase(): SupabaseClient {
        return supabase
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
                val cows=supabase.from("CowDetails").select().decodeList<CowDto>()
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
                    user_id =cow.user_id
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

}