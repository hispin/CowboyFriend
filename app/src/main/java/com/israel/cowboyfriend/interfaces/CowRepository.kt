package com.israel.cowboyfriend.interfaces

import com.israel.cowboyfriend.classes.Cow

interface CowRepository {
    suspend fun createCow(cow: Cow): Boolean
}