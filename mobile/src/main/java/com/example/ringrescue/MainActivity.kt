package com.example.ringrescue

import android.os.Bundle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var controller: NavigationController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        mapView = MapView(this)
        setContentView(mapView)

        val graphhopper = GraphhopperService("e8d1e0f8-1e9e-4034-bc2f-25153ec6bcc1")
        val wearService = PhoneWearService(this)

        controller = NavigationController(graphhopper, wearService)

        mapView.getMapAsync { map ->
            map.setStyle(
                Style.Builder().fromUri(
                    "https://tiles.openfreemap.org/styles/liberty"
                )
            ) { style ->
                scope.launch {
                    try {
                        val route = controller.startNavigation(
                            52.3791, 4.8994,
                            52.3556, 4.9550
                        )
                        
                        drawRoute(style, route.points)

                        val start = route.points.first()

                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(start.first, start.second),
                                15.0
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun drawRoute(
        style: Style,
        points: List<Pair<Double, Double>>
    ) {
        val line = LineString.fromLngLats(
            points.map {
                Point.fromLngLat(
                    it.second,
                    it.first
                )
            }
        )

        val source = GeoJsonSource("route-source")
        // Use toJson() to pass the data as a String.
        source.setGeoJson(line.toJson())

        style.addSource(source)

        val layer = LineLayer("route-layer", "route-source")
            .withProperties(
                lineColor("#ff0000"),
                lineWidth(6f)
            )

        style.addLayer(layer)
    }

    override fun onStart() { super.onStart(); if (::mapView.isInitialized) mapView.onStart() }
    override fun onResume() { super.onResume(); if (::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { super.onPause(); if (::mapView.isInitialized) mapView.onPause() }
    override fun onStop() { super.onStop(); if (::mapView.isInitialized) mapView.onStop() }
    override fun onDestroy() { 
        super.onDestroy()
        if (::mapView.isInitialized) mapView.onDestroy()
        scope.cancel()
    }
}