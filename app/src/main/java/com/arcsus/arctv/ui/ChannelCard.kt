package com.arcsus.arctv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage

/**
 * What a live channel shows where there's no programme still (Continue
 * Watching, the guide): its logo, centred on a plain dark card lit softly
 * from behind, with nothing of the card's own to compete with it. Without a
 * logo (or while one fails to load), the channel's [name]; a blank name
 * leaves the card empty, for a logo that may yet arrive. [logoAlpha] dims
 * the logo under a play button.
 */
@Composable
fun ChannelCard(logo: String, name: String, modifier: Modifier = Modifier, logoAlpha: Float = 1f) {
    var failed by remember(logo) { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize().channelCardBackdrop()) {
        if (logo.isNotBlank() && !failed) {
            AsyncImage(
                model = logo,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                // Room round the logo, so a wide one (itv1, Sky Sports) and a
                // square one (Channel 4) both sit comfortably.
                modifier = Modifier.fillMaxWidth(0.62f).fillMaxHeight(0.48f).alpha(logoAlpha),
            )
        } else if (name.isNotBlank()) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f * logoAlpha),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/** A dark slate card, a touch lighter at the top, with a soft glow behind the logo. */
private fun Modifier.channelCardBackdrop(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF262C38), Color(0xFF13161D))))
    drawRect(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.45f),
            radius = size.width * 0.55f,
        ),
    )
}
