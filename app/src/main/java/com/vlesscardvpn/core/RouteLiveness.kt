package com.vlesscardvpn.core

/**
 * 1.2.136: one missed 3.5 s light probe is not yet a lost route. A slow mobile link (five round trips for a fresh
 * VLESS+TLS request) or a short outage of the phone's own network used to be reported as «связь пропала»: the
 * server was marked dead, Auto hopped to another one, and the user saw endless slow reconnects.
 *
 * Before any recovery the watchdog now asks two independent questions at once:
 *  - does the running route give ANY HTTP answer to one of two URLs within a calmer [CONFIRM_MS] window;
 *  - does the phone's own network reach the internet at all (direct TCP to anycast resolvers and to the VLESS
 *    endpoints themselves — the app is outside its own VPN, so this never travels through the tunnel).
 * Only «network works, route silent» is a dead route. «Network silent» is an outage: wait, blame no server.
 */
object RouteLiveness {
    enum class Verdict { ALIVE, OFFLINE, DEAD }

    const val CONFIRM_MS = 6000L
    const val PHYSICAL_MS = 2000
    const val MAX_SERVER_TARGETS = 3
    const val GSTATIC = "https://www.gstatic.com/generate_204"
    const val CLOUDFLARE = "https://cp.cloudflare.com/generate_204"

    /** 1.2.164: more public liveness endpoints; they all race at once, ANY HTTP answer proves the route.
     *  1.2.172: Debian network-test and GNOME nmcheck added (two more independent operators). */
    val EXTRA_CHECK_URLS: List<String> = listOf(
        "https://network-test.debian.org/nm",
        "https://nmcheck.gnome.org/",
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://www.google.com/generate_204",
        "https://www.msftconnecttest.com/connecttest.txt",
        "https://captive.apple.com/hotspot-detect.html",
        "https://detectportal.firefox.com/success.txt",
        "https://connect.rom.miui.com/generate_204",
        "https://connectivitycheck.platform.hicloud.com/generate_204",
        "https://connectivitycheck.android.com/generate_204",
        "https://www.v2ex.com/generate_204",
        "https://wifi.vivo.com.cn/generate_204",
        "https://www.baidu.com/generate_204",
    )

    /** 1.2.164: the endpoint that answered fastest last time is tried first next time ("fastest stays").
     *  1.2.165: the winner now survives an app restart (own prefs file, no other prefs are touched).
     *  1.2.169: the winner is per network profile: Wi-Fi and mobile each keep their own fastest URL,
     *  so a switch between them starts from the URL that was fastest on THAT network, not the other. */
    @Volatile var preferredUrl: String? = null
    @Volatile private var profile: String = ""
    private var prefs: android.content.SharedPreferences? = null
    /** Legacy keys ("preferred"/"big") stay the default profile's storage, so nothing is lost on upgrade. */
    private fun key(base: String, prof: String): String = if (prof.isBlank()) base else base + ":" + prof
    fun setProfile(prof: String?) {
        val np = prof?.takeIf { it.isNotBlank() } ?: ""
        if (np == profile) return
        profile = np
        preferredUrl = prefs?.getString(key("preferred", np), null)?.takeIf { it.startsWith("https://") }
        preferredBigUrl = prefs?.getString(key("big", np), null)?.takeIf { it.startsWith("https://") }
        preferredSpeedUrl = prefs?.getString(key("speed", np), null)?.takeIf { it.startsWith("https://") }
    }
    fun initPrefs(ctx: android.content.Context) {
        prefs = ctx.getSharedPreferences("check_url", 0)
        preferredUrl = prefs?.getString(key("preferred", profile), null)?.takeIf { it.startsWith("https://") }
        preferredBigUrl = prefs?.getString(key("big", profile), null)?.takeIf { it.startsWith("https://") }
        preferredSpeedUrl = prefs?.getString(key("speed", profile), null)?.takeIf { it.startsWith("https://") }
    }
    fun noteAnswered(url: String) {
        if (url.startsWith("https://") && preferredUrl != url) {
            preferredUrl = url
            prefs?.edit()?.putString(key("preferred", profile), url)?.apply()
        }
    }
    /** A round with no answer from anyone clears the memory: another endpoint must win the next race. */
    fun dropWinner() { preferredUrl = null; prefs?.edit()?.remove(key("preferred", profile))?.apply() }

    /** 1.2.165: the big-download mirror that delivered fastest last time is tried first next time.
     *  1.2.169: remembered per network profile, like the small-URL winner. */
    @Volatile var preferredBigUrl: String? = null
    fun noteBigUrl(url: String) {
        if (url.startsWith("https://") && preferredBigUrl != url) {
            preferredBigUrl = url
            prefs?.edit()?.putString(key("big", profile), url)?.apply()
        }
    }

    /** 1.2.173: the short speed-test mirror that delivered a good sample last time is tried first next time,
     *  remembered per network profile, like the check and big-mirror winners. */
    @Volatile var preferredSpeedUrl: String? = null
    fun noteSpeedUrl(url: String) {
        if (url.startsWith("https://") && preferredSpeedUrl != url) {
            preferredSpeedUrl = url
            prefs?.edit()?.putString(key("speed", profile), url)?.apply()
        }
    }
    /** 1.2.174: a speed mirror that stopped delivering clears its first place, so a dead mirror is not retried first forever. */
    fun dropSpeedUrl() {
        preferredSpeedUrl = null
        prefs?.edit()?.remove(key("speed", profile))?.apply()
    }

    /** Anycast resolvers reachable from Russian and foreign networks alike; a TCP handshake only, no payload.
     *  1.2.170: pool widened 7 -> 10 (Quad9 secondary, OpenDNS secondary, AdGuard), more witnesses survive local blocks. */
    val PHYSICAL_TARGETS: List<Pair<String, Int>> = listOf(
        "77.88.8.8" to 53, "77.88.8.1" to 53, "8.8.8.8" to 443, "1.1.1.1" to 443,
        "9.9.9.9" to 53, "208.67.222.222" to 443, "94.140.14.14" to 53,
        "149.112.112.112" to 53, "208.67.220.220" to 443, "185.228.168.9" to 53,
    )

    /** TCP-carried protocols: a direct handshake to their endpoint proves the physical path. UDP ones cannot. */
    val TCP_PROTOCOLS = setOf("vless", "vmess", "trojan", "shadowsocks", "socks")

    fun verdict(alive: Boolean, physical: Boolean): Verdict = when {
        alive -> Verdict.ALIVE
        !physical -> Verdict.OFFLINE
        else -> Verdict.DEAD
    }

    /** Any HTTP status proves that the route carries traffic both ways; only a transport failure counts as silence. */
    fun answered(code: Int): Boolean = code in 100..599

    /** The configured check URL first, then the fastest winner from last time, then the rest; HTTPS only.
     *  1.2.167: pool widened 9 -> 13 URLs, all still racing at once.
     *  1.2.172: pool widened 13 -> 15 URLs. */
    fun urls(testUrl: String): List<String> =
        (listOf(testUrl.trim(), preferredUrl, GSTATIC, CLOUDFLARE) + EXTRA_CHECK_URLS)
            .filterNotNull().filter { it.startsWith("https://") }.distinct().take(15)

    /** Fixed anycast targets plus a few TCP VLESS endpoints (so a closed lab network still has a physical witness). */
    fun targets(endpoints: List<Pair<String, Int>>): List<Pair<String, Int>> =
        (PHYSICAL_TARGETS + endpoints.filter { it.first.isNotBlank() && it.second in 1..65535 }.distinct().take(MAX_SERVER_TARGETS)).distinct()
}
