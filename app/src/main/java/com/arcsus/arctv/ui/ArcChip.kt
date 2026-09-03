package com.arcsus.arctv.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.theme.ArcBlue

/**
 * A quiet filter chip for dense rows (sorts, genres): dim plain text that
 * becomes a small filled accent pill when selected. Far less visual noise
 * than a row of outlined buttons.
 */
@Composable
fun TextChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same language as the rail: the chosen chip is a white box, focus is a
    // white keyline. The accent stays out of it.
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.Transparent,
            focusedContainerColor = if (selected) Color.White else Color.White.copy(alpha = 0.10f),
            contentColor = if (selected) Color(0xFF0B0F15) else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = if (selected) Color(0xFF0B0F15) else Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = shape,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** The app-wide selectable chip: a white box when chosen, a quiet panel otherwise. */
@Composable
fun ArcChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = if (selected) Color.White else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) Color(0xFF0B0F15) else MaterialTheme.colorScheme.onSurface,
            focusedContentColor = if (selected) Color(0xFF0B0F15) else Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = shape,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}
