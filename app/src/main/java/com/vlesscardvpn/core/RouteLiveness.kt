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

    /** 1.2.164: more public liveness endpoints; they all race at once, ANY HTTP answer proves the route. */
    val EXTRA_CHECK_URLS: List<String> = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://www.google.com/generate_204",
        "https://www.msftconnecttest.com/connecttest.txt",
        "https://captive.apple.com/hotspot-detect.html",
        "https://detectportal.firefox.com/success.txt",
        "https://connect.rom.miui.com/generate_204",
        "https://connectivitycheck.platform.hicloud.com/generate_204",
    )

    /** 1.2.164: the endpoint that answered fastest last time is tried first next time ("fastest stays").
     *  1.2.165: the winner now survives an app restart (own prefs file, no other prefs are touched). */
    @Volatile var preferredUrl: String? = null
    private var prefs: android.content.SharedPreferences? = null
    fun initPrefs(ctx: android.content.Context) {
        prefs = ctx.getSharedPreferences("check_url", 0)
        preferredUrl = prefs?.getString("preferred", null)?.takeIf { it.startsWith("https://") }
    }
    fun noteAnswered(url: String) {
        if (url.startsWith("https://") && preferredUrl != url) {
            preferredUrl = url
            prefs?.edit()?.putString("preferred", url)?.apply()
        }
    }
    /** A round with no answer from anyone clears the memory: another endpoint must win the next race. */
    fun dropWinner() { preferredUrl = null; prefs?.edit()?.remove("preferred")?.apply() }

    /** Anycast resolvers reachable from Russian and foreign networks alike; a TCP handshake only, no payload. */
    val PHYSICAL_TARGETS: List<Pair<String, Int>> = listOf(
        "77.88.8.8" to 53, "77.88.8.1" to 53, "8.8.8.8" to 443, "1.1.1.1" to 443,
        "9.9.9.9" to 53, "208.67.222.222" to 443, "94.140.14.14" to 53,
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

    /** The configured check URL first, then the fastest winner from last time, then the rest; HTTPS only. */
    fun urls(testUrl: String): List<String> =
        (listOf(testUrl.trim(), preferredUrl, GSTATIC, CLOUDFLARE) + EXTRA_CHECK_URLS)
            .filterNotNull().filter { it.startsWith("https://") }.distinct().take(9)

    /** Fixed anycast targets plus a few TCP VLESS endpoints (so a closed lab network still has a physical witness). */
    fun targets(endpoints: List<Pair<String, Int>>): List<Pair<String, Int>> =
        (PHYSICAL_TARGETS + endpoints.filter { it.first.isNotBlank() && it.second in 1..65535 }.distinct().take(MAX_SERVER_TARGETS)).distinct()
}
