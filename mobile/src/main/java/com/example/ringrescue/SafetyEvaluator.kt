package com.example.ringrescue

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

class SafetyEvaluator(context: Context) {

    // Map of Street Name to Combined Safety Score
    private val streetSafetyMap = mutableMapOf<String, Double>()

    init {
        loadData(context)
    }

    private fun loadData(context: Context) {
        try {
            // 1. Load OSM Way ID to Safety Score mapping
            val idToScore = mutableMapOf<Long, Double>()
            
            // Prefer the updated file in internal storage
            val internalFile = File(context.filesDir, "street_safety_scores.csv")
            val inputStream: InputStream = if (internalFile.exists()) {
                internalFile.inputStream()
            } else {
                context.assets.open("street_safety_scores.csv")
            }

            inputStream.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                reader.readLine() // skip header
                reader.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size >= 4) {
                        val osmWayId = parts[0].toLongOrNull()
                        val combinedScore = parts[3].toDoubleOrNull()
                        if (osmWayId != null && combinedScore != null) {
                            idToScore[osmWayId] = combinedScore
                        }
                    }
                }
            }

            // 2. Load OSM Way ID to Street Name mapping and join with scores
            context.assets.open("streets.csv").use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                reader.readLine() // skip header
                reader.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size >= 2) {
                        val osmWayId = parts[0].toLongOrNull()
                        val streetName = parts[1].trim()
                        
                        if (osmWayId != null && streetName.isNotEmpty() && streetName != "unknown") {
                            val score = idToScore[osmWayId]
                            if (score != null) {
                                streetSafetyMap[streetName.lowercase()] = score
                            }
                        }
                    }
                }
            }
            Log.d("SafetyEvaluator", "Loaded safety scores for ${streetSafetyMap.size} unique street names.")
        } catch (e: Exception) {
            Log.e("SafetyEvaluator", "Error loading safety data", e)
        }
    }

    fun calculateRouteSafety(streetNames: List<String>, distances: List<Int>): Double {
        if (streetNames.isEmpty()) return 1.0
        
        var totalWeightedScore = 0.0
        var totalDistance = 0.0
        
        for (i in streetNames.indices) {
            val name = streetNames[i].lowercase().trim()
            val dist = if (i < distances.size) distances[i].toDouble() else 100.0
            
            val score = if (name.isEmpty() || name == "unknown") 1.0 else streetSafetyMap[name] ?: 1.0
            
            totalWeightedScore += score * dist
            totalDistance += dist
        }
        
        return if (totalDistance > 0) totalWeightedScore / totalDistance else 1.0
    }
}