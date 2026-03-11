package com.example.ringrescue

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GraphhopperService(private val apiKey: String) {

    private val client = OkHttpClient()

    suspend fun getRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): NavigationRoute = withContext(Dispatchers.IO) {

        val url =
            "https://graphhopper.com/api/1/route?" +
                    "point=$startLat,$startLon&" +
                    "point=$endLat,$endLon&" +
                    "profile=bike&instructions=true&points_encoded=false&key=$apiKey"

        Log.d("GraphhopperService", "Requesting route: $url")

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e("GraphhopperService", "Error response: $responseBody")
            val errorJson = JSONObject(responseBody)
            val message = if (errorJson.has("message")) {
                errorJson.getString("message")
            } else if (errorJson.has("hints")) {
                errorJson.getJSONArray("hints").getJSONObject(0).getString("message")
            } else {
                "Unknown API error"
            }
            throw Exception(message)
        }

        val json = JSONObject(responseBody)

        if (!json.has("paths")) {
            Log.e("GraphhopperService", "No 'paths' found in JSON: $responseBody")
            throw Exception("No route found between these points.")
        }

        val paths = json.getJSONArray("paths")
        val path = paths.getJSONObject(0)

        val instructions = path.getJSONArray("instructions")

        val cues = mutableListOf<NavigationCue>()

        for (i in 0 until instructions.length()) {

            val ins = instructions.getJSONObject(i)

            val maneuver = when (ins.getInt("sign")) {
                -3 -> ManeuverType.TURN_SHARP_LEFT
                -2 -> ManeuverType.TURN_LEFT
                -1 -> ManeuverType.TURN_SLIGHT_LEFT
                0 -> ManeuverType.STRAIGHT
                1 -> ManeuverType.TURN_SLIGHT_RIGHT
                2 -> ManeuverType.TURN_RIGHT
                3 -> ManeuverType.TURN_SHARP_RIGHT
                4 -> ManeuverType.ARRIVED
                6 -> ManeuverType.ROUNDABOUT_ENTER
                else -> ManeuverType.STRAIGHT
            }

            val interval = ins.getJSONArray("interval")
            val endPointIndex = interval.getInt(1)

            cues.add(
                NavigationCue(
                    instruction = ins.getString("text"),
                    distanceToNextTurn = ins.getDouble("distance").toInt(),
                    nextStreet = ins.optString("street_name", ""),
                    maneuverType = maneuver,
                    estimatedTimeRemaining = ins.getLong("time") / 1000,
                    destinationName = "Destination",
                    totalDistance = path.getDouble("distance").toInt(),
                    bearing = ins.optDouble("heading", 0.0).toFloat(),
                    pointIndex = endPointIndex
                )
            )
        }

        val coordinates =
            path.getJSONObject("points")
                .getJSONArray("coordinates")

        val routePoints = mutableListOf<Pair<Double, Double>>()

        for (i in 0 until coordinates.length()) {

            val coord = coordinates.getJSONArray(i)

            val lon = coord.getDouble(0)
            val lat = coord.getDouble(1)

            routePoints.add(Pair(lat, lon))
        }

        NavigationRoute(cues, routePoints)
    }
}
