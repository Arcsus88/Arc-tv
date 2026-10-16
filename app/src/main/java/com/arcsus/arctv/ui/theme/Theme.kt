package com.arcsus.arctv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// The web app's palette (globals.css), so both clients look like one
// product: a near-black ground, slightly lifted panels, hairline borders,
// cool grey secondary text and the Arc blue accent.
val ArcBackground = Color(0xFF0A0A0C)
val ArcSurface = Color(0xFF14161B)
val ArcSurfaceRaised = Color(0xFF1C1F26)
val ArcBorder = Color(0xFF23262E)
val ArcForeground = Color(0xFFF5F6F8)
val ArcMuted = Color(0xFF9AA3B2)
val ArcBlue = Color(0xFF3D7BFF)
val ArcDestructive = Color(0xFFF87171)

@Composable
fun ArcTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ArcBlue,
            onPrimary = Color.White,
            background = ArcBackground,
            onBackground = ArcForeground,
            surface = ArcSurface,
            onSurface = ArcForeground,
            surfaceVariant = ArcSurfaceRaised,
            onSurfaceVariant = ArcMuted,
            // TV Material draws focus outlines in `border`: Netflix's white
            // keyline. The web's hairline grey is `borderVariant`.
            border = Color.White,
            borderVariant = ArcBorder,
            error = ArcDestructive,
            // TV Material paints focused buttons and cards in the inverse
            // pair: Netflix's white fill with black text.
            inverseSurface = Color.White,
            inverseOnSurface = Color.Black,
        ),
        typography = ArcTypography,
        content = content,
    )
}
