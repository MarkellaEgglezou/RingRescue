package com.example.ringrescue

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MobileWearableService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sosManager: SosManager

    override fun onCreate() {
        super.onCreate()
        Log.d("MobileWearableService", "Service created")
        sosManager = SosManager(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("MobileWearableService", "Message path: ${messageEvent.path}")
        when (messageEvent.path) {
            "/sos_signal" -> {
                Log.d("MobileWearableService", "SOS signal received from watch")
                scope.launch {
                    sosManager.sendSos()
                }
            }
            "/request_navigation_state" -> {
                Log.d("MobileWearableService", "Watch requested navigation state")
                scope.launch {
                    val cue = NavigationRepository.currentCue.value
                    if (cue != null) {
                        val wearService = PhoneWearService(this@MobileWearableService)
                        wearService.sendNavigationStarted()
                        wearService.sendCue(cue)
                    }
                }
            }
        }
    }
}