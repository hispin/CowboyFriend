package com.israel.cowboyfriend.DB

import com.israel.cowboyfriend.classes.MapElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
class UsersInfoDto (
    @SerialName("marking") val marking: String? ,
    @SerialName("name") val name: String? )

//only the column map_element of UsersInfo: the json array of the elements the user added to the map (null = nothing yet)
@Serializable
class UsersInfoMapElementsDto(
    @SerialName("map_element") val mapElement: List<MapElement>? = null)
