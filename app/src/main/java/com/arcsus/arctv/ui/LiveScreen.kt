package com.arcsus.arctv.ui

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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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

/** Channel tiles across the content width. */
private const val CHANNEL_COLUMNS = 5
private val LOGO_PANEL_HEIGHT = 72.dp
private val LOGO_SIZE = 48.dp

@Composable
fun LiveScreen(viewModel: LiveViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    // Opening a group swaps the group grid for its channels, and "Groups"
    // swaps them back: the card that was pressed is gone with its grid, and
    // a TV then throws focus at the first thing on screen -- the rail. Put
    // it where the viewer expects instead: the first channel (or "Groups"
    // while they load), and the first group card on the way back.
    val groupsFocus = remember { FocusRequester() }
    val firstGroupFocus = remember { FocusRequester() }
    val firstChannelFocus = remember { FocusRequester() }
    var shownGroup by remember { mutableStateOf(selectedGroup) }
    LaunchedEffect(selectedGroup) {
        if (selectedGroup == shownGroup) return@LaunchedEffect
        shownGroup = selectedGroup
        when {
            selectedGroup == null -> firstGroupFocus.requestFocusWhenReady()
            !firstChannelFocus.requestFocusWhenReady(frames = 3) -> groupsFocus.requestFocusWhenReady()
        }
    }

    val play = rememberLivePlay()

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
                // Words, not substrings: "UK" is the UK groups and channels,
                // not Milwaukee. Groups that match come first -- the ones not
                // loaded yet can't have their channels searched, but they can
                // be opened from here.
                val groups = state.groups.filter { LiveMatch.matches(it.name, query) }
                val results = state.channels.filter { LiveMatch.matches(it.name, query) }
                SearchResults(
                    groups = groups,
                    channels = results,
                    favoriteUrls = state.favoriteUrls,
                    onOpenGroup = { name ->
                        viewModel.openGroup(name)
                        query = ""
                        selectedGroup = name
                    },
                    onPlay = play,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }

            selectedGroup == null -> {
                // The viewer's region leads (Settings > Live TV > Your
                // region); the panel's hundreds of other groups follow.
                val (home, away) = state.groups.partition { LiveMatch.inRegion(it.name, state.region) }
                val openGroup: (String) -> Unit = { name ->
                    viewModel.openGroup(name)
                    selectedGroup = name
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (state.favorites.isNotEmpty()) {
                        item(key = "__favourites") {
                            GroupCard(
                                name = FAV_GROUP,
                                detail = "${state.favorites.size} channels",
                                accent = true,
                                onClick = { selectedGroup = FAV_GROUP },
                                modifier = Modifier.focusRequester(firstGroupFocus),
                            )
                        }
                    }
                    if (home.isNotEmpty()) {
                        item(key = "__home_heading", span = { GridItemSpan(maxLineSpan) }) {
                            SectionLabel(state.region.uppercase())
                        }
                    }
                    itemsIndexed(home, key = { _, group -> group.name }) { index, group ->
                        GroupCard(
                            name = group.name.ifBlank { "Ungrouped" },
                            detail = if (group.count == 0) "Open to load" else "${group.count} channels",
                            onClick = { openGroup(group.name) },
                            modifier = if (index == 0 && state.favorites.isEmpty()) {
                                Modifier.focusRequester(firstGroupFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                    if (home.isNotEmpty() && away.isNotEmpty()) {
                        item(key = "__away_heading", span = { GridItemSpan(maxLineSpan) }) {
                            SectionLabel("Everything else")
                        }
                    }
                    itemsIndexed(away, key = { _, group -> group.name }) { index, group ->
                        GroupCard(
                            name = group.name.ifBlank { "Ungrouped" },
                            detail = if (group.count == 0) "Open to load" else "${group.count} channels",
                            onClick = { openGroup(group.name) },
                            modifier = if (index == 0 && home.isEmpty() && state.favorites.isEmpty()) {
                                Modifier.focusRequester(firstGroupFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }

            else -> {
                val group = selectedGroup
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { selectedGroup = null },
                        modifier = Modifier.focusRequester(groupsFocus),
                    ) { Text("Groups") }
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
                    ChannelGrid(channels, state.favoriteUrls, play, viewModel::toggleFavorite, firstChannelFocus)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun GroupCard(
    name: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = if (accent) ArcBlue else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Search: matching groups (openable, loaded or not) above matching channels. */
@Composable
private fun SearchResults(
    groups: List<LiveGroup>,
    channels: List<LiveChannel>,
    favoriteUrls: Set<String>,
    onOpenGroup: (String) -> Unit,
    onPlay: (List<LiveChannel>, Int) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
) {
    if (groups.isEmpty() && channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing matches.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val shown = channels.take(MAX_RENDERED)
    LazyVerticalGrid(
        columns = GridCells.Fixed(CHANNEL_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (groups.isNotEmpty()) {
            item(key = "__groups_heading", span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Groups") }
            items(groups.take(MAX_RENDERED), key = { "g:" + it.name }) { group ->
                GroupCard(
                    name = group.name.ifBlank { "Ungrouped" },
                    detail = if (group.count == 0) "Open to load" else "${group.count} channels",
                    onClick = { onOpenGroup(group.name) },
                )
            }
        }
        if (shown.isNotEmpty()) {
            item(key = "__channels_heading", span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Channels") }
            itemsIndexed(shown, key = { _, channel -> "c:" + channel.id }) { index, channel ->
                ChannelTile(
                    channel = channel,
                    displayName = channel.name,
                    favorite = channel.url in favoriteUrls,
                    onPlay = { onPlay(shown, index) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                )
            }
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<LiveChannel>,
    favoriteUrls: Set<String>,
    onPlay: (List<LiveChannel>, Int) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    firstFocus: FocusRequester? = null,
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
    // "UK| " on every tile says nothing the group name doesn't; drop it.
    val prefix = remember(channels) { LiveMatch.sharedPrefix(channels.map { it.name }) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(CHANNEL_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        itemsIndexed(channels.take(visible), key = { _, channel -> channel.id }) { index, channel ->
            ChannelTile(
                channel = channel,
                displayName = channel.name.removePrefix(prefix).trim().ifEmpty { channel.name },
                favorite = channel.url in favoriteUrls,
                onPlay = { onPlay(channels, index) },
                onToggleFavorite = { onToggleFavorite(channel) },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
        if (channels.size > visible) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(onClick = { visible += MAX_RENDERED }, modifier = Modifier.padding(8.dp)) {
                    Text("Show more (${channels.size - visible} left)")
                }
            }
        }
    }
}

/**
 * A channel as Sky lists them: the logo does the talking, large on its own
 * panel, the name beneath. OK plays; long-press OK toggles the favourite,
 * shown as a heart in the corner.
 */
@Composable
private fun ChannelTile(
    channel: LiveChannel,
    displayName: String,
    favorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Card(
        onClick = onPlay,
        onLongClick = onToggleFavorite,
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, androidx.compose.ui.graphics.Color.White), shape = shape),
        ),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(LOGO_PANEL_HEIGHT)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f)),
            ) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(LOGO_SIZE)
                            .align(Alignment.Center),
                    )
                } else {
                    Text(
                        channel.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ArcBlue,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (favorite) {
                    Text(
                        "♥",
                        color = ArcBlue,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
