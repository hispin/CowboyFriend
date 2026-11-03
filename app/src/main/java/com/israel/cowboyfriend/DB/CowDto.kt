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
    @SerialName("last_seen_at") val last_seen_at: Long?,
    @SerialName("cow_type") val cow_type: String?,
    @SerialName("with_mom") val with_mom: Long?,
    @SerialName("marked_tag") val marked_tag: Boolean?,
    @SerialName("num_of_calvings") val num_of_calvings: Int? = null,
    //birth date (timestamptz text): sent on insert from the date chosen on the new cow screen, read only from the raw json when listing
    @SerialName("created_at") val created_at: String? = null)

//the columns needed to find the mother of a new calf and raise her number of calvings
@Serializable
class CowCalvingsDto(
    @SerialName("id") val id: Int,
    @SerialName("cow_type") val cow_type: String? = null,
    @SerialName("corpse") val corpse: Boolean? = null,
    @SerialName("num_of_calvings") val num_of_calvings: Int? = null
)

//a row of the table calvings that is read: the mother, the day of the birth and how the calving went
@Serializable
class CalvingRowDto(
    @SerialName("mother_id") val mother_id: Int,
    @SerialName("calved_at") val calved_at: String? = null,
    @SerialName("difficulty") val difficulty: Int = 0
)

//a calving that is written to the table calvings (the ranch and the writer are filled by the database)
@Serializable
class CalvingInsertDto(
    @SerialName("mother_id") val mother_id: Int,
    @SerialName("calf_id") val calf_id: Int?,
    @SerialName("calved_at") val calved_at: String,
    @SerialName("difficulty") val difficulty: Int,
    //"dead" for a calf that was born dead (it is not registered); the usual "alive" is the default of the table and is not sent
    @SerialName("calf_outcome") val calf_outcome: String = "alive"
)

//only the picture of a cow (the one column read when the picture is replaced)
@Serializable
class CowImageUrlDto(@SerialName("image_url") val image_url: String? = null)