package com.israel.cowboyfriend.interfaces

import com.israel.cowboyfriend.classes.CowDetails


interface OnFragmentListener {
    fun updateLanguage()

    fun onSaveForShareVideo(cowDetails: CowDetails)

    fun onSaveForShareVideo(imgPath: String)

    fun onBack()

}