package com.vlesscardvpn.core

import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** «Авто-блокировщик рекламы»: the bundled geosite blocks the categories the APK shipped with,
 * this layer adds a live list that refreshes itself in the background (AdGuard DNS filter mirrors)
 * so new ad domains get blocked without an app update. A seed list of well-known ad networks keeps
 * blocking real from the very first connect, before any fetch. The refresh never blocks a connect:
 * it runs on its own and lands on the next config build. */
object AdBlockPolicy {
    const val REFRESH_MS = 3L * 24 * 60 * 60 * 1000 // 3 days
    const val MAX_DOMAINS = 6000
    const val MIN_USEFUL_DOMAINS = 100
    const val FILE_HEADER = "# vless-adblock"

    /** The original plus an independent CDN mirror of the same list, raced per fetch. */
    val SOURCES = listOf(
        "https://adguardteam.github.io/HostlistsRegistry/filters/filter_1.txt",
        "https://cdn.jsdelivr.net/gh/AdguardTeam/FiltersRegistry@master/filters/filter_15_DnsFilter/filter_15_DnsFilter.txt",
    )

    /** Second list: the AdGuard mobile filter, fetched in parallel; both merge in. */
    val MOBILE_SOURCES = listOf(
        "https://adguardteam.github.io/HostlistsRegistry/filters/filter_2.txt",
        "https://cdn.jsdelivr.net/gh/AdguardTeam/FiltersRegistry@master/filters/filter_2_MobileFilter/filter_2.txt",
    )

    /** Resolvers are how the tunnel itself resolves; a list entry naming one must never block it. */
    val NEVER_BLOCK = setOf("dns.google", "cloudflare-dns.com", "one.one.one.one", "dns.quad9.net",
        "dns.adguard-dns.com", "common.dot.dns.yandex.net")

    /** Sign-in, payment and download infrastructure the public lists sometimes flag by mistake;
     * a false positive here breaks a site so hard it reads like the VPN is at fault. */
    val SAFE = setOf("accounts.google.com", "appleid.apple.com", "login.microsoftonline.com",
        "login.live.com", "id.vk.com", "passport.yandex.ru", "oauth.yandex.ru",
        "github.com", "gitlab.com", "stackexchange.com", "wikipedia.org")

    /** Curated seed: major ad exchanges and mobile ad networks, in addition to geosite. */
    val SEED = listOf("doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "adservice.google.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "mgid.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "adnxs.com", "adsrvr.org",
        "smartadserver.com", "adform.net", "media.net", "33across.com", "bidswitch.net", "sharethrough.com",
        "teads.tv", "3lift.com", "sonobi.com", "tapad.com", "id5-sync.com", "amazon-adsystem.com",
        "adcolony.com", "applovin.com", "chartboost.com", "vungle.com", "mopub.com", "inmobi.com",
        "startapp.com", "ironsrc.com", "appodeal.com", "scorecardresearch.com", "quantserve.com",
        "doubleverify.com", "adsafeprotected.com", "moatads.com", "adfox.ru", "adriver.ru", "propellerads.com",
        "an.yandex.ru", "awaps.yandex.net", "yandexadexchange.net")

    data class Status(val fetchedAt: Long = 0, val domains: Int = 0)

    /** Merged (seed + fetched) blocking list; public like Tunnel's running state, tests swap it directly. */
    @Volatile var current: List<String> = emptyList()
    @Volatile var status = Status()

    fun isDue(now: Long, at: Long = status.fetchedAt): Boolean = at == 0L || now - at >= REFRESH_MS

    /** Bare domains (config builder adds the "domain:" prefix). */
    fun blocked(): List<String> = current

    /** Restores the last fetched list before a config build, so a restart keeps blocking. */
    fun load(dir: File) {
        val text = runCatching { File(dir, "adblock.txt").takeIf { it.isFile }?.readText() }.getOrNull() ?: return
        val lines = text.lineSequence().toList()
        if (lines.firstOrNull()?.startsWith(FILE_HEADER) != true) return
        val at = lines.first().split(' ').getOrNull(2)?.toLongOrNull() ?: return
        val list = merged(parse(lines.drop(1).joinToString("\n")))
        if (list.isEmpty()) return
        current = list
        status = Status(at, list.size)
    }

    /** Refreshes only when the list is stale; a failed or thin fetch keeps the current list. */
    suspend fun refreshIfDue(dir: File, now: Long = System.currentTimeMillis()): Boolean =
        isDue(now) && refresh(dir, now)

    suspend fun refresh(dir: File, now: Long = System.currentTimeMillis()): Boolean {
        val texts = coroutineScope {
            val dns = async { AutoSubscriptionFetch.firstUseful(SOURCES) { AutoSubscriptionFetch.text(it) } }
            val mobile = async { AutoSubscriptionFetch.firstUseful(MOBILE_SOURCES) { AutoSubscriptionFetch.text(it) } }
            listOfNotNull(dns.await(), mobile.await())
        }
        val list = merged(texts.flatMap { parse(it) })
        if (list.size < MIN_USEFUL_DOMAINS + SEED.size) return false
        current = list
        status = Status(now, list.size)
        runCatching { File(dir, "adblock.txt").writeText("$FILE_HEADER $now ${list.size}\n" + list.joinToString("\n")) }
        return true
    }

    /** Seed first (never lost to a fetch), deduped, capped; resolvers, safe-list domains and
     * their subdomains excluded. */
    fun merged(fetched: List<String>): List<String> = (SEED + fetched).asSequence()
        .filter { it !in NEVER_BLOCK && NEVER_BLOCK.none { r -> it == r || it.endsWith(".$r") } }
        .filter { it !in SAFE && SAFE.none { s -> it.endsWith(".$s") } }
        .distinct().take(MAX_DOMAINS).toList()

    /** AdGuard/hosts syntax: ||domain^ rules, hosts lines, bare domains; wildcards, paths and
     * regexes are skipped (they can't become a plain domain rule). */
    fun parse(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in text.lineSequence()) {
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("#") || line.startsWith("[")) continue
            if (line.startsWith("||")) line = line.removePrefix("||")
            line = line.substringBefore('$').substringBefore('^').trim().trim('.')
            // Hosts files: "0.0.0.0 ads.example.com # comment" — skip the address, take the name.
            val domain = line.split(' ', '\t').map { it.trim().trim('.') }.filter { it.isNotEmpty() }
                .firstOrNull { t -> !t.isIpLiteral() && t.count { c -> c == '.' } > 0 } ?: continue
            if (domain.contains('*') || domain.contains('/') || domain.contains('=') || domain.contains('~') ||
                domain.contains('@') || domain.contains(':') || domain.contains(' ')) continue
            if (domain.length !in 4..255) continue
            val labels = domain.split('.')
            if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') ||
                it.any { c -> c !in 'a'..'z' && c !in '0'..'9' && c != '-' } }) continue
            out.add(domain.lowercase())
        }
        return out.toList()
    }

    private fun String.isIpLiteral(): Boolean = matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) || contains("::")
}
