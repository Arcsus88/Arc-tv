package com.arcsus.arctv

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arcsus.arctv.data.SavedChannel
import com.arcsus.arctv.data.SavedPlaylist
import com.arcsus.arctv.ui.ArcTvViewModelFactory
import com.arcsus.arctv.ui.HomeScreen
import com.arcsus.arctv.ui.theme.ArcTvTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Regression: opening a group on the Live tab (Favourites included) used to
 * bounce the viewer to Home. The pressed card is removed with its grid, so
 * Compose clears focus; on a TV -- no touch mode -- the platform then hands
 * focus to the first focusable view on screen, the rail's top item, and the
 * rail's open-on-rest opened Browse.
 *
 * Runs offline: the playlist is a local M3U served from this process.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-notnight-xhdpi")
class LiveFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: ServerSocket
    private val port: Int get() = server.localPort
    private lateinit var factory: ArcTvViewModelFactory

    @Before
    fun seed() {
        // A one-line HTTP server: every request gets the playlist. (The JDK's
        // HttpServer is not on the Android unit-test classpath.)
        server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use { s ->
                        val input = s.getInputStream().bufferedReader()
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = buildString {
                            appendLine("#EXTM3U")
                            for (i in 1..6) {
                                appendLine("#EXTINF:-1 group-title=\"UK\",Channel $i")
                                appendLine("http://127.0.0.1:$port/stream/$i.ts")
                            }
                        }.toByteArray()
                        val out = s.getOutputStream()
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: audio/x-mpegurl\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(),
                        )
                        out.write(body)
                        out.flush()
                    }
                }
            }
        }
        val app: ArcTvApp = ApplicationProvider.getApplicationContext()
        factory = ArcTvViewModelFactory(app)
        runBlocking {
            app.settingsStore.markLiveSetupDone()
            val url = "http://127.0.0.1:$port/list.m3u"
            app.settingsStore.savePlaylists(listOf(SavedPlaylist(name = "Test", url = url, kind = "m3u")))
            app.settingsStore.toggleFavoriteChannel(
                SavedChannel(name = "Channel 2", url = "http://127.0.0.1:$port/stream/2.ts"),
            )
        }
    }

    @After
    fun stop() {
        server.close()
    }

    private fun settle(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            runCatching { rule.mainClock.advanceTimeByFrame() }
            rule.waitForIdle()
            Thread.sleep(50)
        }
    }

    private fun hasNode(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            settle(100)
            if (hasNode(text)) return true
        }
        return false
    }

    /**
     * Focus a node and press OK on the remote (tv-material3 ignores touch).
     * The node is looked up again before the key press: a rail item is an
     * icon while the rail is closed and text once focus opens it.
     */
    private fun press(find: () -> SemanticsNodeInteraction) {
        find().requestFocus()
        settle(200)
        find().performKeyInput { pressKey(Key.DirectionCenter) }
        settle(100)
    }

    private fun key(k: Key) {
        rule.onRoot().performKeyInput { pressKey(k) }
        settle(120)
    }

    private fun isFocused(text: String): Boolean =
        rule.onAllNodes(hasText(text) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    /**
     * Walk to a rail item with the remote, as a viewer would: LEFT out of the
     * content, UP to the top, DOWN until the item has focus. (Focus put on
     * the rail any other way is, by design, treated as the platform's doing
     * and sent back into the content.)
     */
    private val railTitles = listOf("Browse", "Movies", "TV Shows", "Live", "Guide", "Settings", "Search")

    private fun railTo(title: String) {
        repeat(12) { if (railTitles.none { isFocused(it) }) key(Key.DirectionLeft) }
        repeat(8) { if (!isFocused(title)) key(Key.DirectionUp) }
        repeat(10) { if (!isFocused(title)) key(Key.DirectionDown) }
        assertTrue("could not reach '$title' on the rail", isFocused(title))
    }

    private fun openSection(title: String) {
        railTo(title)
        key(Key.DirectionCenter)
    }

    /**
     * What a TV does the instant the focused view disappears: View.clearFocus
     * re-requests focus on the root when the device is not in touch mode.
     * Robolectric has no input pipeline, so the test does it by hand.
     */
    private fun platformRefocus() {
        val decor = rule.activity.window.decorView
        if (decor.findFocus() == null) decor.requestFocus()
    }

    @Test
    fun openingFavouritesStaysOnLive() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)
        // The closed rail shows icons; their content descriptions carry the title.
        openSection("Live")
        val loaded = waitFor("Favourites", 20_000)
        if (!loaded) println("live-focus: tree=\n" + rule.onRoot().printToString())
        assertTrue("Live tab did not load the playlist", loaded)

        press { rule.onNodeWithText("Favourites", substring = true) }
        platformRefocus()
        settle(900) // past the rail's open-on-rest

        assertTrue("Favourites group did not open", hasNode("Groups"))
        assertTrue("left the Live tab", hasNode("Live TV"))
        assertFalse("Home opened", hasNode("Trending"))
    }

    /** The deliberate path must keep working: resting on a rail item opens it. */
    @Test
    fun restingOnRailItemOpensIt() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)
        railTo("Settings")
        settle(700) // past the open-on-rest delay
        assertTrue("Settings did not open on rest", hasNode("Check for updates"))
    }

    @Test
    fun backToGroupsStaysOnLive() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)
        openSection("Live")
        assertTrue("Live tab did not load the playlist", waitFor("UK", 20_000))

        press { rule.onNode(hasText("UK", substring = true) and hasClickAction()) }
        platformRefocus()
        settle(900)
        assertTrue("group did not open", hasNode("Groups"))

        press { rule.onNodeWithText("Groups") }
        platformRefocus()
        settle(900)

        assertTrue("group grid did not return", hasNode("Favourites"))
        assertFalse("Home opened", hasNode("Trending"))
    }
}
