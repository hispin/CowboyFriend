package com.israel.cowboyfriend.interfaces

import com.israel.cowboyfriend.classes.CowDetails

interface CowRepositoryCBselect {
    fun onRequestResult(cows: ArrayList<CowDetails>?)
}