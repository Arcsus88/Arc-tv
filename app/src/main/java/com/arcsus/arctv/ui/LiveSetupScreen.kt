package com.arcsus.arctv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.theme.ArcBlue
import com.arcsus.arctv.ui.theme.ArcSurface

/**
 * Second step of first-run setup, straight after the debrid sign-in: get the
 * viewer's IPTV playlists onto the TV. The phone does the typing -- the same
 * code hand-off as Settings -> "Add from your phone".
 */
@Composable
fun LiveSetupScreen(factory: ArcTvViewModelFactory, onDone: () -> Unit) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val state by settingsViewModel.state.collectAsState()
    val hasPlaylists = state.playlists.isNotEmpty()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.72f).padding(32.dp),
        ) {
            Text(
                "Step 2 of 2",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Live TV",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Send your IPTV playlists from your phone. Nothing to type on the remote.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ArcSurface, RoundedCornerShape(14.dp))
                    .padding(20.dp),
            ) {
                PairingCard(
                    state = state.pairing,
                    onStart = { settingsViewModel.startPairing() },
                    onCancel = { settingsViewModel.cancelPairing() },
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasPlaylists) {
                    Button(onClick = onDone) { Text("Continue") }
                } else {
                    OutlinedButton(onClick = onDone) { Text("Skip for now") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (hasPlaylists) {
                    "${state.playlists.size} playlist${if (state.playlists.size == 1) "" else "s"} ready. You can add more under Settings any time."
                } else {
                    "You can always add playlists later under Settings, by phone or by hand."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
