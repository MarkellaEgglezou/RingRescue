package com.example.ringrescue

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GraphhopperService(private val apiKey: String) {

    private val client = OkHttpClient()

    /**
     * Fetches multiple routes using different bike profiles.
     */
    suspend fun getRoutes(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        maxPaths: Int = 3
    ): List<NavigationRoute> = withContext(Dispatchers.IO) {

        val profiles = listOf("bike", "mtb", "racingbike").take(maxPaths)
        
        val deferredRoutes = profiles.map { profile ->
            async {
                try {
                    // Try to include details for OSM ID
                    fetchSingleRoute(startLat, startLon, endLat, endLon, profile, includeDetails = true)
                } catch (e: Exception) {
                    Log.e("GraphhopperService", "Failed to fetch route with details for $profile: ${e.message}")
                    try {
                        // Fallback without details if OSM ID is the problem
                        fetchSingleRoute(startLat, startLon, endLat, endLon, profile, includeDetails = false)
                    } catch (e2: Exception) {
                        Log.e("GraphhopperService", "Failed fallback for $profile: ${e2.message}")
                        null
                    }
                }
            }
        }

        val results = deferredRoutes.awaitAll().filterNotNull()
        
        if (results.isEmpty()) {
            try {
                listOf(fetchSingleRoute(startLat, startLon, endLat, endLon, "bike", includeDetails = false))
            } catch (e: Exception) {
                Log.e("GraphhopperService", "Final fallback failed: ${e.message}")
                throw Exception("No routes found: ${e.message}")
            }
        } else {
            results
        }
    }

    private fun fetchSingleRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        profile: String,
        includeDetails: Boolean
    ): NavigationRoute {
        var url = "https://graphhopper.com/api/1/route?" +
                "point=$startLat,$startLon&" +
                "point=$endLat,$endLon&" +
                "profile=$profile&instructions=true&points_encoded=false&key=$apiKey"
        
        if (includeDetails) {
            url += "&details=osm_id"
        }

        Log.d("GraphhopperService", "Requesting route ($profile): $url")

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val message = try {
                val json = JSONObject(responseBody)
                if (json.has("message")) json.getString("message") else "API Error ${response.code}"
            } catch (e: Exception) {
                "HTTP ${response.code}"
            }
            throw Exception(message)
        }

        val json = JSONObject(responseBody)
        val path = json.getJSONArray("paths").getJSONObject(0)
        
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
                    pointIndex = ins.getJSONArray("interval").getInt(1)
                )
            )
        }

        val coordinates = path.getJSONObject("points").getJSONArray("coordinates")
        val routePoints = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until coordinates.length()) {
            val coord = coordinates.getJSONArray(i)
            routePoints.add(Pair(coord.getDouble(1), coord.getDouble(0)))
        }

        val segments = mutableListOf<RouteSegment>()
        if (path.has("details") && path.getJSONObject("details").has("osm_id")) {
            val osmIds = path.getJSONObject("details").getJSONArray("osm_id")
            for (i in 0 until osmIds.length()) {
                val detail = osmIds.getJSONArray(i)
                val osmId = detail.optLong(2)
                if (osmId != 0L) {
                    segments.add(RouteSegment(osmId, 1.0))
                }
            }
        }

        return NavigationRoute(cues, routePoints, segments)
    }

    suspend fun getRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): NavigationRoute {
        return getRoutes(startLat, startLon, endLat, endLon, 1).first()
    }
}