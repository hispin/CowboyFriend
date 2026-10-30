package com.israel.cowboyfriend.DB

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * a row of the table tours (only the columns the app reads): the open tour of the logged in user.
 * started_at is a timestamptz text, for example "2026-10-30T07:12:45.123456+00:00"
 */
@Serializable
class TourRowDto(
    @SerialName("id") val id: String,
    @SerialName("started_at") val started_at: String? = null
)

/**
 * a row of the table tours for the tour history: who, when, the status (open / closed / cancelled) and the summary that
 * was saved when the tour was closed (a json object, null for a tour that is still open)
 */
@Serializable
class TourFullDto(
    @SerialName("id") val id: String,
    @SerialName("started_by") val started_by: String? = null,
    @SerialName("started_at") val started_at: String? = null,
    @SerialName("ended_at") val ended_at: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("summary") val summary: kotlinx.serialization.json.JsonElement? = null
)

//the role of the logged in user in his ranch (table ranch_members): cowboy, coordinator or owner
@Serializable
class RanchRoleDto(@SerialName("role") val role: String? = null)
