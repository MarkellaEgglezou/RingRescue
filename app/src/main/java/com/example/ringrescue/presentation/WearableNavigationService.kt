package com.example.ringrescue.presentation

import android.content.Context
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class WearableNavigationService(private val context: Context,
                                private val simulate: Boolean = false) {
    private val _navigationCue = MutableStateFlow<NavigationCue?>(null)
    val navigationCue: StateFlow<NavigationCue?> = _navigationCue.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private var messageClient: MessageClient? = null
    private var nodeClient: NodeClient? = null
    private val coroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
        when (messageEvent.path) {
            "/navigation_cue" -> {
                try {
                    val cueJson = String(messageEvent.data)
                    val cue = NavigationCue.fromJson(cueJson)
                    _navigationCue.value = cue
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            "/navigation_started" -> {
                _connectionStatus.value = ConnectionStatus.NAVIGATING
            }
            "/navigation_ended" -> {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                _navigationCue.value = null
            }
        }
    }

    fun initialize() {
        if (simulate) {
            simulateNavigation()
            return
        }

        coroutineScope.launch {
            try {
                messageClient = Wearable.getMessageClient(context)
                nodeClient = Wearable.getNodeClient(context)
                messageClient?.removeListener(messageListener)
                messageClient?.addListener(messageListener)

                // Check for connected nodes
                val nodes = nodeClient?.connectedNodes?.await()
                _connectionStatus.value = if (nodes?.isNotEmpty() == true) {
                    ConnectionStatus.CONNECTED
                } else {
                    ConnectionStatus.DISCONNECTED
                }

                // Request current navigation state
                sendMessage("/request_navigation_state", byteArrayOf())
            } catch (e: Exception) {
                e.printStackTrace()
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun simulateNavigation() {
        coroutineScope.launch {
            _connectionStatus.value = ConnectionStatus.NAVIGATING

            delay(2000)

            _navigationCue.value = NavigationCue(
                instruction = "Turn right",
                distanceToNextTurn = 120,
                nextStreet = "Main Street",
                maneuverType = ManeuverType.TURN_RIGHT,
                estimatedTimeRemaining = 600,
                destinationName = "University",
                totalDistance = 2500,
                bearing = 90f
            )

            delay(5000)

            _navigationCue.value = NavigationCue(
                instruction = "Continue straight",
                distanceToNextTurn = 500,
                nextStreet = "Broadway",
                maneuverType = ManeuverType.STRAIGHT,
                estimatedTimeRemaining = 480,
                destinationName = "University",
                totalDistance = 2000,
                bearing = 0f
            )
        }
    }

    fun sendAcknowledgement() {
        coroutineScope.launch {
            sendMessage("/navigation_ack", byteArrayOf())
        }
    }

    fun requestRouteToDestination(destination: String) {
        coroutineScope.launch {
            sendMessage("/request_route", destination.toByteArray())
        }
    }

    private suspend fun sendMessage(path: String, data: ByteArray) {
        try {
            val nodes = nodeClient?.connectedNodes?.await()
            nodes?.forEach { node ->
                messageClient?.sendMessage(node.id, path, data)?.await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cleanup() {
        messageClient?.removeListener(messageListener)
        coroutineScope.cancel()
    }

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTED,
        NAVIGATING
    }
}