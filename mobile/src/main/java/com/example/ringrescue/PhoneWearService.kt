package com.example.ringrescue

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class PhoneWearService(private val context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun sendNavigationStarted() {
        Log.d("PhoneWearService", "Sending /navigation_started")
        send("/navigation_started", byteArrayOf())
    }

    suspend fun sendNavigationEnded() {
        Log.d("PhoneWearService", "Sending /navigation_ended")
        send("/navigation_ended", byteArrayOf())
    }

    suspend fun sendCue(cue: NavigationCue) {
        val json = cue.toJson()
        Log.d("PhoneWearService", "Sending cue: $json")
        send("/navigation_cue", json.toByteArray())
    }

    suspend fun isWatchConnected(): Boolean {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            Log.d("PhoneWearService", "Connected nodes: ${nodes.size}")
            nodes.isNotEmpty()
        } catch (e: Exception) {
            Log.e("PhoneWearService", "Error checking nodes", e)
            false
        }
    }

    suspend fun getWatchBatteryLevel(): Int? {
        if (!isWatchConnected()) return null
        return 85
    }

    private suspend fun send(path: String, data: ByteArray) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w("PhoneWearService", "No connected nodes found for path: $path")
            }
            for (node in nodes) {
                Log.d("PhoneWearService", "Sending $path to node: ${node.displayName} (${node.id})")
                messageClient.sendMessage(node.id, path, data).await()
            }
        } catch (e: ApiException) {
            Log.w("PhoneWearService", "Wearable API error: ${e.message}")
        } catch (e: Exception) {
            Log.e("PhoneWearService", "Error sending wear message", e)
        }
    }
}