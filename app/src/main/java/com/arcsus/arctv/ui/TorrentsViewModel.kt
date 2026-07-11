package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcsus.arctv.data.FilenameParser
import com.arcsus.arctv.data.ParsedMedia
import com.arcsus.arctv.data.RdRepository
import com.arcsus.arctv.data.TorrentItem
import com.arcsus.arctv.data.UnrestrictedLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

private const val PAGE_SIZE = 50

enum class TorrentFilter(val label: String) { ALL("All"), MOVIES("Movies"), TV("TV") }

/** A row in the torrents grid: either a single title or a grouped series. */
sealed interface TorrentEntry {
    val key: String
    val displayTitle: String
    val posterFilename: String

    data class Single(val torrent: TorrentItem, val media: ParsedMedia?) : TorrentEntry {
        override val key get() = "t:${torrent.id}"
        override val displayTitle get() = media?.title ?: torrent.filename
        override val posterFilename get() = torrent.filename
    }

    data class Series(val title: String, val episodes: List<TorrentItem>) : TorrentEntry {
        override val key get() = "s:${title.lowercase(Locale.US)}"
        override val displayTitle get() = title
        override val posterFilename get() = episodes.first().filename
    }
}

class TorrentsViewModel(private val repository: RdRepository) : ViewModel() {

    data class UiState(
        val items: List<TorrentItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val endReached: Boolean = false,
        val filter: TorrentFilter = TorrentFilter.ALL,
        val query: String = "",
    )

    /** A file inside a finished torrent, paired with its restricted link. */
    data class PickableFile(val name: String, val bytes: Long, val link: String)

    sealed interface Picker {
        data object Hidden : Picker
        data class Episodes(val title: String, val episodes: List<TorrentItem>) : Picker
        data class LoadingInfo(val torrent: TorrentItem) : Picker
        data class Files(val torrent: TorrentItem, val files: List<PickableFile>) : Picker
        data class Unrestricting(val torrent: TorrentItem) : Picker
        data class Error(val message: String) : Picker
    }

    private var page = 1

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _picker = MutableStateFlow<Picker>(Picker.Hidden)
    val picker: StateFlow<Picker> = _picker

    /** Set when an unrestricted link is ready to play; consumed by the UI. */
    private val _playRequest = MutableStateFlow<UnrestrictedLink?>(null)
    val playRequest: StateFlow<UnrestrictedLink?> = _playRequest

    init {
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.torrents(page, PAGE_SIZE)
                page++
                _state.update {
                    it.copy(
                        items = dedupeByHash(it.items + result.items),
                        loading = false,
                        endReached = result.items.size < PAGE_SIZE,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    fun refresh() {
        page = 1
        _state.update { UiState(filter = it.filter, query = it.query) }
        loadMore()
    }

    fun setFilter(filter: TorrentFilter) = _state.update { it.copy(filter = filter) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    /**
     * The same torrent can be added to Real-Debrid more than once (same hash,
     * different id). Collapse those to one entry, preferring a finished copy and
     * otherwise the furthest-along one.
     */
    private fun dedupeByHash(items: List<TorrentItem>): List<TorrentItem> {
        val best = LinkedHashMap<String, TorrentItem>()
        for (item in items) {
            val key = item.hash.ifBlank { item.id }
            val existing = best[key]
            if (existing == null || item.isBetterThan(existing)) {
                best[key] = item
            }
        }
        return best.values.toList()
    }

    private fun TorrentItem.isBetterThan(other: TorrentItem): Boolean {
        val mine = if (status == "downloaded") 1 else 0
        val theirs = if (other.status == "downloaded") 1 else 0
        if (mine != theirs) return mine > theirs
        return progress > other.progress
    }

    fun openSeries(series: TorrentEntry.Series) {
        val sorted = series.episodes.sortedWith(
            compareBy(
                { FilenameParser.parse(it.filename)?.season ?: 0 },
                { FilenameParser.parse(it.filename)?.episode ?: 0 },
            ),
        )
        _picker.value = Picker.Episodes(series.title, sorted)
    }

    fun openTorrent(torrent: TorrentItem) {
        if (torrent.status != "downloaded") return
        _picker.value = Picker.LoadingInfo(torrent)
        viewModelScope.launch {
            try {
                val info = repository.torrentInfo(torrent.id)
                val selectedFiles = info.files.filter { it.selected == 1 }
                val files = if (selectedFiles.size == info.links.size && selectedFiles.isNotEmpty()) {
                    selectedFiles.zip(info.links) { file, link ->
                        PickableFile(file.path.trimStart('/'), file.bytes, link)
                    }
                } else {
                    // Fall back to the bare links if files and links don't line up.
                    info.links.mapIndexed { index, link ->
                        PickableFile("File ${index + 1}", 0, link)
                    }
                }
                if (files.isEmpty()) {
                    _picker.value = Picker.Error("This torrent has no downloadable files.")
                } else {
                    _picker.value = Picker.Files(torrent, files)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _picker.value = Picker.Error(friendlyError(e))
            }
        }
    }

    fun unrestrictAndPlay(torrent: TorrentItem, file: PickableFile) {
        _picker.value = Picker.Unrestricting(torrent)
        viewModelScope.launch {
            try {
                _playRequest.value = repository.unrestrict(file.link)
                _picker.value = Picker.Hidden
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _picker.value = Picker.Error(friendlyError(e))
            }
        }
    }

    fun consumePlayRequest() {
        _playRequest.value = null
    }

    fun dismissPicker() {
        _picker.value = Picker.Hidden
    }
}

/**
 * Turns the flat torrent list into display entries: TV episodes of the same show
 * are grouped into one [TorrentEntry.Series]; everything else is a
 * [TorrentEntry.Single]. Then applies the Movie/TV filter and the search query.
 * First-appearance order is preserved.
 */
fun buildTorrentEntries(
    items: List<TorrentItem>,
    filter: TorrentFilter,
    query: String,
): List<TorrentEntry> {
    val parsed = items.map { it to FilenameParser.parse(it.filename) }

    // Collect TV episodes by normalised show title.
    val tvGroups = LinkedHashMap<String, MutableList<TorrentItem>>()
    val tvDisplay = HashMap<String, String>()
    for ((item, media) in parsed) {
        if (media != null && media.isTv) {
            val key = media.title.lowercase(Locale.US).trim()
            tvGroups.getOrPut(key) { mutableListOf() }.add(item)
            tvDisplay.putIfAbsent(key, media.title)
        }
    }

    val emittedSeries = HashSet<String>()
    val entries = mutableListOf<TorrentEntry>()
    for ((item, media) in parsed) {
        if (media != null && media.isTv) {
            val key = media.title.lowercase(Locale.US).trim()
            val group = tvGroups[key].orEmpty()
            if (group.size >= 2) {
                if (emittedSeries.add(key)) {
                    entries += TorrentEntry.Series(tvDisplay[key] ?: media.title, group)
                }
            } else {
                entries += TorrentEntry.Single(item, media)
            }
        } else {
            entries += TorrentEntry.Single(item, media)
        }
    }

    val filtered = entries.filter { entry ->
        when (filter) {
            TorrentFilter.ALL -> true
            TorrentFilter.TV -> entry is TorrentEntry.Series ||
                (entry is TorrentEntry.Single && entry.media?.isTv == true)
            TorrentFilter.MOVIES -> entry is TorrentEntry.Single && entry.media?.isTv != true
        }
    }

    val q = query.trim()
    if (q.isEmpty()) return filtered
    return filtered.filter { entry ->
        entry.displayTitle.contains(q, ignoreCase = true) ||
            (entry is TorrentEntry.Single && entry.torrent.filename.contains(q, ignoreCase = true))
    }
}
