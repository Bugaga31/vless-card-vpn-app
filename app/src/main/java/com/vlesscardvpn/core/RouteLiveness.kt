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
        // 1.2.190: восстановлен расширенный пул свидетелей из 1.2.167/1.2.172/1.2.183 — независимые операторы из разных стран
        "https://connectivitycheck.android.com/generate_204",
        "https://www.v2ex.com",
        "https://wifi.vivo.com.cn/generate_204",
        "https://www.baidu.com",
        "https://network-test.debian.org/nm",
        "https://nmcheck.gnome.org",
        "https://www.msftncsi.com/ncsi.txt",
        "https://cp.cloudflare.com",
        "https://www.qq.com",
        "https://www.taobao.com",
    )

    /** 1.2.164: the endpoint that answered fastest last time is tried first next time ("fastest stays").
     *  1.2.165: the winner now survives an app restart (own prefs file, no other prefs are touched). */
    @Volatile var preferredUrl: String? = null
    private var prefs: android.content.SharedPreferences? = null
    private var appCtx: android.content.Context? = null
    fun initPrefs(ctx: android.content.Context) {
        appCtx = ctx.applicationContext
        prefs = ctx.getSharedPreferences("check_url", 0)
        loadPreferred()
    }
    /** 1.2.169: Wi-Fi and mobile data remember their fastest endpoints separately, so switching networks starts the race from the right one. */
    fun networkKind(): String {
        return try {
        val ctx = appCtx ?: return "other"
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "other"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            else -> "other"
        }
        } catch (e: Exception) { "other" }
    }
    /** Re-reads the winners for the CURRENT network kind (callers call it when the network may have changed). */
    fun loadPreferred() {
        val p = prefs ?: return
        val kind = networkKind()
        preferredUrl = p.getString("preferred_$kind", null)?.takeIf { it.startsWith("https://") }
            ?: p.getString("preferred", null)?.takeIf { it.startsWith("https://") }
        preferredBigUrl = p.getString("big_$kind", null)?.takeIf { it.startsWith("https://") }
            ?: p.getString("big", null)?.takeIf { it.startsWith("https://") }
    }
    fun noteAnswered(url: String) {
        if (url.startsWith("https://")) {
            val kind = networkKind()
            preferredUrl = url
            prefs?.edit()?.putString("preferred_$kind", url)?.apply()
        }
    }
    /** A round with no answer from anyone clears the memory: another endpoint must win the next race. */
    fun dropWinner() {
        preferredUrl = null
        prefs?.edit()?.remove("preferred_" + networkKind())?.apply()
    }

    /** 1.2.165: the big-download mirror that delivered fastest last time is tried first next time. 1.2.173: remembered per network kind. */
    @Volatile var preferredBigUrl: String? = null
    fun noteBigUrl(url: String) {
        if (url.startsWith("https://")) {
            val kind = networkKind()
            preferredBigUrl = url
            prefs?.edit()?.putString("big_$kind", url)?.apply()
        }
    }
    /** 1.2.174/1.2.179: a remembered big mirror that stopped delivering demotes itself from first place on its own. */
    fun dropBigUrl() {
        if (preferredBigUrl != null) {
            preferredBigUrl = null
            prefs?.edit()?.remove("big_" + networkKind())?.apply()
        }
    }

    /** 1.2.168/1.2.176/1.2.178/1.2.184: ping goes to the remembered fastest endpoint first, then gstatic, then Cloudflare generate_204 —
     *  the three fallbacks are deduplicated, and 1.2.181 gives the ping its own 5 s timeout so one dead URL cannot stall the test. */
    fun pingMs(client: okhttp3.OkHttpClient): Int {
        val chain = listOfNotNull(preferredUrl, GSTATIC, CLOUDFLARE).filter { it.startsWith("https://") }.distinct()
        for (u in chain) {
            try {
                val c = client.newBuilder().callTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()
                val t0 = System.nanoTime()
                c.newCall(okhttp3.Request.Builder().url(u).header("User-Agent", "Mozilla/5.0").build()).execute().use { resp ->
                    if (resp.code in 100..599) return ((System.nanoTime() - t0) / 1_000_000).toInt()
                }
            } catch (e: Exception) { /* next fallback */ }
        }
        return 0
    }

    /** Anycast resolvers reachable from Russian and foreign networks alike; a TCP handshake only, no payload. */
    val PHYSICAL_TARGETS: List<Pair<String, Int>> = listOf(
        "77.88.8.8" to 53, "77.88.8.1" to 53, "8.8.8.8" to 443, "1.1.1.1" to 443,
        "9.9.9.9" to 53, "208.67.222.222" to 443, "94.140.14.14" to 53,
        // 1.2.190: восстановлены вторичные свидетели из 1.2.170/1.2.175/1.2.177/1.2.185 (всего 16)
        "149.112.112.112" to 53, "208.67.220.220" to 443, "94.140.14.15" to 53,
        "9.9.9.11" to 53, "94.140.15.15" to 53, "1.0.0.1" to 53, "77.88.8.88" to 53,
        "8.8.4.4" to 443, "9.9.9.10" to 53,
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
            .filterNotNull().filter { it.startsWith("https://") }.distinct().take(21)

    /** Fixed anycast targets plus a few TCP VLESS endpoints (so a closed lab network still has a physical witness). */
    fun targets(endpoints: List<Pair<String, Int>>): List<Pair<String, Int>> =
        (PHYSICAL_TARGETS + endpoints.filter { it.first.isNotBlank() && it.second in 1..65535 }.distinct().take(MAX_SERVER_TARGETS)).distinct()
}
