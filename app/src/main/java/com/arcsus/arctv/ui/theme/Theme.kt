package com.arcsus.arctv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val ArcBlue = Color(0xFF569CFF)
val ArcBackground = Color(0xFF101418)
val ArcSurface = Color(0xFF1A2027)

@Composable
fun ArcTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ArcBlue,
            background = ArcBackground,
            surface = ArcSurface,
            surfaceVariant = Color(0xFF232B35),
            onPrimary = Color(0xFF06101F),
            onBackground = Color(0xFFE8EDF2),
            onSurface = Color(0xFFE8EDF2),
            onSurfaceVariant = Color(0xFFB4BEC9),
        ),
        content = content,
    )
}
