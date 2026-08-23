package com.arcsus.arctv.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.arcsus.arctv.data.LiveChannel

/** How many channels a grid renders at once — huge playlists would lock up the UI. */
private const val MAX_RENDERED = 400

@Composable
fun LiveScreen(viewModel: LiveViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val play: (LiveChannel) -> Unit = { channel ->
        if (!playVideo(context, channel.url, channel.name)) {
            Toast.makeText(context, "No video player installed.", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 12.dp)) {
        if (state.playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Add an M3U link or Xtream login in the Settings tab to watch Live TV.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            state.playlists.forEach { playlist ->
                val isActive = state.active?.key == playlist.key
                Button(
                    onClick = {
                        selectedGroup = null
                        query = ""
                        viewModel.load(playlist, refresh = !isActive)
                    },
                ) {
                    Text(if (isActive) "• ${playlist.name}" else playlist.name)
                }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            state.active?.let { active ->
                Button(onClick = { viewModel.load(active, refresh = true) }, enabled = !state.loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TvSearchField(
            query = query,
            onQueryChange = { query = it },
            onSearch = {},
            placeholder = "Search channels…",
            modifier = Modifier.fillMaxWidth(0.5f),
        )
        Spacer(Modifier.height(12.dp))

        val error = state.error
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading playlist…", style = MaterialTheme.typography.titleMedium)
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            }

            query.isNotBlank() -> {
                val needle = query.trim().lowercase()
                val results = state.channels.filter { it.name.lowercase().contains(needle) }
                ChannelGrid(results, play)
            }

            selectedGroup == null -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.groups, key = { it.name }) { group ->
                        Card(onClick = { selectedGroup = group.name }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    group.name.ifBlank { "Ungrouped" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${group.count} channels",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                val group = selectedGroup
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { selectedGroup = null }) { Text("← Groups") }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        group?.ifBlank { "Ungrouped" }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ChannelGrid(state.channels.filter { it.group == group }, play)
            }
        }
    }
}

@Composable
private fun ChannelGrid(channels: List<LiveChannel>, onPlay: (LiveChannel) -> Unit) {
    if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No channels match.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(channels.take(MAX_RENDERED), key = { it.id }) { channel ->
            Card(onClick = { onPlay(channel) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp),
                ) {
                    if (channel.logo.isNotBlank()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (channels.size > MAX_RENDERED) {
            item {
                Box(Modifier.padding(16.dp)) {
                    Text(
                        "Showing the first $MAX_RENDERED — search to narrow down.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
