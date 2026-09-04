package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.BrowseRepository
import com.arcsus.arctv.data.CatalogItem
import com.arcsus.arctv.data.CatalogRow
import com.arcsus.arctv.data.Episode
import com.arcsus.arctv.data.Genre
import com.arcsus.arctv.data.Genres
import com.arcsus.arctv.data.ResolvedStream
import com.arcsus.arctv.data.Season
import com.arcsus.arctv.data.SettingsStore
import com.arcsus.arctv.data.WatchEntry
import com.arcsus.arctv.data.Source
import com.arcsus.arctv.data.TitleDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrowseTab { HOME, MOVIES, TV }

/** The episode that plays after the current one (may be in the next season). */
data class NextEp(val season: Int, val episode: Int)

enum class SortMode(val key: String, val label: String) {
    POPULAR("popular", "Popular"),
    TOP_RATED("top_rated", "Top rated"),
    NEWEST("newest", "Newest"),
}

class BrowseViewModel(
    private val repository: BrowseRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    /** The pop-up selection flow: seasons → episodes → sources. */
    sealed interface Sheet {
        data object Hidden : Sheet
        data class Loading(val label: String) : Sheet
        data class Seasons(val item: CatalogItem, val seasons: List<Season>) : Sheet
        data class Episodes(val item: CatalogItem, val season: Int, val episodes: List<Episode>) : Sheet
        data class Sources(
            val item: CatalogItem,
            val season: Int?,
            val episode: Int?,
            val sources: List<Source>,
            val playing: String? = null, // magnet currently being played
            val note: String? = null,
            val next: NextEp? = null, // what plays next (may roll into next season)
            val loadingMore: Boolean = false, // still streaming in more sources
        ) : Sheet
        data class Error(val message: String) : Sheet
    }

    data class UiState(
        val query: String = "",
        val loadingHome: Boolean = false,
        val rows: List<CatalogRow> = emptyList(),
        val searching: Boolean = false,
        val searchResults: List<CatalogItem>? = null,
        val error: String? = null,
        // Full-catalogue browsing
        val tab: BrowseTab = BrowseTab.HOME,
        val genres: Genres = Genres(),
        val genreId: Int? = null,
        val sortMode: SortMode = SortMode.POPULAR,
        val discoverItems: List<CatalogItem> = emptyList(),
        val discoverPage: Int = 0,
        val discoverTotalPages: Int = 1,
        val discoverLoading: Boolean = false,
        /** Titles the user hearted — shown as the first Browse carousel. */
        val favorites: List<CatalogItem> = emptyList(),
        /** What was played most recently, for the Continue Watching row. */
        val continueWatching: List<WatchEntry> = emptyList(),
    ) {
        fun isFavorite(item: CatalogItem): Boolean =
            favorites.any { it.id == item.id && it.type == item.type }

        val currentGenres: List<Genre>
            get() = if (tab == BrowseTab.TV) genres.tv else genres.movie
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _sheet = MutableStateFlow<Sheet>(Sheet.Hidden)
    val sheet: StateFlow<Sheet> = _sheet

    /** The TMDB-style details page, or null when closed. */
    data class DetailUi(
        val item: CatalogItem,
        val details: TitleDetails? = null,
        val loading: Boolean = true,
    )

    private val _detail = MutableStateFlow<DetailUi?>(null)
    val detail: StateFlow<DetailUi?> = _detail
    private val detailsCache = mutableMapOf<String, TitleDetails>()

    /** Backdrop URLs for the trending hero, keyed "type:id" ("" = pending/none). */
    private val _heroArt = MutableStateFlow<Map<String, String>>(emptyMap())
    val heroArt: StateFlow<Map<String, String>> = _heroArt

    /** Fetch the wide backdrop for a hero item once (shares the details cache). */
    fun loadHeroArt(item: CatalogItem) {
        val key = "${item.type}:${item.id}"
        if (_heroArt.value.containsKey(key)) return
        _heroArt.value = _heroArt.value + (key to "")
        viewModelScope.launch {
            val d = detailsCache[key] ?: runCatching { repository.details(item) }.getOrNull()
                ?.also { detailsCache[key] = it }
            val backdrop = d?.backdrop.orEmpty()
            if (backdrop.isNotBlank()) _heroArt.value = _heroArt.value + (key to backdrop)
        }
    }

    private val _playRequest = MutableStateFlow<ResolvedStream?>(null)
    val playRequest: StateFlow<ResolvedStream?> = _playRequest

    // Session caches so reopening a title (or returning from a bad link) never
    // re-runs the slow torrent search. Cleared when the ViewModel is recreated.
    /** The discover page in flight, cancelled when the group or section changes. */
    private var discoverJob: Job? = null

    private val seasonsCache = mutableMapOf<Int, List<Season>>()
    private val episodesCache = mutableMapOf<String, List<Episode>>()
    private val sourcesCache = mutableMapOf<String, List<Source>>()

    init {
        loadHome()
        loadGenres()
        viewModelScope.launch {
            settingsStore.favorites.collect { favs ->
                _state.update { it.copy(favorites = favs) }
            }
        }
        viewModelScope.launch {
            settingsStore.continueWatching.collect { entries ->
                _state.update { it.copy(continueWatching = entries) }
            }
        }
    }

    /**
     * Continue Watching: a film goes straight to its sources; a series goes
     * to the sources of the episode after the one played (or the same one
     * again when the next isn't known).
     */
    fun resumeWatch(entry: WatchEntry) {
        val item = entry.item ?: return
        val season = entry.nextSeason ?: entry.season
        val episode = entry.nextEpisode ?: entry.episode
        if (item.isTv && season != null && episode != null) selectEpisode(item, season, episode) else openTitle(item)
    }

    fun removeWatch(key: String) {
        viewModelScope.launch { settingsStore.removeWatch(key) }
    }

    /** Remember what just started playing, with the next episode worked out for series. */
    private fun recordWatch(item: CatalogItem, season: Int?, episode: Int?, next: NextEp?) {
        viewModelScope.launch {
            val n = next ?: runCatching { resolveNext(item, season, episode) }.getOrNull()
            settingsStore.recordWatch(
                WatchEntry(
                    kind = item.type,
                    item = item,
                    season = season,
                    episode = episode,
                    nextSeason = n?.season,
                    nextEpisode = n?.episode,
                ),
            )
        }
    }

    fun toggleFavorite(item: CatalogItem) {
        viewModelScope.launch { settingsStore.toggleFavorite(item) }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(genres = repository.genres()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Non-fatal; genre chips just won't show.
            }
        }
    }

    fun setTab(tab: BrowseTab) {
        if (tab == _state.value.tab) return
        discoverJob?.cancel()
        _state.update { it.copy(tab = tab, genreId = null, discoverLoading = false) }
        if (tab != BrowseTab.HOME) reloadDiscover()
    }

    fun setGenre(genreId: Int?) {
        if (genreId == _state.value.genreId) return
        _state.update { it.copy(genreId = genreId) }
        reloadDiscover()
    }

    fun setSort(sort: SortMode) {
        if (sort == _state.value.sortMode) return
        _state.update { it.copy(sortMode = sort) }
        reloadDiscover()
    }

    private fun reloadDiscover() {
        // Drop the request already in flight: its page belongs to the group we
        // just left. Leaving discoverLoading set made loadMoreDiscover bail out
        // below, so changing group mid-load stranded the grid on "Loading..."
        // until you changed it again.
        discoverJob?.cancel()
        _state.update {
            it.copy(
                discoverItems = emptyList(),
                discoverPage = 0,
                discoverTotalPages = 1,
                discoverLoading = false,
            )
        }
        loadMoreDiscover()
    }

    fun loadMoreDiscover() {
        val s = _state.value
        if (s.tab == BrowseTab.HOME || s.discoverLoading || s.discoverPage >= s.discoverTotalPages) return
        val type = if (s.tab == BrowseTab.TV) "tv" else "movie"
        val nextPage = s.discoverPage + 1
        // What this page is for. A reply that lands after the user has moved on
        // must not be pasted under the new group's chips.
        val forTab = s.tab
        val forGenre = s.genreId
        val forSort = s.sortMode
        _state.update { it.copy(discoverLoading = true, error = null) }
        discoverJob = viewModelScope.launch {
            try {
                val result = repository.discover(type, forGenre, forSort.key, nextPage)
                _state.update {
                    if (it.tab != forTab || it.genreId != forGenre || it.sortMode != forSort) it
                    else it.copy(
                        discoverLoading = false,
                        // Dedupe across pages so grid keys stay unique.
                        discoverItems = (it.discoverItems + result.items).distinctBy { i -> i.type + i.id },
                        discoverPage = result.page,
                        discoverTotalPages = result.totalPages,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    if (it.tab != forTab || it.genreId != forGenre || it.sortMode != forSort) it
                    else it.copy(discoverLoading = false, error = e.message ?: "Couldn't load catalogue.")
                }
            }
        }
    }

    fun loadHome() {
        if (_state.value.loadingHome) return
        _state.update { it.copy(loadingHome = true, error = null) }
        viewModelScope.launch {
            try {
                val rows = repository.home()
                _state.update { it.copy(loadingHome = false, rows = rows) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingHome = false, error = e.message ?: "Couldn't load catalogue.") }
            }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty() || _state.value.searching) return
        _state.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            try {
                val results = repository.search(q)
                _state.update { it.copy(searching = false, searchResults = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, error = e.message ?: "Search failed.") }
            }
        }
    }

    fun clearSearch() = _state.update { it.copy(query = "", searchResults = null, error = null) }

    /** Show the details page for a chosen poster, loading extended metadata. */
    fun openDetails(item: CatalogItem) {
        val key = "${item.type}:${item.id}"
        val cached = detailsCache[key]
        _detail.value = DetailUi(item, cached, loading = cached == null)
        if (cached != null) return
        viewModelScope.launch {
            try {
                val d = repository.details(item)
                detailsCache[key] = d
                if (_detail.value?.item?.id == item.id) _detail.value = DetailUi(item, d, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_detail.value?.item?.id == item.id) _detail.value = DetailUi(item, null, loading = false)
            }
        }
    }

    fun closeDetails() {
        _detail.value = null
    }

    /** Play from the details page: run the existing source search. */
    fun playFromDetails() {
        _detail.value?.item?.let { openTitle(it) }
    }

    /** Entry point when a poster is chosen: movies go straight to sources, TV to seasons. */
    fun openTitle(item: CatalogItem) {
        if (item.isTv) loadSeasons(item) else loadSources(item, null, null)
    }

    private fun loadSeasons(item: CatalogItem) {
        seasonsCache[item.id]?.let {
            _sheet.value = Sheet.Seasons(item, it)
            return
        }
        _sheet.value = Sheet.Loading("Loading seasons…")
        launchSheet {
            val seasons = repository.seasons(item)
            _sheet.value = if (seasons.isEmpty()) {
                Sheet.Error("No season information for this show.")
            } else {
                seasonsCache[item.id] = seasons
                Sheet.Seasons(item, seasons)
            }
        }
    }

    fun selectSeason(item: CatalogItem, season: Int) {
        episodesCache["${item.id}:$season"]?.let {
            _sheet.value = Sheet.Episodes(item, season, it)
            return
        }
        _sheet.value = Sheet.Loading("Loading episodes…")
        launchSheet {
            val episodes = repository.episodes(item, season)
            _sheet.value = if (episodes.isEmpty()) {
                Sheet.Error("No episodes found for that season.")
            } else {
                episodesCache["${item.id}:$season"] = episodes
                Sheet.Episodes(item, season, episodes)
            }
        }
    }

    fun selectEpisode(item: CatalogItem, season: Int, episode: Int) = loadSources(item, season, episode)

    // Bumped each time a source search starts, so a stale stream can't overwrite
    // the sheet after the user has navigated elsewhere.
    private var sourcesEpoch = 0

    private fun loadSources(item: CatalogItem, season: Int?, episode: Int?) {
        val key = "${item.id}:${season ?: ""}:${episode ?: ""}"
        sourcesCache[key]?.let {
            _sheet.value = Sheet.Sources(item, season, episode, it)
            prepareNext(item, season, episode)
            return
        }
        val epoch = ++sourcesEpoch
        // Show the sources sheet straight away (empty + searching) so results
        // appear in place as soon as the first one is found.
        _sheet.value = Sheet.Sources(item, season, episode, emptyList(), loadingMore = true)
        viewModelScope.launch {
            var latest: List<Source> = emptyList()
            try {
                repository.sourcesStream(item, season, episode).collect { partial ->
                    if (epoch != sourcesEpoch) return@collect
                    latest = partial
                    _sheet.value = Sheet.Sources(item, season, episode, partial, loadingMore = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (epoch == sourcesEpoch && latest.isEmpty()) {
                    _sheet.value = Sheet.Error(e.message ?: "Couldn't find sources.")
                    return@launch
                }
            }
            if (epoch != sourcesEpoch) return@launch
            if (latest.isEmpty()) {
                _sheet.value = Sheet.Error("No sources found for this title.")
            } else {
                sourcesCache[key] = latest
                _sheet.value = Sheet.Sources(item, season, episode, latest)
                prepareNext(item, season, episode)
            }
        }
    }

    /**
     * What plays after this episode: the next episode in the season, or — when
     * this is the season finale — episode 1 of the next season. Loads episode/
     * season lists on demand (cached).
     */
    private suspend fun resolveNext(item: CatalogItem, season: Int?, episode: Int?): NextEp? {
        if (season == null || episode == null) return null
        val eps = episodesFor(item, season) ?: return null
        val idx = eps.indexOfFirst { it.episode == episode }
        if (idx >= 0 && idx + 1 < eps.size) return NextEp(season, eps[idx + 1].episode)
        // Season finale — roll over to the first episode of the next season.
        val seasons = seasonsFor(item) ?: return null
        val nextSeason = seasons.filter { it.number > season }.minByOrNull { it.number } ?: return null
        val first = episodesFor(item, nextSeason.number)?.firstOrNull() ?: return null
        return NextEp(nextSeason.number, first.episode)
    }

    private suspend fun episodesFor(item: CatalogItem, season: Int): List<Episode>? {
        episodesCache["${item.id}:$season"]?.let { return it }
        val eps = runCatching { repository.episodes(item, season) }.getOrNull()
        if (!eps.isNullOrEmpty()) episodesCache["${item.id}:$season"] = eps
        return eps
    }

    private suspend fun seasonsFor(item: CatalogItem): List<Season>? {
        seasonsCache[item.id]?.let { return it }
        val seasons = runCatching { repository.seasons(item) }.getOrNull()
        if (!seasons.isNullOrEmpty()) seasonsCache[item.id] = seasons
        return seasons
    }

    /** Resolve the next episode, label the current sheet, and warm its sources. */
    private fun prepareNext(item: CatalogItem, season: Int?, episode: Int?) {
        viewModelScope.launch {
            val n = resolveNext(item, season, episode) ?: return@launch
            (_sheet.value as? Sheet.Sources)?.let { cur ->
                if (cur.item.id == item.id && cur.season == season && cur.episode == episode) {
                    _sheet.value = cur.copy(next = n)
                }
            }
            val key = "${item.id}:${n.season}:${n.episode}"
            if (!sourcesCache.containsKey(key)) {
                runCatching { repository.sources(item, n.season, n.episode) }.getOrNull()?.let {
                    if (it.isNotEmpty()) sourcesCache[key] = it
                }
            }
        }
    }

    /** Load and play the best source of the next episode (auto-advance / one-tap). */
    fun playNextEpisode() {
        val s = _sheet.value as? Sheet.Sources ?: return
        viewModelScope.launch {
            val n = s.next ?: resolveNext(s.item, s.season, s.episode) ?: return@launch
            val key = "${s.item.id}:${n.season}:${n.episode}"
            val sources = sourcesCache[key] ?: runCatching {
                repository.sources(s.item, n.season, n.episode)
            }.getOrNull()?.also { if (it.isNotEmpty()) sourcesCache[key] = it }
            if (sources.isNullOrEmpty()) return@launch
            _sheet.value = Sheet.Sources(s.item, n.season, n.episode, sources)
            prepareNext(s.item, n.season, n.episode)
            sources.firstOrNull()?.let { playSource(it) }
        }
    }

    /** Play the best-ranked source without picking manually. */
    fun autoPlay() {
        val s = _sheet.value as? Sheet.Sources ?: return
        s.sources.firstOrNull()?.let { playSource(it) }
    }

    fun playSource(source: Source) {
        val s = _sheet.value as? Sheet.Sources ?: return
        if (s.playing != null) return
        _sheet.value = s.copy(playing = source.magnet, note = null)
        viewModelScope.launch {
            try {
                val stream = repository.play(source)
                // Keep the sources sheet up (just clear the spinner) so returning
                // from the external player lands back on the list to try another.
                (_sheet.value as? Sheet.Sources)?.let {
                    _sheet.value = it.copy(playing = null, note = null)
                }
                _playRequest.value = stream
                recordWatch(s.item, s.season, s.episode, s.next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Stay on the sheet so the user can pick another source.
                val current = _sheet.value as? Sheet.Sources ?: return@launch
                _sheet.value = current.copy(
                    playing = null,
                    note = e.message ?: "That source isn't cached. Try another.",
                )
            }
        }
    }

    fun dismissSheet() {
        _sheet.value = Sheet.Hidden
    }

    fun consumePlayRequest() {
        _playRequest.value = null
    }

    private fun launchSheet(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sheet.value = Sheet.Error(e.message ?: "Something went wrong.")
            }
        }
    }
}
