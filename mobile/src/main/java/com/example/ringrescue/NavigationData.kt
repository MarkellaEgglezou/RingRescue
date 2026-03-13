package com.example.ringrescue

import org.json.JSONObject

data class NavigationCue(
    val instruction: String,
    val distanceToNextTurn: Int,
    val nextStreet: String,
    val maneuverType: ManeuverType,
    val estimatedTimeRemaining: Long,
    val destinationName: String,
    val totalDistance: Int,
    val bearing: Float,
    val pointIndex: Int = -1 // Index in routePoints where this cue ends
) {

    fun toJson(): String {
        val obj = JSONObject()

        obj.put("instruction", instruction)
        obj.put("distanceToNextTurn", distanceToNextTurn)
        obj.put("nextStreet", nextStreet)
        obj.put("maneuverType", maneuverType.name)
        obj.put("estimatedTimeRemaining", estimatedTimeRemaining)
        obj.put("destinationName", destinationName)
        obj.put("totalDistance", totalDistance)
        obj.put("bearing", bearing)

        return obj.toString()
    }
}

data class RouteSegment(
    val osmWayId: Long,
    val distance: Double
)

data class NavigationRoute(
    val cues: List<NavigationCue>,
    val points: List<Pair<Double, Double>>,
    val segments: List<RouteSegment> = emptyList(),
    var safetyScore: Double = 0.5
)

enum class ManeuverType {
    TURN_LEFT,
    TURN_RIGHT,
    STRAIGHT,
    ARRIVED,
    ROUNDABOUT_ENTER,
    ROUNDABOUT_EXIT,
    MERGE,
    TURN_SLIGHT_LEFT,
    TURN_SLIGHT_RIGHT,
    TURN_SHARP_LEFT,
    TURN_SHARP_RIGHT
}