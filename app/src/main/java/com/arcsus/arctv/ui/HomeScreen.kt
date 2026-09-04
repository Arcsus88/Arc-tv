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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How long focus must rest on a rail item before its section opens. Long
 * enough to travel past a section without loading it, short enough that
 * stopping on one feels immediate.
 */
private const val RAIL_SETTLE_MS = 280L

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
    var proposedTab by remember { mutableIntStateOf(-1) }
    LaunchedEffect(proposedTab) {
        if (proposedTab < 0 || proposedTab == selectedTab) return@LaunchedEffect
        delay(RAIL_SETTLE_MS)
        openSection(proposedTab)
    }

    val liveState by liveViewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Sky Q layout: a left navigation rail (preview tile, vertical menu with
    // the active section boxed, brand and clock at the foot) and the content
    // filling the rest of the panel.
    Row(Modifier.fillMaxSize()) {
        NavRail(
            tabs = tabs,
            selected = selectedTab,
            onSelect = { index ->
                proposedTab = index
                openSection(index)
            },
            onPreview = { index -> proposedTab = index },
            onSearch = {
                selectedTab = 0
                searchOpen = true
            },
            preview = liveState.favorites.firstOrNull(),
            onPreviewClick = { channel ->
                if (!playVideo(context, channel.url, channel.name)) {
                    android.widget.Toast.makeText(
                        context, "No video player installed.", android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
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
        targetValue = if (railFocused) 214.dp else 64.dp,
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
            .onFocusChanged { railFocused = it.hasFocus }
            .padding(horizontal = 10.dp, vertical = 16.dp),
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
