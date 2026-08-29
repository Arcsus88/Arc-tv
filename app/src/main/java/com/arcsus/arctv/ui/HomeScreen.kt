package com.arcsus.arctv.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.theme.ArcBlue

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

    Column(Modifier.fillMaxSize()) {
        UpdateBanner(updateViewModel)

        // Sky-style chrome: one slim line — wordmark, plain-text nav with an
        // accent underline under the active section, search on the right.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 2.dp, start = 40.dp, end = 40.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ArcBlue, fontWeight = FontWeight.Bold)) {
                        append("Arc")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" TV") }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            tabs.forEachIndexed { index, title ->
                TopNavItem(
                    title = title,
                    selected = index == selectedTab,
                    onFocused = { selectedTab = index },
                )
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = {
                selectedTab = 0
                searchOpen = true
            }) {
                Icon(Icons.Default.Search, contentDescription = "Search", Modifier.size(18.dp))
            }
        }

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

/**
 * A top-bar section: plain text that brightens when focused, with an accent
 * underline marking the active section. Switches on focus like the old tabs.
 */
@Composable
private fun TopNavItem(title: String, selected: Boolean, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onFocused,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        modifier = Modifier.onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected || focused) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .height(3.dp)
                    .width(26.dp)
                    .background(
                        if (selected) ArcBlue else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}
