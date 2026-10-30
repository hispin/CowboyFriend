package com.israel.cowboyfriend.classes

data class CowDetails (
    var number: Int?,
    var number_mom: Int?,
    var gender: String?,
    var image_url: String?,
    var user_id: String?,
    var comment: String?,
    var latitude: Double?,
    var longitude: Double?,
    var location_updated_at: Long?,
    var last_seen_at: Long?,
    var cowType: String?,
    var with_mom: Long?,
    var isMarkedTag: Boolean?
){
    var isCorpse: Boolean=false
    var num_of_calvings: Int?=null
    //birth date of the cow: the created_at column of the table (milliseconds), null when unknown
    var created_at: Long?=null
    var id: Int?=null
    //the health problems of the cow (column health_issues): codes, see HealthIssues; empty = no problem
    var healthIssues: List<String> = emptyList()
    //true when the edit window changed the health problems: only then are they sent to the database (see dbUpdateCowDetails)
    var healthIssuesChanged: Boolean = false
}

