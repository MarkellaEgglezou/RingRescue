package com.example.ringrescue

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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
    private lateinit var locationService: LocationService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val useMockLocation = true
    private val mockLat = 52.379214
    private val mockLon = 4.897982

    private var userMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var hasArrived = false

    private lateinit var instructionText: TextView
    private lateinit var distanceText: TextView
    private lateinit var streetText: TextView
    private lateinit var navigationPanel: LinearLayout
    private lateinit var destLatInput: EditText
    private lateinit var destLonInput: EditText
    private lateinit var btnStartNav: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)

        instructionText = findViewById(R.id.instructionText)
        distanceText = findViewById(R.id.distanceText)
        streetText = findViewById(R.id.streetText)
        navigationPanel = findViewById(R.id.navigationPanel)
        destLatInput = findViewById(R.id.destLat)
        destLonInput = findViewById(R.id.destLon)
        btnStartNav = findViewById(R.id.btnStartNav)

        locationService = LocationService(this)

        val graphhopper = GraphhopperService("e8d1e0f8-1e9e-4034-bc2f-25153ec6bcc1")
        val wearService = PhoneWearService(this)

        controller = NavigationController(graphhopper, wearService)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }

        mapView.getMapAsync { map ->
            map.setStyle(
                Style.Builder().fromUri(
                    "https://tiles.openfreemap.org/styles/liberty"
                )
            ) { style ->
                observeRoute(style)
                startLocationUpdates(map)

                btnStartNav.setOnClickListener {
                    val lat = destLatInput.text.toString().toDoubleOrNull() ?: 0.0
                    val lon = destLonInput.text.toString().toDoubleOrNull() ?: 0.0

                    scope.launch {
                        val startLat: Double
                        val startLon: Double

                        if (useMockLocation) {
                            startLat = mockLat
                            startLon = mockLon
                        } else {
                            var loc = locationService.location.value
                            while (loc == null) {
                                delay(500)
                                loc = locationService.location.value
                            }
                            startLat = loc.latitude
                            startLon = loc.longitude
                        }

                        controller.startNavigation(startLat, startLon, lat, lon)
                        
                        hasArrived = false
                        val destLatLng = LatLng(lat, lon)
                        if (destinationMarker == null) {
                            destinationMarker = map.addMarker(
                                MarkerOptions()
                                    .position(destLatLng)
                                    .title("Destination")
                            )
                        } else {
                            destinationMarker?.position = destLatLng
                        }

                        navigationPanel.visibility = View.VISIBLE
                        
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(startLat, startLon),
                                15.0
                            )
                        )
                    }
                }
            }
        }
    }

    private fun observeRoute(style: Style) {
        scope.launch {
            controller.route.collect { route ->
                route?.let {
                    drawRoute(style, it.points)
                }
            }
        }
    }

    private fun startLocationUpdates(map: MapLibreMap) {

        locationService.start()

        scope.launch {
            locationService.location.collect { location ->

                val currentLat: Double
                val currentLon: Double
                val activeLocation: Location

                if (useMockLocation) {
                    currentLat = mockLat
                    currentLon = mockLon
                    activeLocation = Location("mock").apply {
                        latitude = mockLat
                        longitude = mockLon
                        time = System.currentTimeMillis()
                        accuracy = 1.0f
                    }
                } else {
                    if (location == null) return@collect
                    currentLat = location.latitude
                    currentLon = location.longitude
                    activeLocation = location
                }

                val latLng = LatLng(currentLat, currentLon)

                map.easeCamera(
                    CameraUpdateFactory.newLatLng(latLng)
                )

                if (userMarker == null) {
                    userMarker = map.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("You")
                    )
                } else {
                    userMarker!!.position = latLng
                }

                controller.updateLocation(activeLocation)

                if (!hasArrived && destinationMarker != null) {
                    val dist = controller.distanceToDestination(activeLocation)
                    if (dist < 30.0) { // 30 meters threshold
                        hasArrived = true
                        showArrivalDialog()
                    }
                }

                val cue = controller.getCurrentCue()
                cue?.let {
                    instructionText.text = it.instruction
                    distanceText.text = "${it.distanceToNextTurn} m"
                    streetText.text = it.nextStreet
                }
            }
        }
    }

    private fun showArrivalDialog() {
        AlertDialog.Builder(this)
            .setTitle("Arrived")
            .setMessage("You have reached your destination!")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                navigationPanel.visibility = View.GONE
            }
            .show()
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

        val sourceId = "route-source"
        val layerId = "route-layer"

        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source != null) {
            source.setGeoJson(line.toJson())
        } else {
            val newSource = GeoJsonSource(sourceId, line.toJson())
            style.addSource(newSource)

            val layer = LineLayer(layerId, sourceId)
                .withProperties(
                    lineColor("#ff0000"),
                    lineWidth(6f)
                )
            style.addLayer(layer)
        }
    }

    override fun onStart() { super.onStart(); if (::mapView.isInitialized) mapView.onStart() }
    override fun onResume() { super.onResume(); if (::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { super.onPause(); if (::mapView.isInitialized) mapView.onPause() }
    override fun onStop() { super.onStop(); if (::mapView.isInitialized) mapView.onStop() }
    override fun onDestroy() { 
        super.onDestroy()
        if (::mapView.isInitialized) mapView.onDestroy()
        locationService.stop()
        scope.cancel()
    }
}
