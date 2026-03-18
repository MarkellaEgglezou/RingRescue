package com.example.ringrescue

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var controller: NavigationController
    private lateinit var locationService: LocationService
    private lateinit var wearService: PhoneWearService
    private lateinit var feedbackManager: FeedbackManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val useMockLocation = true
    private var mockLat = 52.361448
    private var mockLon = 4.924817
    private var mockMovementJob: Job? = null

    private var userMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var hasArrived = false
    private var isNavigating = false

    private lateinit var instructionText: TextView
    private lateinit var distanceText: TextView
    private lateinit var streetText: TextView
    private lateinit var navigationPanel: View
    private lateinit var destLatInput: EditText
    private lateinit var destLonInput: EditText
    private lateinit var btnStartNav: Button
    private lateinit var btnSOS: Button
    
    private lateinit var footerNavigation: View
    private lateinit var footerDeviceInfo: View
    private lateinit var footerTrustedContacts: View

    private lateinit var sosManager: SosManager

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
        btnSOS = findViewById(R.id.btnSOS)

        footerNavigation = findViewById(R.id.footer_navigation)
        footerDeviceInfo = findViewById(R.id.footer_device_info)
        footerTrustedContacts = findViewById(R.id.footer_trusted_contacts)

        locationService = LocationService(this)
        sosManager = SosManager(this)
        feedbackManager = FeedbackManager(this)

        val graphhopper = GraphhopperService("e8d1e0f8-1e9e-4034-bc2f-25153ec6bcc1")
        wearService = PhoneWearService(this)
        val safetyEvaluator = SafetyEvaluator(this)
        controller = NavigationController(graphhopper, wearService, safetyEvaluator)

        setupFooter()

        btnSOS.setOnClickListener {
            sosManager.sendSos(this)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                observeRoute(style)
                startLocationUpdates(map)

                btnStartNav.setOnClickListener {
                    if (isNavigating) {
                        stopNavigation()
                    } else {
                        startNavigation(map)
                    }
                }
            }
        }
    }

    private fun startNavigation(map: MapLibreMap) {
        val lat = destLatInput.text.toString().toDoubleOrNull() ?: 52.3732
        val lon = destLonInput.text.toString().toDoubleOrNull() ?: 4.8924
        
        btnStartNav.isEnabled = false
        btnStartNav.text = "Loading..."

        scope.launch {
            try {
                val startLat: Double
                val startLon: Double

                if (useMockLocation) {
                    startLat = mockLat
                    startLon = mockLon
                } else {
                    var loc = locationService.location.value
                    withTimeoutOrNull(5000) {
                        while (loc == null) {
                            delay(500)
                            loc = locationService.location.value
                        }
                    }
                    startLat = loc?.latitude ?: mockLat
                    startLon = loc?.longitude ?: mockLon
                }

                val route = controller.startNavigation(startLat, startLon, lat, lon)
                
                isNavigating = true
                NavigationRepository.setNavigating(true)
                hasArrived = false
                
                val destLatLng = LatLng(lat, lon)
                if (destinationMarker == null) {
                    destinationMarker = map.addMarker(MarkerOptions().position(destLatLng).title("Destination"))
                } else {
                    destinationMarker?.position = destLatLng
                }

                navigationPanel.visibility = View.VISIBLE
                btnStartNav.text = "Stop"
                btnStartNav.isEnabled = true
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(startLat, startLon), 15.0))

                Toast.makeText(this@MainActivity, "Safest route calculated!", Toast.LENGTH_SHORT).show()

                if (useMockLocation) {
                    startMockMovement(route)
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Navigation Error", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                btnStartNav.isEnabled = true
                btnStartNav.text = "Start"
            }
        }
    }

    private fun stopNavigation() {
        isNavigating = false
        mockMovementJob?.cancel()
        controller.stopNavigation()
        NavigationRepository.setNavigating(false)
        navigationPanel.visibility = View.GONE
        btnStartNav.text = "Start"
        destinationMarker?.let { it.remove() }
        destinationMarker = null
        
        // Remove route from map
        mapView.getMapAsync { map ->
            map.getStyle { style ->
                style.removeLayer("route-layer")
                style.removeSource("route-source")
            }
        }
    }

    private fun startMockMovement(route: NavigationRoute) {
        mockMovementJob?.cancel()
        mockMovementJob = scope.launch {
            var currentIndex = 0
            while (isActive && currentIndex < route.points.size && !hasArrived) {
                val point = route.points[currentIndex]
                mockLat = point.first
                mockLon = point.second
                
                delay(1000) // Move every second
                currentIndex++
            }
        }
    }

    private fun setupFooter() {
        findViewById<ImageView>(R.id.footer_navigation_icon).setColorFilter(getColor(R.color.primary_red))
        footerNavigation.setOnClickListener { }
        footerDeviceInfo.setOnClickListener { showDeviceInfo() }
        footerTrustedContacts.setOnClickListener {
            startActivity(Intent(this, TrustedContactsActivity::class.java))
        }
    }

    private fun showDeviceInfo() {
        scope.launch {
            val isConnected = wearService.isWatchConnected()
            val batteryLevel = wearService.getWatchBatteryLevel()
            val message = if (isConnected) "Watch is connected.\nBattery Level: ${batteryLevel ?: "Unknown"}%" else "Watch is disconnected."
            AlertDialog.Builder(this@MainActivity).setTitle("Device Information").setMessage(message).setPositiveButton("OK", null).show()
        }
    }

    private fun observeRoute(style: Style) {
        scope.launch {
            controller.route.collect { route ->
                if (route != null) {
                    drawRoute(style, route.points)
                }
            }
        }
    }

    private fun startLocationUpdates(map: MapLibreMap) {
        locationService.start()
        scope.launch {
            locationService.location.collect { location ->
                val activeLocation: Location

                if (useMockLocation) {
                    activeLocation = Location("mock").apply {
                        latitude = mockLat
                        longitude = mockLon
                        time = System.currentTimeMillis()
                        accuracy = 1.0f
                    }
                } else {
                    if (location == null) return@collect
                    activeLocation = location
                }

                val latLng = LatLng(activeLocation.latitude, activeLocation.longitude)
                map.easeCamera(CameraUpdateFactory.newLatLng(latLng))

                if (userMarker == null) {
                    userMarker = map.addMarker(MarkerOptions().position(latLng).title("You"))
                } else {
                    userMarker!!.position = latLng
                }

                if (isNavigating) {
                    controller.updateLocation(activeLocation)

                    if (!hasArrived && destinationMarker != null) {
                        val dist = controller.distanceToDestination(activeLocation)
                        if (dist < 30.0) {
                            hasArrived = true
                            showArrivalDialog()
                        }
                    }

                    val route = controller.route.value
                    val cue = controller.getCurrentCue()
                    if (cue != null && route != null) {
                        val nextPoint = controller.getNextManeuverPoint()
                        if (nextPoint != null) {
                            val distToTurn = controller.distance(activeLocation.latitude, activeLocation.longitude, nextPoint.first, nextPoint.second)
                            
                            instructionText.text = cue.instruction
                            distanceText.text = "${distToTurn.toInt()} m"
                            streetText.text = cue.nextStreet
                            
                            val updatedCue = cue.copy(distanceToNextTurn = distToTurn.toInt())
                            NavigationRepository.updateCue(updatedCue)
                            controller.sendCueToWear(cue, distToTurn.toInt())
                        }
                    }
                }
            }
        }
    }

    private fun showArrivalDialog() {
        val route = controller.route.value
        val osmWayIds = route?.segments?.map { it.osmWayId } ?: emptyList()
        val streetNames = route?.cues?.map { it.nextStreet }?.filter { it.isNotEmpty() } ?: emptyList()
        
        stopNavigation()

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_arrival_feedback, null)
        val ratingLighting = view.findViewById<RatingBar>(R.id.ratingLighting)
        val ratingSafety = view.findViewById<RatingBar>(R.id.ratingSafety)

        AlertDialog.Builder(this)
            .setTitle("You have reached your destination!")
            .setView(view)
            .setPositiveButton("Submit") { _, _ ->
                val lighting = ratingLighting.rating
                val safety = ratingSafety.rating
                feedbackManager.saveFeedback(osmWayIds, streetNames, lighting, safety)
                Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun drawRoute(style: Style, points: List<Pair<Double, Double>>) {
        val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.second, it.first) })
        val sourceId = "route-source"
        val layerId = "route-layer"
        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source != null) {
            source.setGeoJson(line.toJson())
        } else {
            val newSource = GeoJsonSource(sourceId, line.toJson())
            style.addSource(newSource)
            val layer = LineLayer(layerId, sourceId).withProperties(lineColor(getColor(R.color.primary_red)), lineWidth(6f))
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