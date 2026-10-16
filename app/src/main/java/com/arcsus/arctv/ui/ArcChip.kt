package com.arcsus.arctv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * The web app's filter pill (modelled on Netflix's: a translucent white
 * fill with a hairline rim, a brighter keyline once chosen, 10dp corners)
 * with Netflix's TV focus on top: the focused pill turns solid white with
 * black text, so where you are reads from across the room.
 */
@Composable
fun ArcChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (selected) 0.15f else 0.10f),
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.82f),
            focusedContentColor = Color.Black,
            pressedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(if (selected) 1.5.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.8f else 0.1f)),
                shape = shape,
            ),
            focusedBorder = Border(BorderStroke(1.5.dp, Color.White), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(shape),
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 15.dp,
                vertical = if (compact) 6.dp else 8.dp,
            ),
        )
    }
}

/** A denser [ArcChip] for long rows of options (sorts, genres). */
@Composable
fun TextChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = ArcChip(label, selected, onClick, modifier, compact = true)
