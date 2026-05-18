package com.israel.cowboyfriend.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.global.CURRENT_LOCATION
import com.israel.cowboyfriend.global.GET_CURRENT_SINGLE_LOCATION_KEY

class ServiceFindSingleLocation : Service() {
    private val TAG = "ServiceFindSingleLocation"
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            run {
                if (locationResult.lastLocation != null) {
                    //Log.d(TAG,"get location")
                    location = locationResult.lastLocation!!
                    val inn = Intent(GET_CURRENT_SINGLE_LOCATION_KEY)
                    inn.putExtra(CURRENT_LOCATION, location)
                    sendBroadcast(inn)
                    stopSelf()
                }
            }
        }
    }
    lateinit var location: Location


    override fun onBind(intent: Intent?): IBinder? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    override fun onCreate() {
        super.onCreate()
        startSysForeGround()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMaxUpdates(1)
            .build()
        startGetLocation()

        return START_NOT_STICKY
    }

    private fun startGetLocation() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this.applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                //Log.d("ServiceFindLocation"," start location")
                fusedLocationProviderClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            Log.d("startGetLocation", "exception:" + exception.message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            //Log.d("ServiceFindLocation"," onDestroy location")
            fusedLocationProviderClient.removeLocationUpdates(
                locationCallback
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    //The system allows apps to call Context.startForegroundService() even while the app is in the background. However, the app must call that service's startForeground() method within five seconds after the service is created
    private fun startSysForeGround() {
        fun getNotificationIcon(): Int {
            val useWhiteIcon =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            return if (useWhiteIcon) R.mipmap.ic_app_notification else R.mipmap.ic_launcher
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val CHANNEL_ID = "my_channel_01"
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            val `object` = getSystemService(NOTIFICATION_SERVICE)
            if (`object` != null && `object` is NotificationManager) {
                `object`.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentText("SG-Patrol is running")
                .setSmallIcon(getNotificationIcon())
                .build()

            startForeground(1, notification)
        }

    }
}