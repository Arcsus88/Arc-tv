package com.arcsus.arctv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text

@Composable
fun UpdateBanner(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()
    val info = state.available ?: return
    if (state.dismissed) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 40.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Arc TV ${info.version} is available",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Button(onClick = { viewModel.install() }) {
            Text(if (state.downloading) "Downloading…" else "Update now")
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = { viewModel.dismiss() }) {
            Text("Later")
        }
    }
}
