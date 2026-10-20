package com.arcsus.arctv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arcsus.arctv.data.CatalogItem
import com.arcsus.arctv.data.CatalogRow
import com.arcsus.arctv.data.TitleDetails
import com.arcsus.arctv.data.WatchEntry
import com.arcsus.arctv.ui.theme.ArcBackground
import com.arcsus.arctv.ui.theme.ArcBlue
import com.arcsus.arctv.ui.theme.Inter
import com.arcsus.arctv.ui.theme.ArcMuted
import kotlinx.coroutines.delay

// Home, laid out the way Netflix's TV app lays it out: the top of the screen
// is a details area for whatever has focus -- the billboard's featured title
// with Play / More info, or the tile you're on -- over that title's artwork;
// the rows scroll beneath it with the focused row always in the same place
// and the focused tile always first in its row ("fixed focus"), so the eye
// never has to chase the selection.

/** Height of the details area under the menu. */
private val DETAILS_HEIGHT = 214.dp

/** A row's title block above its tiles: where the pinned row's tiles start. */
private val ROW_LEAD = 36.dp

/** Netflix's landscape boxart: about five across the screen. */
internal val BOXART_WIDTH = 188.dp


/**
 * Scroll so the item being brought into view starts [leadPx] in from the
 * start of the list: Netflix's fixed focus position, for rows (each pinned
 * just under the details area) and for tiles (each pinned at the left).
 */
@OptIn(ExperimentalFoundationApi::class)
private class PinnedSpec(private val leadPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - leadPx
}

/** What the details area is describing. */
private sealed interface Spotlight {
    data class Featured(val item: CatalogItem) : Spotlight
    data class Title(val item: CatalogItem, val row: String, val rank: Int? = null) : Spotlight
    data class Banner(val banner: HomeBanner) : Spotlight
    data class Watch(val entry: WatchEntry) : Spotlight
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NetflixHome(
    viewModel: BrowseViewModel,
    onOpenSection: (String) -> Unit,
    canTakeFocus: () -> Boolean,
) {
    val state by viewModel.state.collectAsState()
    val tileArt by viewModel.tileArt.collectAsState()
    val heroArt by viewModel.heroArt.collectAsState()
    val heroDetails by viewModel.heroDetails.collectAsState()

    // Lazy lists crash outright on a repeated key, and a catalogue row can
    // legitimately carry the same title twice (two providers, one film).
    val rows = state.rows
        .distinctBy { it.title }
        .map { row -> row.copy(items = row.items.distinctBy { it.type + it.id }) }
    // The trending row becomes Netflix's Top 10 (the web's hero carousel row).
    val trendingIndex = rows.indexOfFirst { it.title.contains("trend", ignoreCase = true) }.coerceAtLeast(0)
    val trending = rows.getOrNull(trendingIndex)
    val otherRows = rows.filterIndexed { i, _ -> i != trendingIndex }
    // The billboard features one of today's top titles, a different one per
    // visit -- one with wide artwork, which the billboard is built around.
    val featured = remember(trending?.items?.firstOrNull()?.id) {
        val top = trending?.items?.take(5).orEmpty()
        top.filter { it.backdrop.isNotBlank() }.ifEmpty { top }.randomOrNull()
    }

    LaunchedEffect(rows, state.continueWatching, state.favorites) {
        viewModel.loadTileArt(
            rows.flatMap { it.items } + state.continueWatching.mapNotNull { it.item } + state.favorites,
        )
    }
    LaunchedEffect(featured) { featured?.let { viewModel.loadHeroArt(it) } }

    val disneyMeta = state.collections[CatalogCollection.DISNEY.key]?.let { titles ->
        "${titles.size} films · ${titles.count { it.ready }} ready to play"
    }
    val netflixMeta = state.collections[CatalogCollection.NETFLIX.key]?.let { titles ->
        "${titles.count { !it.item.isTv }} films · ${titles.count { it.item.isTv }} series"
    }
    val primeMeta = state.collections[CatalogCollection.PRIME.key]?.let { titles ->
        "${titles.count { !it.item.isTv }} films · ${titles.count { it.item.isTv }} series"
    }
    val appleTvMeta = state.collections[CatalogCollection.APPLETV.key]?.let { titles ->
        "${titles.count { !it.item.isTv }} films · ${titles.count { it.item.isTv }} series"
    }
    val huluMeta = state.collections[CatalogCollection.HULU.key]?.let { titles ->
        "${titles.count { !it.item.isTv }} films · ${titles.count { it.item.isTv }} series"
    }
    val openSection by rememberUpdatedState(onOpenSection)
    // Remembered: the banners (and the spotlight holding one) must stay the
    // same objects across recompositions, or reporting the one in view
    // recomposes, rebuilds them, and reports again, forever.
    val banners = remember(disneyMeta, netflixMeta, primeMeta, appleTvMeta, huluMeta) {
        homeBanners(
            disneyMeta = disneyMeta,
            netflixMeta = netflixMeta,
            primeMeta = primeMeta,
            appleTvMeta = appleTvMeta,
            huluMeta = huluMeta,
            onDisney = { openSection(Section.DISNEY) },
            onSkySports = { openSection(Section.GUIDE) },
            onNetflix = { openSection(Section.NETFLIX) },
            onPrime = { openSection(Section.PRIME) },
            onAppleTv = { openSection(Section.APPLE_TV) },
            onHulu = { openSection(Section.HULU) },
        )
    }

    var spotlight by remember { mutableStateOf<Spotlight?>(null) }
    val shown = spotlight ?: featured?.let { Spotlight.Featured(it) }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(featured != null) {
        // First arrival: Netflix opens with focus on the billboard's Play.
        if (featured != null && canTakeFocus()) playFocus.requestFocusWhenReady()
    }

    Box(Modifier.fillMaxSize()) {
        SpotlightBackdrop(shown, heroArt, tileArt)

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(DETAILS_HEIGHT).padding(horizontal = TV_INSET_H)) {
                SpotlightDetails(
                    spotlight = shown,
                    featured = featured,
                    details = (shown as? Spotlight.Featured)?.item?.let { heroDetails["${it.type}:${it.id}"] },
                    favourite = featured?.let { state.isFavorite(it) } == true,
                    playFocus = playFocus,
                    onFeaturedFocus = { featured?.let { spotlight = Spotlight.Featured(it) } },
                    onPlay = { viewModel.openTitle(it) },
                    onMoreInfo = { viewModel.openDetails(it) },
                    onToggleList = { viewModel.toggleFavorite(it) },
                )
            }

            val density = LocalDensity.current
            val rowPin = remember(density) { PinnedSpec(with(density) { ROW_LEAD.toPx() }) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowPin) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // Room below the last row, so it too can rise to the pinned spot.
                    contentPadding = PaddingValues(bottom = 220.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    if (banners.isNotEmpty()) {
                        item(key = "__banners") {
                            HomeRow(title = null) {
                                HomeBannerRow(
                                    banners = banners,
                                    onShowing = { spotlight = Spotlight.Banner(it) },
                                    // The row runs edge to edge, so banners scroll
                                    // out under the screen's margins; it keeps
                                    // its tiles inside them itself.
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        // UP goes back to the billboard's Play, as on Netflix.
                                        .focusProperties { up = playFocus },
                                )
                            }
                        }
                    }
                    if (state.continueWatching.isNotEmpty()) {
                        item(key = "__continue") {
                            HomeRow("Continue Watching") {
                                TileRow {
                                    items(state.continueWatching, key = { it.key }) { entry ->
                                        ContinueTile(
                                            entry = entry,
                                            art = entry.item?.let { tileArt["${it.type}:${it.id}"] },
                                            viewModel = viewModel,
                                            onFocus = { spotlight = Spotlight.Watch(entry) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (trending != null && trending.items.isNotEmpty()) {
                        item(key = "__top10") {
                            val title = "Top 10 " + trending.title.replace(Regex("(?i)^trending\\s*"), "").ifBlank { "Today" }
                            HomeRow(title) {
                                TileRow(lead = TV_INSET_H) {
                                    itemsIndexed(trending.items.take(10), key = { _, it -> it.type + it.id }) { i, item ->
                                        Top10Tile(
                                            rank = i + 1,
                                            item = item,
                                            onFocus = { spotlight = Spotlight.Title(item, trending.title, i + 1) },
                                            onClick = { viewModel.openDetails(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.favorites.isNotEmpty()) {
                        item(key = "__mylist") {
                            HomeRow("My List") {
                                TileRow {
                                    items(state.favorites.distinctBy { it.type + it.id }, key = { it.type + it.id }) { item ->
                                        BoxartTile(
                                            item = item,
                                            art = tileArt["${item.type}:${item.id}"],
                                            onFocus = { spotlight = Spotlight.Title(item, "My List") },
                                            onClick = { viewModel.openDetails(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(otherRows, key = { it.title }) { row ->
                        CatalogStrip(row, tileArt, viewModel) { item -> spotlight = Spotlight.Title(item, row.title) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRow(title: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.padding(start = TV_INSET_H).height(ROW_LEAD - 8.dp),
            )
        } else {
            Spacer(Modifier.height(ROW_LEAD - 16.dp))
        }
        content()
    }
}

/** A row of tiles that keeps the focused tile pinned [lead] in from the left. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileRow(lead: Dp = TV_INSET_H, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val density = LocalDensity.current
    val pin = remember(density, lead) { PinnedSpec(with(density) { lead.toPx() }) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides pin) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Vertical room for the focused tile's lift; the end padding lets
            // the last tiles reach the pinned spot too.
            contentPadding = PaddingValues(start = TV_INSET_H, end = 400.dp, top = 8.dp, bottom = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun CatalogStrip(
    row: CatalogRow,
    tileArt: Map<String, String>,
    viewModel: BrowseViewModel,
    onFocus: (CatalogItem) -> Unit,
) {
    HomeRow(row.title) {
        TileRow {
            items(row.items, key = { it.type + it.id }) { item ->
                BoxartTile(
                    item = item,
                    art = tileArt["${item.type}:${item.id}"],
                    onFocus = { onFocus(item) },
                    onClick = { viewModel.openDetails(item) },
                )
            }
            // Closes the row: the whole catalogue of that kind.
            val moreType = row.items.firstOrNull()?.type
            if (moreType != null) {
                item(key = "__more") {
                    MoreTile(tv = moreType == "tv") {
                        viewModel.setTab(if (moreType == "tv") BrowseTab.TV else BrowseTab.MOVIES)
                    }
                }
            }
        }
    }
}

// ---- The details area ----

/**
 * The spotlighted title's artwork across the top right, melting into the
 * page on the left and at the foot so the details and rows sit on it.
 * Changes wait a beat, so scrolling along a row doesn't strobe images.
 */
@Composable
private fun SpotlightBackdrop(spotlight: Spotlight?, heroArt: Map<String, String>, tileArt: Map<String, String>) {
    val target: Any? = when (spotlight) {
        is Spotlight.Featured -> heroArt["${spotlight.item.type}:${spotlight.item.id}"].orEmpty()
            .ifBlank { spotlight.item.backdrop }
        is Spotlight.Title -> spotlight.item.backdrop.ifBlank {
            tileArt["${spotlight.item.type}:${spotlight.item.id}"].orEmpty().ifBlank { spotlight.item.poster }
        }
        is Spotlight.Watch -> spotlight.entry.item?.let { it.backdrop.ifBlank { it.poster } }
        is Spotlight.Banner -> spotlight.banner
        null -> null
    }
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (shown != null) delay(220)
        shown = target
    }
    // Home sits below the menu, but its artwork runs up under it to the top
    // of the screen: laid out a menu's height taller and drawn that far up.
    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val extra = NAV_HEIGHT.roundToPx()
                val tall = constraints.maxHeight + extra
                val placeable = measurable.measure(constraints.copy(minHeight = tall, maxHeight = tall))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -extra) }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.76f),
        ) {
            when (val s = shown) {
                is HomeBanner -> Box(
                    Modifier.fillMaxSize().drawBehind { drawEllipseGradient(s.stops, s.center) },
                )
                is String -> if (s.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(s).crossfade(350).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // Fade into the page: from the left (where the details sit) and
            // at the foot (where the rows ride over it).
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to ArcBackground,
                        0.12f to ArcBackground.copy(alpha = 0.92f),
                        0.45f to ArcBackground.copy(alpha = 0.35f),
                        0.75f to Color.Transparent,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to ArcBackground.copy(alpha = 0.35f),
                        0.18f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to ArcBackground,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun SpotlightDetails(
    spotlight: Spotlight?,
    /** The billboard's title, whose buttons stay put while a row is spotlighted. */
    featured: CatalogItem?,
    details: TitleDetails?,
    favourite: Boolean,
    playFocus: FocusRequester,
    onFeaturedFocus: () -> Unit,
    onPlay: (CatalogItem) -> Unit,
    onMoreInfo: (CatalogItem) -> Unit,
    onToggleList: (CatalogItem) -> Unit,
) {
    val onBillboard = spotlight is Spotlight.Featured
    // The title's line count decides how much synopsis fits above the buttons.
    var titleLines by remember { mutableStateOf(1) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.width(520.dp).padding(top = 2.dp)) {
            when (spotlight) {
                null -> Unit
                is Spotlight.Banner -> {
                    KindLabel(spotlight.banner.kind)
                    DetailsTitle(spotlight.banner.title) { titleLines = it }
                    MetaLine(listOf(spotlight.banner.meta))
                    Overview(spotlight.banner.blurb, lines = 3)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "OK to open  ·  ◀ ▶ for the other banners",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcMuted,
                    )
                }
                is Spotlight.Watch -> {
                    val e = spotlight.entry
                    KindLabel(
                        when (e.kind) {
                            "live" -> "LIVE TV"
                            "tv" -> "SERIES"
                            else -> "FILM"
                        },
                    )
                    DetailsTitle(e.title) { titleLines = it }
                    MetaLine(
                        listOfNotNull(
                            e.item?.year?.takeIf { it.isNotBlank() },
                            when {
                                e.kind == "live" -> e.group.ifBlank { null }
                                e.nextSeason != null && e.nextEpisode != null -> "Next up: S${e.nextSeason} E${e.nextEpisode}"
                                e.season != null && e.episode != null -> "Last played: S${e.season} E${e.episode}"
                                else -> null
                            },
                        ),
                    )
                    Overview(e.item?.overview.orEmpty(), lines = 3)
                }
                is Spotlight.Title -> {
                    val item = spotlight.item
                    KindLabel(if (item.isTv) "SERIES" else "FILM")
                    DetailsTitle(item.title) { titleLines = it }
                    MetaLine(
                        listOfNotNull(item.year.takeIf { it.isNotBlank() }, if (item.isTv) "Series" else "Film"),
                        rank = spotlight.rank?.let { "#$it in ${spotlight.row}" },
                    )
                    Overview(item.overview, lines = if (titleLines > 1) 2 else 3)
                }
                is Spotlight.Featured -> {
                    val item = spotlight.item
                    KindLabel(if (item.isTv) "SERIES" else "FILM")
                    DetailsTitle(item.title) { titleLines = it }
                    MetaLine(
                        buildList {
                            val year = details?.year?.takeIf { it.isNotBlank() } ?: item.year
                            if (year.isNotBlank()) add(year)
                            details?.rating?.takeIf { it > 0 }?.let { add("★ %.1f".format(it)) }
                            if (item.isTv) {
                                add(details?.seasons?.let { "$it season${if (it == 1) "" else "s"}" } ?: "Series")
                            } else {
                                details?.runtime?.takeIf { it > 0 }?.let { add(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m") }
                            }
                            details?.genres?.take(2)?.forEach { add(it) }
                        },
                    )
                    Overview(details?.overview?.takeIf { it.isNotBlank() } ?: item.overview, lines = if (titleLines > 1) 1 else 2)
                }
            }
        }
        // The billboard's buttons, at the foot of the details area. They stay
        // composed (invisible) while a row is spotlighted, so UP from the
        // first row comes back to them, as on Netflix.
        if (featured != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 4.dp)
                    .graphicsLayer { alpha = if (onBillboard) 1f else 0f },
            ) {
                BillboardButton(
                    label = "Play",
                    icon = Icons.Default.PlayArrow,
                    primary = true,
                    onClick = { onPlay(featured) },
                    onFocus = onFeaturedFocus,
                    modifier = Modifier.focusRequester(playFocus),
                )
                BillboardButton("More info", Icons.Default.Info, onClick = { onMoreInfo(featured) }, onFocus = onFeaturedFocus)
                BillboardButton(
                    if (favourite) "In My List" else "My List",
                    if (favourite) Icons.Default.Check else Icons.Default.Add,
                    onClick = { onToggleList(featured) },
                    onFocus = onFeaturedFocus,
                )
            }
        }
    }
}

/** Netflix's "N SERIES" mark, in Arc's colours: a blue tick and the kind, letterspaced. */
@Composable
internal fun KindLabel(kind: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 14.dp).background(ArcBlue, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            kind,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.4.sp,
            color = Color.White.copy(alpha = 0.88f),
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DetailsTitle(title: String, onLines: (Int) -> Unit = {}) {
    Text(
        title,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { onLines(it.lineCount) },
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MetaLine(parts: List<String>, rank: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (rank != null) {
            // Netflix's Top 10 badge, in the accent.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(ArcBlue, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text("TOP", fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("10", fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(rank, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            if (parts.isNotEmpty()) Spacer(Modifier.width(12.dp))
        }
        Text(
            parts.joinToString("   ·   "),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Overview(text: String, lines: Int) {
    if (text.isBlank()) return
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.84f),
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Netflix's billboard buttons: Play solid white, the rest a translucent
 * grey; whichever has focus turns white with black text.
 */
@Composable
internal fun BillboardButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.18f),
            contentColor = if (primary) Color.Black else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        contentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

// ---- Tiles ----

/** Netflix's focus: a white keyline round the tile and a small lift. */
private val TileShape = RoundedCornerShape(6.dp)

@Composable
private fun tileBorder() = CardDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = TileShape),
)

/**
 * Landscape boxart. [art] is TMDB's titled key art -- like Netflix's boxart
 * it carries the title's own logo, so it needs no caption: null while still
 * being fetched (the tile stays quiet rather than flashing the wrong
 * picture), "" when there is none -- then the wide still or poster stands
 * in with the title written over it.
 */
@Composable
internal fun BoxartTile(
    item: CatalogItem,
    art: String?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = BOXART_WIDTH,
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(TileShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = tileBorder(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.width(width).onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            val titled = !art.isNullOrBlank()
            val picture = when {
                art == null -> null
                titled -> art
                else -> item.backdrop.ifBlank { item.poster }
            }
            if (picture != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(picture).crossfade(180).build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!titled) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Netflix's Top 10 tile: a tall outlined rank numeral standing on the row's
 * baseline, with the poster tucked over its right edge. The whole tile takes
 * focus (so the numeral pins with it); the keyline is drawn round the poster.
 */
@Composable
private fun Top10Tile(rank: Int, item: CatalogItem, onFocus: () -> Unit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val measurer = rememberTextMeasurer()
    // Letter-spacing only between the digits of "10": on a lone digit it
    // trims the measured width, and the poster would cover the numeral.
    val numeral = remember(rank, measurer) {
        measurer.measure("$rank", NumeralStyle.copy(letterSpacing = if (rank >= 10) (-8).sp else 0.sp))
    }
    val numeralWidth = with(LocalDensity.current) { numeral.size.width.toDp() }
    // Inset by LIFT so the focused poster's lift (and keyline) isn't
    // clipped by the tile's own bounds.
    val posterHeight = TOP10_HEIGHT - LIFT * 2
    val posterWidth = posterHeight * 2f / 3f
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Box(
            Modifier
                .width(numeralWidth + posterWidth - NUMERAL_TUCK + LIFT)
                .height(TOP10_HEIGHT)
                .padding(vertical = LIFT)
                .padding(end = LIFT),
        ) {
            // A dark numeral with a light outline, as Netflix draws them,
            // its baseline on the foot of the poster.
            Canvas(Modifier.width(numeralWidth).fillMaxHeight()) {
                val topLeft = Offset(0f, size.height - numeral.firstBaseline)
                drawText(numeral, color = ArcBackground, topLeft = topLeft)
                drawText(
                    numeral,
                    color = Color.White.copy(alpha = if (focused) 0.85f else 0.5f),
                    topLeft = topLeft,
                    drawStyle = Stroke(width = 2.5.dp.toPx()),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .width(posterWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val s = if (focused) 1.06f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .clip(TileShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .then(if (focused) Modifier.border(3.dp, Color.White, TileShape) else Modifier),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(item.poster.ifBlank { item.backdrop }).crossfade(180).build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Rank numerals: about the poster's height, set tight. */
private val NumeralStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Bold,
    fontSize = 150.sp,
)

/** How far each poster tucks over its numeral. */
private val NUMERAL_TUCK = 10.dp

/** Room round a Top 10 poster for its focus lift. */
private val LIFT = 5.dp

private val TOP10_HEIGHT = 132.dp

/**
 * A Continue Watching tile: the title's boxart with what comes next ("Next
 * up  S2 E6"), or a channel's logo with "Live". Hold OK to drop it.
 */
@Composable
private fun ContinueTile(entry: WatchEntry, art: String?, viewModel: BrowseViewModel, onFocus: () -> Unit) {
    val playLive = rememberLivePlay()
    val item = entry.item
    val channel = entry.channel
    Card(
        onClick = {
            if (entry.kind == "live" && channel != null) {
                playLive(
                    listOf(
                        com.arcsus.arctv.data.LiveChannel(
                            id = "cw:${channel.url}", name = channel.name, logo = channel.logo, group = entry.group, url = channel.url,
                        ),
                    ),
                    0,
                )
            } else {
                viewModel.resumeWatch(entry)
            }
        },
        onLongClick = { viewModel.removeWatch(entry.key) },
        shape = CardDefaults.shape(TileShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = tileBorder(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.width(BOXART_WIDTH).onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (item != null) {
                val picture = if (art.isNullOrBlank()) item.backdrop.ifBlank { item.poster } else art
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(picture).crossfade(180).build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (channel != null) {
                Box(Modifier.fillMaxSize().stadiumBackdrop())
                if (channel.logo.isNotBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        modifier = Modifier.size(64.dp).align(Alignment.Center),
                    )
                } else {
                    Text(
                        channel.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
            )
            val badge = when {
                entry.kind == "live" -> "● LIVE"
                entry.kind == "tv" && entry.nextSeason != null && entry.nextEpisode != null ->
                    "Next up  S${entry.nextSeason} E${entry.nextEpisode}"
                entry.kind == "tv" && entry.season != null && entry.episode != null -> "S${entry.season} E${entry.episode}"
                entry.kind == "tv" -> "Series"
                else -> "Resume"
            }
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (entry.kind == "live") Color(0xFFFF5A5F) else Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** The row's closer: the full catalogue of that kind. */
@Composable
private fun MoreTile(tv: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(TileShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = tileBorder(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.width(BOXART_WIDTH),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("All ${if (tv) "Shows" else "Movies"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("→", style = MaterialTheme.typography.titleLarge, color = ArcBlue)
            }
        }
    }
}
