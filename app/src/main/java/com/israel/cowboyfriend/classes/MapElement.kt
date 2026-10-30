package com.israel.cowboyfriend.classes

import kotlinx.serialization.Serializable

//the types of the elements that can be added to the map by a long press (each type has its own icon and name)
const val MAP_ELEMENT_GATE = "gate"

/**
 * an element the user added to the map (a gate, ...). the list of the elements of a user is saved as a json array in the
 * column map_element of the table UsersInfo, for example: [{"id":"..","type":"gate","lat":32.94,"lng":35.79}]
 */
@Serializable
data class MapElement(
    val id: String,
    val type: String,
    val lat: Double,
    val lng: Double
)
