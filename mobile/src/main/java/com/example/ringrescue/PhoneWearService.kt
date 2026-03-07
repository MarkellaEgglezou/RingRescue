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
        send("/navigation_started", byteArrayOf())
    }

    suspend fun sendNavigationEnded() {
        send("/navigation_ended", byteArrayOf())
    }

    suspend fun sendCue(cue: NavigationCue) {
        send("/navigation_cue", cue.toJson().toByteArray())
    }

    private suspend fun send(path: String, data: ByteArray) {
        try {
            val nodes = nodeClient.connectedNodes.await()

            for (node in nodes) {
                messageClient.sendMessage(node.id, path, data).await()
            }
        } catch (e: ApiException) {
            Log.w("PhoneWearService", "Wearable API not available: ${e.message}")
        } catch (e: Exception) {
            Log.e("PhoneWearService", "Error sending wear message", e)
        }
    }
}