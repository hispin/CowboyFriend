package com.israel.cowboyfriend.UI

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.global.CURRENT_LATITUDE_PREF
import com.israel.cowboyfriend.global.CURRENT_LOCATION_LATITUDE
import com.israel.cowboyfriend.global.CURRENT_LOCATION_LONGITUDE
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
import com.mapbox.maps.extension.style.image.image
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
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

class MapmobFragment : Fragment() , OnMoveListener{
    private var tvLoadLocation: TextView?=null
    private var fbRefresh: FloatingActionButton? = null
    private var myLocate: LatLng? = null
    private var mapView: MapView? = null
    private var myMapboxMap: MapboxMap? = null
    private var isPaused = false
    private var mapType = Style.OUTDOORS
    private var locationManager: LocationManager? = null
    private var myViewModelSupbase: MyViewModelSupbase? = null
    //private var pointAnnotationManager: PointAnnotationManager? = null

    //private var pointAnnotation: PointAnnotation? = null
    //private var pointAnnotationOptions: PointAnnotationOptions?=null

    private var annotationApi: AnnotationPlugin? = null
    private var pointAnnotationManager: PointAnnotationManager? = null

    //private var currentPopup: View? = null




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        //currentPopup?.let { viewAnnotationManager?.removeViewAnnotation(it) }
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
        tvLoadLocation= view.findViewById(R.id.tvLoadLocation)
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
        //viewAnnotationManager = mapView?.viewAnnotationManager
        // Create an instance of the Annotation API and get the PointAnnotationManager.
        annotationApi = mapView?.annotations
        //pointAnnotationManager = annotationApi?.createPointAnnotationManager()
        pointAnnotationManager = annotationApi?.createPointAnnotationManager()
    }

    /**
     * get single current location from gps
     */
    private fun gotoMySingleLocation() {
        tvLoadLocation?.visibility=View.VISIBLE
        activity?.startForegroundService(Intent(context, ServiceFindSingleLocation::class.java))
    }

    /**
     * define broadcast receiver for getting current location
     */
    private val brdReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, inn: Intent) {
            //accept currentAlarm
             if (inn.action == GET_CURRENT_SINGLE_LOCATION_KEY) {
                //val location: Location? = inn.getParcelableExtra(CURRENT_LOCATION)
                val latitude = inn.extras?.getDouble(CURRENT_LOCATION_LATITUDE)
                val longitude = inn.extras?.getDouble(CURRENT_LOCATION_LONGITUDE)
                val location=Location(LocationManager.GPS_PROVIDER)

                if (latitude != null && longitude != null) {

                    location.latitude=latitude
                    location.longitude=longitude

                    //save locally the current location
                    setStringInPreference(
                        activity,
                        CURRENT_LATITUDE_PREF,
                        latitude.toString()
                    )
                    setStringInPreference(
                        activity,
                        CURRENT_LONGTUDE_PREF,
                        longitude.toString()
                    )
                    tvLoadLocation?.visibility=View.GONE
                    moveCamera(location)
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
//            val location = initFindLocation()
//
//
//            //set last location if exist
//            location?.let {
//                myLocate =
//                    LatLng(it.latitude, it.longitude)
//            }
//
//            moveCamera(location)

            gotoMySingleLocation()
        }
    }

    /**
     * Move camera to current location
     */
    private fun moveCamera(location: Location?) {

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
                            )
                            .build()
                        // set camera position
                        myMapboxMap?.setCamera(cameraPosition)
                        showCurrentLocationMarker()
                    }

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
        //showCurrentLocationMarker()

        val iteratorList = details.listIterator()
        while (iteratorList != null && iteratorList.hasNext()) {
            val item = iteratorList.next()
            if (item != null && item.longitude!=null && item.latitude!=null) {
                addMarker(item)
            }
        }


    }

//    /**
//     * show current location marker
//     */
//    fun showMarkers() {
//
//        //remove all markers
//        //pointAnnotationManager?.deleteAll()
//        //pointAnnotation = null
//
//        //clear the markers
//        //markersList = ArrayList<Feature>()
//
//        //show current location marker
//        showCurrentLocationMarker()
//    }


    private lateinit var geojsonSource: GeoJsonSource

    /**
     * show current location marker
     */
    private fun showCurrentLocationMarker() {

        if (myLocate==null)return

        val currentPoint = Point.fromLngLat(
            myLocate?.longitude!!,
            myLocate?.latitude!!
        )


        geojsonSource = geoJsonSource("source-id") {
            feature(Feature.fromGeometry(currentPoint))
        }


        //val mapboxMap = binding.mapView.mapboxMap
        myMapboxMap?.loadStyle(
            style(Style.STANDARD) {
                +image(
                    "marker_icon",
                    ContextCompat.getDrawable(requireActivity(), R.drawable.ic_cowbow)!!.toBitmap()
                )


                +geojsonSource
                +symbolLayer(layerId = "layer-id", sourceId = "source-id") {
                    iconImage("marker_icon")
                    iconIgnorePlacement(true)
                    iconAllowOverlap(true)
                }
            }
        ) {
//            Toast.makeText(
//                this@AnimatedMarkerActivity,
//                getString(R.string.tap_on_map_instruction),
//                Toast.LENGTH_LONG
//            ).show()
//            myMapboxMap?.addOnMapClickListener(requireActivity() as OnMapClickListener)
        }
    }


//    /**
//     * show marker of current location if exist
//     */
//    private fun showCurrentLocationMarker1() {
//
//        if (activity == null) {
//            return
//        }
//
//        if (mapView == null) {
//            return
//        }
//
//        if (myLocate == null) {
//            return
//        }
//
//        if (myLocate != null) {
//
//            if (pointAnnotation != null) {
//                //pointAnnotationManager?.delete(pointAnnotationOptions!!)
//                //pointAnnotationManager?.delete(listOf(pointAnnotation))
//            }
//
//           // if (pointAnnotationOptions == null) {
//                Toast.makeText(activity,"create marker",Toast.LENGTH_SHORT).show()
//                // Set options for the resulting symbol layer.
//                pointAnnotationOptions = PointAnnotationOptions()
//                    // Define a geographic coordinate.
//                    .withPoint(Point.fromLngLat(myLocate?.longitude!!, myLocate?.latitude!!))
//                    // Specify the bitmap you assigned to the point annotation
//                    // The bitmap will be added to map style automatically.
//                    .withIconImage(
//                        BitmapFactory.decodeResource(
//                            requireActivity().resources, R.drawable.ic_cowbow
//                        )
//                    )
//
////                if (pointAnnotation != null) {
////                    pointAnnotationManager?.update(pointAnnotation!!)
////                    //pointAnnotationManager.
////                }else{
//                    // Add the resulting pointAnnotation to the map.
//                    pointAnnotation =pointAnnotationOptions?.let { pointAnnotationManager?.create(it) }
//
//                }
////            } else {
////                Toast.makeText(activity,"update marker",Toast.LENGTH_SHORT).show()
////                //if pointAnnotation is already exist then update the current markers location
////                pointAnnotation?.point =
////                    Point.fromLngLat(myLocate?.longitude!!, myLocate?.latitude!!)
////                if (pointAnnotation != null) {
////                    pointAnnotationManager?.update(pointAnnotation!!)
////                    //pointAnnotationManager.
////                }
////            }
//
//
//  //      }
//
//    }


    /**
     * add one marker to the map
     */
    private fun addMarker(
        cow: CowDetails,
    ): Feature? {

        //if the fragment is not attached to activity
        activity ?: return null

        val myIcon=R.drawable.ic_cow_loc


        // Set options for the resulting symbol layer.
        val pointAnnotationOptions: PointAnnotationOptions=PointAnnotationOptions()
            // Define a geographic coordinate.
            .withPoint(Point.fromLngLat(cow.longitude!!, cow.latitude!!))
            // Specify the bitmap you assigned to the point annotation
            // The bitmap will be added to map style automatically.
            .withIconImage(
                BitmapFactory.decodeResource(
                    requireActivity().resources, myIcon
                )
            )
        val msg=if(cow.isCorpse)
            requireActivity().getString(R.string.corpse)
        else{
            requireActivity().getString(R.string.no_corpse)
        }
        pointAnnotationOptions.textField = "${cow.number}:${msg}"

        //set transparent to hide preview text (without click)
        //TODO to learn how to hide annotation view text
        //pointAnnotationOptions.withTextColor(Color.TRANSPARENT)
        pointAnnotationOptions.withIconAnchor(IconAnchor.BOTTOM)
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


                return true
            }
        })
        return null
    }

    override fun onMove(detector: MoveGestureDetector): Boolean {
        return false
    }

    override fun onMoveBegin(detector: MoveGestureDetector) {
//        if (viewAnnotationManager != null && currentPopup != null)
//            viewAnnotationManager?.removeViewAnnotation(currentPopup!!)
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

    /**
     * create popup for sensor (with marker) info
     */
    private fun createPopup(annotation: PointAnnotation): View {
        // Inflate the popup layout
        val layoutInflater =
            requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popup: View = layoutInflater.inflate(R.layout.popup_marker, null)


        val arr = annotation.textField.toString().split(":")
        popup.findViewById<TextView>(R.id.tvCameraName1).text = arr[0]
        popup.findViewById<TextView>(R.id.tvCameraType1).text = arr[1]

        return popup
    }
}