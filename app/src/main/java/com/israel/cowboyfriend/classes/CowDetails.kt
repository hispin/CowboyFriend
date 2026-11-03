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
    //true when the edit window changed the number of calvings: only then is it sent to the database (see dbUpdateCowDetails)
    var calvingsChanged: Boolean = false
    //the mother of a calf: the id of her row (column mom_id, empty for the calves saved before it existed) and how she is named
    //("12 ’24", see CowLabels.momLabel); the name is worked out when the list is loaded and is never saved
    //how the calving of this new calf went (CalvingDifficulty), written to the table calvings when the calf is saved
    var calvingDifficulty: Int = CalvingDifficulty.NONE
    //the last calving of a mother (read from the table calvings when the list is loaded, never saved with the cow): the day,
    //how it went, and how many of her calvings were hard. null / 0 when there is none
    var lastCalvingAt: Long? = null
    var lastCalvingDifficulty: Int? = null
    var hardCalvings: Int = 0
    //offline: true while a change of this cow waits on the phone to be sent to the database (the card shows a small mark)
    var pendingSync: Boolean = false
    //offline: when this cow was added on this phone (a new calf), used by the summary of a tour that is made on the phone
    var createdLocallyAt: Long? = null
    var momId: Int? = null
    var momLabel: String? = null
}

