package com.arcsus.arctv.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
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
fun BrowseScreen(viewModel: BrowseViewModel) {
    val state by viewModel.state.collectAsState()
    val sheet by viewModel.sheet.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val playRequest by viewModel.playRequest.collectAsState()
    val context = LocalContext.current

    // The next episode we're offering to auto-advance to, and the countdown.
    var autoNext by remember { mutableStateOf<NextEp?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }

    // Launch the external player *for result*. Players that report completion
    // (MX Player) auto-advance instantly. Players that report nothing (VLC) fall
    // back to an "Up next" countdown shown when you return.
    val playLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        when {
            playbackCompleted(data) -> viewModel.playNextEpisode()
            hasPlaybackInfo(data) -> Unit // player says stopped early — don't advance
            else -> (viewModel.sheet.value as? BrowseViewModel.Sheet.Sources)?.next?.let {
                autoNext = it
            }
        }
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
            val launched = try {
                playLauncher.launch(buildPlayIntentForResult(stream.streamUrl, stream.filename))
                true
            } catch (e: android.content.ActivityNotFoundException) {
                false
            }
            if (!launched) {
                android.widget.Toast.makeText(
                    context, "No video player installed.", android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            viewModel.consumePlayRequest()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text("Browse", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
            TvSearchField(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                onSearch = viewModel::search,
                modifier = Modifier.weight(1f),
                placeholder = "Search films & shows…",
            )
            if (state.searchResults != null) {
                Spacer(Modifier.width(12.dp))
                Button(onClick = { viewModel.clearSearch() }) { Text("Clear") }
            }
        }

        if (state.searchResults == null) FilterBar(viewModel)

        when {
            state.searchResults != null -> SearchResults(state.searchResults!!, state.searching, viewModel)
            state.tab != BrowseTab.HOME -> DiscoverGrid(viewModel)
            state.loadingHome && state.rows.isEmpty() -> CenteredMessage("Loading catalogue…")
            state.error != null && state.rows.isEmpty() -> CenteredError(state.error!!) { viewModel.loadHome() }
            else -> CatalogRows(viewModel)
        }
    }

    detail?.let { d -> DetailsScreen(d, viewModel) }

    SheetHost(sheet, viewModel)

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
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.rows, key = { it.title }) { row ->
            Column {
                Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(row.items, key = { it.type + it.id }) { item ->
                        PosterTile(item, Modifier.width(140.dp)) { viewModel.openDetails(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(viewModel: BrowseViewModel) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrowseTab.entries.forEach { tab ->
                Chip(
                    label = when (tab) {
                        BrowseTab.HOME -> "Home"
                        BrowseTab.MOVIES -> "Movies"
                        BrowseTab.TV -> "TV Shows"
                    },
                    selected = state.tab == tab,
                    onClick = { viewModel.setTab(tab) },
                )
            }
            if (state.tab != BrowseTab.HOME) {
                Spacer(Modifier.width(24.dp))
                SortMode.entries.forEach { mode ->
                    Chip(label = mode.label, selected = state.sortMode == mode, onClick = { viewModel.setSort(mode) })
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        if (state.tab != BrowseTab.HOME && state.currentGenres.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Chip(label = "All", selected = state.genreId == null, onClick = { viewModel.setGenre(null) })
                }
                items(state.currentGenres, key = { it.id }) { genre ->
                    Chip(label = genre.name, selected = state.genreId == genre.id, onClick = { viewModel.setGenre(genre.id) })
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(state.discoverItems, key = { it.type + it.id }) { item ->
                    PosterTile(item, Modifier) { viewModel.openDetails(item) }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.colors(containerColor = ArcBlue, contentColor = MaterialTheme.colorScheme.onPrimary),
        ) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
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
                PosterTile(item, Modifier) { viewModel.openDetails(item) }
            }
        }
    }
}

@Composable
private fun PosterTile(item: CatalogItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    if (item.isTv) "TV" else "Film",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.year.isNotBlank()) {
                    Text(item.year, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Button(
                        onClick = { viewModel.playFromDetails() },
                        modifier = Modifier.focusRequester(playFocus),
                        colors = ButtonDefaults.colors(containerColor = ArcBlue, contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (item.isTv) "Play — pick an episode" else "Play")
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
