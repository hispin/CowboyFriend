package com.israel.cowboyfriend.offline

import android.content.Context
import android.util.Log
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions

/**
 * The map of the ranch for work without a network. when the app is online and the map shows the cows, the area around them (the
 * normal map, not the satellite) is downloaded to the phone once, and again after a week or when the cows moved far, so the map
 * is there in the field. the download is done by the Mapbox tile store, which keeps the tiles on the phone and gives them to the
 * map when there is no network.
 */
object OfflineMapRegion {

    private const val TAG = "OfflineMap"
    private const val REGION_ID = "ranch-map"
    private const val RELOAD_MILLIS = 7L * 24 * 60 * 60 * 1000
    //around the cows: about 1.5 km (0.015 degrees of latitude)
    private const val MARGIN_DEGREES = 0.015
    private const val MIN_ZOOM: Byte = 10
    private const val MAX_ZOOM: Byte = 17

    /** the area that holds all the [points], with a margin: west, south, east, north */
    fun boxOf(points: List<Point>): DoubleArray {
        val west = points.minOf { it.longitude() } - MARGIN_DEGREES
        val east = points.maxOf { it.longitude() } + MARGIN_DEGREES
        val south = points.minOf { it.latitude() } - MARGIN_DEGREES
        val north = points.maxOf { it.latitude() } + MARGIN_DEGREES
        return doubleArrayOf(west, south, east, north)
    }

    /**
     * downloads the map around [points] when it was not downloaded yet, when it is older than a week, or when the cows are in
     * another area. nothing happens without a network or without points
     */
    fun ensureDownloaded(@Suppress("UNUSED_PARAMETER") context: Context, points: List<Point>) {
        if (points.isEmpty() || !OfflineHub.network.online.value) return
        val db = OfflineHub.db
        val box = boxOf(points)
        val key = box.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) }
        val last = db.kvGet("map_region_at")?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < RELOAD_MILLIS && db.kvGet("map_region_key") == key) return
        //one download at a time: the time is saved now, so the next call does not start a second one (a failure clears it)
        db.kvSet("map_region_at", System.currentTimeMillis().toString())
        db.kvSet("map_region_key", key)

        runCatching {
            val offlineManager = OfflineManager()
            val tileStore = TileStore.create()
            val area = Polygon.fromLngLats(
                listOf(
                    listOf(
                        Point.fromLngLat(box[0], box[1]), Point.fromLngLat(box[2], box[1]),
                        Point.fromLngLat(box[2], box[3]), Point.fromLngLat(box[0], box[3]),
                        Point.fromLngLat(box[0], box[1])
                    )
                )
            )
            //first the style (the fonts and the icons of the map), then the tiles of the area
            offlineManager.loadStylePack(
                Style.STANDARD,
                StylePackLoadOptions.Builder()
                    .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                    .acceptExpired(true)
                    .build()
            ) { styleResult ->
                if (styleResult.isError) {
                    Log.w(TAG, "the style was not downloaded: ${styleResult.error}")
                    db.kvSet("map_region_at", null)
                    return@loadStylePack
                }
                val descriptor = offlineManager.createTilesetDescriptor(
                    TilesetDescriptorOptions.Builder().styleURI(Style.STANDARD).minZoom(MIN_ZOOM).maxZoom(MAX_ZOOM).build()
                )
                tileStore.loadTileRegion(
                    REGION_ID,
                    TileRegionLoadOptions.Builder().geometry(area).descriptors(listOf(descriptor)).acceptExpired(true).build()
                ) { regionResult ->
                    if (regionResult.isError) {
                        Log.w(TAG, "the map was not downloaded: ${regionResult.error}")
                        db.kvSet("map_region_at", null)
                    } else {
                        Log.i(TAG, "the map of the ranch is on the phone")
                    }
                }
            }
        }.onFailure {
            Log.w(TAG, "the download of the map did not start", it)
            db.kvSet("map_region_at", null)
        }
    }
}
