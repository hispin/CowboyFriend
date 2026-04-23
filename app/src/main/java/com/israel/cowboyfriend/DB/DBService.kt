package com.israel.cowboyfriend.DB

import android.R.attr.apiKey
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.interfaces.CowRepositoryCB
import com.israel.cowboyfriend.interfaces.CowRepositoryCBselect
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking

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
    fun insert(number:Int?, number_mom: Int?, gender: String, cowRepositoryCallback: CowRepositoryCB) {
        runBlocking {
            try {

                if ( number_mom==null || number==null || gender.isEmpty() ||number_mom <= 0|| number <= 0 ) return@runBlocking

                val cow =CowDetails(
                    number=number,
                    number_mom=number_mom,
                    gender=gender
                )
                val cowDto =CowDto(
                    number =  cow.number,
                    number_mom = cow.number_mom,
                    gender = cow.gender
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
}