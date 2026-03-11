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
    private var closestPointIndex = 0

    private var routePoints: List<Pair<Double, Double>> = emptyList()
    private var destinationLat = 0.0
    private var destinationLon = 0.0

    private var isRerouting = false
    private val OFF_COURSE_THRESHOLD_METERS = 50.0

    private val _route = MutableStateFlow<NavigationRoute?>(null)
    val route: StateFlow<NavigationRoute?> = _route.asStateFlow()

    private var lastSentCue: NavigationCue? = null

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
        closestPointIndex = 0

        _route.value = navigationRoute

        if (cues.isNotEmpty()) {
            sendCueToWear(cues[0])
        }

        return navigationRoute
    }

    fun stopNavigation() {
        cues = emptyList()
        currentCueIndex = 0
        routePoints = emptyList()
        closestPointIndex = 0
        _route.value = null
        lastSentCue = null
        scope.launch {
            wearService.sendNavigationEnded()
        }
    }

    fun updateLocation(location: Location) {
        if (routePoints.isEmpty() || isRerouting || currentCueIndex >= cues.size) return

        // Find closest point on route to track progress
        var minDist = Double.MAX_VALUE
        var bestIndex = closestPointIndex
        
        val searchStart = max(0, closestPointIndex - 5)
        val searchEnd = min(routePoints.size, closestPointIndex + 20)
        
        for (i in searchStart until searchEnd) {
            val d = distance(location.latitude, location.longitude, routePoints[i].first, routePoints[i].second)
            if (d < minDist) {
                minDist = d
                bestIndex = i
            }
        }
        closestPointIndex = bestIndex

        // Check if the user is off-course
        if (minDist > OFF_COURSE_THRESHOLD_METERS) {
            Log.d("NavigationController", "Off-course detected (dist: $minDist). Triggering reroute.")
            triggerReroute(location)
            return
        }

        // Advance to next cue if we reached the maneuver point index
        val currentCue = getCurrentCue()
        if (currentCue != null && closestPointIndex >= currentCue.pointIndex) {
            advanceToNextCue()
        }
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
    
    fun advanceToNextCue() {
        if (currentCueIndex < cues.size - 1) {
            currentCueIndex++
            sendCueToWear(cues[currentCueIndex])
        }
    }

    fun sendCueToWear(cue: NavigationCue, currentDist: Int? = null) {
        val cueToSend = if (currentDist != null) {
            cue.copy(distanceToNextTurn = currentDist)
        } else {
            cue
        }
        
        // We only want to log/send if it's a significant change to avoid flooding
        // But for the watch to match exactly, we send updates. 
        // We can add a simple check to not send the exact same cue+dist twice.
        
        scope.launch {
            wearService.sendCue(cueToSend)
        }
    }

    fun distanceToDestination(location: Location): Double {
        return distance(
            location.latitude,
            location.longitude,
            destinationLat,
            destinationLon
        )
    }

    fun getNextManeuverPoint(): Pair<Double, Double>? {
        val cue = getCurrentCue() ?: return null
        return if (cue.pointIndex < routePoints.size) {
            routePoints[cue.pointIndex]
        } else {
            routePoints.last()
        }
    }

    fun distance(
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
