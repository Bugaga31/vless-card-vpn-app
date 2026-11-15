package com.vlesscardvpn.core

import com.vlesscardvpn.model.Server
import com.vlesscardvpn.model.Settings
import com.vlesscardvpn.xray.XrayConfigBuilder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AdBlockAutoR253Test {
    private val reality = "vless://11111111-2222-3333-4444-555555555555@1.2.3.4:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179e30d4fc2&type=tcp#AD"
    private val server = com.vlesscardvpn.model.LinkParser.parse(reality)!!

private fun adRuleDomains(config: JSONObject): List<String>? {
        val rules = config.getJSONObject("routing").getJSONArray("rules")
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            if (r.optString("outboundTag") != "block" || !r.has("domain") || r.has("network")) continue
            val d = r.getJSONArray("domain")
            if (d.length() > 0 && d.getString(0) == "geosite:category-ads-all") return (0 until d.length()).map { d.getString(it) }
        }
        return null
    }

    @Test fun parseAdGuardAndHostsShapes() {
        val text = """
            ! comment
            # comment
            [Adblock Plus 2.0]
            ||ads.example.com^
            ||tracker.example.net^${'$'}third-party
            0.0.0.0 ads.another.example
            127.0.0.1 banner.example.org # inline comment
            ::1 v6ads.example
            plainads.example
            ||wild*.card^
            ||path.example.com/path
            ||regex.example.com/${'$'}script
            ad_with_underscore.example
            =bad.example
            ads.example.com
        """.trimIndent()
        val list = AdBlockPolicy.parse(text)
        assertTrue("ads.example.com" in list)
        assertTrue("tracker.example.net" in list)
        assertTrue("ads.another.example" in list)
        assertTrue("banner.example.org" in list)
        assertTrue("v6ads.example" in list)
        assertTrue("plainads.example" in list)
        assertFalse("wild*.card" in list)
        assertFalse(list.any { it.contains('*') || it.contains('/') })
        assertFalse("ad_with_underscore.example" in list)
        assertFalse("=bad.example" in list)
        assertEquals(1, list.count { it == "ads.example.com" }) // deduped
        assertTrue(list.all { it == it.lowercase() })
    }

    @Test fun ipAndMalformedLinesNeverBecomeDomains() {
        val list = AdBlockPolicy.parse("0.0.0.0\n127.0.0.1\n::1\nnoip\n..dots..example\n-x.example\nx-.example\nlabel.example.")
        assertTrue("noip" !in list)
        assertFalse(list.any { it.startsWith('.') || it.endsWith('.') })
        assertFalse(list.any { it.startsWith("-") || it.startsWith("x-") || it.endsWith("-") })
    }

    @Test fun seedSurvivesFetchAndResolversAreExcluded() {
        val fetched = AdBlockPolicy.SEED.take(3) + listOf("freshads.example", "evil.dns.google", "dns.google", "sub.one.one.one.one")
        val merged = AdBlockPolicy.merged(fetched)
        assertTrue(merged.first() == "doubleclick.net")
        assertEquals(1, merged.count { it == "doubleclick.net" })
        assertTrue("freshads.example" in merged)
        assertFalse(merged.any { it.endsWith(".dns.google") || it == "dns.google" })
        assertFalse(merged.any { it == "one.one.one.one" || it.endsWith(".one.one.one.one") })
        assertTrue(AdBlockPolicy.merged(fetched).size <= AdBlockPolicy.MAX_DOMAINS)
    }

    @Test fun dueWhenNeverFetchedOrStale() {
        assertTrue(AdBlockPolicy.isDue(1_000, 0))
        assertTrue(AdBlockPolicy.isDue(AdBlockPolicy.REFRESH_MS - 1, 0)) // never fetched: always due
        assertFalse(AdBlockPolicy.isDue(100 + AdBlockPolicy.REFRESH_MS - 1, 100))
        assertTrue(AdBlockPolicy.isDue(100 + AdBlockPolicy.REFRESH_MS, 100))
    }

    @Test fun safeListBeatsFetchedFalsePositives() {
        val merged = AdBlockPolicy.merged(AdBlockPolicy.SEED.take(2) + listOf(
            "phishing.github.com", "ads.accounts.google.com", "sso.passport.yandex.ru", "realads.example"))
        assertFalse(merged.any { it.endsWith(".github.com") })
        assertFalse(merged.any { it.endsWith(".accounts.google.com") })
        assertFalse(merged.any { it.endsWith(".passport.yandex.ru") })
        assertTrue("realads.example" in merged)
        assertTrue("doubleclick.net" in merged) // seed untouched by the whitelist
    }

    @Test fun thinFetchIsRejected() {
        val text = "||a.example^\n||b.example^"
        val list = AdBlockPolicy.parse(text)
        assertTrue(list.size < AdBlockPolicy.MIN_USEFUL_DOMAINS)
    }

    @Test fun liveListLandsInConfigAndOffSwitchWins() {
        val previous = AdBlockPolicy.current
        try {
            AdBlockPolicy.current = listOf("example-ads.example.com")
            val on = JSONObject(XrayConfigBuilder.vpnConfig(listOf(server to null), Settings(blockAds = true), 1080))
            val onDomains = adRuleDomains(on)
            checkNotNull(onDomains)
            assertTrue(onDomains.contains("domain:example-ads.example.com"))
            assertTrue(onDomains.size > AdBlockPolicy.SEED.size)

            AdBlockPolicy.current = emptyList()
            val off = JSONObject(XrayConfigBuilder.vpnConfig(listOf(server to null), Settings(blockAds = true), 1080))
            val offDomains = adRuleDomains(off)
            checkNotNull(offDomains)
            assertFalse(offDomains.contains("domain:example-ads.example.com"))
        } finally { AdBlockPolicy.current = previous }
    }

    @Test fun blockedIsTheMergedCurrentList() {
        assertEquals(AdBlockPolicy.current, AdBlockPolicy.blocked())
    }

    @Test fun loadRoundTripsSavedFile() {
        val dir = Files.createTempDirectory("adblock").toFile()
        try {
            val list = AdBlockPolicy.merged(listOf("roundtrip.example") + AdBlockPolicy.SEED.take(5))
            val now = 1_700_000_000_000
            File(dir, "adblock.txt").writeText("${AdBlockPolicy.FILE_HEADER} $now ${list.size}\n" + list.joinToString("\n"))
            val previous = AdBlockPolicy.current; val previousStatus = AdBlockPolicy.status
            try {
                AdBlockPolicy.load(dir)
                assertEquals(list, AdBlockPolicy.current)
                assertEquals(now, AdBlockPolicy.status.fetchedAt)
                assertFalse(AdBlockPolicy.isDue(now, now))
            } finally { AdBlockPolicy.current = previous; AdBlockPolicy.status = previousStatus }
            File(dir, "adblock.txt").writeText("garbage without header\na.example")
            val before = AdBlockPolicy.current
            AdBlockPolicy.load(dir)
            assertEquals(before, AdBlockPolicy.current) // foreign file left untouched
        } finally { dir.deleteRecursively() }
    }
}
