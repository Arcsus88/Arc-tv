package com.arcsus.arctv.data

data class ParsedMedia(
    val title: String,
    val year: Int?,
    val isTv: Boolean,
    val season: Int? = null,
    val episode: Int? = null,
) {
    /** Episodes of the same show share one key (and one poster). */
    val cacheKey: String
        get() = buildString {
            append(if (isTv) "tv:" else "movie:")
            append(title.lowercase())
            if (!isTv && year != null) append(':').append(year)
        }

    val episodeLabel: String?
        get() = if (isTv && season != null && episode != null) {
            "S%02dE%02d".format(season, episode)
        } else {
            null
        }
}

/** Extracts a searchable title from scene-style release filenames. */
object FilenameParser {

    private val VIDEO_EXTENSIONS =
        setOf("mkv", "mp4", "avi", "m4v", "mov", "wmv", "ts", "webm", "mpg", "mpeg")

    private val TV_PATTERN = Regex("(?i)\\bS(\\d{1,2})[ ]?E(\\d{1,3})\\b|\\b(\\d{1,2})x(\\d{2,3})\\b")
    private val YEAR_PATTERN = Regex("\\b(19\\d{2}|20\\d{2})\\b")
    private val NOISE_PATTERN = Regex(
        "(?i)\\b(2160p|1080p|720p|480p|WEB[ -]?DL|WEB[ -]?Rip|WEB|Blu[ -]?Ray|BDRip|BRRip|HDTV|" +
            "DVDRip|REMUX|x264|x265|h[ .]?264|h[ .]?265|HEVC|AV1|10bit|8bit|HDR10\\+?|HDR|DoVi|DV|" +
            "Atmos|DDP?[ .]?[2571][ .]?[01]?|AAC|TrueHD|DTS(?:[ -]?HD)?|AMZN|NF|ATVP|DSNP|HULU|" +
            "HMAX|MAX|PROPER|REPACK|EXTENDED|UNRATED|IMAX|COMPLETE|LIMITED|iNTERNAL|MULTi|DUAL|UHD)\\b"
    )

    fun parse(filename: String): ParsedMedia? {
        var name = filename.substringAfterLast('/')
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in VIDEO_EXTENSIONS) name = name.substringBeforeLast('.')

        name = name
            .replace('.', ' ')
            .replace('_', ' ')
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*\\)")) { match ->
                // keep a bare year inside parentheses, drop other bracketed noise
                val inner = match.value.substring(1, match.value.length - 1).trim()
                if (YEAR_PATTERN.matches(inner)) " $inner " else " "
            }

        val tvMatch = TV_PATTERN.find(name)
        val noiseMatch = NOISE_PATTERN.find(name)

        var cut = minOf(
            tvMatch?.range?.first ?: Int.MAX_VALUE,
            noiseMatch?.range?.first ?: Int.MAX_VALUE,
        )
        // Treat the last year before the cut as the release year, unless the
        // name starts with it (e.g. "2001 A Space Odyssey").
        val yearMatch = YEAR_PATTERN.findAll(name)
            .lastOrNull { it.range.first in 1 until cut }
        if (yearMatch != null) cut = minOf(cut, yearMatch.range.first)

        val title = (if (cut == Int.MAX_VALUE) name else name.substring(0, cut))
            .replace(Regex("[ \\-–]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (title.isBlank()) return null

        val season: Int?
        val episode: Int?
        if (tvMatch != null) {
            val g = tvMatch.groupValues
            season = (g[1].ifEmpty { g[3] }).toIntOrNull()
            episode = (g[2].ifEmpty { g[4] }).toIntOrNull()
        } else {
            season = null
            episode = null
        }

        return ParsedMedia(
            title = title,
            year = yearMatch?.value?.toIntOrNull(),
            isTv = tvMatch != null,
            season = season,
            episode = episode,
        )
    }
}
