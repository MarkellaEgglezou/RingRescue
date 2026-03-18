package com.example.ringrescue

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class PhoneWearService(private val context: Context) {

    companion object {
        private const val WEAR_CAPABILITY = "ring_rescue_wear_app"
    }

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    
    private var batteryLevelDeferred: CompletableDeferred<Int?>? = null

    private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
        if (messageEvent.path == "/battery_level") {
            val batteryLevel = String(messageEvent.data).toIntOrNull()
            Log.d("PhoneWearService", "Received battery level: $batteryLevel")
            batteryLevelDeferred?.complete(batteryLevel)
        }
    }

    init {
        messageClient.addListener(messageListener)
    }

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
            val capabilityInfo = capabilityClient
                .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
            val nodes = capabilityInfo.nodes
            Log.d("PhoneWearService", "Nodes with capability '$WEAR_CAPABILITY': ${nodes.size}")
            nodes.isNotEmpty()
        } catch (e: Exception) {
            Log.e("PhoneWearService", "Error checking nodes with capability", e)
            false
        }
    }

    suspend fun getWatchBatteryLevel(): Int? {
        if (!isWatchConnected()) return null
        
        batteryLevelDeferred = CompletableDeferred()
        
        try {
            send("/request_battery", byteArrayOf())
            // Wait for up to 5 seconds for a response
            return withTimeoutOrNull(5000) {
                batteryLevelDeferred?.await()
            }
        } catch (e: Exception) {
            Log.e("PhoneWearService", "Error getting battery level", e)
            return null
        } finally {
            batteryLevelDeferred = null
        }
    }

    private suspend fun send(path: String, data: ByteArray) {
        try {
            val capabilityInfo = capabilityClient
                .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
            val nodes = capabilityInfo.nodes
            
            if (nodes.isEmpty()) {
                Log.w("PhoneWearService", "No nodes with capability '$WEAR_CAPABILITY' found for path: $path")
                return
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
