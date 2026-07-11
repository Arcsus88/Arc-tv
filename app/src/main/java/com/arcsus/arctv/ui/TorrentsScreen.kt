package com.arcsus.arctv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.data.FilenameParser
import com.arcsus.arctv.data.TorrentItem
import com.arcsus.arctv.ui.theme.ArcBlue

@Composable
fun TorrentsScreen(viewModel: TorrentsViewModel) {
    val state by viewModel.state.collectAsState()
    val picker by viewModel.picker.collectAsState()
    val playRequest by viewModel.playRequest.collectAsState()
    val context = LocalContext.current
    var showNoPlayerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playRequest) {
        playRequest?.let { unrestricted ->
            if (!playVideo(context, unrestricted.download, unrestricted.filename)) {
                showNoPlayerDialog = true
            }
            viewModel.consumePlayRequest()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text(
                "Torrents",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
        }

        when {
            state.items.isEmpty() && state.loading -> CenteredMessage("Loading torrents…")
            state.items.isEmpty() && state.error != null -> CenteredError(state.error!!) {
                viewModel.refresh()
            }
            state.items.isEmpty() -> CenteredMessage("No torrents yet.")
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
                    columns = GridCells.Fixed(6),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(state.items, key = { it.id }) { torrent ->
                        TorrentCard(torrent, onClick = { viewModel.openTorrent(torrent) })
                    }
                }
            }
        }
    }

    when (val p = picker) {
        TorrentsViewModel.Picker.Hidden -> Unit

        is TorrentsViewModel.Picker.LoadingInfo ->
            PickerDialog(title = p.torrent.filename, onDismiss = { viewModel.dismissPicker() }) {
                Text("Loading files…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

        is TorrentsViewModel.Picker.Unrestricting ->
            PickerDialog(title = p.torrent.filename, onDismiss = { viewModel.dismissPicker() }) {
                Text("Unrestricting link…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

        is TorrentsViewModel.Picker.Error ->
            PickerDialog(title = "Something went wrong", onDismiss = { viewModel.dismissPicker() }) {
                Text(p.message)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { viewModel.dismissPicker() }) { Text("Close") }
            }

        is TorrentsViewModel.Picker.Files ->
            PickerDialog(title = "Pick a file to play", onDismiss = { viewModel.dismissPicker() }) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 380.dp),
                ) {
                    items(p.files) { file ->
                        Card(onClick = { viewModel.unrestrictAndPlay(p.torrent, file) }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (file.bytes > 0) {
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        formatBytes(file.bytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    if (showNoPlayerDialog) {
        NoPlayerDialog(onDismiss = { showNoPlayerDialog = false })
    }
}

@Composable
private fun TorrentCard(torrent: TorrentItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val finished = torrent.status == "downloaded"
    val parsed = remember(torrent.filename) { FilenameParser.parse(torrent.filename) }
    Card(
        onClick = {
            if (finished) {
                onClick()
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Torrent not finished yet (${torrent.status.replace('_', ' ')})",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                PosterImage(rememberPosterUrl(torrent.filename), Modifier.fillMaxSize())
                // Status pill over the poster corner.
                Text(
                    statusLabel(torrent),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(torrent),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    parsed?.title ?: torrent.filename,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val meta = listOfNotNull(
                    parsed?.episodeLabel ?: parsed?.year?.toString(),
                    formatBytes(torrent.bytes),
                ).joinToString(" • ")
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun statusLabel(torrent: TorrentItem): String = when (torrent.status) {
    "downloaded" -> "Ready"
    "downloading" -> "Downloading ${torrent.progress.toInt()}%"
    else -> torrent.status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun statusColor(torrent: TorrentItem) = when (torrent.status) {
    "downloaded" -> ArcBlue
    "error", "magnet_error", "virus", "dead" -> androidx.compose.ui.graphics.Color(0xFFFF6B6B)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun PickerDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(28.dp).width(520.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(18.dp))
                content()
            }
        }
    }
}
