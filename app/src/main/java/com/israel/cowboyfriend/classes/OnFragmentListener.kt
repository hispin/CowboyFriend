package com.israel.cowboyfriend.classes


interface OnFragmentListener {
    fun updateLanguage()

    fun onSaveForShareVideo(cowDetails: CowDetails)

    fun onSaveForShareVideo(imgPath: String)

    fun onBack()

}