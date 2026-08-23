package com.arcsus.arctv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text

@Composable
fun HomeScreen(factory: ArcTvViewModelFactory) {
    val updateViewModel: UpdateViewModel = viewModel(factory = factory)
    val browseViewModel: BrowseViewModel = viewModel(factory = factory)
    val downloadsViewModel: DownloadsViewModel = viewModel(factory = factory)
    val torrentsViewModel: TorrentsViewModel = viewModel(factory = factory)
    val liveViewModel: LiveViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    val updateState by updateViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    // Downloads/Torrents browse the Real-Debrid account, so they only appear
    // when Real-Debrid is connected (AllDebrid-only setups hide them).
    val tabs = if (settingsState.rdConnected) {
        listOf("Browse", "Live", "Downloads", "Torrents", "Settings")
    } else {
        listOf("Browse", "Live", "Settings")
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    if (selectedTab >= tabs.size) selectedTab = tabs.size - 1

    Column(Modifier.fillMaxSize()) {
        UpdateBanner(updateViewModel)

        Box(
            Modifier.fillMaxWidth().padding(top = 12.dp, start = 40.dp, end = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                val feedback = when {
                    updateState.checking -> "Checking…"
                    updateState.upToDate -> "You're on the latest version"
                    updateState.error != null -> updateState.error
                    else -> null
                }
                feedback?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Button(
                    onClick = { updateViewModel.checkForUpdate() },
                    enabled = !updateState.checking,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Check for updates")
                }
            }
        }

        when (tabs[selectedTab]) {
            "Browse" -> BrowseScreen(browseViewModel)
            "Live" -> LiveScreen(liveViewModel)
            "Downloads" -> DownloadsScreen(downloadsViewModel)
            "Torrents" -> TorrentsScreen(torrentsViewModel)
            "Settings" -> SettingsScreen(settingsViewModel)
        }
    }
}
