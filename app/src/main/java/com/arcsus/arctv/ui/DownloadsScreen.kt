package com.arcsus.arctv.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arcsus.arctv.data.DownloadItem

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text(
                "Downloads",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (state.totalCount > 0) "${state.items.size} of ${state.totalCount}" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = { viewModel.toggleVideoOnly() }) {
                Icon(Icons.Default.FilterList, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.videoOnly) "Videos only" else "All files")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
        }

        when {
            state.items.isEmpty() && state.loading -> CenteredMessage("Loading downloads…")

            state.items.isEmpty() && state.error != null -> CenteredError(state.error!!) {
                viewModel.refresh()
            }

            state.items.isEmpty() -> CenteredMessage(
                if (state.videoOnly) "No video downloads. Switch the filter to see all files."
                else "No downloads yet."
            )

            else -> {
                val gridState = rememberLazyGridState()
                val itemCount = state.items.size
                LaunchedEffect(gridState, itemCount) {
                    snapshotFlow {
                        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    }.collect { lastVisible ->
                        if (lastVisible >= itemCount - 8) viewModel.loadMore()
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp, top = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            onClick = {},
                            onLongClick = {},
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    androidx.tv.material3.Card(
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Column(Modifier.padding(14.dp).height(112.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HostIcon(host = item.host, iconUrl = item.hostIcon)
                Spacer(Modifier.width(8.dp))
                Text(
                    item.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                item.filename,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatBytes(item.filesize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDate(item.generated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun HostIcon(host: String, iconUrl: String?, modifier: Modifier = Modifier) {
    val url = iconUrl ?: "https://www.google.com/s2/favicons?domain=$host&sz=64"
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.size(20.dp),
    )
}

@Composable
fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
