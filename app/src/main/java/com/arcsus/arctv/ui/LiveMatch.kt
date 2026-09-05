package com.arcsus.arctv.ui

/**
 * Matching rules for the Live tab. Panels stamp a region on every group and
 * channel ("UK| BBC One", "US: CNN", "AR| ECHOUROUK"); a plain substring
 * search for "UK" finds Mil-wauk-ee and B-uk-hari before any of them.
 */
object LiveMatch {

    private val wordSplit = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String): List<String> =
        text.lowercase().split(wordSplit).filter { it.isNotEmpty() }

    /**
     * True if every word of [query] starts a word of [name] ("bbc on" finds
     * "UK| BBC One HD"; "uk" finds "UK| ..." but not "Milwaukee").
     */
    fun matches(name: String, query: String): Boolean {
        val q = words(query)
        if (q.isEmpty()) return true
        val n = words(name)
        return q.all { part -> n.any { it.startsWith(part) } }
    }

    /**
     * True if [name] is stamped with [region]: the region is its first word,
     * so "UK| SPORTS" and "UK - NEWS" match "UK" while "UKRAINE" does not.
     */
    fun inRegion(name: String, region: String): Boolean {
        val r = words(region).firstOrNull() ?: return false
        return words(name).firstOrNull() == r
    }

    /**
     * The prefix a panel stamps on every channel in a list ("UK| ", "US: "),
     * so tiles read "BBC One HD" rather than "UK| BBC One H…".
     */
    fun sharedPrefix(names: List<String>): String {
        if (names.size < 3) return ""
        val first = names.first()
        val cut = first.indexOfFirst { it == '|' || it == ':' }
        if (cut !in 1..7) return ""
        val prefix = first.substring(0, cut + 1)
        val hits = names.count { it.startsWith(prefix) }
        return if (hits * 10 >= names.size * 8) prefix else ""
    }
}
