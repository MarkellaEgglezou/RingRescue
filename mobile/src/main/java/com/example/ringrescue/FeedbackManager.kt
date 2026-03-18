package com.example.ringrescue

import android.content.Context
import android.util.Log
import java.io.*
import java.util.*

class FeedbackManager(private val context: Context) {

    private val observationsFile = File(context.filesDir, "user_observations.csv")
    private val scoresFile = File(context.filesDir, "street_safety_scores.csv")
    
    // Map of street name (lowercase) to set of OSM Way IDs
    private val streetNameToIds = mutableMapOf<String, MutableSet<Long>>()

    init {
        ensureFilesExist()
        loadStreetMappings()
    }

    private fun ensureFilesExist() {
        if (!observationsFile.exists()) {
            copyAssetToFile("user_observations.csv", observationsFile)
        }
        if (!scoresFile.exists()) {
            copyAssetToFile("street_safety_scores.csv", scoresFile)
        }
    }

    private fun copyAssetToFile(assetName: String, outFile: File) {
        try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("FeedbackManager", "Copied $assetName to internal storage")
        } catch (e: Exception) {
            Log.e("FeedbackManager", "Error copying asset $assetName", e)
        }
    }

    private fun loadStreetMappings() {
        try {
            context.assets.open("streets.csv").use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                reader.readLine() // Skip header
                reader.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size >= 2) {
                        val id = parts[0].toLongOrNull()
                        val name = parts[1].trim().lowercase()
                        if (id != null && name.isNotEmpty() && name != "unknown") {
                            streetNameToIds.getOrPut(name) { mutableSetOf() }.add(id)
                        }
                    }
                }
            }
            Log.d("FeedbackManager", "Loaded ${streetNameToIds.size} street name mappings")
        } catch (e: Exception) {
            Log.e("FeedbackManager", "Error loading street mappings", e)
        }
    }

    fun saveFeedback(osmWayIds: List<Long>, streetNames: List<String>, lightingRating: Float, safetyRating: Float) {
        val resolvedIds = osmWayIds.toMutableSet()
        
        // If no IDs from API, resolve from names
        if (resolvedIds.isEmpty()) {
            for (name in streetNames) {
                val ids = streetNameToIds[name.trim().lowercase()]
                if (ids != null) {
                    resolvedIds.addAll(ids)
                }
            }
        }

        if (resolvedIds.isEmpty()) {
            Log.w("FeedbackManager", "No OSM Way IDs found to associate feedback with.")
            return
        }

        val userId = "U" + Random().nextInt(1000000)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeOfDay = if (hour in 10..17) "day" else "night"

        Log.d("FeedbackManager", "Saving feedback for ${resolvedIds.size} ways. Time: $timeOfDay, Lighting: $lightingRating, Safety: $safetyRating")

        // 1. Append to user_observations.csv
        try {
            val writer = BufferedWriter(FileWriter(observationsFile, true))
            for (id in resolvedIds) {
                writer.write("$userId,$id,$timeOfDay,$lightingRating,$safetyRating")
                writer.newLine()
            }
            writer.close()
            Log.d("FeedbackManager", "Successfully appended to user_observations.csv")
        } catch (e: Exception) {
            Log.e("FeedbackManager", "Error appending to observations", e)
        }

        // 2. Update street_safety_scores.csv
        updateSafetyScores(resolvedIds.toList())
    }

    private fun updateSafetyScores(osmWayIds: List<Long>) {
        val allObservations = loadAllObservations()
        val currentScores = loadCurrentScores()

        for (id in osmWayIds) {
            val observationsForId = allObservations.filter { it.osmWayId == id }
            if (observationsForId.isNotEmpty()) {
                val avgLighting = observationsForId.map { it.lighting }.average()
                val avgSafety = observationsForId.map { it.safety }.average()
                // Formula: 40% lighting, 60% safety, normalized to 0.0-1.0
                val combinedScore = (avgLighting / 5.0 * 0.4) + (avgSafety / 5.0 * 0.6)
                
                currentScores[id] = ScoreEntry(avgLighting, avgSafety, combinedScore)
            }
        }

        saveScores(currentScores)
    }

    private fun loadAllObservations(): List<ObservationEntry> {
        val list = mutableListOf<ObservationEntry>()
        try {
            if (!observationsFile.exists()) return emptyList()
            observationsFile.bufferedReader().use { reader ->
                reader.readLine() // Skip header
                reader.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size >= 5) {
                        val id = parts[1].toLongOrNull()
                        val lighting = parts[3].toDoubleOrNull()
                        val safety = parts[4].toDoubleOrNull()
                        if (id != null && lighting != null && safety != null) {
                            list.add(ObservationEntry(id, lighting, safety))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FeedbackManager", "Error loading observations", e)
        }
        return list
    }

    private fun loadCurrentScores(): MutableMap<Long, ScoreEntry> {
        val map = mutableMapOf<Long, ScoreEntry>()
        try {
            if (!scoresFile.exists()) return map
            scoresFile.bufferedReader().use { reader ->
                reader.readLine() // Skip header
                reader.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size >= 4) {
                        val id = parts[0].toLongOrNull()
                        val lighting = parts[1].toDoubleOrNull()
                        val safety = parts[2].toDoubleOrNull()
                        val combined = parts[3].toDoubleOrNull()
                        if (id != null && lighting != null && safety != null && combined != null) {
                            map[id] = ScoreEntry(lighting, safety, combined)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FeedbackManager", "Error loading scores", e)
        }
        return map
    }

    private fun saveScores(scores: Map<Long, ScoreEntry>) {
        try {
            BufferedWriter(FileWriter(scoresFile)).use { writer ->
                writer.write("osm_way_id,lighting_rating,safety_rating,combined_score")
                writer.newLine()
                for ((id, entry) in scores) {
                    writer.write("$id,${entry.lighting},${entry.safety},${entry.combined}")
                    writer.newLine()
                }
            }
            Log.d("FeedbackManager", "Successfully updated street_safety_scores.csv")
        } catch (e: Exception) {
            Log.e("FeedbackManager", "Error saving scores", e)
        }
    }

    data class ObservationEntry(val osmWayId: Long, val lighting: Double, val safety: Double)
    data class ScoreEntry(val lighting: Double, val safety: Double, val combined: Double)
}