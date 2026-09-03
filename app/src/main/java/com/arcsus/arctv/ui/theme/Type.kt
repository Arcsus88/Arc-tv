package com.arcsus.arctv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import com.arcsus.arctv.R

/**
 * Inter, bundled so it looks the same on every set (no Google Play download
 * at runtime). Weights: Regular, Medium, SemiBold, Bold.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight.sp,
    letterSpacing = spacing.sp,
)

/**
 * The 10-foot type scale: sizes close to TV Material's defaults, with
 * tighter tracking on the big sizes (Inter is wide at display sizes) and a
 * touch of tracking on the small labels so they stay legible from the sofa.
 */
val ArcTypography = Typography(
    displayLarge = style(56, FontWeight.Bold, 62, -1.0f),
    displayMedium = style(44, FontWeight.Bold, 50, -0.8f),
    displaySmall = style(36, FontWeight.Bold, 42, -0.6f),
    headlineLarge = style(32, FontWeight.SemiBold, 38, -0.4f),
    headlineMedium = style(28, FontWeight.SemiBold, 34, -0.3f),
    headlineSmall = style(24, FontWeight.SemiBold, 30, -0.2f),
    titleLarge = style(20, FontWeight.SemiBold, 26, -0.1f),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    titleSmall = style(14, FontWeight.Medium, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 16),
    labelLarge = style(14, FontWeight.Medium, 20, 0.1f),
    labelMedium = style(12, FontWeight.Medium, 16, 0.2f),
    labelSmall = style(11, FontWeight.Medium, 14, 0.3f),
)
