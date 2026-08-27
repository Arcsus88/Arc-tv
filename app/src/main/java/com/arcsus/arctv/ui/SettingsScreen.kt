package com.arcsus.arctv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.data.SavedPlaylist
import com.arcsus.arctv.ui.theme.ArcBlue

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionTitle("Debrid providers") }

        item {
            ProviderRow(
                name = "Real-Debrid",
                connected = state.rdConnected,
                onConnect = { viewModel.connect(DebridProvider.REAL_DEBRID) },
                onDisconnect = { viewModel.disconnect(DebridProvider.REAL_DEBRID) },
            )
        }
        item {
            ProviderRow(
                name = "AllDebrid",
                connected = state.adConnected,
                onConnect = { viewModel.connect(DebridProvider.ALL_DEBRID) },
                onDisconnect = { viewModel.disconnect(DebridProvider.ALL_DEBRID) },
            )
        }

        when (val connect = state.connect) {
            is SettingsViewModel.ConnectState.Code -> item {
                Surface(
                    onClick = {},
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "Connect ${connect.provider.label}: on your phone, go to",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(connect.verificationUrl, color = ArcBlue, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "and enter the code  ${connect.userCode}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { viewModel.cancelConnect() }) { Text("Cancel") }
                    }
                }
            }

            is SettingsViewModel.ConnectState.Error -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(connect.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { viewModel.connect(connect.provider) }) { Text("Try again") }
                }
            }

            SettingsViewModel.ConnectState.Idle -> Unit
        }

        item {
            Column {
                Text("TorBox API token (optional)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                var torboxDraft by remember { mutableStateOf(state.torboxToken) }
                LaunchedEffect(state.torboxToken) { torboxDraft = state.torboxToken }
                TvEditField(
                    value = torboxDraft,
                    onValueChange = { torboxDraft = it },
                    placeholder = "From torbox.app settings — tried first for playback",
                    onDeactivate = { viewModel.saveTorboxToken(torboxDraft) },
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }
        }

        item { SectionTitle("Live TV playlists") }

        if (state.playlists.isEmpty()) {
            item {
                Text(
                    "No playlists saved yet — add an M3U link or Xtream login below.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.playlists.size) { index ->
            val playlist = state.playlists[index]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (playlist.isXtream) "Xtream · ${playlist.username} @ ${playlist.url}" else playlist.url,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { viewModel.removePlaylist(playlist.key) }) { Text("Remove") }
            }
        }

        item { AddPlaylistForm(onAdd = { viewModel.addPlaylist(it) }) }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun ProviderRow(
    name: String,
    connected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(
                if (connected) "Connected" else "Not connected",
                style = MaterialTheme.typography.labelMedium,
                color = if (connected) ArcBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = if (connected) onDisconnect else onConnect) {
            Text(if (connected) "Disconnect" else "Connect")
        }
    }
}

@Composable
private fun AddPlaylistForm(onAdd: (SavedPlaylist) -> Unit) {
    var kind by rememberSaveable { mutableStateOf("m3u") }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Add a playlist", style = MaterialTheme.typography.titleSmall)
        Row {
            Button(onClick = { kind = "m3u" }) { Text(if (kind == "m3u") "• M3U link" else "M3U link") }
            Spacer(Modifier.width(10.dp))
            Button(onClick = { kind = "xtream" }) {
                Text(if (kind == "xtream") "• Xtream login" else "Xtream login")
            }
        }
        TvEditField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Name (optional)",
            modifier = Modifier.fillMaxWidth(0.4f),
        )
        TvEditField(
            value = url,
            onValueChange = { url = it },
            placeholder = if (kind == "xtream") "http://panel.example.com:8080" else "https://example.com/playlist.m3u",
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        if (kind == "xtream") {
            TvEditField(
                value = username,
                onValueChange = { username = it },
                placeholder = "Username",
                modifier = Modifier.fillMaxWidth(0.4f),
            )
            TvEditField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                modifier = Modifier.fillMaxWidth(0.4f),
            )
        }
        val ready = url.isNotBlank() && (kind == "m3u" || (username.isNotBlank() && password.isNotBlank()))
        Button(
            onClick = {
                val host = runCatching { java.net.URI(url.trim()).host }.getOrNull()
                onAdd(
                    SavedPlaylist(
                        name = name.trim().ifBlank { host ?: "Playlist" },
                        url = url.trim(),
                        kind = kind,
                        username = username.trim(),
                        password = password.trim(),
                    )
                )
                name = ""
                url = ""
                username = ""
                password = ""
            },
            enabled = ready,
        ) {
            Text("Add playlist")
        }
    }
}
