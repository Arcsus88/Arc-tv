package com.arcsus.arctv.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
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

private const val WINDOW_SECONDS = 90L * 60L
private val CHANNEL_COL = 176.dp

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

                // Sky's guide layout: the group list runs down the left like a
                // sub-menu, with the grid filling the rest.
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(CHANNEL_COL)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(end = 4.dp),
                    ) {
                        Text(
                            "TV Guide",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        validSelected.forEach { name ->
                            GuideGroupItem(
                                label = name.ifBlank { "Ungrouped" },
                                selected = name == current && !showPicker,
                                onActivate = { viewModel.selectGroup(name) },
                            )
                            Spacer(Modifier.height(3.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        GuideGroupItem(
                            label = if (showPicker) "Close picker" else "+ Add group",
                            selected = showPicker,
                            onActivate = { picking = !picking },
                        )
                    }
                    Spacer(Modifier.width(22.dp))
                    Column(Modifier.weight(1f)) {
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
                                // Lambda, not value: reading it here recomposed
                                // every lane on each DPAD move.
                                ProgrammeDetail({ focusedProgramme }, state.nowMs / 1000)
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
    }
}

/** A group entry in the guide's left menu: Sky's white box marks the active one. */
@Composable
private fun GuideGroupItem(label: String, selected: Boolean, onActivate: () -> Unit) {
    Surface(
        onClick = onActivate,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.Transparent,
            focusedContainerColor = ArcBlue,
            contentColor = if (selected) Color(0xFF0B0F15)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
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
        // Panels repeat category names often enough to matter, and a repeated
        // key crashes the grid.
        val matches = state.groups
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
            .distinctBy { it.name }
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
 * described up here — channel, title, times, and the synopsis.
 */
@Composable
private fun ProgrammeDetail(focused: () -> FocusedProgramme?, nowSec: Long) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).heightIn(min = 88.dp)) {
        val info = focused()
        if (info == null) {
            Text(
                "Move around the grid — details appear here. OK plays a channel; long-press OK favourites it.",
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

/**
 * Sky's programme grid: a Today/time ruler, compact hairline-separated rows,
 * and flat title-only cells sized by duration. Cells are focus targets that
 * feed the detail header; OK plays the channel, long-press OK favourites it.
 */
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
        ) {
            Text(
                "Today",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(CHANNEL_COL),
            )
            for (i in 0 until 3) {
                Text(
                    formatTime(windowStart + i * 1800L),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (i == 0) ArcBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        LazyColumn {
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
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(44.dp).padding(vertical = 2.dp),
        ) {
            Card(
                onClick = { onPlay(channel) },
                onLongClick = { onToggleFavorite(channel) },
                modifier = Modifier.width(CHANNEL_COL - 6.dp).fillMaxHeight(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    if (channel.logo.isNotBlank()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                channel.name.trim()
                                    .firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = ArcBlue,
                            )
                        }
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (favorite) {
                        Spacer(Modifier.width(4.dp))
                        Text("♥", color = ArcBlue, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                if (entries.isNullOrEmpty()) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.03f)),
                    ) {
                        Text(
                            if (epgLoading) "Loading guide…" else "No guide data",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                } else {
                    for (lane in lanesFor(entries, windowStart, windowEnd, nowSec)) {
                        val weight = (lane.end - lane.start).toFloat().coerceAtLeast(1f)
                        if (lane.title != null && lane.entry != null) {
                            Surface(
                                onClick = { onPlay(channel) },
                                onLongClick = { onToggleFavorite(channel) },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(3.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (lane.onAir) ArcBlue.copy(alpha = 0.24f)
                                    else Color.White.copy(alpha = 0.06f),
                                    focusedContainerColor = Color(0xFF0A0F16),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    focusedContentColor = Color.White,
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(
                                        BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f)),
                                        shape = RoundedCornerShape(3.dp),
                                    ),
                                ),
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight()
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onProgrammeFocus(FocusedProgramme(channel, lane.entry))
                                        }
                                    },
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    Text(
                                        lane.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (lane.onAir) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(horizontal = 9.dp),
                                    )
                                    if (lane.onAir && lane.end > lane.start) {
                                        val progress =
                                            ((nowSec - lane.start).toFloat() / (lane.end - lane.start))
                                                .coerceIn(0f, 1f)
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth(progress)
                                                .height(2.dp)
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
                                    .background(Color.White.copy(alpha = 0.02f)),
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}
