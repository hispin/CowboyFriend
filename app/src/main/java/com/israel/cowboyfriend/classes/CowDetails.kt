package com.israel.cowboyfriend.classes

data class CowDetails (
    var number: Int?,
    var number_mom: Int?,
    var gender: String?,
    var image_url: String?,
    var user_id: String?,
    var comment: String?,
    var latitude: Double?,
    var longitude: Double?
){
    var isCorpse: Boolean=false
}

