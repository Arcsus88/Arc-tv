package com.arcsus.arctv.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.theme.ArcBlue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val tabs = if (settingsState.rdConnected) {
        listOf("Browse", "Live", "Guide", "Downloads", "Torrents", "Settings")
    } else {
        listOf("Browse", "Live", "Guide", "Settings")
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    if (selectedTab >= tabs.size) selectedTab = tabs.size - 1
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val liveState by liveViewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Sky Q layout: a left navigation rail (preview tile, vertical menu with
    // the active section boxed, brand and clock at the foot) and the content
    // filling the rest of the panel.
    Row(Modifier.fillMaxSize()) {
        NavRail(
            tabs = tabs,
            selected = selectedTab,
            onSelect = { selectedTab = it },
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
        Column(Modifier.weight(1f)) {
            UpdateBanner(updateViewModel)
            when (tabs[selectedTab]) {
                "Browse" -> BrowseScreen(browseViewModel, searchOpen) { searchOpen = it }
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
    onSearch: () -> Unit,
    preview: com.arcsus.arctv.data.SavedChannel?,
    onPreviewClick: (com.arcsus.arctv.data.SavedChannel) -> Unit,
) {
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
            .width(206.dp)
            .background(Color(0xFF0E1520))
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        // Sky's preview slot: the first favourite channel, one press to play.
        // (A live video preview would hold the IPTV panel's single allowed
        // connection hostage, so artwork stands in for the tuner picture.)
        if (preview != null) {
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
                activateOnFocus = true,
                onActivate = { onSelect(index) },
            )
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        RailItem(
            title = "Search",
            selected = false,
            activateOnFocus = false,
            onActivate = onSearch,
            icon = Icons.Default.Search,
        )
        Spacer(Modifier.height(16.dp))
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
    }
}

/**
 * One rail entry, Sky-style: the active section sits in a white box with dark
 * text; focus fills the accent. Section items switch on focus; action items
 * (Search) only on click.
 */
@Composable
private fun RailItem(
    title: String,
    selected: Boolean,
    activateOnFocus: Boolean,
    onActivate: () -> Unit,
    icon: ImageVector? = null,
) {
    Surface(
        onClick = onActivate,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.Transparent,
            focusedContainerColor = ArcBlue,
            contentColor = if (selected) Color(0xFF0B0F15)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused && activateOnFocus) onActivate() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
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
    }
}

private fun clockNow(): Pair<String, String> {
    val now = Date()
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) to
        SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
}
