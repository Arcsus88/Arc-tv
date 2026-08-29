package com.arcsus.arctv.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Button
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.data.EpgEntry
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.ui.theme.ArcBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(unixSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(unixSeconds * 1000))

/** One block in a channel's programme lane, clipped to the guide window. */
private data class Lane(
    val start: Long,
    val end: Long,
    val title: String?,
    val onAir: Boolean,
    val entry: EpgEntry? = null,
)

/** The programme the grid focus is resting on, feeding the detail panel. */
internal data class FocusedProgramme(val channel: LiveChannel, val entry: EpgEntry)

/** Walk a channel's listings across [windowStart, windowEnd), filling gaps. */
private fun lanesFor(
    entries: List<EpgEntry>?,
    windowStart: Long,
    windowEnd: Long,
    nowSec: Long,
): List<Lane> {
    val out = mutableListOf<Lane>()
    var cursor = windowStart
    for (e in entries.orEmpty().sortedBy { it.start }) {
        if (e.stop <= cursor || e.start >= windowEnd) continue
        val s = maxOf(e.start, cursor)
        val end = minOf(e.stop, windowEnd)
        if (s > cursor) out.add(Lane(cursor, s, null, false))
        out.add(Lane(s, end, e.title, e.start <= nowSec && nowSec < e.stop, e))
        cursor = end
        if (cursor >= windowEnd) break
    }
    if (cursor < windowEnd) out.add(Lane(cursor, windowEnd, null, false))
    return out
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
                        var focusedProgramme by remember(current) {
                            mutableStateOf<FocusedProgramme?>(null)
                        }
                        ProgrammeDetail(focusedProgramme, state.nowMs / 1000)
                        GuideList(
                            channels = groupChannels,
                            epg = state.epg,
                            epgLoading = state.epgLoading,
                            nowMs = state.nowMs,
                            favoriteUrls = state.favChannels.mapTo(HashSet()) { it.url },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onProgrammeFocus = { focusedProgramme = it },
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

/**
 * Sky's detail header: whichever programme the grid focus rests on is
 * described up here — channel, title, times, and the synopsis the guide
 * data already carries.
 */
@Composable
private fun ProgrammeDetail(info: FocusedProgramme?, nowSec: Long) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 92.dp)) {
        if (info == null) {
            Text(
                "Move around the grid — programme details appear here. OK plays the channel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        Text(
            info.channel.name,
            style = MaterialTheme.typography.labelMedium,
            color = ArcBlue,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            info.entry.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        val minutes = ((info.entry.stop - info.entry.start) / 60).toInt()
        val onAir = info.entry.start <= nowSec && nowSec < info.entry.stop
        Text(
            (if (onAir) "On now · until ${formatTime(info.entry.stop)}"
            else "Starts at ${formatTime(info.entry.start)}") + "   ·   ${minutes}m",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (info.entry.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                info.entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }
    }
}

/** Sky-style guide: channel column on the left, a 90-minute timeline of
 * proportionally sized programme blocks on the right, the on-air block
 * highlighted with a progress line. Blocks are decoration — the channel cell
 * plays, the heart favourites. */
private const val WINDOW_SECONDS = 90L * 60L

@Composable
private fun GuideList(
    channels: List<LiveChannel>,
    epg: Map<String, List<EpgEntry>>,
    epgLoading: Boolean,
    nowMs: Long,
    favoriteUrls: Set<String>,
    onToggleFavorite: (LiveChannel) -> Unit,
    onProgrammeFocus: (FocusedProgramme) -> Unit,
    onPlay: (LiveChannel) -> Unit,
) {
    val nowSec = nowMs / 1000
    val windowStart = nowSec - nowSec % 1800
    val windowEnd = windowStart + WINDOW_SECONDS

    Column {
        // Time ruler, aligned with the programme lanes below.
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(Modifier.width(198.dp))
            for (i in 0 until 3) {
                Text(
                    formatTime(windowStart + i * 1800L),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == 0) ArcBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.width(58.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(channels.size, key = { channels[it].id }) { index ->
                val channel = channels[index]
                GuideRow(
                    channel = channel,
                    entries = epg[channel.url],
                    epgLoading = epgLoading,
                    nowSec = nowSec,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    favorite = channel.url in favoriteUrls,
                    onToggleFavorite = onToggleFavorite,
                    onProgrammeFocus = onProgrammeFocus,
                    onPlay = onPlay,
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: LiveChannel,
    entries: List<EpgEntry>?,
    epgLoading: Boolean,
    nowSec: Long,
    windowStart: Long,
    windowEnd: Long,
    favorite: Boolean,
    onToggleFavorite: (LiveChannel) -> Unit,
    onProgrammeFocus: (FocusedProgramme) -> Unit,
    onPlay: (LiveChannel) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(onClick = { onPlay(channel) }, modifier = Modifier.width(190.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Text(
                            channel.name.trim()
                                .firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "\u2022",
                            style = MaterialTheme.typography.labelLarge,
                            color = ArcBlue,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    channel.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).height(58.dp)) {
            if (entries.isNullOrEmpty()) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                ) {
                    Text(
                        if (epgLoading) "Loading guide\u2026" else "No guide data",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            } else {
                for (lane in lanesFor(entries, windowStart, windowEnd, nowSec)) {
                    val weight = (lane.end - lane.start).toFloat().coerceAtLeast(1f)
                    if (lane.title != null && lane.entry != null) {
                        // A programme cell, Sky-style: focusable, darkening into
                        // a highlight box and feeding the detail panel above.
                        Surface(
                            onClick = { onPlay(channel) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (lane.onAir) ArcBlue.copy(alpha = 0.24f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = Color(0xFF0A0F16),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContentColor = Color.White,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            ),
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .padding(end = 2.dp)
                                .onFocusChanged {
                                    if (it.isFocused) onProgrammeFocus(FocusedProgramme(channel, lane.entry))
                                },
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                    Text(
                                        lane.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (lane.onAir) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatTime(lane.start),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (lane.onAir && lane.end > lane.start) {
                                    val progress = ((nowSec - lane.start).toFloat() / (lane.end - lane.start))
                                        .coerceIn(0f, 1f)
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth(progress)
                                            .height(3.dp)
                                            .background(ArcBlue),
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .padding(end = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { onToggleFavorite(channel) }) {
            Text(if (favorite) "\u2665" else "\u2661", color = ArcBlue)
        }
    }
}
