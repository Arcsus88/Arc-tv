package com.arcsus.arctv.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.arcsus.arctv.data.EpgEntry
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.ui.theme.ArcBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(unixSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(unixSeconds * 1000))

private fun nowAndNext(entries: List<EpgEntry>?, nowMs: Long): Pair<EpgEntry?, EpgEntry?> {
    if (entries.isNullOrEmpty()) return null to null
    val nowSec = nowMs / 1000
    val current = entries.firstOrNull { it.start <= nowSec && nowSec < it.stop }
    val next = entries.firstOrNull { it.start >= (current?.stop ?: nowSec) }
    return current to next
}

@Composable
fun GuideScreen(viewModel: GuideViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var picking by rememberSaveable { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    val play: (LiveChannel) -> Unit = { channel ->
        if (!playVideo(context, channel.url, channel.name)) {
            Toast.makeText(context, "No video player installed.", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 12.dp)) {
        when {
            state.playlist == null && !state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Add an M3U link or Xtream login in the Settings tab to use the guide.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading channels…", style = MaterialTheme.typography.titleMedium)
                }
            }

            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            else -> {
                val validSelected = viewModel.validGroups(state)
                val current = viewModel.currentGroup(state)
                val showPicker = picking || validSelected.isEmpty()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    validSelected.forEach { name ->
                        ArcChip(
                            label = name.ifBlank { "Ungrouped" },
                            selected = name == current && !showPicker,
                            onClick = { viewModel.selectGroup(name) },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    ArcChip(
                        label = if (showPicker) "Close picker" else "+ Add group",
                        selected = showPicker,
                        onClick = { picking = !picking },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatTime(state.nowMs / 1000),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (showPicker) {
                    GroupPicker(
                        state = state,
                        validSelected = validSelected,
                        search = search,
                        onSearchChange = { search = it },
                        onAdd = {
                            viewModel.addGroup(it)
                            picking = false
                            search = ""
                        },
                        onRemove = { viewModel.removeGroup(it) },
                    )
                } else if (current != null) {
                    if (!state.epgCapable) {
                        Text(
                            "This playlist has no programme data — guides need an Xtream login (or a get.php link).",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val groupChannels = viewModel.channelsFor(state, current)
                    if (groupChannels.isEmpty()) {
                        Text(
                            if (state.epgLoading) "Loading channels…" else "No channels in this group.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        GuideList(
                            channels = groupChannels,
                            epg = state.epg,
                            epgLoading = state.epgLoading,
                            nowMs = state.nowMs,
                            favoriteUrls = state.favChannels.mapTo(HashSet()) { it.url },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlay = play,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupPicker(
    state: GuideViewModel.UiState,
    validSelected: List<String>,
    search: String,
    onSearchChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            if (validSelected.isEmpty())
                "Pick the channel groups you want in your guide — try searching “UK”."
            else "Add another group, or remove one you no longer want.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        TvEditField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = "Search groups…",
            modifier = Modifier.fillMaxWidth(0.45f),
        )
        Spacer(Modifier.height(12.dp))
        val needle = search.trim().lowercase()
        val matches = state.groups
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(matches, key = { it.name.ifBlank { "__ungrouped" } }) { group ->
                val selected = validSelected.contains(group.name)
                Card(onClick = { if (selected) onRemove(group.name) else onAdd(group.name) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                group.name.ifBlank { "Ungrouped" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (group.count > 0) "${group.count} channels" else "loads when picked",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideList(
    channels: List<LiveChannel>,
    epg: Map<String, List<EpgEntry>>,
    epgLoading: Boolean,
    nowMs: Long,
    favoriteUrls: Set<String>,
    onToggleFavorite: (LiveChannel) -> Unit,
    onPlay: (LiveChannel) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(channels.size, key = { channels[it].id }) { index ->
            val channel = channels[index]
            val (now, next) = nowAndNext(epg[channel.url], nowMs)
            Row(verticalAlignment = Alignment.CenterVertically) {
            Card(onClick = { onPlay(channel) }, modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (channel.logo.isNotBlank()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    } else {
                        // Lettered tile, matching the web guide's logo fallback.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                channel.name.trim()
                                    .firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                                style = MaterialTheme.typography.titleSmall,
                                color = ArcBlue,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.width(220.dp)) {
                        Text(
                            channel.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (now != null) {
                            Text(
                                "● ON AIR",
                                style = MaterialTheme.typography.labelSmall,
                                color = ArcBlue,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        if (now != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    now.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${formatTime(now.start)}–${formatTime(now.stop)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            val progress = if (now.stop > now.start) {
                                ((nowMs / 1000.0 - now.start) / (now.stop - now.start))
                                    .coerceIn(0.0, 1.0).toFloat()
                            } else 0f
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.12f)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .background(ArcBlue),
                                )
                            }
                            if (next != null) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "${formatTime(next.start)}  ${next.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            Text(
                                if (epgLoading) "Loading guide…" else "No guide data for this channel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onToggleFavorite(channel) }) {
                Text(if (channel.url in favoriteUrls) "♥" else "♡", color = ArcBlue)
            }
            }
        }
    }
}
