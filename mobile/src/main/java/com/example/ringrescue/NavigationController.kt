package com.example.ringrescue

import kotlinx.coroutines.*

class NavigationController(
    private val graphhopperService: GraphhopperService,
    private val wearService: PhoneWearService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    suspend fun startNavigation(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): NavigationRoute {

        wearService.sendNavigationStarted()

        val route = graphhopperService.getRoute(
            startLat,
            startLon,
            endLat,
            endLon
        )

        scope.launch {

            for (cue in route.cues) {

                wearService.sendCue(cue)

                delay(8000)
            }

            wearService.sendNavigationEnded()
        }

        return route
    }
}