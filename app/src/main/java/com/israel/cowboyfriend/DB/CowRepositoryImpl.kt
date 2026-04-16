package com.israel.cowboyfriend.DB

import com.israel.cowboyfriend.classes.Cow
import com.israel.cowboyfriend.interfaces.CowRepository
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CowRepositoryImpl@Inject constructor(
    private val postgrest: Postgrest,
    private val storage: SessionSource.Storage,
) : CowRepository {
    override suspend fun createCow(cow: Cow): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val cowDto =CowDto(
                    number =  cow.number,
                    number_mom = cow.number_mom,
                    gender = cow.gender
                )
                postgrest.from("CowDetails").insert(cowDto)
                true
            }
            true
        } catch (e: java.lang.Exception) {
            throw e
        }
    }

}