package com.arcsus.arctv.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The web app's `.stadium-tile` backdrop for live-TV tiles with no
 * programme still: a stadium at night -- floodlight glow from above, two
 * faint light beams, and pitch markings (centre circle on the halfway line,
 * corner arcs) fading in over a green glow at the foot. Drawn, no assets.
 * Layers bottom to top, as the CSS lists them top to bottom.
 */
fun Modifier.stadiumBackdrop(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val white = Color.White
    // Base: linear-gradient(160deg, #1b2436 0%, #101624 52%, #05070c 100%).
    drawRect(cssLinear(160f, arrayOf(0f to Color(0xFF1B2436), 0.52f to Color(0xFF101624), 1f to Color(0xFF05070C))))
    // Green pitch glow: radial-gradient(90% 55% at 50% 105%, rgba(74,222,128,.1), transparent 65%).
    drawEllipse(arrayOf(0f to Color(0x1A4ADE80), 0.65f to Color.Transparent), Offset(w * 0.5f, h * 1.05f), w * 0.9f, h * 0.55f)
    // Corner arcs at the bottom corners (the SVG's r=18 in a 200-wide box).
    val r = w * 0.09f
    val arcStroke = Stroke(width = (1.5f * w / 200f).coerceAtLeast(1f))
    drawArc(white.copy(alpha = 0.13f), 270f, 90f, false, Offset(-r, h - r), Size(2 * r, 2 * r), style = arcStroke)
    drawArc(white.copy(alpha = 0.13f), 180f, 90f, false, Offset(w - r, h - r), Size(2 * r, 2 * r), style = arcStroke)
    // Centre circle: a ring at 92-97% of a 58%-sized box's closest side.
    val ring = min(w, h) * 0.29f
    drawCircle(white.copy(alpha = 0.13f), radius = ring * 0.945f, center = center, style = Stroke(width = ring * 0.05f))
    // Centre spot.
    drawCircle(white.copy(alpha = 0.16f), radius = (2.2f * density).coerceAtLeast(1.5f), center = center)
    // Halfway line.
    val line = 1.5f * density / 2f
    drawRect(white.copy(alpha = 0.12f), topLeft = Offset(0f, h / 2 - line / 2), size = Size(w, line))
    // Light beams: linear-gradient(115deg, …).
    drawRect(
        cssLinear(
            115f,
            arrayOf(
                0.22f to Color.Transparent, 0.34f to white.copy(alpha = 0.05f), 0.44f to Color.Transparent,
                0.58f to Color.Transparent, 0.68f to white.copy(alpha = 0.04f), 0.78f to Color.Transparent,
            ),
        ),
    )
    // Floodlight glow: radial-gradient(130% 85% at 50% -25%, rgba(61,123,255,.3), transparent 62%).
    drawEllipse(arrayOf(0f to Color(0x4D3D7BFF), 0.62f to Color.Transparent), Offset(w * 0.5f, -h * 0.25f), w * 1.3f, h * 0.85f)
}

/** CSS `linear-gradient(<angle>deg, …)` over the whole draw area. */
private fun DrawScope.cssLinear(angleDeg: Float, stops: Array<Pair<Float, Color>>): Brush {
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = sin(a).toFloat()
    val dy = -cos(a).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    return Brush.linearGradient(
        *stops,
        start = Offset(center.x - dx * half, center.y - dy * half),
        end = Offset(center.x + dx * half, center.y + dy * half),
    )
}

/** CSS `radial-gradient(<rx> <ry> at <center>, …)`: a circle squashed to the ellipse. */
private fun DrawScope.drawEllipse(stops: Array<Pair<Float, Color>>, c: Offset, rx: Float, ry: Float) {
    val k = (ry / rx).coerceAtLeast(0.001f)
    scale(scaleX = 1f, scaleY = k, pivot = c) {
        drawRect(
            brush = Brush.radialGradient(*stops, center = c, radius = rx),
            topLeft = Offset(0f, c.y - c.y / k),
            size = Size(size.width, size.height / k),
        )
    }
}
