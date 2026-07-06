package com.israel.cowboyfriend.DB

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CowDto (
    //@SerialName("id") val id: Int?,
    @SerialName("number") val number: Int?,
    @SerialName("number_mom") val number_mom: Int?,
    @SerialName("gender") val gender: String?,
    @SerialName("image_url") val image_url: String?,
    @SerialName("user_id") val user_id: String?,
    @SerialName("comment") val comment: String?,
    @SerialName("latitude") val latitude: Double?,
    @SerialName("longitude") val longitude: Double?,
    @SerialName("location_updated_at") val location_updated_at: Long?,
    @SerialName("corpse") val corpse: Boolean?,
    @SerialName("last_seen_at") val last_seen_at: Long?
    )
