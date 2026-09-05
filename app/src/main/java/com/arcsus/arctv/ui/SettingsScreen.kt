package com.arcsus.arctv.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
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
fun SettingsScreen(viewModel: SettingsViewModel, updateViewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()
    val updateState by updateViewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionTitle("App") }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Arc TV ${com.arcsus.arctv.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val feedback = when {
                        updateState.checking -> "Checking…"
                        updateState.upToDate -> "You're on the latest version"
                        updateState.error != null -> updateState.error
                        else -> "Updates install from GitHub Releases"
                    }
                    Text(
                        feedback ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { updateViewModel.checkForUpdate() },
                    enabled = !updateState.checking,
                ) {
                    Text("Check for updates")
                }
            }
        }

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

        item { SectionTitle("Players") }

        item {
            // Which installed app opens what. Films and series go to the
            // player that handles the file (VLC decodes DTS audio; the TV's
            // own player often doesn't); live TV can also use Arc's player.
            val context = LocalContext.current
            val players = remember { installedVideoPlayers(context) }
            Column {
                PlayerChoice(
                    title = "Films & series",
                    current = state.moviePlayer,
                    players = players,
                    includeArc = false,
                    onPick = { viewModel.saveMoviePlayer(it) },
                )
                Spacer(Modifier.height(14.dp))
                PlayerChoice(
                    title = "Live TV",
                    current = state.livePlayer,
                    players = players,
                    includeArc = true,
                    onPick = { viewModel.saveLivePlayer(it) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "System default is whatever the TV opens video with. Ask each time shows the chooser. " +
                        "Arc TV's own live player changes channel with up and down, but uses Arc's network " +
                        "route -- pick an external player if only that player sits in your VPN's app list.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionTitle("Live TV") }

        item {
            Column {
                Text("Your region", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                var regionDraft by remember { mutableStateOf(state.liveRegion) }
                LaunchedEffect(state.liveRegion) { regionDraft = state.liveRegion }
                TvEditField(
                    value = regionDraft,
                    onValueChange = { regionDraft = it.uppercase().take(6) },
                    placeholder = "UK",
                    onDeactivate = { viewModel.saveLiveRegion(regionDraft) },
                    modifier = Modifier.fillMaxWidth(0.25f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "The tag your panel puts in front of its groups (UK| Sports, US: News). Those groups " +
                        "come first on the Live tab and load ahead, so search finds their channels.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionTitle("Live TV playlists") }

        item {
            PairingCard(
                state = state.pairing,
                onStart = { viewModel.startPairing() },
                onCancel = { viewModel.cancelPairing() },
            )
        }

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

/** One row of player chips: default, chooser, (Arc), then every installed video app. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlayerChoice(
    title: String,
    current: String,
    players: List<PlayerApp>,
    includeArc: Boolean,
    onPick: (String) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArcChip(label = "System default", selected = current == PLAYER_DEFAULT, onClick = { onPick(PLAYER_DEFAULT) })
            ArcChip(label = "Ask each time", selected = current == PLAYER_ASK, onClick = { onPick(PLAYER_ASK) })
            if (includeArc) {
                ArcChip(label = "Arc TV", selected = current == PLAYER_ARC, onClick = { onPick(PLAYER_ARC) })
            }
            players.forEach { app ->
                ArcChip(label = app.label, selected = current == app.packageName, onClick = { onPick(app.packageName) })
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(20.dp)
                .background(ArcBlue, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
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
