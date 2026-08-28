package com.arcsus.arctv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
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

    Column(Modifier.fillMaxSize()) {
        UpdateBanner(updateViewModel)

        // Wordmark on the left, tabs centered — the update button lives in
        // Settings now so nothing can overlap the tab row.
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, start = 40.dp, end = 40.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ArcBlue, fontWeight = FontWeight.Bold)) {
                        append("Arc")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" TV") }
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.align(Alignment.Center)) {
                tabs.forEachIndexed { index, title ->
                    key(index) {
                        Tab(
                            selected = index == selectedTab,
                            onFocus = { selectedTab = index },
                        ) {
                            Text(
                                title,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        when (tabs[selectedTab]) {
            "Browse" -> BrowseScreen(browseViewModel)
            "Live" -> LiveScreen(liveViewModel)
            "Guide" -> GuideScreen(guideViewModel)
            "Downloads" -> DownloadsScreen(downloadsViewModel)
            "Torrents" -> TorrentsScreen(torrentsViewModel)
            "Settings" -> SettingsScreen(settingsViewModel, updateViewModel)
        }
    }
}
