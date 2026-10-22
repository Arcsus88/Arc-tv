package com.arcsus.arctv.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arcsus.arctv.data.EpgEntry
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.data.SkyChannel
import com.arcsus.arctv.data.SkyEpg
import com.arcsus.arctv.ui.theme.ArcBlue
import com.arcsus.arctv.ui.theme.ArcMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(unixSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(unixSeconds * 1000))

/** The programme airing at [nowSec] and what follows it (the web's nowAndNext). */
private fun nowAndNext(entries: List<EpgEntry>?, nowSec: Long): Pair<EpgEntry?, EpgEntry?> {
    if (entries.isNullOrEmpty()) return null to null
    val now = entries.firstOrNull { it.start <= nowSec && nowSec < it.stop }
    val next = entries.firstOrNull { it.start >= (now?.stop ?: nowSec) }
    return now to next
}

/** One guide row's height: a 16:9 still beside two lines of now and next. */
private val ROW_HEIGHT = 104.dp
private val STILL_WIDTH = 160.dp
private val CHANNEL_WIDTH = 86.dp

/**
 * The TV guide, as the web app draws it (after the Sky Q mobile guide):
 * the channel groups you picked as pills across the top, then one row per
 * channel -- its logo over its Sky number, a 16:9 still of what's on with a
 * play button and a progress bar, then now and next. Listings, numbers,
 * logos and stills come from Sky's own EPG where Sky carries the channel,
 * else from the playlist's Xtream panel. OK plays; hold OK to favourite.
 */
@Composable
fun GuideScreen(viewModel: GuideViewModel) {
    val state by viewModel.state.collectAsState()
    var picking by rememberSaveable { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    // A home banner asked for a group this playlist doesn't have: offer the
    // closest ones instead (every sports group, for "UK Sports").
    LaunchedEffect(state.pickerRequest) {
        val request = state.pickerRequest ?: return@LaunchedEffect
        picking = true
        search = request
        viewModel.consumePickerRequest()
    }

    // The guide's channels are the zap list, so up/down in the player walk
    // the same group the guide shows.
    val playLive = rememberLivePlay()
    val play: (LiveChannel) -> Unit = { channel ->
        val list = state.channels
        val at = list.indexOfFirst { it.url == channel.url }
        if (at >= 0) playLive(list, at) else playLive(listOf(channel), 0)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = TV_INSET_H)) {
        when {
            state.playlist == null && !state.loading -> CenteredNote(
                "Add an M3U link or Xtream login in Settings (or the Live TV section) to use the guide.",
            )
            state.loading -> CenteredNote("Loading channels…")
            state.error != null -> CenteredNote(state.error.orEmpty(), error = true)
            else -> {
                val validSelected = viewModel.validGroups(state)
                val current = viewModel.currentGroup(state)
                val showPicker = picking || validSelected.isEmpty()

                GuideHeader(
                    groups = validSelected,
                    current = current.takeIf { !showPicker },
                    picking = showPicker,
                    nowMs = state.nowMs,
                    onGroup = { picking = false; viewModel.selectGroup(it) },
                    onTogglePicker = { picking = !picking },
                )
                Spacer(Modifier.height(12.dp))
                if (showPicker) {
                    GroupPicker(
                        state = state,
                        validSelected = validSelected,
                        search = search,
                        onSearchChange = { search = it },
                        // The picker stays open: pick as many groups as you
                        // like, then press Done. Closing on the first add
                        // threw away the focused card.
                        onAdd = { viewModel.addGroup(it) },
                        onRemove = { viewModel.removeGroup(it) },
                    )
                } else if (current != null) {
                    if (!state.epgCapable) {
                        Text(
                            "This playlist has no programme data of its own (that needs an Xtream login or a " +
                                "get.php link), so only channels Sky carries have listings.",
                            style = MaterialTheme.typography.labelMedium,
                            color = ArcMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.borderVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val groupChannels = viewModel.channelsFor(state, current)
                    if (groupChannels.isEmpty()) {
                        CenteredNote(if (state.epgLoading) "Loading channels…" else "No channels in this group.")
                    } else {
                        GuideList(
                            channels = groupChannels,
                            epg = state.epg,
                            sky = state.sky,
                            pending = state.epgLoading || state.skyLoading,
                            nowMs = state.nowMs,
                            favoriteUrls = state.favChannels.mapTo(HashSet()) { it.url },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlay = play,
                            onRowsVisible = viewModel::loadEpgFor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredNote(text: String, error: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (error) MaterialTheme.colorScheme.error else ArcMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.6f),
        )
    }
}

/**
 * "TV Guide", the guide's groups as pills (the one showing selected), the
 * add/remove pill, and the time with the web's pulsing live dot.
 */
@Composable
private fun GuideHeader(
    groups: List<String>,
    current: String?,
    picking: Boolean,
    nowMs: Long,
    onGroup: (String) -> Unit,
    onTogglePicker: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("TV Guide", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(18.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(groups, key = { it.ifBlank { "__ungrouped" } }) { name ->
                ArcChip(name.ifBlank { "Ungrouped" }, selected = name == current, onClick = { onGroup(name) })
            }
            item(key = "__manage") {
                ArcChip(
                    if (picking) "Done" else "+ Groups",
                    selected = picking,
                    onClick = onTogglePicker,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        val pulse by rememberInfiniteTransition(label = "live").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "dot",
        )
        Box(Modifier.size(7.dp).graphicsLayer { alpha = pulse }.background(ArcBlue, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(formatTime(nowMs / 1000), style = MaterialTheme.typography.titleSmall, color = ArcMuted)
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
            if (validSelected.isEmpty()) "Pick the channel groups you want in your guide — try searching “UK”."
            else "Your guide’s groups. Add more, or remove ones you don’t watch.",
            style = MaterialTheme.typography.titleSmall,
            color = ArcMuted,
        )
        Spacer(Modifier.height(10.dp))
        TvSearchField(
            query = search,
            onQueryChange = onSearchChange,
            onSearch = {},
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
        if (matches.isEmpty()) {
            Text("No groups match.", style = MaterialTheme.typography.bodyMedium, color = ArcMuted)
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
        ) {
            items(matches, key = { it.name.ifBlank { "__ungrouped" } }) { group ->
                val selected = validSelected.contains(group.name)
                val shape = RoundedCornerShape(10.dp)
                Card(
                    onClick = { if (selected) onRemove(group.name) else onAdd(group.name) },
                    shape = CardDefaults.shape(shape),
                    colors = CardDefaults.colors(
                        // The web's chosen-group chip: an accent tint and rim.
                        containerColor = if (selected) ArcBlue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                    ),
                    scale = CardDefaults.scale(focusedScale = 1.03f),
                    border = CardDefaults.border(
                        border = Border(
                            BorderStroke(1.dp, if (selected) ArcBlue.copy(alpha = 0.55f) else MaterialTheme.colorScheme.borderVariant),
                            shape = shape,
                        ),
                        focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
                    ),
                ) {
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
                                color = ArcMuted,
                            )
                        }
                        Text(
                            if (selected) "✕" else "+",
                            color = if (selected) Color.White else ArcMuted,
                            fontWeight = FontWeight.Bold,
                        )
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
    sky: Map<String, SkyChannel>,
    pending: Boolean,
    nowMs: Long,
    favoriteUrls: Set<String>,
    onToggleFavorite: (LiveChannel) -> Unit,
    onPlay: (LiveChannel) -> Unit,
    onRowsVisible: (List<LiveChannel>) -> Unit = {},
) {
    val nowSec = nowMs / 1000
    val listState = rememberLazyListState()
    // Ask for listings a screen ahead of where the viewer is, so rows are
    // filled by the time they reach them.
    LaunchedEffect(listState, channels) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val to = (last + 20).coerceAtMost(channels.size)
                if (to > 0) onRowsVisible(channels.subList(0, to))
            }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(channels, key = { it.id }) { channel ->
            val section = remember(channel.name) { SkyEpg.separatorLabel(channel.name) }
            if (section != null) {
                // Playlists pad groups with "##### SKY SPORTS #####" entries:
                // headings, as the web guide shows them.
                Text(
                    section.ifBlank { "—" }.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = ArcMuted,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp),
                )
            } else {
                val skyChannel = sky[channel.url]
                GuideRow(
                    channel = channel,
                    sky = skyChannel,
                    entries = skyChannel?.entries?.takeIf { it.isNotEmpty() } ?: epg[channel.url],
                    pending = pending,
                    nowSec = nowSec,
                    favorite = channel.url in favoriteUrls,
                    onToggleFavorite = onToggleFavorite,
                    onPlay = onPlay,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: LiveChannel,
    sky: SkyChannel?,
    entries: List<EpgEntry>?,
    /** A listings source is still loading, so "no data" may yet change. */
    pending: Boolean,
    nowSec: Long,
    favorite: Boolean,
    onToggleFavorite: (LiveChannel) -> Unit,
    onPlay: (LiveChannel) -> Unit,
) {
    // Images that failed, by URL: Sky's logo first (it's drawn for dark
    // backgrounds), then the playlist's; each new programme brings a still.
    var failed by remember(channel.url) { mutableStateOf(emptySet<String>()) }
    val logo = listOfNotNull(sky?.logo, channel.logo.ifBlank { null }).firstOrNull { it !in failed }
    val (now, next) = nowAndNext(entries, nowSec)
    val progress = if (now != null && now.stop > now.start) {
        ((nowSec - now.start).toFloat() / (now.stop - now.start)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val name = SkyEpg.withoutCountryPrefix(channel.name)
    val still = now?.image?.takeIf { it.isNotBlank() && it !in failed }

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = { onPlay(channel) },
        onLongClick = { onToggleFavorite(channel) },
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.07f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        ) {
            // Channel: logo over its Sky number.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(CHANNEL_WIDTH),
            ) {
                if (logo != null) {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onError = { failed = failed + logo },
                        modifier = Modifier.height(36.dp).fillMaxWidth(),
                    )
                } else if (pending) {
                    // Sky's logo may be on its way; don't flash an initial first.
                    Box(Modifier.size(width = 48.dp, height = 28.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp)))
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    ) {
                        Text(
                            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
                if (sky?.number != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(sky.number, style = MaterialTheme.typography.labelLarge, color = ArcMuted)
                }
            }
            Spacer(Modifier.width(14.dp))

            // What's on: its still (else the channel's card), play button, progress.
            Box(
                Modifier
                    .width(STILL_WIDTH)
                    .fillMaxHeight()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(7.dp)),
            ) {
                // The channel's logo card stands in until (and unless) the
                // still arrives; its name only once no logo is on the way.
                if (still == null) {
                    ChannelCard(
                        logo = logo.orEmpty(),
                        name = if (pending) "" else name,
                        logoAlpha = if (focused) 0.35f else 1f,
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(still).crossfade(200).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { failed = failed + still },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Over a still, always; over the logo card, only on focus, so
                // the logo has the card to itself until then.
                if (still != null || focused) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(38.dp)
                            .background(if (focused) ArcBlue.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.35f), CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                    ) {
                        Text("▶", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 2.dp))
                    }
                }
                if (now != null) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.55f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(ArcBlue))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))

            // Now and next.
            Column(Modifier.weight(1f)) {
                when {
                    now != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(now.start), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            // Without a logo, nothing else says which channel this is.
                            if (logo == null || favorite) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    (if (favorite) "♥ " else "") + if (logo == null) name.uppercase() else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (favorite) ArcBlue else ArcMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            now.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (now.description.isNotBlank()) {
                            Text(
                                now.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = ArcMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (next != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${formatTime(next.start)}   ${next.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ArcMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    pending -> {
                        Box(Modifier.size(width = 44.dp, height = 12.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(3.dp)))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(0.7f).height(14.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(3.dp)))
                    }
                    else -> {
                        Text(
                            (if (favorite) "♥ " else "") + name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("No guide data", style = MaterialTheme.typography.bodyMedium, color = ArcMuted)
                    }
                }
            }
        }
    }
}
