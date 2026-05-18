package com.israel.cowboyfriend.global

import android.content.Context


//read Long share preference
fun getLongInPreference(context: Context?, key: String, default: Long): Long? {
    if (context == null) {
        return null
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    return pref.getLong(key, default)
}


//write Long to share preference
fun setLongInPreference(context: Context?, key: String, value: Long) {
    if (context == null) {
        return
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    val edit=pref.edit()
    edit.putLong(key, value)
    edit.apply()
}


//write String to share preference
fun setStringInPreference(context: Context?, key: String, value: String?) {
    if (context == null) {
        return
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    val edit=pref.edit()
    edit.putString(key, value)
    edit.apply()
}

//remove share preference
fun removePreference(context: Context?, key: String) {
    if (context == null) {
        return
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    val edit=pref.edit()
    edit.remove(key)
    edit.apply()
}


//read String share preference
fun getStringInPreference(context: Context?, key: String, default: String?): String? {
    if (context == null) {
        return null
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    return pref.getString(key, default)
}

//read String share preference
fun removeStringInPreference(context: Context?, key: String) {
    if (context == null) {
        return
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    val edit=pref.edit()
    edit.remove(key)
    edit.apply()
}

//read Boolean share preference
fun getBooleanInPreference(context: Context?, key: String, default: Boolean): Boolean {
    if (context == null) {
        return false
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    return pref.getBoolean(key, default)
}


//write Boolean to share preference
fun setBooleanInPreference(context: Context?, key: String, value: Boolean) {
    if (context == null) {
        return
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    val edit=pref.edit()
    edit.putBoolean(key, value)
    edit.apply()
}

//read Int share preference
fun getIntInPreference(context: Context?, key: String, default: Int): Int? {
    if (context == null) {
        return null
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    return pref.getInt(key, default)
}


//write Int to share preference
fun setIntInPreference(context: Context?, key: String, value: Int) {
    if (context == null) {
        return
    }
    val pref=context.getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    val edit=pref.edit()
    edit.putInt(key, value)
    edit.apply()
}



