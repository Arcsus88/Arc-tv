package com.arcsus.arctv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
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
    val playRequest by viewModel.playRequest.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(playRequest) {
        playRequest?.let { stream ->
            if (!playVideo(context, stream.streamUrl, stream.filename)) {
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
            SearchField(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                onSearch = viewModel::search,
                modifier = Modifier.weight(1f),
            )
            if (state.searchResults != null) {
                Spacer(Modifier.width(12.dp))
                Button(onClick = { viewModel.clearSearch() }) { Text("Clear") }
            }
        }

        when {
            state.searchResults != null -> SearchResults(state.searchResults!!, state.searching, viewModel)
            state.loadingHome && state.rows.isEmpty() -> CenteredMessage("Loading catalogue…")
            state.error != null && state.rows.isEmpty() -> CenteredError(state.error!!) { viewModel.loadHome() }
            else -> CatalogRows(viewModel)
        }
    }

    SheetHost(sheet, viewModel)
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
                        PosterTile(item, Modifier.width(140.dp)) { viewModel.openTitle(item) }
                    }
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
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(results, key = { it.type + it.id }) { item ->
                PosterTile(item, Modifier) { viewModel.openTitle(item) }
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
            Button(onClick = { viewModel.autoPlay() }, enabled = s.playing == null) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Auto-play best")
            }
            s.note?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B))
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
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(24.dp), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search films & shows…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(ArcBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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
