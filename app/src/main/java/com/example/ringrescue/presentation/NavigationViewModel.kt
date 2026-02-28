package com.example.ringrescue.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NavigationViewModel(
    private val wearableService: WearableNavigationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private val _navigationCue = MutableStateFlow<NavigationCue?>(null)
    val navigationCue: StateFlow<NavigationCue?> = _navigationCue.asStateFlow()

    init {
        viewModelScope.launch {
            wearableService.navigationCue.collect { cue ->
                _navigationCue.value = cue
                updateUiState()
                if (cue != null) {
                    wearableService.sendAcknowledgement()
                }
            }
        }

        viewModelScope.launch {
            wearableService.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }

        wearableService.initialize()
    }

    private fun updateUiState() {
        val cue = _navigationCue.value
        _uiState.update {
            it.copy(
                currentInstruction = cue?.instruction ?: "No active navigation",
                distanceToNextTurn = cue?.distanceToNextTurn ?: 0,
                nextStreet = cue?.nextStreet ?: "",
                maneuverType = cue?.maneuverType,
                estimatedTimeRemaining = cue?.estimatedTimeRemaining ?: 0,
                destinationName = cue?.destinationName ?: "",
                totalDistance = cue?.totalDistance ?: 0,
                bearing = cue?.bearing ?: 0f
            )
        }
    }

    fun requestRouteToDestination(destination: String) {
        viewModelScope.launch {
            wearableService.requestRouteToDestination(destination)
        }
    }

    public override fun onCleared() {
        super.onCleared()
        wearableService.cleanup()
    }
}

data class NavigationUiState(
    val currentInstruction: String = "No active navigation",
    val distanceToNextTurn: Int = 0,
    val nextStreet: String = "",
    val maneuverType: ManeuverType? = null,
    val estimatedTimeRemaining: Long = 0,
    val destinationName: String = "",
    val totalDistance: Int = 0,
    val bearing: Float = 0f,
    val connectionStatus: WearableNavigationService.ConnectionStatus = WearableNavigationService.ConnectionStatus.DISCONNECTED
)