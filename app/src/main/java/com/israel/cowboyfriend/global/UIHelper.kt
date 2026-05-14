package com.israel.cowboyfriend.global

import android.content.Context
import android.widget.Toast

class UIHelper {

    companion object {
        fun showToast(context: Context?, error: String?) {
            try {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


}