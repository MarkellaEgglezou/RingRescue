package com.example.ringrescue

import android.location.Location
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

class NavigationController(
    private val graphhopperService: GraphhopperService,
    private val wearService: PhoneWearService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cues: List<NavigationCue> = emptyList()
    private var currentCueIndex = 0

    private var routePoints: List<Pair<Double, Double>> = emptyList()
    private var destinationLat = 0.0
    private var destinationLon = 0.0

    private var isRerouting = false
    private val OFF_COURSE_THRESHOLD_METERS = 20.0

    private val _route = MutableStateFlow<NavigationRoute?>(null)
    val route: StateFlow<NavigationRoute?> = _route.asStateFlow()

    suspend fun startNavigation(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): NavigationRoute {

        wearService.sendNavigationStarted()

        val navigationRoute = graphhopperService.getRoute(
            startLat,
            startLon,
            endLat,
            endLon
        )

        routePoints = navigationRoute.points
        destinationLat = endLat
        destinationLon = endLon
        cues = navigationRoute.cues
        currentCueIndex = 0

        _route.value = navigationRoute

        if (cues.isNotEmpty()) {
            wearService.sendCue(cues[0])
        }

        return navigationRoute
    }

    fun updateLocation(location: Location) {
        if (routePoints.isEmpty() || isRerouting || currentCueIndex >= cues.size) return

        // 1. Check if the user is off-course
        val minDistance = routePoints.minOf { 
            distance(location.latitude, location.longitude, it.first, it.second)
        }

        if (minDistance > OFF_COURSE_THRESHOLD_METERS) {
            Log.d("NavigationController", "Off-course detected (dist: $minDistance). Triggering reroute.")
            triggerReroute(location)
            return
        }

        // 2. Advance to next cue logic could be added here
    }

    private fun triggerReroute(location: Location) {
        if (isRerouting) return
        isRerouting = true

        scope.launch {
            try {
                startNavigation(
                    location.latitude,
                    location.longitude,
                    destinationLat,
                    destinationLon
                )
                Log.d("NavigationController", "Reroute successful.")
            } catch (e: Exception) {
                Log.e("NavigationController", "Reroute failed", e)
            } finally {
                isRerouting = false
            }
        }
    }

    fun getCurrentCue(): NavigationCue? {
        if (currentCueIndex >= cues.size) return null
        return cues[currentCueIndex]
    }

    fun distanceToDestination(location: Location): Double {
        return distance(
            location.latitude,
            location.longitude,
            destinationLat,
            destinationLon
        )
    }

    private fun distance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {

        val R = 6371000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) *
                    cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) *
                    sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }
}
