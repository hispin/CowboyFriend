package com.israel.cowboyfriend.DB

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CowDto (
    @SerialName("number")
    val number: Int,
    @SerialName("number_mom")
    val number_mom: Int,
    @SerialName("gender")
    val gender: String?,
    )