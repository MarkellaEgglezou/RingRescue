package com.example.ringrescue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NavigationRepository {
    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating

    private val _currentCue = MutableStateFlow<NavigationCue?>(null)
    val currentCue: StateFlow<NavigationCue?> = _currentCue

    fun setNavigating(navigating: Boolean) {
        _isNavigating.value = navigating
        if (!navigating) _currentCue.value = null
    }

    fun updateCue(cue: NavigationCue) {
        _currentCue.value = cue
        _isNavigating.value = true
    }
}