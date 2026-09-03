package com.arcsus.arctv.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.ui.theme.ArcBlue

/** How many channels a grid renders at once — huge playlists would lock up the UI. */
private const val MAX_RENDERED = 400

/** Virtual group holding the user's hearted channels. */
private const val FAV_GROUP = "♥ Favourites"

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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }
            return@Column
        }

        // Sky's section header: the title, then the playlist choices, with
        // the refresh tucked to the far right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Live TV",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(Modifier.width(28.dp))
            state.playlists.forEach { playlist ->
                val isActive = state.active?.key == playlist.key
                ArcChip(
                    label = playlist.name,
                    selected = isActive,
                    onClick = {
                        selectedGroup = null
                        query = ""
                        viewModel.load(playlist, refresh = !isActive)
                    },
                )
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            state.active?.let { active ->
                OutlinedButton(onClick = { viewModel.load(active, refresh = true) }, enabled = !state.loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TvSearchField(
            query = query,
            onQueryChange = { query = it },
            onSearch = {},
            placeholder = "Search channels…",
            modifier = Modifier.fillMaxWidth(0.42f),
        )
        Spacer(Modifier.height(16.dp))

        val error = state.error
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading playlist…", style = MaterialTheme.typography.titleMedium)
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }

            query.isNotBlank() -> {
                val needle = query.trim().lowercase()
                val results = state.channels.filter { it.name.lowercase().contains(needle) }
                ChannelGrid(results, state.favoriteUrls, play, viewModel::toggleFavorite)
            }

            selectedGroup == null -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (state.favorites.isNotEmpty()) {
                        item(key = "__favourites") {
                            Card(onClick = { selectedGroup = FAV_GROUP }) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        FAV_GROUP,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ArcBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${state.favorites.size} channels",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    items(state.groups, key = { it.name }) { group ->
                        Card(
                            onClick = {
                                viewModel.openGroup(group.name)
                                selectedGroup = group.name
                            },
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    group.name.ifBlank { "Ungrouped" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (group.count == 0) "Open to load" else "${group.count} channels",
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
                    OutlinedButton(onClick = { selectedGroup = null }) { Text("Groups") }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        group?.ifBlank { "Ungrouped" }.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Hold OK to favourite a channel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val channels = if (group == FAV_GROUP) {
                    state.favorites.mapIndexed { i, c ->
                        LiveChannel(id = "fav:$i:${c.url}", name = c.name, logo = c.logo, group = FAV_GROUP, url = c.url)
                    }
                } else {
                    state.channels.filter { it.group == group }
                }
                if (channels.isEmpty() && state.groupLoading == group) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading channels…", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    ChannelGrid(channels, state.favoriteUrls, play, viewModel::toggleFavorite)
                }
            }
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<LiveChannel>,
    favoriteUrls: Set<String>,
    onPlay: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
) {
    var visible by rememberSaveable(channels.size) { mutableStateOf(MAX_RENDERED) }
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
        items(channels.take(visible), key = { it.id }) { channel ->
            // One clean focus target per channel: OK plays, long-press OK
            // toggles the favourite (shown as a small heart on the card).
            Card(
                onClick = { onPlay(channel) },
                onLongClick = { onToggleFavorite(channel) },
            ) {
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
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                channel.name.trim()
                                    .firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                                style = MaterialTheme.typography.labelLarge,
                                color = ArcBlue,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (channel.url in favoriteUrls) {
                        Spacer(Modifier.width(6.dp))
                        Text("♥", color = ArcBlue, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (channels.size > visible) {
            item {
                Button(onClick = { visible += MAX_RENDERED }, modifier = Modifier.padding(8.dp)) {
                    Text("Show more (${channels.size - visible} left)")
                }
            }
        }
    }
}
