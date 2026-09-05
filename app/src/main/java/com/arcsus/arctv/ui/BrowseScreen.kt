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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
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
import coil.request.ImageRequest
import androidx.tv.material3.Glow
import androidx.tv.material3.CardDefaults
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
        val playerLabel = remember(stream) { resolvePlayerLabel(context, stream.streamUrl) }
        var probe by remember(stream) { mutableStateOf("Checking the link…") }
        LaunchedEffect(stream) {
            probe = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                probeStream(stream.streamUrl)
            }
        }
        SheetDialog("That didn't play", onDismiss = { quickExit = null }) {
            Text(
                stream.filename.ifBlank { "(no filename)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "from $host" + (playerLabel?.let { "   ·   player: $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                probe,
                style = MaterialTheme.typography.bodySmall,
                color = ArcBlue,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "The player closed within seconds of starting. Try again, or pick a " +
                    "different source from the list.",
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
    // Lazy lists crash outright on a repeated key, and a catalogue row can
    // legitimately carry the same title twice (two providers, one film). Dedupe
    // rows and their contents before they become keys.
    val rows = (
        if (state.favorites.isEmpty()) state.rows
        else listOf(com.arcsus.arctv.data.CatalogRow("♥ Favourites", state.favorites)) + state.rows
        )
        .distinctBy { it.title }
        .map { row -> row.copy(items = row.items.distinctBy { it.type + it.id }) }
    val heroItems = state.rows.firstOrNull()?.items?.take(10).orEmpty()
    // Titled key art for every tile on the page (posters stand in until it
    // arrives, and for titles that have none).
    val tileArt by viewModel.tileArt.collectAsState()
    LaunchedEffect(rows, state.continueWatching) {
        viewModel.loadTileArt(rows.flatMap { it.items } + state.continueWatching.mapNotNull { it.item })
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val showHero = heroItems.isNotEmpty() && rows.isNotEmpty()
        if (showHero) {
            item(key = "__hero") {
                val heroArt by viewModel.heroArt.collectAsState()
                HeroBanner(
                    items = heroItems,
                    art = heroArt,
                    loadArt = viewModel::loadHeroArt,
                    onOpen = { viewModel.openDetails(it) },
                    rowTitle = rows.first().title,
                    rowItems = rows.first().items,
                    tileArt = tileArt,
                    onMore = { tv -> viewModel.setTab(if (tv) BrowseTab.TV else BrowseTab.MOVIES) },
                )
            }
        }
        // Netflix's "Continue watching": whatever played last, one press
        // from playing again. Films reopen their sources, series the next
        // episode, channels tune straight in. Hold OK on a tile to drop it.
        if (state.continueWatching.isNotEmpty()) {
            item(key = "__continue") {
                ContinueWatchingRow(state.continueWatching, tileArt, viewModel)
            }
        }
        items(if (showHero) rows.drop(1) else rows, key = { it.title }) { row ->
            Column {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
                ) {
                    items(row.items, key = { it.type + it.id }) { item ->
                        LandscapeTile(item, tileArt["${item.type}:${item.id}"], Modifier.width(LANDSCAPE_WIDTH)) {
                            viewModel.openDetails(item)
                        }
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
 * Sky-style full-bleed hero: the featured title's backdrop runs edge to edge
 * with the first content row riding its fade — Sky's "Today's top picks on
 * the artwork". The info block is its own focus target (DPAD left/right
 * steps titles, OK opens details) so steering never hijacks the row below.
 */
@Composable
private fun HeroBanner(
    items: List<CatalogItem>,
    art: Map<String, String>,
    loadArt: (CatalogItem) -> Unit,
    onOpen: (CatalogItem) -> Unit,
    rowTitle: String,
    rowItems: List<CatalogItem>,
    tileArt: Map<String, String>,
    onMore: (Boolean) -> Unit,
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
    val backdrop = art["${item.type}:${item.id}"].orEmpty().ifBlank { item.backdrop }

    // The billboard takes the whole first screen, Sky Glass-style: artwork
    // edge to edge, the title block in the top-left, and the first rail of
    // posters riding the fade at the foot. Rows below scroll up over it.
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        if (backdrop.isNotBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Keep the art where the mask is opaque; let the page
                        // gradient show through at the foot and the left.
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black, 0.4f to Color.Black, 1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.08f), 0.38f to Color.Black.copy(alpha = 0.55f),
                                0.7f to Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF1B2534), ArcSurface)),
                ),
            )
        }
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
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(HERO_INFO_HEIGHT)
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
            Column(Modifier.padding(start = 40.dp, top = 36.dp, end = 40.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
                if (item.year.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.year + (if (item.isTv) "  \u00b7  Series" else "  \u00b7  Film"),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                if (item.overview.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                // Where you are in the billboard: quiet dots, the current one
                // a short white bar. Left/right on the D-pad moves along it.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    items.forEachIndexed { i, _ ->
                        Box(
                            Modifier
                                .height(4.dp)
                                .width(if (i == index) 22.dp else 6.dp)
                                .background(
                                    Color.White.copy(alpha = if (i == index) 0.95f else 0.32f),
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
            }
        }

        // The first content row rides the artwork's fade, Sky-style.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                rowTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            Spacer(Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
            ) {
                items(rowItems, key = { it.type + it.id }) { rowItem ->
                    LandscapeTile(rowItem, tileArt["${rowItem.type}:${rowItem.id}"], Modifier.width(LANDSCAPE_WIDTH)) {
                        onOpen(rowItem)
                    }
                }
                val moreType = rowItems.firstOrNull()?.type
                if (moreType != null && !rowTitle.startsWith("\u2665")) {
                    item(key = "__more") {
                        MoreTile(tv = moreType == "tv") { onMore(moreType == "tv") }
                    }
                }
            }
        }
    }
}

/**
 * One row above the grid: how to sort, then the genre as a single drop-down
 * chip. A strip of every genre plus the focused title's synopsis used to sit
 * here and squeezed the posters to a sliver; the synopsis now waits on the
 * details page, one press away.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryBar(viewModel: BrowseViewModel) {
    val state by viewModel.state.collectAsState()
    var pickingGenre by remember { mutableStateOf(false) }
    val genreName = state.currentGenres.firstOrNull { it.id == state.genreId }?.name
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        SortMode.entries.forEach { mode ->
            TextChip(label = mode.label, selected = state.sortMode == mode, onClick = { viewModel.setSort(mode) })
            Spacer(Modifier.width(6.dp))
        }
        if (state.currentGenres.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            TextChip(
                label = (genreName ?: "All genres") + "  ▾",
                selected = genreName != null,
                onClick = { pickingGenre = true },
            )
        }
    }
    if (pickingGenre) {
        val currentFocus = remember { FocusRequester() }
        SheetDialog("Genre", onDismiss = { pickingGenre = false }) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextChip(
                    label = "All genres",
                    selected = state.genreId == null,
                    onClick = { viewModel.setGenre(null); pickingGenre = false },
                    modifier = if (state.genreId == null) Modifier.focusRequester(currentFocus) else Modifier,
                )
                state.currentGenres.forEach { genre ->
                    TextChip(
                        label = genre.name,
                        selected = state.genreId == genre.id,
                        onClick = { viewModel.setGenre(genre.id); pickingGenre = false },
                        modifier = if (state.genreId == genre.id) Modifier.focusRequester(currentFocus) else Modifier,
                    )
                }
            }
        }
        LaunchedEffect(Unit) { currentFocus.requestFocusWhenReady() }
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
            // Posters, and nothing else: the title's details are on the
            // page that opens when a poster is pressed.
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(state.discoverItems, key = { it.type + it.id }) { item ->
                    WideTile(item, Modifier) { viewModel.openDetails(item) }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(results: List<CatalogItem>, searching: Boolean, viewModel: BrowseViewModel) {
    when {
        searching && results.isEmpty() -> CenteredMessage("Searching…")
        results.isEmpty() -> CenteredMessage("No results.")
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(results.distinctBy { it.type + it.id }, key = { it.type + it.id }) { item ->
                WideTile(item, Modifier, showType = true) { viewModel.openDetails(item) }
            }
        }
    }
}

/** Row closer in the style of Sky's "More Top Picks" tile. */
/** Poster tile width in rows; the grids size theirs from their columns. */
private val POSTER_WIDTH = 168.dp

/** Sky Q's home rails use landscape tiles: about four across the content. */
private val LANDSCAPE_WIDTH = 236.dp

/**
 * A Sky Q "top picks" tile: 16:9 artwork, a small badge and the title in
 * the bottom-left over a scrim, a white outline on focus.
 */
/**
 * A Sky-style landscape tile. [art] is the titled key art: null while it is
 * still being fetched (the tile stays blank rather than flashing the wrong
 * picture), "" when the title has none -- then the poster stands in, since
 * a textless scene still reads as the wrong film.
 */
@Composable
private fun LandscapeTile(item: CatalogItem, art: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = shape),
        ),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val picture = when {
                art == null -> null
                art.isNotBlank() -> art
                else -> item.poster.ifBlank { item.backdrop }
            }
            if (picture != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(picture)
                        .crossfade(180)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))),
                    ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    if (item.isTv) "Series" else "Film",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    entries: List<com.arcsus.arctv.data.WatchEntry>,
    tileArt: Map<String, String>,
    viewModel: BrowseViewModel,
) {
    val playLive = rememberLivePlay()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 40.dp)) {
            Text(
                "Continue watching",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                "Hold OK to remove",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
        ) {
            items(entries, key = { it.key }) { entry ->
                ContinueTile(
                    entry = entry,
                    art = entry.item?.let { tileArt["${it.type}:${it.id}"] },
                    onOpen = {
                        val channel = entry.channel
                        if (entry.kind == "live" && channel != null) {
                            playLive(
                                listOf(
                                    com.arcsus.arctv.data.LiveChannel(
                                        id = "cw:${channel.url}",
                                        name = channel.name,
                                        logo = channel.logo,
                                        group = entry.group,
                                        url = channel.url,
                                    ),
                                ),
                                0,
                            )
                        } else {
                            viewModel.resumeWatch(entry)
                        }
                    },
                    onRemove = { viewModel.removeWatch(entry.key) },
                )
            }
        }
    }
}

/**
 * A Continue Watching tile: the title's wide artwork with what comes next
 * ("Next up  S2 E6"), or a channel's logo on the panel colour with "Live".
 */
@Composable
private fun ContinueTile(
    entry: com.arcsus.arctv.data.WatchEntry,
    art: String?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val item = entry.item
    val channel = entry.channel
    Card(
        onClick = onOpen,
        onLongClick = onRemove,
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = shape),
        ),
        modifier = Modifier.width(LANDSCAPE_WIDTH),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item != null) {
                val picture = if (art.isNullOrBlank()) item.poster.ifBlank { item.backdrop } else art
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(picture)
                        .crossfade(180)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (channel != null) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        modifier = Modifier.size(56.dp).align(Alignment.Center),
                    )
                } else {
                    Text(
                        channel.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ArcBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))),
                    ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
                val badge = when {
                    entry.kind == "live" -> "Live"
                    entry.kind == "tv" && entry.nextSeason != null && entry.nextEpisode != null ->
                        "Next up  S${entry.nextSeason} E${entry.nextEpisode}"
                    entry.kind == "tv" && entry.season != null && entry.episode != null ->
                        "S${entry.season} E${entry.episode}"
                    entry.kind == "tv" -> "Series"
                    else -> "Film"
                }
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.kind == "live") ArcBlue else Color.White,
                    fontWeight = if (entry.kind == "live") FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The billboard fills the first screen (540dp minus the overscan inset). */
private val HERO_HEIGHT = 486.dp
private val HERO_INFO_HEIGHT = 186.dp

@Composable
private fun MoreTile(tv: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.07f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        modifier = Modifier.width(LANDSCAPE_WIDTH),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
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

/**
 * Sky Glass content tile: wide 16:9 artwork with the title overlaid on a
 * bottom scrim (and the Film/TV badge above it in mixed lists). Falls back
 * to a centre crop of the poster when no wide art exists.
 */
@Composable
private fun WideTile(
    item: CatalogItem,
    modifier: Modifier = Modifier,
    showType: Boolean = false,
    onFocus: ((CatalogItem) -> Unit)? = null,
    onClick: () -> Unit,
) {
    // Poster-shaped (2:3), like the artwork itself: taller, bigger, and the
    // title sits on a scrim at the foot. Focus lifts the tile with a soft
    // accent glow and a white keyline.
    val shape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.07f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = ArcBlue.copy(alpha = 0.55f), elevation = 14.dp),
        ),
        modifier = modifier.let { m ->
            if (onFocus == null) m
            else m.onFocusChanged { if (it.isFocused) onFocus(item) }
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.poster.ifBlank { item.backdrop })
                    .crossfade(180)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                if (showType) {
                    Text(
                        if (item.isTv) "TV" else "Film",
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.year.isNotBlank()) {
                    Text(
                        item.year,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
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
                items(s.sources.distinctBy { it.magnet }, key = { it.magnet }) { source ->
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
