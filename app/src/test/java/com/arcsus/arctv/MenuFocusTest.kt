package com.arcsus.arctv

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arcsus.arctv.ui.ArcTvViewModelFactory
import com.arcsus.arctv.ui.HomeScreen
import com.arcsus.arctv.ui.UpdateViewModel
import com.arcsus.arctv.ui.theme.ArcTvTheme
import com.arcsus.arctv.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Focus between the menu, the update banner and the screen.
 *
 * Regressions: the update banner's buttons could not keep focus. UP from a
 * screen reached "Later" / "Update now", and a moment later focus was back
 * down in the screen. The banner sits between the menu and the content, and
 * the recovery that returns focus when nothing holds it counted only those
 * two -- so focus on the banner looked lost and was taken back. Back, which
 * should go up to the menu, was sent straight back down the same way.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-notnight-xhdpi")
class MenuFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var factory: ArcTvViewModelFactory

    @Before
    fun setUp() {
        factory = ArcTvViewModelFactory(ApplicationProvider.getApplicationContext())
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

    private fun key(k: Key) {
        rule.onRoot().performKeyInput { pressKey(k) }
        settle(120)
    }

    /** A focused node carrying [text] itself or in a child (TV surfaces don't merge). */
    private fun isFocused(text: String): Boolean =
        rule.onAllNodes(isFocused() and (hasText(text) or hasContentDescription(text) or hasAnyDescendant(hasText(text))))
            .fetchSemanticsNodes().isNotEmpty()

    private fun anyFocused(): Boolean = rule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun onBanner() = isFocused("Update now") || isFocused("Later")

    private val menuTitles = listOf(
        "Search", "Home", "Shows", "Movies", "Disney", "Netflix", "Prime", "Apple", "Live", "Guide", "Settings",
    )

    private fun onMenu() = menuTitles.any { isFocused(it) }

    /** UP out of the content, RIGHT along the menu to [title], OK. */
    private fun openSection(title: String) {
        repeat(12) { if (!onMenu()) key(Key.DirectionUp) }
        repeat(14) { if (!isFocused(title)) key(Key.DirectionRight) }
        assertTrue("could not reach '$title' on the menu", isFocused(title))
        key(Key.DirectionCenter)
        settle(600)
    }

    /** What a TV does when the focused view disappears (Robolectric has no input pipeline). */
    private fun platformRefocus() {
        val decor = rule.activity.window.decorView
        if (decor.findFocus() == null) decor.requestFocus()
    }

    @Suppress("UNCHECKED_CAST")
    private fun showBanner() {
        val vm = ViewModelProvider(rule.activity, factory)[UpdateViewModel::class.java]
        val state = UpdateViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            .get(vm) as MutableStateFlow<UpdateViewModel.UiState>
        // Let the launch check finish first, so it can't clear the banner we put up.
        val end = System.currentTimeMillis() + 20_000
        while (state.value.checking && System.currentTimeMillis() < end) settle(100)
        rule.runOnUiThread {
            state.value = state.value.copy(available = UpdateInfo("9.9.9", "http://127.0.0.1/none.apk"), dismissed = false)
        }
        settle(300)
    }

    @Test
    fun bannerKeepsFocus() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)
        showBanner()
        openSection("Settings")

        // Into the screen, then UP until the banner has focus, as in the report.
        key(Key.DirectionDown)
        repeat(20) { if (!onBanner()) key(Key.DirectionUp) }
        assertTrue("UP never reached the update banner", onBanner())

        settle(1_000)
        assertTrue("the update banner lost focus on its own", onBanner())

        key(Key.DirectionLeft)
        assertTrue("LEFT did not move to Update now", isFocused("Update now"))
        settle(1_000)
        assertTrue("Update now lost focus on its own", isFocused("Update now"))
    }

    @Test
    fun laterReturnsFocusToTheScreen() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)
        showBanner()
        openSection("Settings")
        key(Key.DirectionDown)
        repeat(20) { if (!isFocused("Later")) key(if (onBanner()) Key.DirectionRight else Key.DirectionUp) }
        assertTrue(isFocused("Later"))

        key(Key.DirectionCenter) // the banner goes, and its button with it
        platformRefocus()
        settle(1_000)
        assertFalse("the banner is still up", rule.onAllNodes(hasText("Later")).fetchSemanticsNodes().isNotEmpty())
        assertTrue("nothing has focus", anyFocused())
        assertFalse("focus was left on the menu", onMenu())
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle(300)
    }

    @Test
    fun backFromAScreenGoesUpToTheMenu() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)
        openSection("Settings")
        key(Key.DirectionDown)
        assertFalse(onMenu())

        back()
        assertTrue("Back did not go up to the section's menu item", isFocused("Settings"))
        settle(1_000)
        assertTrue("the menu lost focus on its own after Back", isFocused("Settings"))
    }

    @Test
    fun backFromTheBannerGoesUpToTheMenu() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)
        showBanner()
        openSection("Settings")
        key(Key.DirectionDown)
        repeat(20) { if (!onBanner()) key(Key.DirectionUp) }
        assertTrue(onBanner())

        back()
        settle(1_000)
        assertTrue("Back from the banner did not go up to the menu", isFocused("Settings"))
    }
}
