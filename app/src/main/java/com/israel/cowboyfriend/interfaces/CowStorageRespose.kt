package com.israel.cowboyfriend.interfaces

interface CowStorageRespose {
    fun onRequestResult(url:String)

    /** called when uploading the picture failed, so the screen can stop its loading indicator and show a message */
    fun onError() {}
}
