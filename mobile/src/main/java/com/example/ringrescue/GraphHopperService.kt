package com.example.ringrescue

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

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        val json = JSONObject(response.body!!.string())

        val paths = json.getJSONArray("paths")
        val path = paths.getJSONObject(0)

        val instructions = path.getJSONArray("instructions")

        val cues = mutableListOf<NavigationCue>()

        for (i in 0 until instructions.length()) {

            val ins = instructions.getJSONObject(i)

            val maneuver = when (ins.getInt("sign")) {
                -2 -> ManeuverType.TURN_LEFT
                2 -> ManeuverType.TURN_RIGHT
                0 -> ManeuverType.STRAIGHT
                else -> ManeuverType.STRAIGHT
            }

            cues.add(
                NavigationCue(
                    instruction = ins.getString("text"),
                    distanceToNextTurn = ins.getDouble("distance").toInt(),
                    nextStreet = ins.optString("street_name", ""),
                    maneuverType = maneuver,
                    estimatedTimeRemaining = 0,
                    destinationName = "Destination",
                    totalDistance = path.getDouble("distance").toInt(),
                    bearing = 0f
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