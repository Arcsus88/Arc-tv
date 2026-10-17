package com.arcsus.arctv

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ExperimentalTestApi
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
import com.arcsus.arctv.data.CatalogItem
import com.arcsus.arctv.ui.ArcTvViewModelFactory
import com.arcsus.arctv.ui.BrowseViewModel
import com.arcsus.arctv.ui.HomeScreen
import com.arcsus.arctv.ui.theme.ArcTvTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression: the Netflix-style redesign (1.22.0) left Movies and Shows with
 * no search box of their own -- only the small magnifier in the menu. Each
 * has one again beside its genre and sort pills, the remote can reach it,
 * and its results take the grid's place until Back.
 *
 * Runs offline: with no debrid account the catalogue loads fail, and the
 * results are seeded straight into the view model.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-notnight-xhdpi")
class SectionSearchTest {

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

    private fun hasNode(text: String): Boolean =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** A poster's title: its artwork's description, or the title drawn in its place. */
    private fun hasPoster(title: String): Boolean =
        rule.onAllNodes(hasContentDescription(title) or hasText(title)).fetchSemanticsNodes().isNotEmpty()

    /** Menu items are labels, or icons (Search, Settings) named by their description. */
    private fun isFocused(text: String): Boolean =
        rule.onAllNodes(isFocused() and (hasText(text) or hasContentDescription(text)))
            .fetchSemanticsNodes().isNotEmpty()

    /** A text field: until OK unlocks it, it is read-only and has no set-text action. */
    private fun searchBoxFocused(): Boolean =
        rule.onAllNodes(isFocused() and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
            .fetchSemanticsNodes().isNotEmpty()

    private val menuTitles = listOf(
        "Search", "Home", "Shows", "Movies", "Disney", "Netflix", "Prime", "Live TV", "Guide", "Settings",
    )

    /** UP out of the content, LEFT to the start of the menu, RIGHT to [title], OK. */
    private fun openSection(title: String) {
        repeat(12) { if (menuTitles.none { isFocused(it) }) key(Key.DirectionUp) }
        repeat(12) { if (!isFocused("Search")) key(Key.DirectionLeft) }
        repeat(12) { if (!isFocused(title)) key(Key.DirectionRight) }
        assertTrue("could not reach '$title' on the menu", isFocused(title))
        key(Key.DirectionCenter)
        settle(600)
    }

    /** DOWN out of the menu into the page, then RIGHT along its bar to the search box. */
    private fun toSearchBox() {
        key(Key.DirectionDown)
        repeat(10) { if (!searchBoxFocused()) key(Key.DirectionRight) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun browseState(): MutableStateFlow<BrowseViewModel.UiState> {
        val vm = ViewModelProvider(rule.activity, factory)[BrowseViewModel::class.java]
        return BrowseViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            .get(vm) as MutableStateFlow<BrowseViewModel.UiState>
    }

    @Test
    fun moviesAndShowsEachHaveASearchBox() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)

        openSection("Movies")
        assertTrue("Movies has no search box", hasNode("Search movies…"))
        toSearchBox()
        assertTrue("the remote could not reach Movies' search box", searchBoxFocused())

        openSection("Shows")
        assertTrue("Shows has no search box", hasNode("Search shows…"))
        toSearchBox()
        assertTrue("the remote could not reach Shows' search box", searchBoxFocused())
    }

    @Test
    fun resultsTakeTheGridsPlaceUntilBack() {
        rule.setContent { ArcTvTheme { HomeScreen(factory) } }
        settle(300)
        openSection("Movies")
        toSearchBox()

        val state = browseState()
        val film = CatalogItem(693134, "movie", "Dune: Part Two", "2024", "", "", "")
        val show = CatalogItem(1396, "tv", "Breaking Bad", "2008", "", "", "")
        rule.runOnUiThread { state.value = state.value.copy(query = "dune", searchResults = listOf(film, show)) }
        settle(400)
        assertTrue("the film it found is not shown", hasPoster("Dune: Part Two"))
        assertFalse("Movies showed a series", hasPoster("Breaking Bad"))

        key(Key.DirectionDown) // onto the results
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle(400)
        assertNull("Back left the results up", state.value.searchResults)
        assertTrue("Back did not return to the search box", searchBoxFocused())
        assertFalse(hasPoster("Dune: Part Two"))
    }
}
