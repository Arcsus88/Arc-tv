package com.arcsus.arctv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.theme.ArcBackground
import com.arcsus.arctv.ui.theme.ArcBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How long focus must rest on a menu item before its section opens. Long
 * enough to travel past a section without loading it, short enough that
 * stopping on one feels immediate.
 */
private const val NAV_SETTLE_MS = 280L

/** A D-pad press older than this cannot be the reason focus arrived somewhere. */
private const val DELIBERATE_NS = 600_000_000L

/** How long after the content lost focus a landing on the menu counts as stray. */
private const val STRAY_NS = 1_500_000_000L

/**
 * Where focus was when the last D-pad press happened. Focus can arrive on
 * the menu for two very different reasons: the viewer moved there (UP out of
 * the content, or LEFT/RIGHT along the menu), or the platform put it there
 * because whatever was focused vanished -- a pressed group card removed with
 * its grid, a list refetched. Android TVs are not in touch mode, so the
 * moment the focused view goes, View.clearFocus hands focus to the first
 * focusable on screen: the menu. Only the first kind is a reason to change
 * section.
 */
private class NavIntent {
    private var at = 0L
    private var key: Key? = null
    private var fromNav = false
    private var fromContent = false

    fun record(pressed: Key, navHadFocus: Boolean, contentHadFocus: Boolean) {
        at = System.nanoTime()
        key = pressed
        fromNav = navHadFocus
        fromContent = contentHadFocus
    }

    private fun recent(): Boolean = at != 0L && System.nanoTime() - at in 0..DELIBERATE_NS

    /** True if a menu item gaining focus right now is the viewer's doing. */
    fun deliberateNavMove(): Boolean = recent() && (fromNav || (key == Key.DirectionUp && fromContent))

    /** True if focus just came up out of the content into the menu. */
    fun cameUpFromContent(): Boolean = recent() && key == Key.DirectionUp && fromContent
}

/**
 * Safe margin between the screen edge and content: about 4% of the width,
 * Netflix's TV gutter. Panels and artwork run to the edge; only what's
 * inside them is inset.
 */
internal val TV_INSET_H = 40.dp
internal val TV_INSET_V = 14.dp

/** Height of the top menu; screens start below it (Home's artwork runs under it). */
internal val NAV_HEIGHT = 56.dp

/** The top-level sections, in menu order. */
internal object Section {
    const val HOME = "Home"
    const val SHOWS = "Shows"
    const val MOVIES = "Movies"
    const val DISNEY = "Disney"
    const val NETFLIX = "Netflix"
    const val PRIME = "Prime Video"
    const val LIVE = "Live TV"
    const val GUIDE = "Guide"
    const val DOWNLOADS = "Downloads"
    const val TORRENTS = "Torrents"
    const val SETTINGS = "Settings"
}

/** The guide group the home page's Sky Sports banner opens (the web app's SKY_SPORTS_GROUP). */
internal const val SKY_SPORTS_GROUP = "UK Sports"

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
    // Netflix's TV menu, across the top: Home, Shows, Movies, then Arc's own
    // sections. Downloads/Torrents browse the Real-Debrid account, so they
    // only appear when Real-Debrid is connected. Settings sits at the far
    // end as a gear, like the web app's header.
    val sections = buildList {
        addAll(
            listOf(
                Section.HOME, Section.SHOWS, Section.MOVIES, Section.DISNEY, Section.NETFLIX, Section.PRIME,
                Section.LIVE, Section.GUIDE,
            ),
        )
        if (settingsState.rdConnected) addAll(listOf(Section.DOWNLOADS, Section.TORRENTS))
        add(Section.SETTINGS)
    }
    var selectedName by rememberSaveable { mutableStateOf(Section.HOME) }
    if (selectedName !in sections) selectedName = Section.HOME
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    // Opening a section mounts a whole screen and starts its loading. Menu
    // focus therefore only *proposes* a section: travelling along the menu
    // used to open -- and start fetching for -- every section it passed
    // through. Pressing OK commits at once; resting on an item commits
    // shortly after.
    val openSection: (String) -> Unit = { name ->
        selectedName = name
        when (name) {
            Section.HOME -> browseViewModel.setTab(BrowseTab.HOME)
            Section.MOVIES -> browseViewModel.setTab(BrowseTab.MOVIES)
            Section.SHOWS -> browseViewModel.setTab(BrowseTab.TV)
            Section.DISNEY -> browseViewModel.setTab(BrowseTab.DISNEY)
            Section.NETFLIX -> browseViewModel.setTab(BrowseTab.NETFLIX)
            Section.PRIME -> browseViewModel.setTab(BrowseTab.PRIME)
        }
    }
    var navHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var contentLostFocusAt by remember { mutableLongStateOf(0L) }
    val nav = remember { NavIntent() }
    val contentFocus = remember { FocusRequester() }
    val navFocus = remember { mutableMapOf<String, FocusRequester>() }
    val scope = rememberCoroutineScope()

    var proposed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(proposed) {
        val name = proposed ?: return@LaunchedEffect
        if (name == selectedName) return@LaunchedEffect
        delay(NAV_SETTLE_MS)
        // Still resting there? Focus that has since gone back into the
        // content -- by the viewer, or by the recovery below -- withdraws
        // the proposal.
        if (navHasFocus) openSection(name)
    }

    // A menu item took focus. Only a D-pad move counts as a proposal (see
    // NavIntent). Focus the platform pushed onto the menu because the
    // focused element vanished goes back into the content, once the screen
    // that lost it has had a couple of frames to place it itself.
    val onNavItemFocused: (String) -> Unit = { name ->
        if (nav.cameUpFromContent() && name != selectedName) {
            // Netflix: coming up out of a section lands on that section's
            // menu item, wherever above it focus happened to arrive.
            scope.launch {
                withFrameNanos { }
                runCatching { navFocus[selectedName]?.requestFocus() }
            }
        } else if (nav.deliberateNavMove()) {
            // Search (or any item that isn't a section) withdraws whatever
            // was proposed on the way past.
            proposed = name.takeIf { it in sections }
        } else if (contentLostFocusAt != 0L && System.nanoTime() - contentLostFocusAt in 0..STRAY_NS) {
            scope.launch {
                repeat(3) { withFrameNanos { } }
                if (!contentHasFocus && navHasFocus) runCatching { contentFocus.requestFocus() }
            }
        }
    }

    // Netflix: Back from anywhere in a section goes up to the menu; Back on
    // the menu goes Home; Back on Home's menu leaves the app.
    BackHandler(enabled = contentHasFocus || (navHasFocus && selectedName != Section.HOME)) {
        if (contentHasFocus) {
            runCatching { navFocus[selectedName]?.requestFocus() }
        } else {
            proposed = null
            openSection(Section.HOME)
            runCatching { navFocus[Section.HOME]?.requestFocus() }
        }
    }

    // A home banner asks for another section: Disney, Netflix, Prime Video,
    // or the guide opened on the UK sports channels.
    val openFromHome: (String) -> Unit = { name ->
        if (name == Section.GUIDE) guideViewModel.openGroup(SKY_SPORTS_GROUP)
        openSection(name)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight ->
                            nav.record(event.key, navHasFocus, contentHasFocus)
                    }
                }
                false
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // Always below the menu, Home included (its artwork reaches up
                // under the menu by drawing past its bounds): 2D focus search
                // compares the two groups' bounds, and a content group
                // overlapping the menu is never "below" it, so DOWN from the
                // menu found nothing.
                .padding(top = NAV_HEIGHT)
                .focusRequester(contentFocus)
                .onFocusChanged { state ->
                    contentHasFocus = state.hasFocus
                    if (!state.hasFocus) {
                        contentLostFocusAt = System.nanoTime()
                        // Nothing took focus at all (touch-mode devices):
                        // bring it back rather than leave the remote dead.
                        scope.launch {
                            delay(80)
                            if (!navHasFocus && !contentHasFocus) runCatching { contentFocus.requestFocus() }
                        }
                    }
                }
                // Only UP leaves the content, and only into the menu. A
                // DPAD-down or sideways press that finds nothing focusable --
                // a grid still reloading after picking a genre, say -- would
                // otherwise let focus fall into the menu, and menu items
                // switch section once focus rests on them.
                .focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Up) FocusRequester.Default else FocusRequester.Cancel
                    }
                }
                .focusGroup(),
        ) {
            when (selectedName) {
                Section.HOME, Section.SHOWS, Section.MOVIES, Section.DISNEY, Section.NETFLIX, Section.PRIME ->
                    BrowseScreen(
                        viewModel = browseViewModel,
                        searchOpen = searchOpen,
                        onSearchOpenChange = { searchOpen = it },
                        onOpenSection = openFromHome,
                        canTakeFocus = { !navHasFocus },
                    )
                Section.LIVE -> LiveScreen(liveViewModel)
                Section.GUIDE -> GuideScreen(guideViewModel)
                Section.DOWNLOADS -> DownloadsScreen(downloadsViewModel)
                Section.TORRENTS -> TorrentsScreen(torrentsViewModel)
                Section.SETTINGS -> SettingsScreen(settingsViewModel, updateViewModel)
            }
        }

        Column(Modifier.fillMaxWidth().align(Alignment.TopStart)) {
            TopNav(
                sections = sections,
                selected = selectedName,
                requesterFor = { name -> navFocus.getOrPut(name) { FocusRequester() } },
                onFocusChanged = { navHasFocus = it },
                onFocused = onNavItemFocused,
                onActivate = { name ->
                    proposed = name
                    openSection(name)
                },
                onSearch = {
                    openSection(Section.HOME)
                    searchOpen = true
                },
            )
            UpdateBanner(updateViewModel)
        }
    }
}

/**
 * Netflix's TV menu (2025 redesign): one row across the top of the screen,
 * always there. The brand and search on the left, the sections as plain
 * labels -- the one you're in white and bold, the rest dimmed -- and the
 * clock and settings on the right. Focus is Netflix's white pill with black
 * text. Coming up from the content lands on the section you're in.
 */
@Composable
private fun TopNav(
    sections: List<String>,
    selected: String,
    requesterFor: (String) -> FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onFocused: (String) -> Unit,
    onActivate: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val time by produceState(initialValue = clockNow()) {
        while (true) {
            value = clockNow()
            delay(20_000)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(NAV_HEIGHT)
            // Legible over Home's artwork, invisible on the plain screens.
            .background(Brush.verticalGradient(listOf(ArcBackground.copy(alpha = 0.92f), ArcBackground.copy(alpha = 0f))))
            .padding(horizontal = TV_INSET_H)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .focusGroup(),
    ) {
        // The web app's wordmark: "Arc" with the blue "TV" tag.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Arc", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(5.dp))
            Text(
                "TV",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .background(ArcBlue, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.width(22.dp))
        NavIcon(
            Icons.Default.Search,
            "Search",
            selected = false,
            onClick = onSearch,
            modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused("Search") },
        )
        Spacer(Modifier.width(6.dp))
        sections.filter { it != Section.SETTINGS }.forEach { name ->
            NavLabel(
                label = name,
                selected = name == selected,
                onClick = { onActivate(name) },
                modifier = Modifier
                    .focusRequester(requesterFor(name))
                    .onFocusChanged { if (it.isFocused) onFocused(name) },
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            time,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(12.dp))
        NavIcon(
            Icons.Default.Settings,
            Section.SETTINGS,
            selected = selected == Section.SETTINGS,
            onClick = { onActivate(Section.SETTINGS) },
            modifier = Modifier
                .focusRequester(requesterFor(Section.SETTINGS))
                .onFocusChanged { if (it.isFocused) onFocused(Section.SETTINGS) },
        )
    }
}

@Composable
private fun NavLabel(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.62f),
            focusedContentColor = Color.Black,
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier,
    ) {
        // Snug: with Downloads and Torrents (Real-Debrid) the menu has
        // nine labels to fit across a 960dp-wide TV.
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NavIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            focusedContentColor = Color.Black,
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = modifier,
    ) {
        Box(Modifier.padding(7.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

/** "7.11pm", as Sky and the UK write it. */
private fun clockNow(): String =
    SimpleDateFormat("h.mma", Locale.UK).format(Date()).lowercase(Locale.UK)
