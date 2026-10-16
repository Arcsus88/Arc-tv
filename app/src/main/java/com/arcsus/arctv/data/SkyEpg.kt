package com.arcsus.arctv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** One Sky channel in the line-up: its service id, EPG number and name. */
data class SkyService(val sid: String, val number: String, val name: String, val tokens: List<String>)

/** What Sky knows about one IPTV channel. */
data class SkyChannel(
    val sid: String,
    /** Sky EPG number, e.g. "103" for ITV1 HD. */
    val number: String,
    val logo: String,
    /** Today's (and, late evening, tomorrow's) programmes in start order. */
    val entries: List<EpgEntry>,
)

/**
 * Sky's own EPG, as the web app's guide uses it: the "hawk" linear API
 * behind Sky's TV guides. It needs no login. It dresses the guide the way
 * the Sky Q mobile guide looks -- Sky channel numbers and logos, a 16:9
 * still and synopsis for every programme -- and fills gaps in the panel's
 * EPG. IPTV channels are matched to Sky's by name.
 *
 * A Kotlin port of the web app's src/lib/live/sky-epg.ts; keep them in step.
 */
object SkyEpg {
    private const val HAWK = "https://awk.epgsky.com/hawk/linear"
    // Bouquet 4101 is Sky's HD line-up; sub-bouquet 1 is the London region.
    private const val LINEUP_URL = "$HAWK/services/4101/1"
    // The schedule endpoint answers 400 for more than 20 sids at once.
    private const val MAX_SIDS = 20
    private const val SCHEDULE_TTL_MS = 10 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    fun logoUrl(sid: String): String =
        // Sized to fit a 240x104 box; Sky draws these for dark backgrounds.
        "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/240/104/skychb$sid.png"

    private fun imageUrl(programmeUuid: String): String =
        "https://images.metadata.sky.com/pd-image/$programmeUuid/16-9/480"

    // -----------------------------------------------------------------------
    // Name matching. Sky's names are squeezed into ~14 characters
    // ("SkySpMainEvHD", "SkySp F'ball HD", "ComedyCentHD"), so each Sky word
    // is treated as an abbreviation: its letters must appear in order,
    // starting on the first letter of an IPTV word (or straight after the
    // previous Sky word, for "CBeebies" against "CBEEBIES"), and it may run
    // across words ("PL" -> "Premier League"). Every IPTV word must be touched
    // and every Sky word used, so "Sky Mix" never matches "Sky Sports Mix".
    // When several fit, the longest Sky name wins.

    // Quality and housekeeping words IPTV panels bolt onto names.
    private val NOISE = setOf(
        "hd", "fhd", "uhd", "sd", "hq", "lq", "4k", "8k", "hevc", "h264", "h265",
        "raw", "backup", "vip", "multi", "fps", "uk", "gb",
    )
    // IPTV words Sky leaves out: Sky Cinema channels are "Sky Premiere" etc.
    private val OPTIONAL = setOf("cinema")
    private val NUMBER_WORDS = mapOf("one" to "1", "two" to "2", "three" to "3", "four" to "4")
    // "UK| BBC One", "UK: BBC One", "[UK] BBC One".
    private val COUNTRY_PREFIX = Regex("^\\s*\\[?[A-Za-z]{2,3}]?\\s*(?::|\\||-|–)\\s*")
    private val U_AND = Regex("^\\s*u\\s*&\\s*", RegexOption.IGNORE_CASE)
    private val LONDON = Regex("^lon(don)?$")

    fun tokenize(name: String): List<String> {
        val words = Normalizer.normalize(name, Normalizer.Form.NFKC)
            .replace(COUNTRY_PREFIX, "")
            .replace(U_AND, "") // UKTV's "U&Dave" is plain "Dave" elsewhere
            .replace(Regex("['’]"), "") // "F'ball" -> "Fball"
            .replace("+", " plus ")
            .replace(Regex("([a-z])([A-Z])"), "$1 $2") // "SkySp" -> "Sky Sp"
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2") // "TNTSports" -> "TNT Sports"
            .replace(Regex("([A-Za-z])(\\d)"), "$1 $2") // "ITV1" -> "ITV 1"
            .replace(Regex("(\\d)([A-Za-z])"), "$1 $2") // "5STAR" -> "5 STAR"
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() && it !in NOISE }
            .map { NUMBER_WORDS[it] ?: it } // "BBC One" = "BBC 1"
            .toMutableList()
        // London is the default region: "BBC One Lon HD" is plain BBC One.
        if (words.size > 1 && LONDON.matches(words.last())) words.removeAt(words.size - 1)
        // "Channel 5" is just "5" on Sky (but "Channel 4" keeps its name).
        val i = words.indexOf("channel")
        if (i != -1 && words.getOrNull(i + 1) == "5") words.removeAt(i)
        // Plain "ITV" means ITV1.
        if (words.size == 1 && words[0] == "itv") words.add("1")
        return words
    }

    private fun isNumber(w: String) = w.firstOrNull()?.isDigit() == true

    /** Where [abbr] can end if its first letter sits at words[w][k]: (word, index after). */
    private fun spans(abbr: String, words: List<String>, w: Int, k: Int): List<Pair<Int, Int>> {
        val word = words.getOrNull(w) ?: return emptyList()
        if (word.getOrNull(k) != abbr[0]) return emptyList()
        // Numbers must match a whole word: "1" is not short for "10".
        if (isNumber(abbr) || isNumber(word)) {
            return if (k == 0 && abbr == word) listOf(w to word.length) else emptyList()
        }
        val ends = mutableListOf<Pair<Int, Int>>()
        val seen = HashSet<Triple<Int, Int, Int>>()
        fun walk(p: Int, cw: Int, ck: Int) {
            if (!seen.add(Triple(p, cw, ck))) return
            if (p == abbr.length) {
                ends.add(cw to ck)
                return
            }
            // Next letter later in the same word (leftmost is always best)...
            val x = words[cw].indexOf(abbr[p], ck)
            if (x != -1) walk(p + 1, cw, x + 1)
            // ...or as the first letter of the following word.
            if (words.getOrNull(cw + 1)?.firstOrNull() == abbr[p]) walk(p + 1, cw + 1, 1)
        }
        walk(1, w, k + 1)
        return ends
    }

    private fun matches(sky: List<String>, iptv: List<String>): Boolean {
        val memo = HashMap<Triple<Int, Int, Int>, Boolean>()
        // Sky words [i..] still to place; the last one placed ended at iptv[w][k).
        fun go(i: Int, w: Int, k: Int): Boolean {
            val key = Triple(i, w, k)
            memo[key]?.let { return it }
            val ok = if (i == sky.size) {
                iptv.drop(w + 1).all { it in OPTIONAL }
            } else {
                val next = w + 1
                // Skip an optional IPTV word, start on the next word, or carry
                // on straight after the previous Sky word inside the same word.
                (next < iptv.size && iptv[next] in OPTIONAL && go(i, next, iptv[next].length)) ||
                    spans(sky[i], iptv, next, 0).any { (ew, ek) -> go(i + 1, ew, ek) } ||
                    (w >= 0 && spans(sky[i], iptv, w, k).any { (ew, ek) -> go(i + 1, ew, ek) })
            }
            memo[key] = ok
            return ok
        }
        return go(0, -1, 0)
    }

    private fun letters(s: SkyService) = s.tokens.sumOf { it.length }

    /** The Sky channel an IPTV channel name refers to, if any. */
    fun match(name: String, lineup: List<SkyService>): SkyService? {
        val words = tokenize(name)
        if (words.isEmpty()) return null
        val hits = lineup.filter { it.tokens.isNotEmpty() && matches(it.tokens, words) }
        if (hits.isEmpty()) return null
        // The most specific (longest) Sky name wins: "Sky Sports +" is SkySp+,
        // not SkySp PL, and "MUTV" isn't MTV. Same name twice (e.g. "Sky
        // Comedy" is 113 and Sky Cinema's 308): the lowest number, or the 3xx
        // film channel when the IPTV name says cinema.
        val best = hits.maxOf { letters(it) }
        val top = hits.filter { letters(it) == best }.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
        if ("cinema" in words) top.firstOrNull { it.number.startsWith("3") }?.let { return it }
        return top.first()
    }

    // -----------------------------------------------------------------------
    // Fetching.

    private val lineupLock = Mutex()
    private var lineup: List<SkyService>? = null

    /** Sky's channel line-up (fetched once per run; a failure lets the next guide retry). */
    suspend fun lineup(): List<SkyService> = lineupLock.withLock {
        lineup ?: withContext(Dispatchers.IO) { parseLineup(get(LINEUP_URL)) }.also { lineup = it }
    }

    internal fun parseLineup(body: String): List<SkyService> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return (root["services"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val sid = o.str("sid")
            val title = o.str("t")
            val n = o.str("c").toIntOrNull()
            val adult = (o["adult"] as? JsonPrimitive)?.booleanOrNull == true
            if (sid.isEmpty() || title.isEmpty() || n == null || adult) return@mapNotNull null
            // 8xx SD repeats of the 1xx HD channels, and the box's menu "channels".
            if ((n in 800..899 && o.str("sf") == "SD") || n >= 996) return@mapNotNull null
            SkyService(sid, o.str("c"), title, tokenize(title))
        }
    }

    /** Sky schedules are per UK calendar day, named yyyyMMdd. */
    private fun skyDay(ms: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.UK).apply { timeZone = TimeZone.getTimeZone("Europe/London") }
            .format(Date(ms))

    private val scheduleCache = HashMap<String, Pair<Long, Map<String, List<EpgEntry>>>>()
    private val scheduleLock = Mutex()

    private suspend fun schedule(day: String, sids: List<String>): Map<String, List<EpgEntry>> {
        val key = "$day|${sids.joinToString(",")}"
        scheduleLock.withLock {
            scheduleCache[key]?.let { (at, data) ->
                if (System.currentTimeMillis() - at < SCHEDULE_TTL_MS) return data
            }
        }
        val data = withContext(Dispatchers.IO) {
            val root = json.parseToJsonElement(get("$HAWK/schedule/$day/${sids.joinToString(",")}")) as? JsonObject
            val out = HashMap<String, List<EpgEntry>>()
            for (s in (root?.get("schedule") as? JsonArray).orEmpty()) {
                val o = s as? JsonObject ?: continue
                val sid = o.str("sid")
                if (sid.isEmpty()) continue
                out[sid] = (o["events"] as? JsonArray).orEmpty().mapNotNull { ev ->
                    val e = ev as? JsonObject ?: return@mapNotNull null
                    val title = e.str("t")
                    val st = (e["st"] as? JsonPrimitive)?.longOrNull
                    val d = (e["d"] as? JsonPrimitive)?.longOrNull
                    if (title.isEmpty() || st == null || d == null) return@mapNotNull null
                    val uuid = e.str("programmeuuid")
                    EpgEntry(
                        // Sky prefixes premieres with "New: "; the Sky Q guide doesn't show it.
                        title = title.replace(Regex("^New:\\s*", RegexOption.IGNORE_CASE), ""),
                        description = e.str("sy"),
                        start = st,
                        stop = st + d,
                        image = if (uuid.isEmpty()) "" else imageUrl(uuid),
                    )
                }
            }
            out
        }
        scheduleLock.withLock { scheduleCache[key] = System.currentTimeMillis() to data }
        return data
    }

    private val matchCache = HashMap<String, SkyService?>()

    /**
     * Sky's guide for whichever of [channels] it carries, keyed by channel
     * URL. Channels Sky doesn't carry are simply absent. A failed schedule
     * batch still leaves the matched channels their Sky number and logo.
     */
    suspend fun guide(channels: List<LiveChannel>, nowMs: Long = System.currentTimeMillis()): Map<String, SkyChannel> {
        val services = lineup()
        val byUrl = LinkedHashMap<String, SkyService>()
        withContext(Dispatchers.Default) {
            for (c in channels) {
                if (byUrl.containsKey(c.url) || separatorLabel(c.name) != null) continue
                val service = synchronized(matchCache) {
                    if (matchCache.containsKey(c.name)) matchCache[c.name]
                    else match(c.name, services).also { matchCache[c.name] = it }
                }
                if (service != null) byUrl[c.url] = service
            }
        }
        val sids = byUrl.values.map { it.sid }.distinct()
        if (sids.isEmpty()) return emptyMap()

        // Late in the evening, also fetch tomorrow so "next" works past midnight.
        val days = listOf(skyDay(nowMs), skyDay(nowMs + 4 * 3600 * 1000L)).distinct()
        val batches = coroutineScope {
            days.flatMap { day ->
                sids.chunked(MAX_SIDS).map { chunk ->
                    async { runCatching { schedule(day, chunk) }.getOrDefault(emptyMap()) }
                }
            }.awaitAll()
        }
        val bySid = HashMap<String, MutableList<EpgEntry>>()
        for (batch in batches) {
            for ((sid, entries) in batch) bySid.getOrPut(sid) { mutableListOf() }.addAll(entries)
        }
        return byUrl.mapValues { (_, service) ->
            // Day files overlap around midnight; keep one copy of each programme.
            val entries = bySid[service.sid].orEmpty().distinctBy { it.start }.sortedBy { it.start }
            SkyChannel(service.sid, service.number, logoUrl(service.sid), entries)
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw LiveException("Sky guide request failed (${response.code}).")
            return response.body?.string().orEmpty()
        }
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it != "null" }.orEmpty()

    // -----------------------------------------------------------------------
    // Display helpers the guide shares with the web app.

    /** "UK| SKY SPORTS F1 HD" -> "SKY SPORTS F1 HD", for display. */
    fun withoutCountryPrefix(name: String): String = name.replace(COUNTRY_PREFIX, "").trim().ifEmpty { name }

    // IPTV playlists pad groups with dummy "channels" like "##### SKY SPORTS #####".
    private val SEPARATOR = Regex("^\\s*[#=*~_★☆●•▬━─|-]{3,}|[#=*~_★☆●•▬━─|-]{3,}\\s*$")
    private val SEPARATOR_MARKS = Regex("[#=*~_★☆●•▬━─|]+|-{2,}")

    /** The heading text for a separator entry, or null for a real channel. */
    fun separatorLabel(name: String): String? {
        if (!SEPARATOR.containsMatchIn(name)) return null
        return name.replace(SEPARATOR_MARKS, " ").replace(Regex("\\s+"), " ").trim()
    }
}
