package com.arcsus.arctv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.theme.ArcBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How long focus must rest on a rail item before its section opens. Long
 * enough to travel past a section without loading it, short enough that
 * stopping on one feels immediate.
 */
private const val RAIL_SETTLE_MS = 280L

/** A D-pad press older than this cannot be the reason focus arrived somewhere. */
private const val DELIBERATE_NS = 600_000_000L

/** How long after the content lost focus a landing on the rail counts as stray. */
private const val STRAY_NS = 1_500_000_000L

/**
 * Where focus was when the last D-pad press happened. Focus can arrive on the
 * rail for two very different reasons: the viewer moved there (LEFT out of
 * the content, or UP/DOWN along the rail), or the platform put it there
 * because whatever was focused vanished -- a pressed group card removed with
 * its grid, a list refetched. Android TVs are not in touch mode, so the
 * moment the focused view goes, View.clearFocus hands focus to the first
 * focusable on screen: the rail's top item. Only the first kind is a reason
 * to change section.
 */
private class NavIntent {
    private var at = 0L
    private var key: Key? = null
    private var fromRail = false
    private var fromContent = false

    fun record(pressed: Key, railHadFocus: Boolean, contentHadFocus: Boolean) {
        at = System.nanoTime()
        key = pressed
        fromRail = railHadFocus
        fromContent = contentHadFocus
    }

    /** True if a rail item gaining focus right now is the viewer's doing. */
    fun deliberateRailMove(): Boolean {
        if (at == 0L) return false
        val age = System.nanoTime() - at
        if (age !in 0..DELIBERATE_NS) return false
        return fromRail || (key == Key.DirectionLeft && fromContent)
    }
}

/**
 * Safe margin between the screen edge and content. Modest: most sets show
 * the whole picture, and a big inset stacked on each screen's own gutter
 * left an empty band all the way round. Panels (the rail) run to the edge;
 * only what's inside them is inset.
 */
internal val TV_INSET_H = 20.dp
internal val TV_INSET_V = 14.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(factory: ArcTvViewModelFactory) {
    val updateViewModel: UpdateViewModel = viewModel(factory = factory)
    val browseViewModel: BrowseViewModel = viewModel(factory = factory)
    val downloadsViewModel: DownloadsViewModel = viewModel(factory = factory)
    val torrentsViewModel: TorrentsViewModel = viewModel(factory = factory)
    val liveViewModel: LiveViewModel = viewModel(factory = factory)
    val guideViewModel: GuideViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    val settingsState by settingsViewModel.state.collectAsState()
    // Downloads/Torrents browse the Real-Debrid account, so they only appear
    // when Real-Debrid is connected (AllDebrid-only setups hide them).
    // Movies and TV Shows are rail sections like Sky's menu; all three render
    // the Browse screen in the matching mode.
    val tabs = if (settingsState.rdConnected) {
        listOf("Browse", "Movies", "TV Shows", "Live", "Guide", "Downloads", "Torrents", "Settings")
    } else {
        listOf("Browse", "Movies", "TV Shows", "Live", "Guide", "Settings")
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    if (selectedTab >= tabs.size) selectedTab = tabs.size - 1
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    // Opening a section mounts a whole screen and starts its loading. Rail
    // focus therefore only *proposes* a section: travelling down the rail used
    // to open -- and start fetching for -- every section it passed through,
    // which is what made moving around the menu crawl. Pressing OK commits at
    // once; resting on an item commits shortly after.
    val openSection: (Int) -> Unit = { index ->
        selectedTab = index
        when (tabs.getOrNull(index)) {
            "Browse" -> browseViewModel.setTab(BrowseTab.HOME)
            "Movies" -> browseViewModel.setTab(BrowseTab.MOVIES)
            "TV Shows" -> browseViewModel.setTab(BrowseTab.TV)
        }
    }
    var railHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var contentLostFocusAt by remember { mutableLongStateOf(0L) }
    val nav = remember { NavIntent() }
    val contentFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var proposedTab by remember { mutableIntStateOf(-1) }
    LaunchedEffect(proposedTab) {
        if (proposedTab < 0 || proposedTab == selectedTab) return@LaunchedEffect
        delay(RAIL_SETTLE_MS)
        // Still resting there? Focus that has since gone back into the
        // content -- by the viewer, or by the recovery below -- withdraws
        // the proposal.
        if (railHasFocus) openSection(proposedTab)
    }

    // A rail item took focus. Only a D-pad move counts as a proposal (see
    // NavIntent). Focus the platform pushed onto the rail because the
    // focused element vanished -- "I pressed Favourites and it sent me
    // Home" -- goes back into the content, once the screen that lost it has
    // had a couple of frames to place it itself.
    val onRailItemFocused: (Int) -> Unit = { index ->
        if (nav.deliberateRailMove()) {
            proposedTab = index
        } else if (contentLostFocusAt != 0L && System.nanoTime() - contentLostFocusAt in 0..STRAY_NS) {
            scope.launch {
                repeat(3) { withFrameNanos { } }
                if (!contentHasFocus && railHasFocus) runCatching { contentFocus.requestFocus() }
            }
        }
    }

    val liveState by liveViewModel.state.collectAsState()
    val playLive = rememberLivePlay()

    // Sky Q layout: a left navigation rail (preview tile, vertical menu with
    // the active section boxed, brand and clock at the foot) and the content
    // filling the rest of the panel.
    Row(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight ->
                            nav.record(event.key, railHasFocus, contentHasFocus)
                    }
                }
                false
            },
    ) {
        NavRail(
            tabs = tabs,
            selected = selectedTab,
            onFocusChanged = { railHasFocus = it },
            onSelect = { index ->
                proposedTab = index
                openSection(index)
            },
            onPreview = onRailItemFocused,
            onSearch = {
                selectedTab = 0
                searchOpen = true
            },
            preview = liveState.favorites.firstOrNull(),
            onPreviewClick = { channel ->
                // Favourites are the zap list from the preview tile.
                val favourites = liveState.favorites.map {
                    com.arcsus.arctv.data.LiveChannel(
                        id = "fav:${it.url}", name = it.name, logo = it.logo, group = "Favourites", url = it.url,
                    )
                }
                playLive(favourites, favourites.indexOfFirst { it.url == channel.url }.coerceAtLeast(0))
            },
        )
        Column(
            Modifier
                .weight(1f)
                .padding(top = 0.dp, end = TV_INSET_H)
                .focusRequester(contentFocus)
                .onFocusChanged { state ->
                    contentHasFocus = state.hasFocus
                    if (!state.hasFocus) {
                        contentLostFocusAt = System.nanoTime()
                        // Nothing took focus at all (touch-mode devices):
                        // bring it back rather than leave the remote dead.
                        scope.launch {
                            delay(80)
                            if (!railHasFocus && !contentHasFocus) runCatching { contentFocus.requestFocus() }
                        }
                    }
                }
                // Up/down stays inside the content. Without this, a DPAD-down
                // that finds nothing focusable below it -- a grid still
                // reloading after picking a genre, say -- lets focus fall
                // sideways into the rail, and rail items switch section the
                // moment they take focus: pressing down under Movies dropped
                // you into the Guide. The rail is still one LEFT away.
                .focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Up || direction == FocusDirection.Down) {
                            FocusRequester.Cancel
                        } else {
                            FocusRequester.Default
                        }
                    }
                }
                .focusGroup(),
        ) {
            UpdateBanner(updateViewModel)
            when (tabs[selectedTab]) {
                "Browse", "Movies", "TV Shows" ->
                    BrowseScreen(browseViewModel, searchOpen) { searchOpen = it }
                "Live" -> LiveScreen(liveViewModel)
                "Guide" -> GuideScreen(guideViewModel)
                "Downloads" -> DownloadsScreen(downloadsViewModel)
                "Torrents" -> TorrentsScreen(torrentsViewModel)
                "Settings" -> SettingsScreen(settingsViewModel, updateViewModel)
            }
        }
    }
}

@Composable
private fun NavRail(
    tabs: List<String>,
    selected: Int,
    onFocusChanged: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    onSearch: () -> Unit,
    preview: com.arcsus.arctv.data.SavedChannel?,
    onPreviewClick: (com.arcsus.arctv.data.SavedChannel) -> Unit,
) {
    // Sky Q's rail, top to bottom: brand and clock, the live preview tile,
    // the menu. It slims to icons while focus is in the content and opens
    // again the moment focus returns.
    var railFocused by remember { mutableStateOf(false) }
    val railWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = (if (railFocused) 214.dp else 64.dp) + TV_INSET_H,
        label = "railWidth",
    )
    val expanded = railFocused
    val time by produceState(initialValue = clockNow()) {
        while (true) {
            value = clockNow()
            delay(20_000)
        }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(Color.White.copy(alpha = 0.045f))
            .onFocusChanged {
                railFocused = it.hasFocus
                onFocusChanged(it.hasFocus)
            }
            .padding(start = TV_INSET_H + 8.dp, end = 10.dp, top = TV_INSET_V + 6.dp, bottom = TV_INSET_V + 6.dp),
    ) {
        // Brand + clock, as Sky puts them: top-left, always.
        if (expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ArcBlue, fontWeight = FontWeight.Bold)) { append("Arc") }
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" TV") }
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    time,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        } else {
            Text(
                "A",
                style = MaterialTheme.typography.titleLarge,
                color = ArcBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.height(14.dp))

        // Sky's preview slot: the first favourite channel, one press to play.
        // (A live video preview would hold the IPTV panel's single allowed
        // connection hostage, so artwork stands in for the tuner picture.)
        if (expanded && preview != null) {
            androidx.tv.material3.Card(
                onClick = { onPreviewClick(preview) },
                shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(6.dp)),
                scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.03f),
                border = androidx.tv.material3.CardDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(6.dp)),
                ),
                modifier = Modifier.fillMaxWidth().height(106.dp),
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (preview.logo.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = preview.logo,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).align(Alignment.Center),
                        )
                    } else {
                        Text(
                            preview.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                            style = MaterialTheme.typography.headlineSmall,
                            color = ArcBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Text(
                        preview.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        tabs.forEachIndexed { index, title ->
            RailItem(
                title = title,
                icon = railIcon(title),
                selected = index == selected,
                onActivate = { onSelect(index) },
                onFocused = { onPreview(index) },
                subdued = title == "Settings",
                expanded = expanded,
            )
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.weight(1f))
        RailItem(
            title = "Search",
            icon = Icons.Default.Search,
            selected = false,
            onActivate = onSearch,
            expanded = expanded,
            showIconExpanded = true,
        )
    }
}

/** The icon each section shows while the rail is closed. */
private fun railIcon(title: String): ImageVector = when (title) {
    "Browse" -> Icons.Default.Home
    "Movies" -> Icons.Default.Movie
    "TV Shows" -> Icons.Default.Tv
    "Live" -> Icons.Default.LiveTv
    "Guide" -> Icons.Default.ListAlt
    "Downloads" -> Icons.Default.Download
    "Torrents" -> Icons.Default.CloudDownload
    "Settings" -> Icons.Default.Settings
    else -> Icons.Default.Search
}

/**
 * One rail entry. Sky Q's highlight is a translucent light box on the blue
 * ground: the section you're in keeps it; focus brightens it and adds a
 * fine keyline. [onFocused] reports that focus has arrived -- the caller
 * decides when that becomes a section change -- while OK activates at once.
 */
@Composable
private fun RailItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onActivate: () -> Unit,
    onFocused: (() -> Unit)? = null,
    subdued: Boolean = false,
    expanded: Boolean = true,
    showIconExpanded: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onActivate,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = if (selected) 0.32f else 0.22f),
            contentColor = when {
                selected -> Color.White
                subdued -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else -> Color.White.copy(alpha = 0.86f)
            },
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() },
    ) {
        if (expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (showIconExpanded) {
                    Icon(icon, contentDescription = null, Modifier.size(17.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) {
                Icon(icon, contentDescription = title, Modifier.size(21.dp))
            }
        }
    }
}

/** Sky's clock: "7.11pm". */
private fun clockNow(): String =
    SimpleDateFormat("h.mma", Locale.UK).format(Date()).lowercase(Locale.UK)
