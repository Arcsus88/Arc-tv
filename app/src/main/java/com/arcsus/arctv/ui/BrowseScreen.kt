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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.data.BrowseResult
import com.arcsus.arctv.ui.theme.ArcBlue

@Composable
fun BrowseScreen(viewModel: BrowseViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text(
                "Browse",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(24.dp))
            BrowseSearchField(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                onSearch = viewModel::search,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { viewModel.search() }, enabled = !state.searching) {
                Text(if (state.searching) "Searching…" else "Search")
            }
        }

        when {
            state.searching && state.results.isEmpty() -> CenteredMessage("Searching torrents…")
            state.error != null && state.results.isEmpty() -> CenteredError(state.error!!) {
                viewModel.search()
            }
            !state.searched -> CenteredMessage("Search for a film or show to add it to Real-Debrid.")
            state.results.isEmpty() -> CenteredMessage("No results for \"${state.query}\".")
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.results, key = { it.magnet }) { result ->
                        BrowseCard(
                            result = result,
                            adding = result.magnet in state.adding,
                            added = result.magnet in state.added,
                            onClick = { viewModel.add(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseSearchField(
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
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Search films & shows…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
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
private fun BrowseCard(
    result: BrowseResult,
    adding: Boolean,
    added: Boolean,
    onClick: () -> Unit,
) {
    Card(onClick = onClick) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                PosterImage(rememberPosterUrl(result.title), Modifier.fillMaxSize())
                // Seed count, top-left.
                Text(
                    "▲ ${result.seeds}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                // Add state indicator, top-right.
                val (icon, tint) = when {
                    added -> Icons.Default.Check to ArcBlue
                    else -> Icons.Default.Add to MaterialTheme.colorScheme.onSurface
                }
                Icon(
                    icon,
                    contentDescription = if (added) "Added" else "Add",
                    tint = tint,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(4.dp)
                        .size(16.dp),
                )
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    cleanTitle(result.title),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        adding -> "Adding…"
                        added -> "Added ✓"
                        else -> "${result.size}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (added || adding) ArcBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun cleanTitle(raw: String): String =
    com.arcsus.arctv.data.FilenameParser.parse(raw)?.let { m ->
        buildString {
            append(m.title)
            m.year?.let { append(" (").append(it).append(")") }
            m.episodeLabel?.let { append(" ").append(it) }
        }
    } ?: raw
