package com.arcsus.arctv

import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.arcsus.arctv.data.SavedPlaylist
import com.arcsus.arctv.ui.ArcTvViewModelFactory
import com.arcsus.arctv.ui.AuthScreen
import com.arcsus.arctv.ui.AuthViewModel
import com.arcsus.arctv.ui.HomeScreen
import com.arcsus.arctv.ui.LiveSetupScreen
import com.arcsus.arctv.ui.theme.ArcTvTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Design-loop capture: renders the real screens with live data on the JVM
 * (Robolectric + Roborazzi) and writes PNGs to .design-loop/iter-N/. Not a
 * pass/fail test; it exists so a critique can look at pixels.
 *
 * Needs ARC_AD_KEY (AllDebrid key) in the environment; the IPTV playlist is
 * optional (ARC_IPTV_URL / ARC_IPTV_USER / ARC_IPTV_PASS).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-notnight-xhdpi")
class DesignLoopScreens {

    @get:Rule
    val rule = createComposeRule()

    private val outDir: File by lazy {
        val iter = System.getenv("DESIGN_ITER") ?: "1"
        File(System.getenv("DESIGN_OUT") ?: "../.design-loop", "iter-$iter").apply { mkdirs() }
    }
    private lateinit var app: ArcTvApp
    private lateinit var factory: ArcTvViewModelFactory

    @Before
    fun seed() {
        val adKey = System.getenv("ARC_AD_KEY").orEmpty()
        assumeTrue("ARC_AD_KEY not set; skipping design captures", adKey.isNotBlank())
        app = ApplicationProvider.getApplicationContext()
        factory = ArcTvViewModelFactory(app)
        runBlocking {
            app.tokenStore.saveAdApiKey(adKey)
            app.settingsStore.markLiveSetupDone()
            val url = System.getenv("ARC_IPTV_URL").orEmpty()
            val user = System.getenv("ARC_IPTV_USER").orEmpty()
            val pass = System.getenv("ARC_IPTV_PASS").orEmpty()
            if (url.isNotBlank()) {
                val playlist = SavedPlaylist(name = "IPTV", url = url, kind = "xtream", username = user, password = pass)
                app.settingsStore.savePlaylists(listOf(playlist))
                // Open the guide on a real UK group so the grid (not the picker) is captured.
                val groups = runCatching { app.liveRepository.panelGroups(playlist) }.getOrDefault(emptyList())
                val uk = groups.firstOrNull { it.contains("UK", true) && it.contains("ENTERTAIN", true) }
                    ?: groups.firstOrNull { it.contains("UK", true) }
                println("design-loop: panel groups=${groups.size}, guide group=$uk")
                if (uk != null) {
                    app.settingsStore.saveGuideGroups(listOf(uk))
                    app.settingsStore.saveActiveGuideGroup(uk)
                }
            }
        }
    }

    /** Same frame MainActivity draws: theme, background, overscan inset. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        ArcTvTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.Color(0xFF17284A),
                                    androidx.compose.ui.graphics.Color(0xFF0F1A30),
                                    com.arcsus.arctv.ui.theme.ArcBackground,
                                ),
                                start = androidx.compose.ui.geometry.Offset.Zero,
                                end = androidx.compose.ui.geometry.Offset.Infinite,
                            ),
                        )
                ) { content() }
            }
        }
    }

    /** Let background work (network, image decode) land and be drawn. */
    private fun settle(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            runCatching { rule.mainClock.advanceTimeByFrame() }
            rule.waitForIdle()
            Thread.sleep(100)
        }
    }

    private fun waitForText(text: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            if (rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()) return true
            Thread.sleep(150)
        }
        return false
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(outDir, "$name.png").path)
        println("design-loop: captured $name")
    }

    private fun key(k: androidx.compose.ui.input.key.Key) {
        rule.onRoot().performKeyInput { pressKey(k) }
        settle(120)
    }

    private fun isFocused(text: String): Boolean =
        rule.onAllNodes(hasText(text) and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty()

    /**
     * Open a rail section the way a viewer does: LEFT out of the content,
     * UP to the top of the rail, DOWN until the item has focus, then OK.
     * Focus placed on the rail any other way is treated as the platform's
     * doing and sent back into the content, so a plain requestFocus won't do.
     */
    private fun focus(text: String): Boolean {
        key(androidx.compose.ui.input.key.Key.DirectionLeft)
        repeat(8) { if (!isFocused(text)) key(androidx.compose.ui.input.key.Key.DirectionUp) }
        repeat(10) { if (!isFocused(text)) key(androidx.compose.ui.input.key.Key.DirectionDown) }
        val reached = isFocused(text)
        println("design-loop: rail '$text' reached=$reached")
        if (!reached) return false
        key(androidx.compose.ui.input.key.Key.DirectionCenter)
        settle(1_200)
        return true
    }

    /** Focus a node and press OK -- tv-material3 controls ignore touch. */
    private fun press(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        node.requestFocus()
        settle(150)
        node.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
        settle(150)
    }

    @Test
    fun captureHome() {
        rule.setContent { Frame { HomeScreen(factory) } }
        val loaded = waitForText("Trending", 60_000)
        println("design-loop: home loaded=$loaded")
        settle(6_000) // artwork
        snap("01-home")
        // Rail expanded (focus on the rail), then Movies opened.
        focus("TV Shows")
        snap("02-home-rail")
        focus("Movies")
        waitForText("Popular", 30_000)
        settle(6_000)
        snap("03-movies")
        // Focus a tile inside the grid so the detail header and focus state show.
        runCatching {
            val tiles = rule.onAllNodes(isFocusable() and hasClickAction()).fetchSemanticsNodes()
            if (tiles.size > 12) {
                rule.onAllNodes(isFocusable() and hasClickAction())[12].requestFocus()
                settle(800)
                snap("04-movies-focused")
            }
        }
        focus("Live")
        waitForText("channels", 60_000)
        settle(3_000)
        snap("05-live")
        focus("Guide")
        settle(14_000) // channels + EPG
        snap("06-guide")
        runCatching {
            // Pick a UK group if the picker is showing.
            val uk = rule.onAllNodes(hasText("ENTERTAINMENT", substring = true) and hasClickAction())
            if (uk.fetchSemanticsNodes().isNotEmpty()) {
                press(uk[0])
                settle(12_000)
                snap("07-guide-group")
            }
        }
        focus("Settings")
        settle(1_500)
        snap("08-settings")
    }

    @Test
    fun captureSetup() {
        val authViewModel = factory.create(AuthViewModel::class.java)
        rule.setContent { Frame { AuthScreen(authViewModel) } }
        settle(800)
        snap("09-signin")
    }

    @Test
    fun captureLiveSetup() {
        rule.setContent { Frame { LiveSetupScreen(factory) {} } }
        settle(800)
        snap("10-live-setup")
        runCatching {
            press(rule.onNodeWithText("Show code"))
            waitForText("enter the code", 20_000)
            settle(1_500)
            snap("11-live-setup-code")
        }
    }
}
