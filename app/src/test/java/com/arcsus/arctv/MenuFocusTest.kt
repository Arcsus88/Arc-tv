package com.arcsus.arctv

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arcsus.arctv.ui.ArcTvViewModelFactory
import com.arcsus.arctv.ui.BrowseTab
import com.arcsus.arctv.ui.BrowseViewModel
import com.arcsus.arctv.ui.HomeScreen
import com.arcsus.arctv.ui.UpdateViewModel
import com.arcsus.arctv.ui.theme.ArcTvTheme
import com.arcsus.arctv.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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

    private val menuTitles = listOf("Search", "Home", "Shows", "Movies", "Live", "Guide", "More", "Settings")

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

    private val moreTitles = listOf("Disney", "Netflix", "Prime", "Apple", "Hulu")

    /** The "more" list is a popup: a second root, which takes the remote while it's open. */
    private fun roots() = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size

    private fun popupKey(k: Key) {
        rule.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(k) }
        settle(120)
    }

    /** Back as the TV sends it: to the popup's window, which closes on it. */
    private fun popupBack() {
        val view = (rule.onAllNodes(isRoot()).onLast().fetchSemanticsNode().root as ViewRootForTest).view.rootView
        rule.runOnUiThread {
            view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
            view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        }
        settle(300)
    }

    private fun browseTab() = ViewModelProvider(rule.activity, factory)[BrowseViewModel::class.java].state.value.tab

    /**
     * The menu was too long: every collection had a label. Now it carries the
     * everyday sections and a "more" button for the collections. One picked
     * there opens, joins the menu while you're in it, and is where Back from
     * the screen goes up to.
     */
    @Test
    fun moreHoldsTheCollections() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)

        // Along the whole menu: the collections aren't on it.
        repeat(12) { if (!onMenu()) key(Key.DirectionUp) }
        repeat(12) { if (!isFocused("Search")) key(Key.DirectionLeft) }
        val walked = mutableListOf<String>()
        repeat(12) {
            (menuTitles + moreTitles).firstOrNull { isFocused(it) }?.let { if (walked.lastOrNull() != it) walked += it }
            if (!isFocused("More")) key(Key.DirectionRight)
        }
        assertEquals(menuTitles.dropLast(1), walked)

        // Resting on "more" opens nothing; settings is past it.
        settle(700)
        assertFalse("resting on more opened a section", shows("Check for updates"))
        key(Key.DirectionRight)
        assertTrue(isFocused("Settings"))
        key(Key.DirectionLeft)

        // OK opens the list on its first item, and the remote stays in it.
        assertTrue(isFocused("More"))
        key(Key.DirectionCenter)
        assertEquals("the list did not open", 2, roots())
        assertTrue("the list did not take focus", isFocused("Disney"))
        repeat(6) { popupKey(Key.DirectionDown) }
        assertTrue("DOWN left the list", isFocused("Hulu"))
        repeat(3) { popupKey(Key.DirectionUp) }
        assertTrue(isFocused("Netflix"))

        // OK opens Netflix, closes the list and leaves focus on Netflix's new label.
        popupKey(Key.DirectionCenter)
        settle(600)
        assertEquals("the list stayed open", 1, roots())
        assertEquals(BrowseTab.NETFLIX, browseTab())
        assertTrue("Netflix's label did not take focus", isFocused("Netflix"))
        settle(1_000)
        assertTrue("Netflix's label lost focus on its own", isFocused("Netflix"))

        // Back from the screen goes up to it.
        key(Key.DirectionDown)
        assertFalse(isFocused("Netflix"))
        back()
        assertTrue("Back did not go up to Netflix's label", isFocused("Netflix"))

        // The list reopens on the section you're in; Back closes it and leaves focus on "more".
        key(Key.DirectionRight)
        assertTrue(isFocused("More"))
        key(Key.DirectionCenter)
        assertEquals(2, roots())
        // ("more" keeps focus in the menu's window, under the list.)
        assertTrue("the list did not open on Netflix", isFocused("Netflix"))
        popupBack()
        assertEquals("Back did not close the list", 1, roots())
        assertTrue("focus did not return to more", isFocused("More"))
        assertEquals(BrowseTab.NETFLIX, browseTab())

        // Leaving for an everyday section takes Netflix off the menu again.
        key(Key.DirectionLeft) // Netflix
        key(Key.DirectionLeft) // Guide
        key(Key.DirectionCenter)
        settle(600)
        assertTrue(isFocused("Guide"))
        assertFalse("Netflix stayed on the menu", shows("Netflix"))
    }

    private fun shows(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

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
