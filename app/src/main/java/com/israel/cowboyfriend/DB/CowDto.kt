package com.israel.cowboyfriend.DB

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CowDto (
    @SerialName("number") val number: Int?,
    @SerialName("number_mom") val number_mom: Int?,
    @SerialName("gender") val gender: String?,
    @SerialName("image_url") val image_url: String?,
    @SerialName("user_id") val user_id: String?
    )
