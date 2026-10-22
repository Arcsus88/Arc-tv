package com.arcsus.arctv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import com.arcsus.arctv.ui.theme.ArcMuted
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import coil.request.ImageRequest
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arcsus.arctv.data.CatalogItem
import com.arcsus.arctv.data.Source
import com.arcsus.arctv.ui.theme.ArcBlue

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    searchOpen: Boolean = false,
    onSearchOpenChange: (Boolean) -> Unit = {},
    /** A home banner opening another section (a collection, or the guide). */
    onOpenSection: (String) -> Unit = {},
    /** Whether Home may put focus on its billboard (not while the menu has it). */
    canTakeFocus: () -> Boolean = { true },
) {
    val state by viewModel.state.collectAsState()
    val sheet by viewModel.sheet.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val playRequest by viewModel.playRequest.collectAsState()
    val context = LocalContext.current

    // The next episode we're offering to auto-advance to, and the countdown.
    var autoNext by remember { mutableStateOf<NextEp?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }

    // Arc's own player says when an episode played to the end.
    val openVideo = rememberVideoPlay(onPlayedToEnd = {
        (viewModel.sheet.value as? BrowseViewModel.Sheet.Sources)?.next?.let { autoNext = it }
    })

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
    // while means the video likely ended (offer the next episode). Arc's own
    // player needs none of this: it reports back, and shows its own errors.
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
            launchedAt = if (openVideo.inApp) 0L else System.currentTimeMillis()
            if (!openVideo(stream.streamUrl, stream.filename)) {
                launchedAt = 0L
                android.widget.Toast.makeText(
                    context, "No video player installed.", android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            viewModel.consumePlayRequest()
        }
    }

    Column(Modifier.fillMaxSize()) {
        val collection = state.tab.collection
        when {
            collection != null -> CollectionScreen(viewModel, collection)
            state.tab != BrowseTab.HOME -> Column(Modifier.fillMaxSize().padding(horizontal = TV_INSET_H)) {
                val searchFocus = remember { FocusRequester() }
                Spacer(Modifier.height(6.dp))
                CategoryBar(viewModel, searchFocus)
                if (state.searchResults != null || state.searching) {
                    SectionResults(viewModel)
                    // Back from the results: the grid again, and the box
                    // ready for another search. (On the menu, Back still
                    // means Home.)
                    BackHandler(enabled = canTakeFocus()) {
                        viewModel.clearSearch()
                        runCatching { searchFocus.requestFocus() }
                    }
                } else {
                    DiscoverGrid(viewModel)
                }
            }
            state.loadingHome && state.rows.isEmpty() -> CenteredMessage("Loading catalogue…")
            state.error != null && state.rows.isEmpty() -> CenteredError(state.error!!) { viewModel.loadHome() }
            else -> NetflixHome(viewModel, onOpenSection, canTakeFocus)
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
            val dts = hasDtsAudio(stream.filename)
            Text(
                if (dts) {
                    "This file has DTS audio, which the TV's own player can't decode -- it opens and " +
                        "quits. Open it with VLC, or pick a source with AC3, DD or AAC sound."
                } else {
                    "The player closed within seconds of starting. Try again, open it with a " +
                        "different player, or pick another source from the list."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (dts) androidx.compose.ui.graphics.Color(0xFFFFC96B) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    quickExit = null
                    lastStream = stream
                    launchedAt = if (openVideo.inApp) 0L else System.currentTimeMillis()
                    if (!openVideo(stream.streamUrl, stream.filename)) {
                        launchedAt = 0L
                        android.widget.Toast.makeText(
                            context, "No video player installed.", android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text("Try again") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = {
                    quickExit = null
                    lastStream = stream
                    launchedAt = System.currentTimeMillis()
                    if (!playVideoWith(context, stream.streamUrl, stream.filename)) launchedAt = 0L
                }) { Text("Open with…") }
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

/**
 * Movies / Shows: the sort as pills, then the genre as one drop-down pill
 * (the web's filter bar), and a search box like the collections' -- over a
 * grid of posters.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryBar(viewModel: BrowseViewModel, searchFocus: FocusRequester) {
    val state by viewModel.state.collectAsState()
    var pickingGenre by remember { mutableStateOf(false) }
    val genreName = state.currentGenres.firstOrNull { it.id == state.genreId }?.name
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            if (state.tab == BrowseTab.TV) "Shows" else "Movies",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(20.dp))
        if (state.currentGenres.isNotEmpty()) {
            ArcChip(
                label = (genreName ?: "All Genres") + "  ▾",
                selected = genreName != null,
                onClick = { pickingGenre = true },
            )
            Spacer(Modifier.width(14.dp))
        }
        SortMode.entries.forEach { mode ->
            ArcChip(label = mode.label, selected = state.sortMode == mode, onClick = { viewModel.setSort(mode) }, compact = true)
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.width(14.dp))
        TvSearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            onSearch = viewModel::search,
            modifier = Modifier.weight(1f).focusRequester(searchFocus),
            placeholder = if (state.tab == BrowseTab.TV) "Search shows…" else "Search movies…",
        )
    }
    if (pickingGenre) {
        val currentFocus = remember { FocusRequester() }
        SheetDialog(if (state.tab == BrowseTab.TV) "TV genres" else "Movie genres", onDismiss = { pickingGenre = false }) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextChip(
                    label = "All Genres",
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

/** Full-screen search, opened from the menu's magnifier. */
@Composable
private fun SearchOverlay(viewModel: BrowseViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().padding(horizontal = TV_INSET_H, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Search",
                        style = MaterialTheme.typography.headlineMedium,
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
                    SearchResults(state.searchResults!!, state.searching, viewModel, state.error)
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
        state.discoverItems.isEmpty() -> CenteredMessage(state.error ?: "Nothing here.")
        else -> {
            val gridState = rememberLazyGridState()
            val count = state.discoverItems.size
            LaunchedEffect(gridState, count) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    .collect { last -> if (last >= count - 18) viewModel.loadMoreDiscover() }
            }
            // Posters, and nothing else: the title's details are on the
            // page that opens when a poster is pressed.
            LazyVerticalGrid(
                columns = GridCells.Fixed(POSTER_COLUMNS),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(state.discoverItems, key = { it.type + it.id }) { item ->
                    PosterTile(item) { viewModel.openDetails(item) }
                }
            }
        }
    }
}

/**
 * Movies / Shows search: the matches of the section's own kind, in the
 * grid's place. The search itself spans both, so a film searched for under
 * Shows is pointed to Movies rather than reported missing.
 */
@Composable
private fun SectionResults(viewModel: BrowseViewModel) {
    val state by viewModel.state.collectAsState()
    val tv = state.tab == BrowseTab.TV
    val all = state.searchResults.orEmpty()
    val mine = all.filter { it.isTv == tv }
    if (mine.isEmpty() && all.isNotEmpty() && !state.searching) {
        CenteredMessage(
            if (tv) "No shows match “${state.query.trim()}”. ${all.size} found in Movies."
            else "No movies match “${state.query.trim()}”. ${all.size} found in Shows.",
        )
    } else {
        SearchResults(mine, state.searching, viewModel, state.error)
    }
}

@Composable
private fun SearchResults(
    results: List<CatalogItem>,
    searching: Boolean,
    viewModel: BrowseViewModel,
    error: String? = null,
) {
    when {
        searching && results.isEmpty() -> CenteredMessage("Searching…")
        results.isEmpty() -> CenteredMessage(error ?: "Nothing found. Try another title.")
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(POSTER_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(results.distinctBy { it.type + it.id }, key = { it.type + it.id }) { item ->
                PosterTile(item, showType = true) { viewModel.openDetails(item) }
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
    val backdrop = d?.backdrop?.takeIf { it.isNotBlank() } ?: item.backdrop.takeIf { it.isNotBlank() }
    val tagline = d?.tagline
    val bg = MaterialTheme.colorScheme.background
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.id) { playFocus.requestFocusWhenReady() }
    val browseState by viewModel.state.collectAsState()
    val fav = browseState.isFavorite(item)

    val meta = buildList {
        if (year.isNotBlank()) add(year)
        d?.rating?.let { if (it > 0) add("★ %.1f".format(it)) }
        if (item.isTv) add(d?.seasons?.let { "$it season${if (it == 1) "" else "s"}" } ?: "Series")
        d?.runtime?.let { if (it > 0 && !item.isTv) add(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m") }
    }.joinToString("   ·   ")

    // Netflix's details page: the artwork fills the right of the screen and
    // melts into the page, the title block and buttons sit on the left, and
    // Back closes it.
    Dialog(
        onDismissRequest = { viewModel.closeDetails() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(bg)) {
            if (backdrop != null) {
                Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.76f).fillMaxHeight()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(backdrop).crossfade(300).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(0f to bg, 0.18f to bg.copy(alpha = 0.9f), 0.55f to bg.copy(alpha = 0.3f), 0.85f to Color.Transparent),
                        ),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0.5f to Color.Transparent, 1f to bg),
                        ),
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.58f).padding(start = 48.dp, top = 32.dp, bottom = 32.dp),
            ) {
                KindLabel(if (item.isTv) "SERIES" else "FILM")
                Text(
                    title,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(meta, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                }
                val genres = d?.genres.orEmpty()
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(genres.take(4).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = ArcMuted)
                }
                if (!tagline.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                if (overview.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BillboardButton(
                        label = if (item.isTv) "Episodes" else "Play",
                        icon = Icons.Default.PlayArrow,
                        primary = true,
                        onClick = { viewModel.playFromDetails() },
                        onFocus = {},
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    BillboardButton(
                        label = if (fav) "In My List" else "My List",
                        icon = if (fav) Icons.Default.Check else Icons.Default.Add,
                        onClick = { viewModel.toggleFavorite(item) },
                        onFocus = {},
                    )
                }
                if (ui.loading) {
                    Spacer(Modifier.height(12.dp))
                    Text("Loading details…", style = MaterialTheme.typography.labelMedium, color = ArcMuted)
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
            } else if (s.stored) {
                Text(
                    "Searched ahead of time for the Disney collection.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.autoPlay() }, enabled = s.playing == null) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-play best")
                }
                if (s.stored) {
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { viewModel.searchLive() }, enabled = s.playing == null) {
                        Text("Search live")
                    }
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
                if (hasDtsAudio(source.title)) {
                    // The TV's built-in player has no DTS licence; flag it
                    // before the viewer finds out the hard way.
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "DTS audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color(0xFFFFC96B),
                        fontWeight = FontWeight.Bold,
                    )
                }
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

/** The web's panel: the surface colour, a hairline border, 16dp corners. */
@Composable
private fun SheetDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = Border(BorderStroke(1.dp, MaterialTheme.colorScheme.borderVariant), shape = RoundedCornerShape(16.dp)),
        ) {
            Column(Modifier.padding(28.dp).width(560.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(18.dp))
                content()
            }
        }
    }
}
