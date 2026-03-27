package com.israel.cowboyfriend.classes

import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class CowDetails (val id: Int,  val number:Int, val number_mom:Int, val gender: String)
