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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: NavigationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wearableService = WearableNavigationService(this, simulate = false)
        viewModel = NavigationViewModel(wearableService)

        setContent {
            RingRescueTheme {
                WearApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onCleared()
    }
}

@Composable
fun WearApp(viewModel: NavigationViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    AppScaffold {
        ScreenScaffold { contentPadding ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Main content: Navigation or Connection Status
                // Added bottom padding to ensure it doesn't overlap with the SOS button
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 55.dp)) {
                    if (uiState.connectionStatus == WearableNavigationService.ConnectionStatus.NAVIGATING) {
                        NavigationScreen(uiState)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("${uiState.connectionStatus}")
                        }
                    }
                }

                // SOS Button: Positioned at the very bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 4.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Button(
                        onClick = { viewModel.sendSos() },
                        modifier = Modifier.size(52.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "SOS",
                            style = androidx.wear.compose.material3.MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // SOS Confirmation Overlay
                if (uiState.showSosSent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🚨",
                                style = androidx.wear.compose.material3.MaterialTheme.typography.displaySmall
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "SOS Sent",
                                style = androidx.wear.compose.material3.MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationScreen(uiState: NavigationUiState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ManeuverArrow(uiState.maneuverType)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${uiState.distanceToNextTurn} m",
                style = androidx.wear.compose.material3.MaterialTheme.typography.displayMedium
            )
            if (uiState.nextStreet.isNotEmpty()) {
                Text(
                    text = uiState.nextStreet,
                    style = androidx.wear.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center
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
        style = androidx.wear.compose.material3.MaterialTheme.typography.displayLarge,
        textAlign = TextAlign.Center
    )
}