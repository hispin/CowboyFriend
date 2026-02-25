package com.israel.cowboyfriend.global

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

fun getStringFromCalendar(calendar: Calendar,format:String,context: Context):String{
    val locale=if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) context.resources.configuration.locales.getFirstMatch(context.resources.assets.locales)
    else context.resources.configuration.locale
    val dateFormat = SimpleDateFormat(format, locale)//"kk:mm dd/MM/yy"
    //dateFormat.timeZone = TimeZone.getTimeZone("UTC")
    val dateString = dateFormat.format(calendar.time)
    return dateString
}

//get string format by current date time in milliseconds format
fun getStrDateTimeByMilliSeconds(milliSeconds: Long,format:String,context: Context):String{
    val locale=if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) context.resources.configuration.locales.getFirstMatch(context.resources.assets.locales)
    else context.resources.configuration.locale
    val dateFormat= SimpleDateFormat(format, locale)
    val date = Date(milliSeconds)
    return dateFormat.format(date)
}