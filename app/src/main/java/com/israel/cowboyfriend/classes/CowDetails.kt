package com.israel.cowboyfriend.classes

import kotlinx.serialization.Serializable

@Serializable
data class CowDetails (
    val number: Int,
    val number_mom: Int,
    val gender: String,
    val image_url: String
)
