package com.example.ringrescue.presentation

import org.json.JSONObject

data class NavigationCue(
    val instruction: String,
    val distanceToNextTurn: Int, // in meters
    val nextStreet: String,
    val maneuverType: ManeuverType,
    val estimatedTimeRemaining: Long, // in seconds
    val destinationName: String,
    val totalDistance: Int, // in meters
    val bearing: Float // direction in degrees (0-360)
) {
    companion object {
        fun fromJson(json: String): NavigationCue? {
            return try {
                val obj = JSONObject(json)
                NavigationCue(
                    instruction = obj.getString("instruction"),
                    distanceToNextTurn = obj.getInt("distanceToNextTurn"),
                    nextStreet = obj.getString("nextStreet"),
                    maneuverType = ManeuverType.valueOf(obj.getString("maneuverType")),
                    estimatedTimeRemaining = obj.getLong("estimatedTimeRemaining"),
                    destinationName = obj.getString("destinationName"),
                    totalDistance = obj.getInt("totalDistance"),
                    bearing = obj.getDouble("bearing").toFloat()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

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