package com.arcsus.arctv

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.arcsus.arctv.data.CatalogItem
import com.arcsus.arctv.data.CatalogRow
import com.arcsus.arctv.data.CollectionTitle
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.data.SavedPlaylist
import com.arcsus.arctv.data.SkyEpg
import com.arcsus.arctv.data.WatchEntry
import com.arcsus.arctv.ui.ArcTvViewModelFactory
import com.arcsus.arctv.ui.BrowseViewModel
import com.arcsus.arctv.ui.GuideGroup
import com.arcsus.arctv.ui.GuideViewModel
import com.arcsus.arctv.ui.HomeScreen
import com.arcsus.arctv.ui.theme.ArcBackground
import com.arcsus.arctv.ui.theme.ArcTvTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Design captures of the Netflix-style redesign, with no debrid account:
 * the view models are seeded with real catalogue titles (TMDB artwork) and
 * the guide with Sky's real line-up and today's listings, then the app is
 * driven with the D-pad and photographed. Not a pass/fail test; it writes
 * PNGs to .design-loop/redesign/ for a critique to look at.
 *
 * Opt in with ARC_REDESIGN_SHOTS=1 (it needs network for artwork and Sky).
 */
@OptIn(ExperimentalTestApi::class, com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// API 28: Compose draws layers without RenderNode there, and Robolectric's
// RenderNode emulation leaves TV Material surfaces created after the first
// frame (cards, chips, buttons) drawn without their content.
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-television-notnight-xhdpi")
class RedesignScreens {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir: File by lazy {
        File(System.getenv("DESIGN_OUT") ?: "../.design-loop", "redesign").apply { mkdirs() }
    }
    private lateinit var app: ArcTvApp
    private lateinit var factory: ArcTvViewModelFactory

    @Before
    fun setUp() {
        assumeTrue("ARC_REDESIGN_SHOTS not set; skipping redesign captures", System.getenv("ARC_REDESIGN_SHOTS") == "1")
        app = rule.activity.application as ArcTvApp
        factory = ArcTvViewModelFactory(app)
        runBlocking {
            app.settingsStore.markLiveSetupDone()
            app.settingsStore.savePlaylists(listOf(PLAYLIST))
            app.settingsStore.saveGuideGroups(listOf("UK| SPORTS", "UK| ENTERTAINMENT"))
            app.settingsStore.saveActiveGuideGroup("UK| SPORTS")
        }
    }

    // ---- Seed data ----

    private fun tmdb(path: String, size: String = "w500") = "https://image.tmdb.org/t/p/$size/$path.jpg"

    private val dune = CatalogItem(
        693134, "movie", "Dune: Part Two", "2024", tmdb("6izwz7rsy95ARzTR3poZ8H6c5pp"), tmdb("xOMo8BRK7PfcJv9JCnx7s5hj0PX", "w780"),
        "Paul Atreides unites with Chani and the Fremen while on a path of revenge against the conspirators who destroyed his family.",
    )
    private val insideOut2 = CatalogItem(
        1022789, "movie", "Inside Out 2", "2024", tmdb("vpnVM9B6NMmQpWeZvzLvDESb2QY"), tmdb("stKGOm8UyhuLPR9sZLjs5AkmncA", "w780"),
        "Teenager Riley's mind headquarters is undergoing a sudden demolition to make room for something entirely unexpected: new Emotions.",
    )
    private val breakingBad = CatalogItem(
        1396, "tv", "Breaking Bad", "2008", tmdb("anFx9aTOOYqgS3v7x3R84Kz67ly"), tmdb("tsRy63Mu5cu8etL1X7ZLyf7UP1M", "w780"),
        "A chemistry teacher diagnosed with terminal lung cancer teams up with a former student to secure his family's future.",
    )
    private val spiderVerse = CatalogItem(
        569094, "movie", "Spider-Man: Across the Spider-Verse", "2023", tmdb("8Vt6mWEReuy4Of61Lnj5Xj704m8"),
        tmdb("4HodYYKEIsGOdinkGi2Ucz6X9i0", "w780"),
        "Miles Morales catapults across the multiverse, where he encounters a team of Spider-People charged with protecting its very existence.",
    )
    private val avengers = CatalogItem(
        24428, "movie", "The Avengers", "2012", tmdb("RYMX2wcKCBAr24UyPD7xwmjaTn"), tmdb("9BBTo63ANSmhC4e6r62OJFuK2GL", "w780"),
        "When an unexpected enemy emerges and threatens global safety and security, Nick Fury finds himself in need of a team to pull the world back from the brink of disaster.",
    )
    private val shawshank = CatalogItem(
        278, "movie", "The Shawshank Redemption", "1994", tmdb("9cqNxx0GxF0bflZmeSMuL5tnGzr"), tmdb("zfbjgQE1uSd9wiPTX4VzsLi0rGG", "w780"),
        "Imprisoned in the 1940s for the double murder of his wife and her lover, upstanding banker Andy Dufresne begins a new life at the Shawshank prison.",
    )
    private fun poster(id: Int, type: String, title: String, year: String, path: String) =
        CatalogItem(id, type, title, year, tmdb(path), "", "")

    private val films = listOf(
        dune, insideOut2,
        poster(1084244, "movie", "Toy Story 5", "2026", "sfQtVlIHljToOwYjhe21KPGzZWK"),
        poster(1084242, "movie", "Zootopia 2", "2025", "oJ7g2CifqpStmoYQyaLQgEU32qO"),
        poster(1184918, "movie", "The Wild Robot", "2024", "wTnV3PCVW5O92JMrFvvrRcV39RU"),
        poster(558449, "movie", "Gladiator II", "2024", "2cxhvwyEwRlysAmRH4iodkvo0z5"),
        poster(129, "movie", "Spirited Away", "2001", "39wmItIWsg5sZMyRUHLkWBcuVCM"),
        poster(698687, "movie", "Transformers One", "2024", "iRCgqpdVE4wyLQvGYU3ZP7pAtUc"),
        poster(354912, "movie", "Coco", "2017", "6Ryitt95xrO8KXuqRGm1fUuNwqF"),
        poster(4935, "movie", "Howl's Moving Castle", "2004", "13kOl2v0nD2OLbVSHnHk8GUFEhO"),
    )
    private val shows = listOf(
        breakingBad,
        poster(2316, "tv", "The Office", "2005", "7DJKHzAi83BmQrWLrYYOqcoKfhR"),
        poster(60625, "tv", "Rick and Morty", "2013", "owhkU6KRqdXoUQpjV8uyZGPtX58"),
        poster(79744, "tv", "The Rookie", "2018", "70kTz0OmjjZe7zHvIDrq2iKW7PJ"),
        poster(1408, "tv", "House", "2004", "3Cz7ySOQJmqiuTdrc6CY0r65yDI"),
        poster(5920, "tv", "The Mentalist", "2008", "acYXu4KaDj1NIkMgObnhe4C4a0T"),
        poster(1911, "tv", "Bones", "2005", "eyTu5c8LniVciRZIOSHTvvkkgJa"),
    )
    private val disney = listOf(
        Triple(1084244, "Toy Story 5", "sfQtVlIHljToOwYjhe21KPGzZWK"),
        Triple(1084242, "Zootopia 2", "oJ7g2CifqpStmoYQyaLQgEU32qO"),
        Triple(1022789, "Inside Out 2", "vpnVM9B6NMmQpWeZvzLvDESb2QY"),
        Triple(1108427, "Moana", "gaet1xQ2nxrG0V1Ep9T20ZMNEIC"),
        Triple(354912, "Coco", "6Ryitt95xrO8KXuqRGm1fUuNwqF"),
        Triple(1327819, "Hoppers", "xjtWQ2CL1mpmMNwuU5HeS4Iuwuu"),
        Triple(150540, "Inside Out", "2H1TmgdfNtsKlU9jKdeNyYL5y8T"),
        Triple(585, "Monsters, Inc.", "wFSpyMsp7H0ttERbxY7Trlv8xry"),
        Triple(105864, "The Good Dinosaur", "8RSkxOO80btfKjyiC5ZiTaCHIT8"),
        Triple(12155, "Alice in Wonderland", "o0kre9wRCZz3jjSjaru7QU0UtFz"),
        Triple(863, "Toy Story 2", "4rbcp3ng8n1MKHjpeqW0L7Fnpzz"),
        Triple(508947, "Turning Red", "qsdjk9oAKSQMWs0Vt5Pyfh6O4GZ"),
    ).mapIndexed { i, (id, title, path) ->
        CollectionTitle(id, "movie", title, "", tmdb(path), seeds = if (i == 9) 0 else 40 - i, scanned = true, best = "")
    }
    private val netflix = (films.drop(4) + shows.drop(1)).shuffled(kotlin.random.Random(7)).map {
        CollectionTitle(it.id, it.type, it.title, it.year, it.poster)
    }

    /** IPTV-style channel names, as a UK panel spells them. */
    private val sportsChannels = listOf(
        "##### UK SPORTS #####", "UK| Sky Sports Main Event HD", "UK| Sky Sports Premier League FHD",
        "UK| SKY SPORTS FOOTBALL HD", "UK| Sky Sports F1 HD", "UK| Sky Sports Cricket", "UK| TNT Sports 1 HD",
        "UK| TNT SPORTS 2", "UK| Sky Sports News", "UK| Sky Sports Golf", "UK| Premier Sports 1",
    ).mapIndexed { i, name -> LiveChannel("c$i", name, "", "UK| SPORTS", "http://panel.example/live/u/p/${1000 + i}.ts") }

    // ---- Harness ----

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
                Box(Modifier.fillMaxSize().background(ArcBackground)) { content() }
            }
        }
    }

    private fun settle(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            runCatching { rule.mainClock.advanceTimeByFrame() }
            rule.waitForIdle()
            Thread.sleep(100)
        }
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        // The whole window, as the screen shows it: capturing the root node
        // alone left the content of TV Material's offscreen-composited
        // surfaces (cards, buttons) stale or blank under Robolectric.
        captureScreenRoboImage(File(outDir, "$name.png").path)
        println("redesign: captured $name")
    }

    /** All windows, for the details page (a dialog). */
    private fun snapScreen(name: String) {
        rule.waitForIdle()
        captureScreenRoboImage(File(outDir, "$name.png").path)
        println("redesign: captured $name")
    }

    private fun key(k: Key, times: Int = 1) {
        repeat(times) {
            rule.onRoot().performKeyInput { pressKey(k) }
            settle(150)
        }
    }

    /** A focused node carrying [text] itself or in a child (TV surfaces don't merge). */
    private fun focused(text: String): Boolean =
        rule.onAllNodes(isFocused() and (hasText(text) or hasAnyDescendant(hasText(text))))
            .fetchSemanticsNodes().isNotEmpty()

    /** UP into the menu, then along it to [label], then OK. */
    private fun openSection(label: String) {
        repeat(12) { if (!focusOnMenu()) key(Key.DirectionUp) }
        repeat(12) { if (!focused(label)) key(Key.DirectionLeft) }
        repeat(12) { if (!focused(label)) key(Key.DirectionRight) }
        println("redesign: menu '$label' reached=${focused(label)}")
        key(Key.DirectionCenter)
        settle(1_500)
    }

    private val menu = listOf("Home", "Shows", "Movies", "Disney", "Netflix", "Live TV", "Guide")
    private fun focusOnMenu() = menu.any { focused(it) }

    @Suppress("UNCHECKED_CAST")
    private fun <S> stateOf(vm: ViewModel, field: String = "_state"): MutableStateFlow<S> =
        vm.javaClass.getDeclaredField(field).apply { isAccessible = true }.get(vm) as MutableStateFlow<S>

    @Test
    fun captureRedesign() {
        val provider = ViewModelProvider(rule.activity, factory)
        val browse = provider[BrowseViewModel::class.java]
        val guide = provider[GuideViewModel::class.java]
        settle(4_000) // let the (account-less) loads fail before seeding over them

        val browseState = stateOf<BrowseViewModel.UiState>(browse)
        browseState.value = browseState.value.copy(
            loadingHome = false,
            error = null,
            rows = listOf(
                CatalogRow("Trending Movies", listOf(dune, spiderVerse, insideOut2, avengers, shawshank) + films.drop(2)),
                CatalogRow("Popular TV", shows),
                CatalogRow("Top Rated Movies", films.reversed()),
            ),
            continueWatching = listOf(
                WatchEntry(kind = "tv", item = breakingBad, season = 2, episode = 5, nextSeason = 2, nextEpisode = 6),
                WatchEntry(kind = "movie", item = films[4]),
            ),
            collections = mapOf("disney" to disney, "netflix" to netflix),
            collectionErrors = emptyMap(),
        )

        // The guide: the playlist's sports group, dressed from Sky's real EPG.
        val sky = runBlocking { runCatching { SkyEpg.guide(sportsChannels) }.getOrDefault(emptyMap()) }
        println("redesign: Sky matched ${sky.size} of ${sportsChannels.size} channels")
        val guideState = stateOf<GuideViewModel.UiState>(guide)
        guideState.value = guideState.value.copy(
            playlist = PLAYLIST,
            epgCapable = true,
            channels = sportsChannels,
            groups = listOf(GuideGroup("UK| SPORTS", sportsChannels.size), GuideGroup("UK| ENTERTAINMENT", 0)),
            selectedGroups = listOf("UK| SPORTS", "UK| ENTERTAINMENT"),
            activeGroup = "UK| SPORTS",
            loading = false,
            error = null,
            sky = sky,
            nowMs = System.currentTimeMillis(),
        )

        rule.setContent { Frame { HomeScreen(factory) } }
        settle(20_000) // artwork
        snap("01-home-billboard")

        key(Key.DirectionDown)
        settle(2_500)
        snap("02-home-banner-disney")
        key(Key.DirectionRight)
        settle(2_000)
        snap("03-home-banner-sky")

        key(Key.DirectionDown) // Continue watching
        key(Key.DirectionDown) // Top 10
        key(Key.DirectionRight)
        settle(12_000)
        snap("04-home-top10")

        key(Key.DirectionDown, 2) // a catalogue row
        key(Key.DirectionRight)
        settle(12_000)
        snap("05-home-row")

        key(Key.DirectionCenter)
        settle(3_000)
        snapScreen("06-details")
        rule.activity.runOnUiThread { browse.closeDetails() }
        settle(800)

        openSection("Disney")
        settle(5_000)
        snap("07-disney")

        openSection("Netflix")
        settle(5_000)
        snap("08-netflix")

        openSection("Guide")
        // The guide's own loading runs again on mount; re-seed what it overwrote.
        guideState.value = guideState.value.copy(
            playlist = PLAYLIST, epgCapable = true, channels = sportsChannels, loading = false, error = null, sky = sky,
            groups = listOf(GuideGroup("UK| SPORTS", sportsChannels.size), GuideGroup("UK| ENTERTAINMENT", 0)),
        )
        settle(6_000)
        snap("09-guide")
        key(Key.DirectionDown, 2)
        settle(1_500)
        snap("10-guide-focused")

        openSection("Movies")
        browseState.value = browseState.value.copy(discoverItems = films + shows.map { it.copy(type = "movie") }, discoverLoading = false, error = null)
        settle(5_000)
        snap("11-movies")

        openSection("Home")
        settle(3_000)
        snap("12-home-menu")
    }

    private companion object {
        val PLAYLIST = SavedPlaylist(name = "IPTV", url = "http://panel.example", kind = "xtream", username = "u", password = "p")
    }
}
