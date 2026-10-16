package com.arcsus.arctv

import com.arcsus.arctv.data.SkyEpg
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The guide's IPTV-to-Sky channel matcher, checked against Sky's real HD
 * line-up (a snapshot in test resources). The expected numbers are what the
 * web app's sky-epg.ts answers for the same names, so the two guides agree
 * on every channel.
 */
class SkyEpgMatchTest {

    private val lineup = SkyEpg.parseLineup(
        javaClass.classLoader!!.getResource("sky-lineup.json")!!.readText(),
    )

    /** IPTV name -> Sky EPG number ("-" for no match), from sky-epg.ts. */
    private val expected = listOf(
        "UK| SKY SPORTS F1 HD" to "407",
        "UK: Sky Sports Premier League FHD" to "402",
        "UK| Sky Sports Main Event" to "401",
        "UK| SKY SPORTS FOOTBALL HD" to "403",
        "UK| Sky Sports Mix HD" to "416",
        "UK| Sky Mix HD" to "151",
        "UK| TNT Sports 1 HD" to "410",
        "UK| TNT SPORTS 4" to "414",
        "UK| Sky Sports + HD" to "404",
        "UK| Sky Sports News" to "409",
        "UK| SKY SPORTS CRICKET FHD" to "405",
        "UK| SKY SPORTS RACING" to "415",
        "UK| Sky Sports Box Office" to "491",
        "UK| BBC One HD" to "101",
        "UK| BBC ONE LONDON" to "101",
        "UK| ITV HD" to "103",
        "UK| ITV 1 +1" to "203",
        "UK| Channel 4 HD" to "104",
        "UK| Channel 5" to "105",
        "UK| 5 HD" to "105",
        "UK| Sky Atlantic HD" to "108",
        "UK| U&Dave" to "111",
        "UK| Dave HD" to "111",
        "UK| Comedy Central HD" to "112",
        "UK| Sky Comedy" to "113",
        "UK| Sky Cinema Comedy HD" to "308",
        "UK| MTV" to "126",
        "UK| MUTV" to "418",
        "UK| E4 HD" to "135",
        "UK| More 4" to "136",
        "UK| CBEEBIES" to "608",
        "[UK] Sky Sports Golf" to "406",
        "UK - Sky Sports Tennis" to "408",
        "BBC One" to "101",
    )

    @Test
    fun matchesLikeTheWebGuide() {
        val wrong = expected.mapNotNull { (name, number) ->
            val got = SkyEpg.match(name, lineup)?.number ?: "-"
            if (got == number) null else "$name: expected $number, got $got"
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun separatorsBecomeHeadings() {
        assertEquals("UK SPORTS", SkyEpg.separatorLabel("##### UK SPORTS #####"))
        assertEquals("SKY SPORTS", SkyEpg.separatorLabel("===== SKY SPORTS ====="))
        assertEquals(null, SkyEpg.separatorLabel("UK| BBC One HD"))
    }

    @Test
    fun countryPrefixIsDropped() {
        assertEquals("SKY SPORTS F1 HD", SkyEpg.withoutCountryPrefix("UK| SKY SPORTS F1 HD"))
        assertEquals("Sky Sports Tennis", SkyEpg.withoutCountryPrefix("UK - Sky Sports Tennis"))
        assertEquals("BBC One", SkyEpg.withoutCountryPrefix("BBC One"))
    }
}
