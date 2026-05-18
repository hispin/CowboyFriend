package com.israel.cowboyfriend.UI

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.global.CURRENT_LATITUDE_PREF
import com.israel.cowboyfriend.global.CURRENT_LOCATION
import com.israel.cowboyfriend.global.CURRENT_LONGTUDE_PREF
import com.israel.cowboyfriend.global.GET_CURRENT_SINGLE_LOCATION_KEY
import com.israel.cowboyfriend.global.getStringInPreference
import com.israel.cowboyfriend.global.setStringInPreference
import com.israel.cowboyfriend.services.ServiceFindSingleLocation
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.viewannotation.ViewAnnotationManager

class MapmobFragment : Fragment() , OnMoveListener{

    private var fbRefresh: FloatingActionButton? = null
    private var myLocate: LatLng? = null
    private var mapView: MapView? = null
    private var myMapboxMap: MapboxMap? = null
    private var isPaused = false
    private var mapType = Style.OUTDOORS
    private var locationManager: LocationManager? = null
    //annotations (markers)
    private var viewAnnotationManager: ViewAnnotationManager? = null//mapView?.viewAnnotationManager
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var annotationApi: AnnotationPlugin? = null
    private var pointAnnotation: PointAnnotation? = null
    //////////////




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var view= inflater.inflate(R.layout.fragment_mapmob, container, false)
        init(view)
        initializeAnnotation()
        return view
    }

    private fun init(view: View) {
        mapView=view.findViewById(R.id.mapView)
        fbRefresh = view.findViewById(R.id.fbRefresh1)
        fbRefresh?.setOnClickListener {
            gotoMySingleLocation()
        }
    }

    private fun setFilter() {
        val filter = IntentFilter(GET_CURRENT_SINGLE_LOCATION_KEY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity?.registerReceiver(usbReceiver, filter, AppCompatActivity.RECEIVER_EXPORTED)
        } else {
            activity?.registerReceiver(usbReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.unregisterReceiver(usbReceiver)
    }

    override fun onStart() {
        super.onStart()
        setFilter()
        //initMapType()
        //mapView?.onStart()
    }


    /**
     * initialize annotation for markers
     */
    private fun initializeAnnotation() {
        viewAnnotationManager = mapView?.viewAnnotationManager
        // Create an instance of the Annotation API and get the PointAnnotationManager.
        annotationApi = mapView?.annotations
        pointAnnotationManager = annotationApi?.createPointAnnotationManager()
    }

    /**
     * get current location from gps
     */
    private fun gotoMySingleLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.startForegroundService(Intent(context, ServiceFindSingleLocation::class.java))
        } else {
            activity?.startService(Intent(context, ServiceFindSingleLocation::class.java))
        }
    }

    /**
     * define broadcast receiver for getting current location
     */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, inn: Intent) {
            //accept currentAlarm
             if (inn.action == GET_CURRENT_SINGLE_LOCATION_KEY) {
                val location: Location? = inn.getParcelableExtra(CURRENT_LOCATION)
                if (location != null) {
                    //save locally the current location
                    setStringInPreference(
                        activity,
                        CURRENT_LATITUDE_PREF,
                        location.latitude.toString()
                    )
                    setStringInPreference(
                        activity,
                        CURRENT_LONGTUDE_PREF,
                        location.longitude.toString()
                    )
                    showLocation(location)
                } else {
                    Toast.makeText(activity, "error in location2", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    //get last location from shared preference
    private fun getLastLocationLocally(): LatLng? {
        val latitude = getStringInPreference(activity, CURRENT_LATITUDE_PREF, "-1")
        val longtude = getStringInPreference(activity, CURRENT_LONGTUDE_PREF, "-1")
        var lat: Double? = null
        var lon: Double? = null

        if (!latitude.equals("-1") && !longtude.equals("-1")) {
            try {
                lat = latitude?.toDouble()
                lon = longtude?.toDouble()

            } catch (ex: NumberFormatException) {
            }
        }
        if (lat != null && lon != null) {
            return LatLng(lat, lon)
        }
        return null
    }

    private fun setMyLocate(myLocate: LatLng) {
        this.myLocate = myLocate
    }

    override fun onResume() {
        super.onResume()

        isPaused = false
        //load map
        if (isAdded) {

            myMapboxMap = mapView?.mapboxMap

            myMapboxMap?.loadStyle(mapType)

            //detect map dragging
            myMapboxMap?.addOnMoveListener(this)

            myMapboxMap?.addOnMapLongClickListener { point ->
//                currentLongitude = point.longitude()
//                currentLatitude = point.latitude()
//                showDialogSensorsList()
                true
            }

            //go to last location
            val location = initFindLocation()


            //set last location if exist
            location?.let {
                myLocate =
                    LatLng(it.latitude, it.longitude)
            }

            showLocation(location)

            //gotoMyLocation()

        }
    }

    //Done move the camera to ic_mark location
    private fun showLocation(location: Location?) {

        if (location != null) {
            setMyLocate(
                LatLng(
                    location.latitude, location.longitude
                )
            )
        } else {

            myLocate = getLastLocationLocally()

            if (myLocate == null) {
                //set default location (london)
                myLocate =LatLng(51.509865, -0.118092)
                //set default location (london) if there is no last location
                setMyLocate(LatLng(51.509865, -0.118092))
            }
        }
        //add marker at the focus of the map
        myLocate?.let {
            //load the camera
            if (myLocate != null && myLocate?.latitude != null &&
                myLocate?.longitude != null
            ) {

                pointAnnotationManager =
                    mapView?.annotations?.createPointAnnotationManager().apply {

                        val cameraPosition = CameraOptions.Builder()
                            .zoom(15.0)
                            .center(
                                Point.fromLngLat(
                                    myLocate?.longitude!!,
                                    myLocate?.latitude!!
                                )
                            )//Point.fromLngLat(myLocate?.latitude!!, myLocate?.longitude!!))
                            .build()
                        // set camera position
                        myMapboxMap?.setCamera(cameraPosition)
                    }
                //show all markers
                //showMarkers()

            }

        }

    }

    override fun onMove(detector: MoveGestureDetector): Boolean {
        return false
    }

    override fun onMoveBegin(detector: MoveGestureDetector) {
        //TODO("Not yet implemented")
    }

    override fun onMoveEnd(detector: MoveGestureDetector) {
        //TODO("Not yet implemented")
    }

    //get last location
    private fun initFindLocation(): Location? {
        locationManager = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager


        if (context?.let {
                ContextCompat.checkSelfPermission(
                    it,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            } == PackageManager.PERMISSION_GRANTED
        ) {

            return locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }


        return null
    }



}