package com.israel.cowboyfriend.interfaces

import com.israel.cowboyfriend.DB.CowDto

interface CowRepositoryCBselect {
    fun onRequestResult(cows: ArrayList<CowDto>?)
}