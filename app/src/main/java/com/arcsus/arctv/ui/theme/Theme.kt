package com.arcsus.arctv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val ArcBlue = Color(0xFF569CFF)
val ArcBackground = Color(0xFF0B0F15)
val ArcSurface = Color(0xFF161D26)

@Composable
fun ArcTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ArcBlue,
            background = ArcBackground,
            surface = ArcSurface,
            surfaceVariant = Color(0xFF212B37),
            onPrimary = Color(0xFF06101F),
            onBackground = Color(0xFFEAF0F6),
            onSurface = Color(0xFFEAF0F6),
            onSurfaceVariant = Color(0xFFAFBAC7),
        ),
        content = content,
    )
}
