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
import androidx.compose.material.icons.filled.Search
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
    // Sky Glass behaviour: the rail slims down to initials while you're in
    // the content, expanding as soon as focus lands back on it.
    var railFocused by remember { mutableStateOf(false) }
    val railWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (railFocused) 206.dp else 58.dp,
        label = "railWidth",
    )
    val expanded = railFocused
    // Live clock and date, Sky-style ("Monday \u00b7 19:11") at the rail's foot.
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
            .background(Color(0xFF0E1520))
            .onFocusChanged { railFocused = it.hasFocus }
            .padding(horizontal = 10.dp, vertical = 18.dp),
    ) {
        // Sky's preview slot: the first favourite channel, one press to play.
        // (A live video preview would hold the IPTV panel's single allowed
        // connection hostage, so artwork stands in for the tuner picture.)
        if (expanded && preview != null) {
            androidx.tv.material3.Card(
                onClick = { onPreviewClick(preview) },
                modifier = Modifier.fillMaxWidth().height(96.dp),
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (preview.logo.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = preview.logo,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).align(Alignment.Center),
                        )
                    } else {
                        Text(
                            preview.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "\u2022",
                            style = MaterialTheme.typography.headlineSmall,
                            color = ArcBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Text(
                        "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0xFFD64545), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Text(
                        preview.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
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
                selected = index == selected,
                onActivate = { onSelect(index) },
                onFocused = { onPreview(index) },
                subdued = title == "Settings",
                expanded = expanded,
            )
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        RailItem(
            title = "Search",
            selected = false,
            onActivate = onSearch,
            icon = Icons.Default.Search,
            expanded = expanded,
        )
        Spacer(Modifier.height(16.dp))
        if (expanded) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ArcBlue, fontWeight = FontWeight.Bold)) {
                            append("Arc")
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" TV") }
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${time.second} \u00b7 ${time.first}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}

/**
 * One rail entry, Sky-style: the active section sits in a white box with dark
 * text; focus fills the accent. [onFocused] reports that focus has arrived --
 * the caller decides when that becomes a section change -- while OK activates
 * straight away.
 */
@Composable
private fun RailItem(
    title: String,
    selected: Boolean,
    onActivate: () -> Unit,
    onFocused: (() -> Unit)? = null,
    icon: ImageVector? = null,
    subdued: Boolean = false,
    expanded: Boolean = true,
) {
    // Sky Q's signature: the section you're in sits in a solid white box.
    // Focus is a white keyline (with a faint fill) -- it becomes the white
    // box itself a moment later, when the section opens.
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onActivate,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.Transparent,
            focusedContainerColor = if (selected) Color.White else Color.White.copy(alpha = 0.10f),
            contentColor = when {
                selected -> Color(0xFF0B0F15)
                subdued -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            focusedContentColor = if (selected) Color(0xFF0B0F15) else Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, Color.White), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() },
    ) {
        if (expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = title, Modifier.size(16.dp))
                } else {
                    Text(
                        title.take(1),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun clockNow(): Pair<String, String> {
    val now = Date()
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) to
        SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
}
