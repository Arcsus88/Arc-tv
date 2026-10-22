package com.arcsus.arctv.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.arcsus.arctv.R
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One of Home's link banners (Disney, Sky Sports, Netflix, Prime Video, Apple TV, Hulu), as on the web.
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

/** The web app's six banners, in its order: Disney, Sky Sports, Netflix, Prime Video, Apple TV, Hulu. */
fun homeBanners(
    disneyMeta: String?,
    netflixMeta: String?,
    primeMeta: String?,
    appleTvMeta: String?,
    huluMeta: String?,
    onDisney: () -> Unit,
    onSkySports: () -> Unit,
    onNetflix: () -> Unit,
    onPrime: () -> Unit,
    onAppleTv: () -> Unit,
    onHulu: () -> Unit,
): List<HomeBanner> = buildList {
    // The collections wait on their catalogues; Sky Sports is always there.
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
                logoHeight = 50.dp,
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
            logoHeight = 30.dp,
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
                logoHeight = 36.dp,
                // radial-gradient(ellipse_at_center,#4a0a0f_0%,#17050a_55%,#050102_100%)
                stops = arrayOf(0f to Color(0xFF4A0A0F), 0.55f to Color(0xFF17050A), 1f to Color(0xFF050102)),
                center = Offset(0.5f, 0.5f),
                onOpen = onNetflix,
            ),
        )
    }
    if (primeMeta != null) {
        add(
            HomeBanner(
                id = "prime",
                title = "Prime Video",
                kind = "COLLECTION",
                blurb = "The films and series included with Prime Video in the UK. Sources are " +
                    "searched live, as for Movies and Shows.",
                meta = primeMeta,
                logo = R.drawable.logo_prime_video,
                logoHeight = 44.dp,
                // radial-gradient(ellipse_at_center,#0b2a52_0%,#04122a_55%,#00050d_100%)
                stops = arrayOf(0f to Color(0xFF0B2A52), 0.55f to Color(0xFF04122A), 1f to Color(0xFF00050D)),
                center = Offset(0.5f, 0.5f),
                onOpen = onPrime,
            ),
        )
    }
    if (appleTvMeta != null) {
        add(
            HomeBanner(
                id = "appletv",
                title = "Apple TV",
                kind = "COLLECTION",
                blurb = "The films and series streaming on Apple TV in the UK. Sources are " +
                    "searched live, as for Movies and Shows.",
                meta = appleTvMeta,
                logo = R.drawable.logo_apple_tv,
                logoHeight = 40.dp,
                // radial-gradient(ellipse_at_top,#3a3a3e_0%,#161618_55%,#050505_100%)
                stops = arrayOf(0f to Color(0xFF3A3A3E), 0.55f to Color(0xFF161618), 1f to Color(0xFF050505)),
                center = Offset(0.5f, 0f),
                onOpen = onAppleTv,
            ),
        )
    }
    if (huluMeta != null) {
        add(
            HomeBanner(
                id = "hulu",
                title = "Hulu",
                kind = "COLLECTION",
                blurb = "The films and series streaming on Hulu, which only runs in the US. Sources " +
                    "are searched live, as for Movies and Shows.",
                meta = huluMeta,
                logo = R.drawable.logo_hulu,
                logoHeight = 34.dp,
                // radial-gradient(ellipse_at_center,#0b3d26_0%,#04170e_55%,#010503_100%)
                stops = arrayOf(0f to Color(0xFF0B3D26), 0.55f to Color(0xFF04170E), 1f to Color(0xFF010503)),
                center = Offset(0.5f, 0.5f),
                onOpen = onHulu,
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
 * The web's banners in a row that scrolls, as on the web: four fit between
 * the screen's margins, each a quarter of that width, and the next peeks in
 * at the edge. LEFT/RIGHT moves between them, the row scrolling only once
 * the focused banner would cross a margin; OK opens the focused one. While a
 * catalogue is still loading (or unreachable) the others keep their width,
 * centred. [onShowing] reports the focused banner, for the details area.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeBannerRow(
    banners: List<HomeBanner>,
    onShowing: (HomeBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banners.isEmpty()) return
    val listState = rememberLazyListState()
    // Disney's banner arrives in front of the rest once its catalogue loads,
    // and the row would keep the banner it started on in view, opening it
    // scrolled past Disney. Back to the first.
    val first = banners.first().id
    LaunchedEffect(first) { listState.scrollToItem(0) }
    val density = LocalDensity.current
    val inset = remember(density) { InsetSpec(with(density) { TV_INSET_H.toPx() }) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - TV_INSET_H * 2 - BANNER_GAP * 3) / 4
        CompositionLocalProvider(LocalBringIntoViewSpec provides inset) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(BANNER_GAP, Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = TV_INSET_H),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(banners, key = { it.id }) { banner ->
                    BannerTile(banner, onFocus = { onShowing(banner) }, modifier = Modifier.width(tileWidth))
                }
            }
        }
    }
}

/**
 * Scrolls the banner row only as far as it takes to bring the focused banner
 * inside the screen's margins ([insetPx] at either side), so moving along the
 * four in view leaves the row still.
 */
@OptIn(ExperimentalFoundationApi::class)
private class InsetSpec(private val insetPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = containerSize - insetPx
        return when {
            offset < insetPx -> offset - insetPx
            offset + size > end -> offset + size - end
            else -> 0f
        }
    }
}

internal val BANNER_HEIGHT = 108.dp
private val BANNER_GAP = 12.dp

@Composable
private fun BannerTile(banner: HomeBanner, onFocus: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = banner.onOpen,
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
        ),
        colors = CardDefaults.colors(containerColor = Color.Black),
        modifier = modifier
            .height(BANNER_HEIGHT)
            .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawEllipseGradient(banner.stops, banner.center) }
                .padding(horizontal = 18.dp),
        ) {
            Image(
                painter = painterResource(banner.logo),
                contentDescription = banner.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(banner.logoHeight),
            )
        }
    }
}
