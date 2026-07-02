package com.israel.cowboyfriend.UI

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
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
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.global.CURRENT_LATITUDE_PREF
import com.israel.cowboyfriend.global.CURRENT_LOCATION
import com.israel.cowboyfriend.global.CURRENT_LONGTUDE_PREF
import com.israel.cowboyfriend.global.GET_CURRENT_SINGLE_LOCATION_KEY
import com.israel.cowboyfriend.global.getStringInPreference
import com.israel.cowboyfriend.global.setStringInPreference
import com.israel.cowboyfriend.services.ServiceFindSingleLocation
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
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
    private var myViewModelSupbase: MyViewModelSupbase? = null
    //annotations (markers)
    private var viewAnnotationManager: ViewAnnotationManager? = null//mapView?.viewAnnotationManager
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var annotationApi: AnnotationPlugin? = null
    private var pointAnnotation: PointAnnotation? = null
    private var pointAnnotationOptions: PointAnnotationOptions?=null
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
        myViewModelSupbase = ViewModelProvider(requireActivity())[MyViewModelSupbase::class.java]
        setObservers()
        getCowDetails()
        return view
    }
    /**
     * set observers
     */
    private fun setObservers() {
        myViewModelSupbase?._cowsDetails?.observe(requireActivity()) {
            if(it!=null) {
                showMarkers(it)
                //showCowsDetails(it as ArrayList<CowDetails>?)
            }
        }
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
            activity?.registerReceiver(brdReceiver, filter, AppCompatActivity.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                requireActivity(),
                brdReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.unregisterReceiver(brdReceiver)
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
        activity?.startForegroundService(Intent(context, ServiceFindSingleLocation::class.java))
    }

    /**
     * define broadcast receiver for getting current location
     */
    private val brdReceiver = object : BroadcastReceiver() {
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
                showMarkers()

            }

        }

    }

    /**
     * show all markers
     */
    fun showMarkers(details: List<CowDetails>) {

        //remove all markers
        pointAnnotationManager?.deleteAll()
        //pointAnnotation = null

        //clear the markers
        //markersList = ArrayList<Feature>()

        //show current location marker
        showCurrentLocationMarker()

        val iteratorList = details.listIterator()
        while (iteratorList != null && iteratorList.hasNext()) {
            val item = iteratorList.next()
            if (item != null && item.longitude!=null && item.latitude!=null) {
                addMarker(item)
            }
        }


    }

    /**
     * show current location marker
     */
    fun showMarkers() {

        //remove all markers
        pointAnnotationManager?.deleteAll()
        //pointAnnotation = null

        //clear the markers
        //markersList = ArrayList<Feature>()

        //show current location marker
        showCurrentLocationMarker()
    }

    /**
     * show marker of current location if exist
     */
    private fun showCurrentLocationMarker() {

        if (activity == null) {
            return
        }

        if (mapView == null) {
            return
        }

        if (myLocate == null) {
            return
        }

        if (myLocate != null) {

            if (pointAnnotation == null && pointAnnotationOptions == null) {
                // Set options for the resulting symbol layer.
                pointAnnotationOptions = PointAnnotationOptions()
                    // Define a geographic coordinate.
                    .withPoint(Point.fromLngLat(myLocate?.longitude!!, myLocate?.latitude!!))
                    // Specify the bitmap you assigned to the point annotation
                    // The bitmap will be added to map style automatically.
                    .withIconImage(
                        BitmapFactory.decodeResource(
                            requireActivity().resources, R.mipmap.ic_my_locate
                        )
                    )
                // Add the resulting pointAnnotation to the map.
                pointAnnotation =pointAnnotationOptions?.let { pointAnnotationManager?.create(it) }
            } else {
                //if pointAnnotation is already exist then update the current markers location
                pointAnnotation?.point =
                    Point.fromLngLat(myLocate?.longitude!!, myLocate?.latitude!!)
                if (pointAnnotation != null) {
                    pointAnnotationManager?.update(pointAnnotation!!)
                }
            }


        }

    }


    /**
     * add one marker to the map
     */
    private fun addMarker(
        cows: CowDetails,
    ): Feature? {

        if(requireActivity()==null) return null

        val myIcon=R.drawable.ic_cow_loc


        // Set options for the resulting symbol layer.
        val pointAnnotationOptions: PointAnnotationOptions=PointAnnotationOptions()
            // Define a geographic coordinate.
            .withPoint(Point.fromLngLat(cows.longitude!!, cows.latitude!!))
            // Specify the bitmap you assigned to the point annotation
            // The bitmap will be added to map style automatically.
            .withIconImage(
                BitmapFactory.decodeResource(
                    requireActivity().resources, myIcon
                )
            )
        //pointAnnotationOptions.textField = "$cameraName:$type"

        //set transparent to hide preview text (without click)
        //TODO to learn how to hide annotation view text
        pointAnnotationOptions.withTextColor(Color.TRANSPARENT)
        // Add the resulting pointAnnotation to the map.
        pointAnnotationManager?.create(pointAnnotationOptions)
        pointAnnotationManager?.addClickListener(object : OnPointAnnotationClickListener {
            override fun onAnnotationClick(annotation: PointAnnotation): Boolean {


                // Remove existing popup if any
//                viewAnnotationManager?.removeAllViewAnnotations()
//
//                currentPopup = createPopup(annotation)
//
//
//                val options: ViewAnnotationOptions?
//                options = viewAnnotationOptions {
//                    geometry(
//                        Point.fromLngLat(
//                            annotation.point.longitude(),
//                            annotation.point.latitude()
//                        )
//                    )
//                    allowOverlap(false)
//                    annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
//                    //visible(false)
//                }
//
//                val lp = LinearLayout.LayoutParams(
//                    WRAP_CONTENT,
//                    WRAP_CONTENT
//                )
//                currentPopup?.layoutParams = lp
//
//
//                if (currentPopup != null) {
//                    // Add the popup as a ViewAnnotation
//                    viewAnnotationManager?.addViewAnnotation(currentPopup!!, options)
//                }
//
//
                return true
            }
        })
        return null
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


    /**
     * get details of all cows
     */
    private fun getCowDetails(){
        myViewModelSupbase?.dbGetCowsDetails()
    }
}