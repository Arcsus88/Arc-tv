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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arcsus.arctv.data.CatalogItem
import com.arcsus.arctv.ui.theme.ArcMuted
import java.text.Normalizer

/** Posters across a grid: the web's lg:grid-cols-6. */
internal const val POSTER_COLUMNS = 6

/** Lower-case, accent-free, "&" as "and": what the web's search compares. */
private fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/**
 * A collection's page, after the web's CollectionView: every Disney-branded
 * and Pixar feature with its pre-searched best source (films with nothing
 * seeded are greyed out), or the films and series on Netflix UK, Prime
 * Video UK or Apple TV UK, which search live. The whole catalogue is loaded,
 * so search filters as you type and the sort is instant; the collections
 * with series get All / Films / Series chips.
 */
@Composable
internal fun CollectionScreen(viewModel: BrowseViewModel, collection: CatalogCollection) {
    val state by viewModel.state.collectAsState()
    val titles = state.collections[collection.key]
    val error = state.collectionErrors[collection.key]
    val sort = state.collectionSorts[collection.key] ?: CollectionSort.POPULAR
    val query = state.collectionQuery
    val kind = state.collectionKind

    Column(Modifier.fillMaxSize().padding(horizontal = TV_INSET_H)) {
        when {
            titles == null && error != null -> CenteredError(error) { viewModel.loadCollection(collection) }
            titles == null -> CenteredMessage("Loading ${collection.label}…")
            else -> {
                val shown = remember(titles, query, sort, kind) {
                    val words = norm(query).split(" ").filter { it.isNotEmpty() }
                    val list = titles.filter { t ->
                        if (kind.type != null && t.type != kind.type) return@filter false
                        if (words.isEmpty()) return@filter true
                        val hay = " ${norm(t.title)} ${t.year} "
                        words.all { hay.contains(it) }
                    }
                    // The backend already orders by popularity; the sorts are stable.
                    when (sort) {
                        CollectionSort.POPULAR -> list
                        CollectionSort.NEWEST -> list.sortedByDescending { it.year.toIntOrNull() ?: 0 }
                        CollectionSort.OLDEST -> list.sortedBy { it.year.toIntOrNull() ?: 9999 }
                        CollectionSort.AZ -> list.sortedBy { it.title.lowercase() }
                    }
                }
                val films = titles.count { it.type == "movie" }
                val series = titles.count { it.type == "tv" }
                val presearched = titles.any { it.scanned != null }
                val summary = buildList {
                    add("$films films")
                    if (series > 0) add("$series series")
                    if (presearched) add("${titles.count { it.ready }} ready to play")
                    titles.count { it.noSeeds }.takeIf { it > 0 }?.let { add("$it with no seeds") }
                    titles.count { it.scanned == false }.takeIf { it > 0 }?.let { add("$it still being checked") }
                }.joinToString(" · ")

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(collection.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArcMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (collection.hasSeries) {
                        CollectionKind.entries.forEach { k ->
                            ArcChip(k.label, selected = kind == k, onClick = { viewModel.setCollectionKind(k) })
                            Spacer(Modifier.width(8.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    CollectionSort.entries.forEach { s ->
                        ArcChip(s.label, selected = sort == s, onClick = { viewModel.setCollectionSort(collection, s) }, compact = true)
                        Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    TvSearchField(
                        query = query,
                        onQueryChange = viewModel::setCollectionQuery,
                        onSearch = {},
                        modifier = Modifier.weight(1f),
                        placeholder = "Search ${collection.label} ${collection.noun}…",
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (shown.isEmpty()) {
                    val what = when (kind) {
                        CollectionKind.FILMS -> "films"
                        CollectionKind.SERIES -> "series"
                        CollectionKind.ALL -> collection.noun
                    }
                    CenteredMessage(
                        "No ${collection.label} $what" + if (query.isNotBlank()) " match “${query.trim()}”." else " yet.",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(POSTER_COLUMNS),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // A film and a series can share a TMDB id.
                        items(shown, key = { it.type + it.id }) { t ->
                            PosterTile(t.item, noSeeds = t.noSeeds, showType = collection.hasSeries && kind == CollectionKind.ALL) {
                                viewModel.openDetails(t.item)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val PosterShape = RoundedCornerShape(8.dp)

/** Greyscale, for a film whose sources are all dead. */
private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * The web's PosterCard for a remote: the 2:3 poster alone (the details page
 * says the rest), lifted with Netflix's white keyline on focus. [noSeeds]
 * greys it out with a "No seeds" label, still pressable; [showType] tags
 * series in mixed lists.
 */
@Composable
internal fun PosterTile(
    item: CatalogItem,
    modifier: Modifier = Modifier,
    noSeeds: Boolean = false,
    showType: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(PosterShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, MaterialTheme.colorScheme.borderVariant), shape = PosterShape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = PosterShape),
        ),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            val picture = item.poster.ifBlank { item.backdrop }
            if (picture.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(picture).crossfade(180).build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    alpha = if (noSeeds) 0.4f else 1f,
                    colorFilter = if (noSeeds) Greyscale else null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ArcMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(10.dp),
                )
            }
            if (showType && item.isTv) {
                Text(
                    "SERIES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (noSeeds) {
                Text(
                    "NO SEEDS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}
