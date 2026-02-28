package com.example.ringrescue.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.ringrescue.presentation.theme.RingRescueTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    // Create the ViewModel instance manually
    private lateinit var viewModel: NavigationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the wearable service
        val wearableService = WearableNavigationService(this, simulate = true)

        // Create ViewModel manually
        viewModel = NavigationViewModel(wearableService)

        setContent {
            RingRescueTheme {
                // Pass the ViewModel to your composable
                WearApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onCleared()
    }
}

// Composable that accepts ViewModel as parameter
@Composable
fun WearApp(viewModel: NavigationViewModel) {
    // Use the ViewModel directly
    val uiState by viewModel.uiState.collectAsState()

    AppScaffold {
        ScreenScaffold { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.connectionStatus ==
                    WearableNavigationService.ConnectionStatus.NAVIGATING
                ) {
                    NavigationScreen(uiState)
                } else {
                    Text("${uiState.connectionStatus}")
                }
            }
        }
    }
}

@Composable
fun NavigationScreen(uiState: NavigationUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Big Arrow
            ManeuverArrow(uiState.maneuverType)

            Spacer(modifier = Modifier.height(8.dp))

            // Distance
            Text(
                text = "${uiState.distanceToNextTurn} m",
                style = androidx.wear.compose.material3.MaterialTheme.typography.displayMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Next street
            if (uiState.nextStreet.isNotEmpty()) {
                Text(
                    text = uiState.nextStreet,
                    style = androidx.wear.compose.material3.MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ManeuverArrow(maneuverType: ManeuverType?) {

    val arrow = when (maneuverType) {
        ManeuverType.TURN_LEFT -> "←"
        ManeuverType.TURN_RIGHT -> "→"
        ManeuverType.STRAIGHT -> "↑"
        ManeuverType.TURN_SLIGHT_LEFT -> "↖"
        ManeuverType.TURN_SLIGHT_RIGHT -> "↗"
        ManeuverType.TURN_SHARP_LEFT -> "⤺"
        ManeuverType.TURN_SHARP_RIGHT -> "⤻"
        ManeuverType.ROUNDABOUT_ENTER -> "⟳"
        ManeuverType.ROUNDABOUT_EXIT -> "⤿"
        ManeuverType.MERGE -> "⇢"
        ManeuverType.ARRIVED -> "✓"
        else -> "•"
    }

    Text(
        text = arrow,
        style = androidx.wear.compose.material3.MaterialTheme.typography.displayLarge
    )
}