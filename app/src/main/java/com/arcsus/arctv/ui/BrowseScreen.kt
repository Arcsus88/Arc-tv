package com.arcsus.arctv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arcsus.arctv.data.CatalogItem
import com.arcsus.arctv.data.Source
import com.arcsus.arctv.ui.theme.ArcBackground
import com.arcsus.arctv.ui.theme.ArcBlue
import com.arcsus.arctv.ui.theme.ArcSurface

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    searchOpen: Boolean = false,
    onSearchOpenChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val sheet by viewModel.sheet.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val playRequest by viewModel.playRequest.collectAsState()
    val context = LocalContext.current

    // The next episode we're offering to auto-advance to, and the countdown.
    var autoNext by remember { mutableStateOf<NextEp?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }

    // Diagnosing instant player exits: what we launched and when, plus the
    // stream to show in the "that didn't play" dialog when the player dies
    // within seconds of starting.
    var lastStream by remember { mutableStateOf<com.arcsus.arctv.data.ResolvedStream?>(null) }
    var launchedAt by remember { mutableStateOf(0L) }
    var quickExit by remember { mutableStateOf<com.arcsus.arctv.data.ResolvedStream?>(null) }

    // Movies launch with the same plain NEW_TASK intent Live TV uses — the
    // for-result flow made some TV players (VLC included) die before showing
    // any UI. Without a result we watch our own lifecycle instead: coming back
    // within seconds means the player choked (diagnose); coming back after a
    // while means the video likely ended (offer the next episode).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && launchedAt > 0) {
                val elapsed = System.currentTimeMillis() - launchedAt
                launchedAt = 0L
                if (elapsed < 12_000) {
                    quickExit = lastStream
                } else {
                    (viewModel.sheet.value as? BrowseViewModel.Sheet.Sources)?.next?.let {
                        autoNext = it
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Count down, then auto-play the next episode unless cancelled.
    LaunchedEffect(autoNext) {
        if (autoNext == null) return@LaunchedEffect
        secondsLeft = 10
        while (secondsLeft > 0) {
            delay(1000)
            if (autoNext == null) return@LaunchedEffect
            secondsLeft -= 1
        }
        autoNext = null
        viewModel.playNextEpisode()
    }

    LaunchedEffect(playRequest) {
        playRequest?.let { stream ->
            lastStream = stream
            launchedAt = System.currentTimeMillis()
            if (!playVideo(context, stream.streamUrl, stream.filename)) {
                launchedAt = 0L
                android.widget.Toast.makeText(
                    context, "No video player installed.", android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            viewModel.consumePlayRequest()
        }
    }

    Column(Modifier.fillMaxSize()) {
        when {
            state.tab != BrowseTab.HOME -> Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
                Spacer(Modifier.height(10.dp))
                CategoryBar(viewModel)
                DiscoverGrid(viewModel)
            }
            state.loadingHome && state.rows.isEmpty() -> CenteredMessage("Loading catalogue…")
            state.error != null && state.rows.isEmpty() -> CenteredError(state.error!!) { viewModel.loadHome() }
            else -> CatalogRows(viewModel)
        }
    }

    if (searchOpen) {
        SearchOverlay(
            viewModel = viewModel,
            onClose = {
                onSearchOpenChange(false)
                viewModel.clearSearch()
            },
        )
    }

    detail?.let { d -> DetailsScreen(d, viewModel) }

    SheetHost(sheet, viewModel)

    quickExit?.let { stream ->
        val host = runCatching { java.net.URI(stream.streamUrl).host }.getOrNull() ?: "unknown host"
        SheetDialog("That didn't play", onDismiss = { quickExit = null }) {
            Text(
                stream.filename.ifBlank { "(no filename)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "from $host",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The player closed within seconds of starting — the link probably isn't " +
                    "reachable from this device, or the file format defeated the player. " +
                    "Try again, or pick a different source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    quickExit = null
                    lastStream = stream
                    launchedAt = System.currentTimeMillis()
                    if (!playVideo(context, stream.streamUrl, stream.filename)) {
                        launchedAt = 0L
                        android.widget.Toast.makeText(
                            context, "No video player installed.", android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text("Try again") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { quickExit = null }) { Text("Close") }
            }
        }
    }

    autoNext?.let { n ->
        SheetDialog("Up next", onDismiss = { autoNext = null }) {
            Text(
                "S%02dE%02d".format(n.season, n.episode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Playing in ${secondsLeft}s…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { autoNext = null; viewModel.playNextEpisode() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play now")
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { autoNext = null }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun CatalogRows(viewModel: BrowseViewModel) {
    val state by viewModel.state.collectAsState()
    val rows = if (state.favorites.isEmpty()) state.rows
    else listOf(com.arcsus.arctv.data.CatalogRow("♥ Favourites", state.favorites)) + state.rows
    val heroItems = state.rows.firstOrNull()?.items?.take(10).orEmpty()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (heroItems.isNotEmpty()) {
            item(key = "__hero") {
                val heroArt by viewModel.heroArt.collectAsState()
                HeroBanner(
                    items = heroItems,
                    art = heroArt,
                    loadArt = viewModel::loadHeroArt,
                    onOpen = { viewModel.openDetails(it) },
                    pill = { CategoryPill(viewModel) },
                )
            }
        } else {
            item(key = "__pill") {
                Box(Modifier.padding(start = 40.dp, top = 10.dp)) { CategoryPill(viewModel) }
            }
        }
        items(rows, key = { it.title }) { row ->
            Column {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 40.dp),
                ) {
                    items(row.items, key = { it.type + it.id }) { item ->
                        PosterTile(item, Modifier.width(150.dp)) { viewModel.openDetails(item) }
                    }
                    // Sky's "More Top Picks" closer: jumps to the full catalogue.
                    val moreType = row.items.firstOrNull()?.type
                    if (moreType != null && !row.title.startsWith("♥")) {
                        item(key = "__more") {
                            MoreTile(tv = moreType == "tv") {
                                viewModel.setTab(if (moreType == "tv") BrowseTab.TV else BrowseTab.MOVIES)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sky-style full-bleed hero: the featured title's wide backdrop runs edge to
 * edge behind the top of the page with the title overlaid, fading into the
 * background so the rows ride over it. DPAD left/right steps through the
 * trending titles (auto-cycle pauses after manual browsing); OK opens details.
 * The category pill sits over the banner's top-left corner.
 */
@Composable
private fun HeroBanner(
    items: List<CatalogItem>,
    art: Map<String, String>,
    loadArt: (CatalogItem) -> Unit,
    onOpen: (CatalogItem) -> Unit,
    pill: @Composable () -> Unit,
) {
    var index by remember(items) { mutableStateOf(0) }
    var interactedAt by remember { mutableStateOf(0L) }
    LaunchedEffect(items) {
        while (true) {
            delay(12_000)
            if (System.currentTimeMillis() - interactedAt > 20_000) {
                index = (index + 1) % items.size
            }
        }
    }
    val item = items[index]
    // Warm the featured and next backdrops so cycling never pops in late art.
    LaunchedEffect(item) {
        loadArt(item)
        loadArt(items[(index + 1) % items.size])
    }
    val backdrop = art["${item.type}:${item.id}"].orEmpty()

    Surface(
        onClick = { onOpen(item) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.4f))),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(292.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        index = (index - 1 + items.size) % items.size
                        interactedAt = System.currentTimeMillis()
                        true
                    }
                    Key.DirectionRight -> {
                        index = (index + 1) % items.size
                        interactedAt = System.currentTimeMillis()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (backdrop.isNotBlank()) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF1B2534), ArcSurface)),
                    ),
                )
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 56.dp)
                        .width(150.dp)
                        .height(225.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                )
            }
            // Legibility scrims: fade to the page background below, darken the
            // left edge behind the text.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to ArcBackground,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to ArcBackground.copy(alpha = 0.86f),
                        0.45f to Color.Transparent,
                    ),
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 16.dp),
            ) {
                pill()
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 40.dp, end = 40.dp, bottom = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TRENDING",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(ArcBlue.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${index + 1} / ${items.size}   \u2039 \u203a browse   \u00b7   OK for details",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
                if (item.year.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.year + (if (item.isTv) "  \u00b7  Series" else "  \u00b7  Film"),
                        style = MaterialTheme.typography.titleSmall,
                        color = ArcBlue,
                    )
                }
                if (item.overview.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.55f),
                    )
                }
            }
        }
    }
}

private fun categoryLabel(tab: BrowseTab): String = when (tab) {
    BrowseTab.HOME -> "Home"
    BrowseTab.MOVIES -> "Movies"
    BrowseTab.TV -> "TV Shows"
}

/**
 * Slim category line: one dropdown pill for Home/Movies/TV Shows, joined by
 * sort and genre chips only while browsing the full catalogue.
 */
/** The "Home ▾" pill plus its category menu — usable on its own over the hero. */
@Composable
private fun CategoryPill(viewModel: BrowseViewModel) {
    val state by viewModel.state.collectAsState()
    var menu by remember { mutableStateOf(false) }
    ArcChip(
        label = "${categoryLabel(state.tab)}  ▾",
        selected = true,
        onClick = { menu = true },
    )
    if (menu) {
        SheetDialog("Browse", onDismiss = { menu = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BrowseTab.entries.forEach { tab ->
                    RowCard(
                        lead = if (state.tab == tab) "●" else "",
                        title = categoryLabel(tab),
                        trailing = "",
                        onClick = {
                            viewModel.setTab(tab)
                            menu = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBar(viewModel: BrowseViewModel) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryPill(viewModel)
            if (state.tab != BrowseTab.HOME) {
                Spacer(Modifier.width(18.dp))
                // Divider keeps the category pill visually apart from filters.
                Box(
                    Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(Color.White.copy(alpha = 0.14f)),
                )
                Spacer(Modifier.width(18.dp))
                SortMode.entries.forEach { mode ->
                    TextChip(label = mode.label, selected = state.sortMode == mode, onClick = { viewModel.setSort(mode) })
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        if (state.tab != BrowseTab.HOME && state.currentGenres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    TextChip(label = "All", selected = state.genreId == null, onClick = { viewModel.setGenre(null) })
                }
                items(state.currentGenres, key = { it.id }) { genre ->
                    TextChip(label = genre.name, selected = state.genreId == genre.id, onClick = { viewModel.setGenre(genre.id) })
                }
            }
        }
    }
}

/** Full-screen search, opened from the top bar's magnifier. */
@Composable
private fun SearchOverlay(viewModel: BrowseViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Search",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(24.dp))
                    TvSearchField(
                        query = state.query,
                        onQueryChange = viewModel::setQuery,
                        onSearch = viewModel::search,
                        modifier = Modifier.weight(1f),
                        placeholder = "Search films & shows…",
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onClose) { Text("Close") }
                }
                Spacer(Modifier.height(18.dp))
                if (state.searchResults != null) {
                    SearchResults(state.searchResults!!, state.searching, viewModel)
                } else {
                    Text(
                        "Type a title and press OK to search films and shows.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverGrid(viewModel: BrowseViewModel) {
    val state by viewModel.state.collectAsState()
    when {
        state.discoverItems.isEmpty() && state.discoverLoading -> CenteredMessage("Loading…")
        state.discoverItems.isEmpty() -> CenteredMessage("Nothing here.")
        else -> {
            val gridState = rememberLazyGridState()
            val count = state.discoverItems.size
            LaunchedEffect(gridState, count) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    .collect { last -> if (last >= count - 12) viewModel.loadMoreDiscover() }
            }
            // Sky's browse pattern: the focused tile is described above the grid.
            var focusedItem by remember { mutableStateOf<CatalogItem?>(null) }
            Column(Modifier.fillMaxSize()) {
                FocusDetailHeader(focusedItem)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(state.discoverItems, key = { it.type + it.id }) { item ->
                        PosterTile(item, Modifier, onFocus = { focusedItem = it }) {
                            viewModel.openDetails(item)
                        }
                    }
                }
            }
        }
    }
}

/** Sky's browse header: whichever tile the focus rests on is described here. */
@Composable
private fun FocusDetailHeader(item: CatalogItem?) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).heightIn(min = 78.dp)) {
        if (item == null) {
            Text(
                "Move around the grid — details appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                listOfNotNull(
                    item.year.takeIf { it.isNotBlank() },
                    if (item.isTv) "Series" else "Film",
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = ArcBlue,
            )
        }
        if (item.overview.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                item.overview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }
    }
}

@Composable
private fun SearchResults(results: List<CatalogItem>, searching: Boolean, viewModel: BrowseViewModel) {
    when {
        searching && results.isEmpty() -> CenteredMessage("Searching…")
        results.isEmpty() -> CenteredMessage("No results.")
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(results, key = { it.type + it.id }) { item ->
                PosterTile(item, Modifier, showType = true) { viewModel.openDetails(item) }
            }
        }
    }
}

/** Row closer in the style of Sky's "More Top Picks" tile. */
@Composable
private fun MoreTile(tv: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(150.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "MORE",
                    style = MaterialTheme.typography.titleMedium,
                    color = ArcBlue,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (tv) "TV Shows" else "Movies",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text("→", style = MaterialTheme.typography.titleMedium, color = ArcBlue)
            }
        }
    }
}

@Composable
private fun PosterTile(
    item: CatalogItem,
    modifier: Modifier = Modifier,
    showType: Boolean = false,
    onFocus: ((CatalogItem) -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.let { m ->
            if (onFocus == null) m
            else m.onFocusChanged { if (it.isFocused) onFocus(item) }
        },
    ) {
        // Strict 2:3 poster with the title on a bottom scrim — no text block
        // below fighting the artwork for height.
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.year.isNotBlank()) {
                    Text(
                        item.year,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            if (showType) {
                Text(
                    if (item.isTv) "TV" else "Film",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ---- Details page ----

@Composable
private fun DetailsScreen(ui: BrowseViewModel.DetailUi, viewModel: BrowseViewModel) {
    val item = ui.item
    val d = ui.details
    val title = d?.title?.takeIf { it.isNotBlank() } ?: item.title
    val year = d?.year?.takeIf { it.isNotBlank() } ?: item.year
    val overview = d?.overview?.takeIf { it.isNotBlank() } ?: item.overview
    val backdrop = d?.backdrop?.takeIf { it.isNotBlank() }
    val tagline = d?.tagline
    val bg = MaterialTheme.colorScheme.background
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.id) { runCatching { playFocus.requestFocus() } }

    val meta = buildList {
        if (year.isNotBlank()) add(year)
        if (item.isTv) add(d?.seasons?.let { "$it season${if (it == 1) "" else "s"}" } ?: "TV")
        d?.runtime?.let { if (it > 0) add(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m") }
        d?.rating?.let { if (it > 0) add("★ %.1f".format(it)) }
    }.joinToString("   •   ")

    Dialog(
        onDismissRequest = { viewModel.closeDetails() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(bg)) {
            if (backdrop != null) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxHeight(0.75f).fillMaxWidth().align(Alignment.TopEnd),
                )
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(bg, bg.copy(alpha = 0.55f), Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, bg))))
            }

            Column(Modifier.fillMaxSize().padding(48.dp)) {
                OutlinedButton(onClick = { viewModel.closeDetails() }) { Text("← Back") }
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth(0.62f)) {
                    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(meta, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!tagline.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(tagline, style = MaterialTheme.typography.bodyMedium, color = ArcBlue)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { viewModel.playFromDetails() },
                            modifier = Modifier.focusRequester(playFocus),
                            colors = ButtonDefaults.colors(containerColor = ArcBlue, contentColor = MaterialTheme.colorScheme.onPrimary),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (item.isTv) "Play — pick an episode" else "Play")
                        }
                        Spacer(Modifier.width(12.dp))
                        val browseState by viewModel.state.collectAsState()
                        val fav = browseState.isFavorite(item)
                        OutlinedButton(onClick = { viewModel.toggleFavorite(item) }) {
                            Text(if (fav) "♥ Favourited" else "♡ Favourite")
                        }
                    }
                    if (overview.isNotBlank()) {
                        Spacer(Modifier.height(20.dp))
                        Text(overview, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    if (ui.loading) {
                        Spacer(Modifier.height(12.dp))
                        Text("Loading details…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---- Selection sheets ----

@Composable
private fun SheetHost(sheet: BrowseViewModel.Sheet, viewModel: BrowseViewModel) {
    when (val s = sheet) {
        BrowseViewModel.Sheet.Hidden -> Unit

        is BrowseViewModel.Sheet.Loading -> SheetDialog("Please wait", { viewModel.dismissSheet() }) {
            Text(s.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is BrowseViewModel.Sheet.Error -> SheetDialog("Something went wrong", { viewModel.dismissSheet() }) {
            Text(s.message)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { viewModel.dismissSheet() }) { Text("Close") }
        }

        is BrowseViewModel.Sheet.Seasons -> SheetDialog(s.item.title, { viewModel.dismissSheet() }) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                items(s.seasons, key = { it.number }) { season ->
                    RowCard(
                        lead = "S${season.number}",
                        title = season.name,
                        trailing = "${season.episodeCount} eps",
                        onClick = { viewModel.selectSeason(s.item, season.number) },
                    )
                }
            }
        }

        is BrowseViewModel.Sheet.Episodes -> SheetDialog(
            "${s.item.title} · Season ${s.season}", { viewModel.dismissSheet() },
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                items(s.episodes, key = { it.episode }) { ep ->
                    RowCard(
                        lead = "E%02d".format(ep.episode),
                        title = ep.name,
                        trailing = "",
                        onClick = { viewModel.selectEpisode(s.item, s.season, ep.episode) },
                    )
                }
            }
        }

        is BrowseViewModel.Sheet.Sources -> SheetDialog(sourcesTitle(s), { viewModel.dismissSheet() }) {
            if (s.sources.isEmpty() && s.loadingMore) {
                Text("Searching for sources…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@SheetDialog
            }
            if (s.playing != null) {
                Text("Starting playback…", color = ArcBlue, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.autoPlay() }, enabled = s.playing == null) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-play best")
                }
                val nextEp = s.next
                if (nextEp != null) {
                    Spacer(Modifier.width(12.dp))
                    val label = if (nextEp.season != s.season) {
                        "Next: S%02dE%02d".format(nextEp.season, nextEp.episode)
                    } else {
                        "Next: E%02d".format(nextEp.episode)
                    }
                    OutlinedButton(
                        onClick = { viewModel.playNextEpisode() },
                        enabled = s.playing == null,
                    ) {
                        Text(label)
                    }
                }
            }
            s.note?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B))
            }
            if (s.loadingMore) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Finding more sources…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 340.dp)) {
                items(s.sources, key = { it.magnet }) { source ->
                    SourceRow(source, playing = s.playing == source.magnet) {
                        viewModel.playSource(source)
                    }
                }
            }
        }
    }
}

private fun sourcesTitle(s: BrowseViewModel.Sheet.Sources): String {
    val base = s.item.title
    return if (s.season != null && s.episode != null) {
        "$base · S%02dE%02d".format(s.season, s.episode)
    } else {
        base
    }
}

@Composable
private fun RowCard(lead: String, title: String, trailing: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(lead, style = MaterialTheme.typography.labelLarge, color = ArcBlue, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (trailing.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceRow(source: Source, playing: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                source.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(qualityLabel(source.title), style = MaterialTheme.typography.labelSmall, color = ArcBlue, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                if (source.size.isNotBlank()) {
                    Text(source.size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                }
                Text("▲ ${source.seeds}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (source.cached) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "✓ Cached",
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color(0xFF4CD964),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (playing) Text("Playing…", style = MaterialTheme.typography.labelSmall, color = ArcBlue)
            }
        }
    }
}

private fun qualityLabel(title: String): String {
    val t = title.lowercase()
    val res = when {
        t.contains("2160p") || t.contains("uhd") || t.contains(" 4k") -> "4K"
        t.contains("1080p") -> "1080p"
        t.contains("720p") -> "720p"
        else -> "SD"
    }
    val src = when {
        t.contains("remux") -> "REMUX"
        t.contains("bluray") || t.contains("blu-ray") || t.contains("bdrip") -> "BluRay"
        t.contains("web-dl") || t.contains("webdl") -> "WEB-DL"
        t.contains("webrip") || t.contains(".web.") -> "WEBRip"
        t.contains("hdrip") -> "HDRip"
        t.contains("cam") || t.contains("telesync") || t.contains("hdts") || t.contains("dcprip") || t.contains("hdscr") -> "CAM"
        else -> ""
    }
    return if (src.isBlank()) res else "$res · $src"
}

@Composable
private fun SheetDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(28.dp).width(560.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                content()
            }
        }
    }
}
