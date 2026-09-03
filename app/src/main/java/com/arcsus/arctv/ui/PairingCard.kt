package com.arcsus.arctv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.SettingsViewModel.PairState
import com.arcsus.arctv.ui.theme.ArcBlue
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * "Add from your phone": the Sky-style pairing card. Shows a short code (and
 * a QR code straight to the web form) while the TV waits for playlists sent
 * from the phone, then reports what arrived.
 */
@Composable
fun PairingCard(
    state: PairState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        PairState.Idle -> Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Add from your phone", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Shows a short code; enter it on the website to send your saved playlists here. Nothing to type on the remote.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onStart) { Text("Show code") }
        }

        is PairState.Showing -> Surface(
            onClick = {},
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("On your phone, go to", style = MaterialTheme.typography.titleSmall)
                    Text(
                        state.url.removePrefix("https://"),
                        color = ArcBlue,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("and enter the code", style = MaterialTheme.typography.titleSmall)
                    Text(
                        state.code.chunked(3).joinToString("  "),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 6.sp,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Waiting for your phone… the code works for ten minutes.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
                Spacer(Modifier.width(24.dp))
                QrCode("${state.url}?code=${state.code}", size = 168.dp)
            }
        }

        is PairState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        state.added == 0 -> "Those playlists were already on this TV"
                        state.added == 1 -> "Added 1 playlist from your phone"
                        else -> "Added ${state.added} playlists from your phone"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = ArcBlue,
                )
                if (state.received > state.added && state.added > 0) {
                    Text(
                        "${state.received - state.added} already here, skipped.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onCancel) { Text("OK") }
        }

        is PairState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = onStart) { Text("Try again") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onCancel) { Text("Close") }
        }
    }
}

/** A QR code for [content], drawn crisp (no filtering) on a white tile. */
@Composable
private fun QrCode(content: String, size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(content) { qrBitmap(content, 264) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR code opening $content",
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .size(size)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(6.dp),
        )
    }
}

private fun qrBitmap(content: String, px: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        px,
        px,
        mapOf(EncodeHintType.MARGIN to 0),
    )
    val pixels = IntArray(px * px)
    for (y in 0 until px) {
        for (x in 0 until px) {
            pixels[y * px + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
}.getOrNull()
