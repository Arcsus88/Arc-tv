package com.arcsus.arctv.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.arcsus.arctv.R
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One of Home's link banners (Disney, Sky Sports, Netflix), as on the web.
 * [stops] and [center] describe the web banner's CSS backdrop, a
 * `radial-gradient(ellipse at …)`; [blurb] is what the details area above
 * the rows says while the banner is focused.
 */
class HomeBanner(
    val id: String,
    val title: String,
    val kind: String,
    val blurb: String,
    val meta: String,
    @DrawableRes val logo: Int,
    val logoHeight: Dp,
    val stops: Array<Pair<Float, Color>>,
    val center: Offset,
    val onOpen: () -> Unit,
)

/** The web app's three banners, in its order: Disney, Sky Sports, Netflix. */
fun homeBanners(
    disneyMeta: String?,
    netflixMeta: String?,
    onDisney: () -> Unit,
    onSkySports: () -> Unit,
    onNetflix: () -> Unit,
): List<HomeBanner> = buildList {
    // Disney's and Netflix's wait on their catalogues; Sky Sports is always there.
    if (disneyMeta != null) {
        add(
            HomeBanner(
                id = "disney",
                title = "Disney",
                kind = "COLLECTION",
                blurb = "Every Disney-branded and Pixar feature from 1937 to 2026. Films are " +
                    "searched ahead of time, so Play starts the best source straight away.",
                meta = disneyMeta,
                logo = R.drawable.logo_disney,
                logoHeight = 56.dp,
                // radial-gradient(ellipse_at_top,#2f5bd8_0%,#13246b_55%,#070d2e_100%)
                stops = arrayOf(0f to Color(0xFF2F5BD8), 0.55f to Color(0xFF13246B), 1f to Color(0xFF070D2E)),
                center = Offset(0.5f, 0f),
                onOpen = onDisney,
            ),
        )
    }
    add(
        HomeBanner(
            id = "sky-sports",
            title = "Sky Sports",
            kind = "LIVE SPORT",
            blurb = "The UK sports channels, opened in the TV guide with what's on now and next " +
                "on every channel.",
            meta = "Guide · UK Sports",
            logo = R.drawable.logo_sky_sports,
            logoHeight = 40.dp,
            // radial-gradient(ellipse_at_bottom,#5c1024_0%,#141a4a_55%,#060a22_100%)
            stops = arrayOf(0f to Color(0xFF5C1024), 0.55f to Color(0xFF141A4A), 1f to Color(0xFF060A22)),
            center = Offset(0.5f, 1f),
            onOpen = onSkySports,
        ),
    )
    if (netflixMeta != null) {
        add(
            HomeBanner(
                id = "netflix",
                title = "Netflix",
                kind = "COLLECTION",
                blurb = "The films and series streaming on Netflix in the UK. Sources are " +
                    "searched live, as for Movies and Shows.",
                meta = netflixMeta,
                logo = R.drawable.logo_netflix,
                logoHeight = 46.dp,
                // radial-gradient(ellipse_at_center,#4a0a0f_0%,#17050a_55%,#050102_100%)
                stops = arrayOf(0f to Color(0xFF4A0A0F), 0.55f to Color(0xFF17050A), 1f to Color(0xFF050102)),
                center = Offset(0.5f, 0.5f),
                onOpen = onNetflix,
            ),
        )
    }
}

/**
 * CSS `radial-gradient(ellipse at …)` with its default farthest-corner
 * size: a circle drawn in a space squashed to the ellipse's aspect.
 */
fun DrawScope.drawEllipseGradient(stops: Array<Pair<Float, Color>>, centerFraction: Offset) {
    val c = Offset(size.width * centerFraction.x, size.height * centerFraction.y)
    val rx = max(c.x, size.width - c.x) * sqrt(2f)
    val ry = max(c.y, size.height - c.y) * sqrt(2f)
    val k = (ry / rx).coerceAtLeast(0.001f)
    scale(scaleX = 1f, scaleY = k, pivot = c) {
        drawRect(
            brush = Brush.radialGradient(*stops, center = c, radius = rx),
            topLeft = Offset(0f, c.y - c.y / k),
            size = Size(size.width, size.height / k),
        )
    }
}

/**
 * The web's banner carousel, made for a remote: one wide banner at a time,
 * LEFT/RIGHT slides to the next (wrapping round, like the web's arrows and
 * swipe), OK opens the one showing. [onShowing] reports the banner in view
 * while it has focus, for the details area.
 */
@Composable
fun HomeBannerCarousel(
    banners: List<HomeBanner>,
    onShowing: (HomeBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banners.isEmpty()) return
    val n = banners.size
    var index by remember { mutableIntStateOf(0) }
    // The list can shrink or grow (Disney's and Netflix's wait on their catalogues).
    val current = index.coerceIn(0, n - 1)
    var focused by remember { mutableStateOf(false) }
    val position by animateFloatAsState(current.toFloat(), tween(450), label = "banner")
    val showingId = banners[current].id
    LaunchedEffect(showingId, focused) { if (focused) onShowing(banners[current]) }

    val shape = RoundedCornerShape(14.dp)
    Card(
        onClick = { banners[current].onOpen() },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.015f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
        ),
        colors = CardDefaults.colors(containerColor = Color.Black),
        modifier = modifier
            .fillMaxWidth()
            .height(BANNER_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || n < 2) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { index = (current - 1 + n) % n; true }
                    Key.DirectionRight -> { index = (current + 1) % n; true }
                    else -> false
                }
            },
    ) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            // Every banner in a strip, slid so the current one fills the card.
            Layout(
                content = { banners.forEach { BannerFace(it) } },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val placeables = measurables.map { it.measure(constraints.copy(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h)) }
                layout(w, h) {
                    placeables.forEachIndexed { i, p -> p.placeRelative(((i - position) * w).toInt(), 0) }
                }
            }
            if (n > 1) {
                if (focused) {
                    BannerArrow("‹", Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
                    BannerArrow("›", Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp),
                ) {
                    repeat(n) { i ->
                        Box(
                            Modifier
                                .height(4.dp)
                                .width(if (i == current) 18.dp else 6.dp)
                                .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.35f), RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }
}

internal val BANNER_HEIGHT = 108.dp

@Composable
private fun BannerFace(banner: HomeBanner) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawEllipseGradient(banner.stops, banner.center) },
    ) {
        Image(
            painter = painterResource(banner.logo),
            contentDescription = banner.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(banner.logoHeight),
        )
    }
}

@Composable
private fun BannerArrow(glyph: String, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(32.dp)
            .graphicsLayer { alpha = 0.92f }
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
