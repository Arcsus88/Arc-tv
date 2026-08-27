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
                TvTextField(
                    value = state.torboxToken,
                    placeholder = "From torbox.app settings — tried first for playback",
                    onCommit = { viewModel.saveTorboxToken(it) },
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
        TvTextField(
            value = name,
            placeholder = "Name (optional)",
            onCommit = { name = it },
            modifier = Modifier.fillMaxWidth(0.4f),
        )
        TvTextField(
            value = url,
            placeholder = if (kind == "xtream") "http://panel.example.com:8080" else "https://example.com/playlist.m3u",
            onCommit = { url = it },
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        if (kind == "xtream") {
            TvTextField(
                value = username,
                placeholder = "Username",
                onCommit = { username = it },
                modifier = Modifier.fillMaxWidth(0.4f),
            )
            TvTextField(
                value = password,
                placeholder = "Password",
                onCommit = { password = it },
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

/**
 * A TV-friendly text field, built exactly like TvSearchField: D-pad focus
 * lands on a button-like surface without opening the keyboard; pressing OK
 * activates editing, requests focus on the text field and shows the
 * keyboard, and Done (or focusing away) commits. The active branch uses a
 * NON-clickable surface and grabs focus immediately — otherwise focus falls
 * back to the tab row, whose tabs switch on focus and yank the user to
 * Browse mid-edit.
 */
@Composable
private fun TvTextField(
    value: String,
    placeholder: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var active by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }
    // After Done collapses the field, put D-pad focus back on it — otherwise
    // focus falls to the tab row and yanks the user to the Browse tab.
    var reclaimFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Reset each time we (re)enter the active state; guards against the field
    // collapsing on the initial onFocusChanged(false) before requestFocus runs.
    var everFocused by remember(active) { mutableStateOf(false) }

    if (active) {
        LaunchedEffect(Unit) {
            draft = value
            focusRequester.requestFocus()
            keyboard?.show()
        }
        Surface(shape = RoundedCornerShape(8.dp), modifier = modifier) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (draft.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(ArcBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onCommit(draft.trim())
                        active = false
                        reclaimFocus = true
                        keyboard?.hide()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) everFocused = true
                            // Only collapse once it has actually held focus, so
                            // activating it doesn't instantly close. Committing
                            // here keeps typed text when navigating away.
                            else if (everFocused) {
                                onCommit(draft.trim())
                                active = false
                            }
                        },
                )
            }
        }
    } else {
        if (reclaimFocus) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                reclaimFocus = false
            }
        }
        Surface(
            onClick = { active = true },
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            modifier = modifier.focusRequester(focusRequester),
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    value.ifBlank { placeholder },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
