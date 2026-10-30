package com.israel.cowboyfriend.UI

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.israel.cowboyfriend.R
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.MAP_ELEMENT_GATE
import com.israel.cowboyfriend.classes.MapElement
import com.israel.cowboyfriend.classes.RanchStats
import com.israel.cowboyfriend.global.CowLabels
import com.israel.cowboyfriend.global.DialogSizing
import com.israel.cowboyfriend.global.CURRENT_LATITUDE_PREF
import com.israel.cowboyfriend.global.CURRENT_LOCATION_LATITUDE
import com.israel.cowboyfriend.global.CURRENT_LOCATION_LONGITUDE
import com.israel.cowboyfriend.global.CURRENT_LONGTUDE_PREF
import com.israel.cowboyfriend.global.GET_CURRENT_SINGLE_LOCATION_KEY
import com.israel.cowboyfriend.global.getStringFromCalendar
import com.israel.cowboyfriend.global.setStringInPreference
import com.israel.cowboyfriend.services.ServiceFindSingleLocation
import com.israel.cowboyfriend.viewmodel.MyViewModelSupbase
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.style.expressions.dsl.generated.rgba
import com.mapbox.maps.extension.style.layers.generated.HeatmapLayer
import com.mapbox.maps.extension.style.layers.generated.heatmapLayer
import com.mapbox.maps.extension.style.layers.getLayerAs
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.common.Cancelable
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.extension.style.image.image
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.animation.easeTo
import java.util.Calendar
import java.util.UUID

/**
 * The map screen (Mapbox) in the style of the app: a map in a warm dusk light, my location as a cowboy on a horse,
 * a pin for every cow that has a location. the details of a cow are not drawn on the map, they appear in a
 * parchment popup only when its pin is tapped (and the popup closes when the map is tapped).
 */
class MapmobFragment : Fragment() {
    private var llLoadLocation: View? = null
    private var ivLoadLocation: ImageView? = null
    private var tvMapCount: TextView? = null
    private var fbRefresh: FloatingActionButton? = null
    private var mapView: MapView? = null
    private var myMapboxMap: MapboxMap? = null
    private var myViewModelSupbase: MyViewModelSupbase? = null
    private var pointAnnotationManager: PointAnnotationManager? = null

    //the style (with the layer of my location) is loaded once; a location that arrives earlier waits here
    private var styleRequested = false
    private var styleLoaded = false
    private var pendingMyLocation: Point? = null

    //the cow behind every pin (by the id of the annotation), so a tap on a pin can show the details of its cow
    private val cowsByAnnotation = HashMap<String, CowDetails>()
    //the popup that is open now (a child of the popup layer over the map), null when none, and the place of its pin
    private var currentPopup: View? = null
    private var popupPoint: Point? = null
    //how many pixels of the icon are above its point (the popup stands on top of them)
    private var popupAnchorHeight = 0
    //the size of the icon of the popup (relative to its picture) before the zoom scale: the popup follows the icon size
    private var popupIconFactor = 1.0
    //the scale of the icons that follows the zoom of the map (see zoomScale), 1.0 at the default zoom
    private var zoomScaleNow = 1.0

    //the map elements (gates, ...): their own pin manager, and the element behind every icon (by the id of the annotation)
    private var elementsManager: PointAnnotationManager? = null
    private val elementsByAnnotation = HashMap<String, MapElement>()
    private val gateBitmap by lazy { AppCompatResources.getDrawable(requireContext(), R.drawable.ic_map_gate)!!.toBitmap() }

    //result message (success / failure) of adding or removing an element; hides itself after a few seconds
    private var resultBanner: View? = null
    private var ivResultIcon: ImageView? = null
    private var tvResultText: TextView? = null
    private val resultHandler = Handler(Looper.getMainLooper())
    private val hideResultRunnable = Runnable { hideResult() }
    private var popupLayer: FrameLayout? = null
    //follows the camera so the popup stays above its pin while the map moves
    private var cameraSubscription: Cancelable? = null

    //the map type: false = the normal map (dusk), true = the satellite
    private var isSatellite = false
    private var fbMapType: FloatingActionButton? = null

    //the heat map: its button, the mode (MyViewModelSupbase.HEAT_*) and the cows it is made from
    private var fbHeat: FloatingActionButton? = null
    private var heatMode = MyViewModelSupbase.HEAT_OFF
    private var lastCows: List<CowDetails> = emptyList()
    //the camera has to be fitted to the heat points as soon as there are some
    private var heatFitPending = false

    //the pin images are made once from the drawables
    private val cowPinBitmap by lazy { AppCompatResources.getDrawable(requireContext(), R.drawable.ic_map_cow_pin)!!.toBitmap() }
    private val corpsePinBitmap by lazy { AppCompatResources.getDrawable(requireContext(), R.drawable.ic_map_corpse_pin)!!.toBitmap() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_mapmob, container, false)
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
        myViewModelSupbase?._cowsDetails?.observe(viewLifecycleOwner) {
            if (it != null) {
                showMarkers(it)
            }
        }
        //the elements of the user (gates, ...) are drawn again whenever the list changes
        myViewModelSupbase?._mapElements?.observe(viewLifecycleOwner) {
            if (it != null) showElements(it)
        }
    }

    private fun init(view: View) {
        mapView = view.findViewById(R.id.mapView)
        tvMapCount = view.findViewById(R.id.tvMapCount)
        llLoadLocation = view.findViewById(R.id.llLoadLocation)
        ivLoadLocation = view.findViewById(R.id.ivLoadLocation)
        fbRefresh = view.findViewById(R.id.fbRefresh1)
        fbRefresh?.setOnClickListener {
            gotoMySingleLocation()
        }
        resultBanner = view.findViewById(R.id.resultBanner)
        ivResultIcon = view.findViewById(R.id.ivResultIcon)
        tvResultText = view.findViewById(R.id.tvResultText)
        resultBanner?.setOnClickListener { hideResult() }
        popupLayer = view.findViewById(R.id.flPopupLayer)
        //the layer is positioned in screen coordinates (left to right) whatever the direction of the language
        popupLayer?.layoutDirection = View.LAYOUT_DIRECTION_LTR
        //the heat map button: off / all the cows / only the cows that need attention (see cycleHeatMode)
        fbHeat = view.findViewById(R.id.fbHeat)
        fbHeat?.setOnClickListener { cycleHeatMode() }
        fbMapType = view.findViewById(R.id.fbMapType)
        updateMapTypeButton()
        fbMapType?.setOnClickListener {
            isSatellite = !isSatellite
            updateMapTypeButton()
            applyMapStyle()
        }
    }

    //the toggle is brass for the normal map and turquoise while the satellite is shown
    private fun updateMapTypeButton() {
        val color = ContextCompat.getColor(requireContext(), if (isSatellite) R.color.ww_turquoise else R.color.ww_brass)
        fbMapType?.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
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
    }

    /**
     * the manager of the cow pins is created once, with one click listener: a tap on a pin opens the popup of its cow,
     * a tap on the empty map closes the popup
     */
    private fun initializeAnnotation() {
        pointAnnotationManager = mapView?.annotations?.createPointAnnotationManager()
        pointAnnotationManager?.addClickListener(OnPointAnnotationClickListener { annotation ->
            showCowPopup(annotation)
            true
        })
        //the elements (gates, ...) have their own manager, created after the cows so they are drawn above them
        elementsManager = mapView?.annotations?.createPointAnnotationManager()
        elementsManager?.addClickListener(OnPointAnnotationClickListener { annotation ->
            showElementPopup(annotation)
            true
        })
        mapView?.mapboxMap?.addOnMapClickListener {
            closePopup()
            false
        }
        //a long press on the empty map opens the list of the elements that can be added there
        mapView?.mapboxMap?.addOnMapLongClickListener { point ->
            closePopup()
            showAddElementDialog(point)
            true
        }
        //keep the popup above its pin while the map moves
        cameraSubscription = mapView?.mapboxMap?.subscribeCameraChanged {
            updateIconScale()
            positionPopup()
        }
    }

    override fun onDestroyView() {
        cameraSubscription?.cancel()
        cameraSubscription = null
        resultHandler.removeCallbacks(hideResultRunnable)
        super.onDestroyView()
    }

    /**
     * show the loading plate (the spinning horseshoe with the message) and ask the service for the current location
     */
    private fun gotoMySingleLocation() {
        llLoadLocation?.visibility = View.VISIBLE
        (ivLoadLocation?.drawable as? android.graphics.drawable.Animatable)?.start()
        activity?.startForegroundService(Intent(context, ServiceFindSingleLocation::class.java))
    }

    private fun hideLoadLocation() {
        (ivLoadLocation?.drawable as? android.graphics.drawable.Animatable)?.stop()
        llLoadLocation?.visibility = View.GONE
    }

    /**
     * define broadcast receiver for getting current location
     */
    private val brdReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, inn: Intent) {
            if (inn.action == GET_CURRENT_SINGLE_LOCATION_KEY) {
                val latitude = inn.extras?.getDouble(CURRENT_LOCATION_LATITUDE)
                val longitude = inn.extras?.getDouble(CURRENT_LOCATION_LONGITUDE)

                if (latitude != null && longitude != null) {
                    //save locally the current location
                    setStringInPreference(activity, CURRENT_LATITUDE_PREF, latitude.toString())
                    setStringInPreference(activity, CURRENT_LONGTUDE_PREF, longitude.toString())
                    hideLoadLocation()
                    showMyLocation(Point.fromLngLat(longitude, latitude))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAdded) {
            myMapboxMap = mapView?.mapboxMap
            loadMapStyle()
            //the ranch dashboard asked for a heat map: it is turned on (the style may still be loading: its callback fills it)
            val asked = myViewModelSupbase?.pendingHeatMode ?: MyViewModelSupbase.HEAT_OFF
            if (asked != MyViewModelSupbase.HEAT_OFF) {
                myViewModelSupbase?.pendingHeatMode = MyViewModelSupbase.HEAT_OFF
                heatMode = asked
                applyHeatMode(fitCamera = true)
            }
            gotoMySingleLocation()
        }
    }

    /**
     * loads the map style once: the Standard style in a warm dusk light (the Wild West colors), without the clutter of
     * the points of interest, and with the layer of my location (the cowboy on the horse), which is moved by [showMyLocation]
     */
    private fun loadMapStyle() {
        if (styleRequested) return
        styleRequested = true
        applyMapStyle()
    }

    /**
     * loads the style of the chosen map type (the normal map or the satellite) with the layer of my location; the cow
     * pins are annotations and stay on the map when the style is replaced
     */
    private fun applyMapStyle() {
        styleLoaded = false
        val riderBitmap = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_rider_marker)!!.toBitmap()

        myMapboxMap?.loadStyle(
            style(if (isSatellite) Style.STANDARD_SATELLITE else Style.STANDARD) {
                //the heat map of the cows (hidden until the heat button / the ranch dashboard turns it on): the more cows
                //in a place, the warmer it is (dark turquoise, brass, orange, rust)
                +geoJsonSource(HEAT_SOURCE) { }
                +heatmapLayer(HEAT_LAYER, HEAT_SOURCE) {
                    visibility(Visibility.NONE)
                    heatmapRadius(
                        interpolate {
                            linear()
                            zoom()
                            stop { literal(10.0); literal(26.0) }
                            stop { literal(16.0); literal(60.0) }
                            stop { literal(19.0); literal(110.0) }
                        }
                    )
                    heatmapOpacity(0.85)
                    heatmapColor(
                        interpolate {
                            linear()
                            heatmapDensity()
                            stop { literal(0.0); rgba(63.0, 124.0, 116.0, 0.0) }
                            stop { literal(0.2); rgba(63.0, 124.0, 116.0, 0.55) }
                            stop { literal(0.45); rgba(228.0, 205.0, 134.0, 0.8) }
                            stop { literal(0.7); rgba(232.0, 146.0, 75.0, 0.9) }
                            stop { literal(1.0); rgba(181.0, 69.0, 29.0, 0.95) }
                        }
                    )
                }
                +image(RIDER_IMAGE, riderBitmap)
                +geoJsonSource(RIDER_SOURCE) { }
                +symbolLayer(layerId = RIDER_LAYER, sourceId = RIDER_SOURCE) {
                    iconImage(RIDER_IMAGE)
                    iconAnchor(IconAnchor.CENTER)
                    iconIgnorePlacement(true)
                    iconAllowOverlap(true)
                    //the cowboy is smaller than its picture and grows / shrinks with the zoom, like the other icons
                    iconSize(
                        interpolate {
                            linear()
                            zoom()
                            stop { literal(ZOOM_MIN); literal(RIDER_BASE_SCALE * zoomScale(ZOOM_MIN)) }
                            stop { literal(ZOOM_DEFAULT); literal(RIDER_BASE_SCALE) }
                            stop { literal(ZOOM_MAX); literal(RIDER_BASE_SCALE * zoomScale(ZOOM_MAX)) }
                        }
                    )
                }
            }
        ) { style ->
            //a warm light, and only the labels that help to find the way
            if (!isSatellite) style.setStyleImportConfigProperty("basemap", "lightPreset", Value.valueOf("dusk"))
            style.setStyleImportConfigProperty("basemap", "showPointOfInterestLabels", Value.valueOf(false))
            style.setStyleImportConfigProperty("basemap", "showTransitLabels", Value.valueOf(false))
            styleLoaded = true
            //the pin layers may have been created again with the style: set their size for the current zoom
            updateIconScale(force = true)
            pendingMyLocation?.let { updateMyLocationLayer(it) }
            updateHeatLayer()
        }
    }

    //the heat button cycles: off -> all the cows -> only the cows that need attention -> off
    private fun cycleHeatMode() {
        heatMode = (heatMode + 1) % 3
        applyHeatMode(fitCamera = heatMode != MyViewModelSupbase.HEAT_OFF)
    }

    /**
     * turns the heat map to [heatMode]: the layer and its points, the button color and a short message. when the heat map is
     * on, the camera shows all its points ([fitCamera]); my location does not move the camera while it is on
     */
    private fun applyHeatMode(fitCamera: Boolean) {
        val color = if (heatMode == MyViewModelSupbase.HEAT_OFF) R.color.ww_brass else R.color.ww_rust
        fbHeat?.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), color))
        updateHeatLayer()
        showResult(
            when (heatMode) {
                MyViewModelSupbase.HEAT_ALL -> R.string.map_heat_all
                MyViewModelSupbase.HEAT_ATTENTION -> R.string.map_heat_attention
                else -> R.string.map_heat_off
            },
            true
        )
        //the cows may not be loaded yet: then the camera is fitted when they arrive (see showMarkers)
        heatFitPending = fitCamera
        if (fitCamera && heatPoints().isNotEmpty()) {
            fitCameraToHeatPoints()
            heatFitPending = false
        }
    }

    //the points of the heat map for the current mode (the living cows with a location, or only the ones that need attention)
    private fun heatPoints(): List<Point> {
        val stats = RanchStats(lastCows)
        val points = if (heatMode == MyViewModelSupbase.HEAT_ATTENTION) stats.attentionPoints else stats.allPoints
        return points.map { Point.fromLngLat(it.first, it.second) }
    }

    //fills the heat source with the points of the current mode and shows / hides the heat layer
    private fun updateHeatLayer() {
        if (!styleLoaded) return
        val on = heatMode != MyViewModelSupbase.HEAT_OFF
        val points = if (on) heatPoints() else emptyList()
        myMapboxMap?.getStyle { style ->
            style.getSourceAs<GeoJsonSource>(HEAT_SOURCE)
                ?.featureCollection(FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(it) }))
            style.getLayerAs<HeatmapLayer>(HEAT_LAYER)?.visibility(if (on) Visibility.VISIBLE else Visibility.NONE)
        }
    }

    //moves the camera so that all the points of the heat map are on the screen
    private fun fitCameraToHeatPoints() {
        val points = heatPoints()
        if (points.isEmpty()) return
        val map = myMapboxMap ?: return
        val camera = map.cameraForCoordinates(points, CameraOptions.Builder().build(), EdgeInsets(120.0, 80.0, 120.0, 80.0), 17.0, null)
        map.setCamera(camera)
    }

    /**
     * the current location arrived: move the camera to it and put the cowboy on the horse there
     */
    private fun showMyLocation(point: Point) {
        //while the heat map is on the camera shows the whole herd: it does not jump to my location
        if (heatMode == MyViewModelSupbase.HEAT_OFF) {
            myMapboxMap?.setCamera(
                CameraOptions.Builder()
                    .zoom(15.0)
                    .center(point)
                    .build()
            )
        }
        pendingMyLocation = point
        if (styleLoaded) updateMyLocationLayer(point)
    }

    //moves the cowboy on the horse to the point (the style is already loaded)
    private fun updateMyLocationLayer(point: Point) {
        myMapboxMap?.getStyle { style ->
            style.getSourceAs<GeoJsonSource>(RIDER_SOURCE)?.feature(Feature.fromGeometry(point))
        }
    }

    /**
     * show all the cow pins (only the cows that have a location) and the counter of them
     */
    fun showMarkers(details: List<CowDetails>) {
        closePopup()
        pointAnnotationManager?.deleteAll()
        cowsByAnnotation.clear()

        var count = 0
        for (item in details) {
            if (item.longitude != null && item.latitude != null) {
                addMarker(item)
                count++
            }
        }
        tvMapCount?.text = getString(R.string.map_cows_count, count)
        tvMapCount?.visibility = if (count > 0) View.VISIBLE else View.GONE
        //the heat map follows the cows
        lastCows = details
        updateHeatLayer()
        if (heatFitPending && heatPoints().isNotEmpty()) {
            fitCameraToHeatPoints()
            heatFitPending = false
        }
    }

    /**
     * add the pin of one cow: a brass pin with a cow face, or a dark pin with a skull for a dead cow. no text is drawn
     * on the map, the details wait for a tap on the pin
     */
    private fun addMarker(cow: CowDetails) {
        //if the fragment is not attached to activity
        activity ?: return

        val options = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(cow.longitude!!, cow.latitude!!))
            .withIconImage(if (cow.isCorpse) corpsePinBitmap else cowPinBitmap)
            //the tip of the pin is the place of the cow
            .withIconAnchor(IconAnchor.BOTTOM)
        pointAnnotationManager?.create(options)?.let { cowsByAnnotation[it.id] = cow }
    }

    /**
     * the popup of the tapped pin: the cow number with the two digits of the birth year, the cow type and the status
     * chips, as a parchment card standing on the pin
     */
    private fun showCowPopup(annotation: PointAnnotation) {
        val cow = cowsByAnnotation[annotation.id] ?: return
        val mapView = mapView ?: return
        closePopup()

        val popup = LayoutInflater.from(requireContext()).inflate(R.layout.popup_marker, mapView, false)
        popup.findViewById<TextView>(R.id.tvPopupNumber).text =
            getString(R.string.card_cow_number, cow.number?.toString() ?: "-")

        //the birth year as two digits next to the number
        val yearView = popup.findViewById<TextView>(R.id.tvPopupYear)
        val created = cow.created_at
        if (created != null) {
            val year = Calendar.getInstance().apply { timeInMillis = created }.get(Calendar.YEAR)
            yearView.text = String.format(java.util.Locale.US, "%02d", year % 100)
            yearView.visibility = View.VISIBLE
        } else {
            yearView.visibility = View.GONE
        }

        val typeView = popup.findViewById<TextView>(R.id.tvPopupType)
        //the cow type is saved in Hebrew: it is shown in the language of the app
        typeView.text = CowLabels.typeLabel(requireContext(), cow.cowType)
        typeView.visibility = if (cow.cowType.isNullOrBlank()) View.GONE else View.VISIBLE

        //alive (green) or corpse (dark stamp with the skull); a corpse is not "seen"
        val statusView = popup.findViewById<TextView>(R.id.tvPopupStatus)
        val seenView = popup.findViewById<TextView>(R.id.tvPopupSeen)
        if (cow.isCorpse) {
            styleChip(statusView, R.drawable.shape_ww_chip_dead_bg, R.drawable.ic_chip_skull, R.color.ww_brass_light)
            statusView.setText(R.string.corpse)
            seenView.visibility = View.GONE
        } else {
            styleChip(statusView, R.drawable.shape_ww_chip_ok_bg, R.drawable.ic_chip_check, R.color.ww_turquoise_dark)
            statusView.setText(R.string.map_status_alive)
            showSeenChip(cow, seenView)
        }

        attachPopup(popup, annotation.point, cowPinBitmap.height, COW_BASE_SCALE)
    }

    /**
     * the popup of the tapped element (gate, ...): its name and a delete button, above the icon of the element
     */
    private fun showElementPopup(annotation: PointAnnotation) {
        val element = elementsByAnnotation[annotation.id] ?: return
        val mapView = mapView ?: return
        closePopup()

        val popup = LayoutInflater.from(requireContext()).inflate(R.layout.popup_map_element, mapView, false)
        popup.findViewById<TextView>(R.id.tvElementName).setText(elementNameRes(element.type))
        popup.findViewById<View>(R.id.btnElementDelete).setOnClickListener {
            closePopup()
            deleteElement(element)
        }
        //the icon of an element is centered on its place, so the popup stands above the upper half of the icon
        attachPopup(popup, annotation.point, elementBitmap(element.type).height / 2, ELEMENT_BASE_SCALE)
    }

    /**
     * adds the popup to the layer over the map and places it above the point; [anchorHeight] is how many pixels of the
     * icon are above the point (the whole pin, or the upper half of a centered icon)
     */
    private fun attachPopup(popup: View, point: Point, anchorHeight: Int, iconFactor: Double) {
        //the popup is added hidden to the layer over the map; it is shown (above the icon) when its size is known
        val layer = popupLayer ?: return
        popupAnchorHeight = anchorHeight
        popupIconFactor = iconFactor
        popup.visibility = View.INVISIBLE
        popup.setOnClickListener { closePopup() }
        layer.addView(
            popup,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.LEFT
            )
        )
        currentPopup = popup
        popupPoint = point
        popup.post { positionPopup(true) }
    }

    /**
     * puts the popup above its pin, centered on it and kept inside the screen. when the pin is too high for the popup
     * ([panIfNeeded]) the map is moved down a little so the whole popup is visible
     */
    private fun positionPopup(panIfNeeded: Boolean = false) {
        val popup = currentPopup ?: return
        val point = popupPoint ?: return
        val layer = popupLayer ?: return
        val map = mapView?.mapboxMap ?: return
        if (popup.width == 0 || layer.width == 0) return

        val density = resources.displayMetrics.density
        val margin = 8 * density
        val pin = map.pixelForCoordinate(point)
        val x = (pin.x.toFloat() - popup.width / 2f)
            .coerceIn(margin, maxOf(margin, layer.width - popup.width - margin))
        //the bottom of the popup touches the top of the pin image (the tip of the pin is the cow)
        //the icon is drawn smaller / bigger than its picture (base size and zoom), so the popup stands on the drawn icon
        val drawnAnchor = popupAnchorHeight * popupIconFactor * zoomScaleNow
        val y = pin.y.toFloat() - drawnAnchor.toFloat() - popup.height - 4 * density
        popup.translationX = x
        //the popup never goes above the counter chip and the scale bar at the top of the map
        val topLimit = 64 * density
        popup.translationY = maxOf(y, topLimit)
        popup.visibility = View.VISIBLE

        if (panIfNeeded && y < topLimit) {
            //move the camera up so the content (and the popup) moves down by the missing space
            val shift = topLimit - y + 8 * density
            val center = map.coordinateForPixel(
                ScreenCoordinate(layer.width / 2.0, layer.height / 2.0 - shift)
            )
            mapView?.camera?.easeTo(
                CameraOptions.Builder().center(center).build(),
                MapAnimationOptions.mapAnimationOptions { duration(300) }
            )
        }
    }

    //the seen chip of the popup: green when seen today, amber with a warning and the date of the last visit otherwise
    private fun showSeenChip(cow: CowDetails, seenView: TextView) {
        val last = cow.last_seen_at
        var seenToday = false
        var dateText: String? = null
        if (last != null) {
            val seen = Calendar.getInstance().apply { timeInMillis = last }
            val today = Calendar.getInstance()
            seenToday = seen.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && seen.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            dateText = getStringFromCalendar(seen, "dd/MM/yy", requireContext())
        }
        if (seenToday) {
            styleChip(seenView, R.drawable.shape_ww_chip_ok_bg, R.drawable.ic_chip_check, R.color.ww_turquoise_dark)
            seenView.setText(R.string.status_seen_today)
        } else {
            styleChip(seenView, R.drawable.shape_ww_chip_warn_bg, R.drawable.ic_chip_warning, R.color.ww_saddle_dark)
            seenView.text = if (dateText != null) getString(R.string.status_not_seen_since, dateText) else getString(R.string.last_not_seen)
        }
        seenView.visibility = View.VISIBLE
    }

    //a chip like the ones of the cow card: the background, the icon in front and the color of the text and the icon
    private fun styleChip(chip: TextView, backgroundRes: Int, iconRes: Int, colorRes: Int) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        chip.setBackgroundResource(backgroundRes)
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
        chip.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(color)
        chip.setTextColor(color)
    }

    //closes the open popup, if any
    private fun closePopup() {
        currentPopup?.let { popupLayer?.removeView(it) }
        currentPopup = null
        popupPoint = null
    }

    /**
     * get details of all cows (and the elements the user added to the map)
     */
    private fun getCowDetails() {
        myViewModelSupbase?.dbGetCowsDetails()
        myViewModelSupbase?.dbGetMapElements()
    }

    //the icon of an element by its type (unknown types, for example from a newer version of the app, show as a gate)
    private fun elementBitmap(type: String) = when (type) {
        MAP_ELEMENT_GATE -> gateBitmap
        else -> gateBitmap
    }

    //the name of an element by its type
    private fun elementNameRes(type: String) = when (type) {
        MAP_ELEMENT_GATE -> R.string.map_element_gate
        else -> R.string.map_element_gate
    }

    /**
     * draws all the map elements: the old icons are removed and one icon, centered on its place, is added for each element
     */
    private fun showElements(elements: List<MapElement>) {
        closePopup()
        elementsManager?.deleteAll()
        elementsByAnnotation.clear()
        for (element in elements) {
            val options = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(element.lng, element.lat))
                .withIconImage(elementBitmap(element.type))
                .withIconAnchor(IconAnchor.CENTER)
            elementsManager?.create(options)?.let { elementsByAnnotation[it.id] = element }
        }
    }

    /**
     * the window of a long press: the elements that can be added at [point]. choosing one saves it in the database
     */
    private fun showAddElementDialog(point: Point) {
        val ctx = context ?: return
        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_map_add_element, null, false)
        val dialog = android.app.Dialog(ctx).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            DialogSizing.apply(this, 0.86f)
            window?.setDimAmount(0.55f)
        }
        content.findViewById<View>(R.id.llAddGate).setOnClickListener {
            dialog.dismiss()
            addElement(MAP_ELEMENT_GATE, point)
        }
        content.findViewById<View>(R.id.btnAddCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    //adds an element of the type at the point: saved in the database, drawn when the saved list comes back, then a message
    private fun addElement(type: String, point: Point) {
        val element = MapElement(UUID.randomUUID().toString(), type, point.latitude(), point.longitude())
        myViewModelSupbase?.dbChangeMapElements({ it + element }) { ok ->
            if (isAdded) showResult(if (ok) R.string.map_element_added else R.string.map_element_failed, ok)
        }
    }

    //removes the element from the database (and so from the map), then shows a message
    private fun deleteElement(element: MapElement) {
        myViewModelSupbase?.dbChangeMapElements({ list -> list.filter { it.id != element.id } }) { ok ->
            if (isAdded) showResult(if (ok) R.string.map_element_deleted else R.string.map_element_failed, ok)
        }
    }

    /**
     * message at the bottom of the map: slides in and hides itself after a few seconds, or when tapped
     */
    private fun showResult(messageRes: Int, success: Boolean) {
        val banner = resultBanner ?: return
        ivResultIcon?.setImageResource(if (success) R.drawable.ic_banner_success else R.drawable.ic_banner_fail)
        tvResultText?.setText(messageRes)
        resultHandler.removeCallbacks(hideResultRunnable)
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = 80 * resources.displayMetrics.density
        banner.animate().alpha(1f).translationY(0f).setDuration(220).start()
        resultHandler.postDelayed(hideResultRunnable, 3200L)
    }

    private fun hideResult() {
        resultHandler.removeCallbacks(hideResultRunnable)
        val banner = resultBanner ?: return
        banner.animate().alpha(0f).translationY(80 * resources.displayMetrics.density).setDuration(200)
            .withEndAction { banner.visibility = View.GONE }.start()
    }

    /**
     * the icons (the cows, the elements) grow when the map is zoomed in and shrink when it is zoomed out. the pins have
     * their own base size, so the cowboy, the pins and the gates look balanced. [force] sets the size even when the
     * zoom did not change (a new style was loaded)
     */
    private fun updateIconScale(force: Boolean = false) {
        val zoom = mapView?.mapboxMap?.cameraState?.zoom ?: return
        val scale = zoomScale(zoom)
        if (!force && Math.abs(scale - zoomScaleNow) < 0.01) return
        zoomScaleNow = scale
        pointAnnotationManager?.iconSize = COW_BASE_SCALE * scale
        elementsManager?.iconSize = ELEMENT_BASE_SCALE * scale
    }

    private companion object {
        //icon scales: the size of an icon at the default zoom, relative to its picture
        const val RIDER_BASE_SCALE = 0.72
        const val ELEMENT_BASE_SCALE = 0.72
        const val COW_BASE_SCALE = 1.0

        //the zoom range of the icon scale: smallest, the default zoom of the map (scale 1) and biggest
        const val ZOOM_MIN = 10.0
        const val ZOOM_DEFAULT = 15.0
        const val ZOOM_MAX = 19.0

        /**
         * the scale of the icons for a zoom: 1.0 at the default zoom, smaller when zoomed out (down to 0.35) and bigger
         * when zoomed in (up to about 1.5). linear between the stops, like the interpolation of the cowboy layer
         */
        fun zoomScale(zoom: Double): Double {
            val z = zoom.coerceIn(ZOOM_MIN, ZOOM_MAX)
            return if (z <= ZOOM_DEFAULT) 0.35 + (z - ZOOM_MIN) / (ZOOM_DEFAULT - ZOOM_MIN) * 0.65
            else 1.0 + (z - ZOOM_DEFAULT) / (ZOOM_MAX - ZOOM_DEFAULT) * 0.5
        }

        //names of the image, source and layer of my location in the map style
        const val RIDER_IMAGE = "rider-image"
        const val RIDER_SOURCE = "rider-source"
        const val RIDER_LAYER = "rider-layer"
        //names of the source and the layer of the heat map in the map style
        const val HEAT_SOURCE = "heat-source"
        const val HEAT_LAYER = "heat-layer"
    }
}
