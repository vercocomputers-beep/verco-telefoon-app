package org.verco.telefoon

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder

/** Instellingen die de centrale geeft op /prov/vt (versie 1). */
data class Colleague(val name: String, val ext: String)
data class Function(val name: String, val code: String)

/** Rechtstreeks adres van de centrale (lokaal IP), naast het tussenstation. De app gebruikt dit als het de centrale
 *  rechtstreeks kan bereiken (kantoor-wifi of VPN) — dan blijft bellen werken ook als het tussenstation uitvalt.
 *  'probe' is een snelle bereikbaarheidstest (de lokale webpoort van de centrale). */
data class DirectConfig(val domain: String, val port: Int, val transport: String, val phonebook: String, val probeHost: String, val probePort: Int)

/** Gegevens om de app bij Firebase aan te melden (Android-push), van de centrale. Niet geheim. */
data class FcmConfig(val projectId: String, val appId: String, val apiKey: String, val senderId: String)

data class ProvisioningConfig(
    val company: String,
    val userName: String,
    val ext: String,
    val sipUser: String,
    val sipPassword: String,
    val domain: String,
    val port: Int,
    val transport: String,
    val voicemail: String,
    val phonebook: String,
    val functions: List<Function>,
    val colleagues: List<Colleague>,
    val pushEnabled: Boolean,
    val fcm: FcmConfig?,
    val source: String,
    val raw: String,
    val direct: DirectConfig? = null,
) {
    val server: String get() = if (port == 5060) domain else "$domain:$port"

    companion object {
        fun parse(json: String, source: String): ProvisioningConfig {
            val o = JSONObject(json)
            require(o.optInt("version") == 1) { "Deze app is te oud voor de centrale. Werk de app bij." }
            val u = o.getJSONObject("user")
            val s = o.getJSONObject("sip")
            val f = o.optJSONArray("functions")
            val c = o.optJSONArray("colleagues")
            return ProvisioningConfig(
                company = o.optString("company"),
                userName = u.optString("name"),
                ext = u.optString("extension"),
                sipUser = s.getString("username"),
                sipPassword = s.getString("password"),
                domain = s.getString("domain"),
                port = s.optInt("port", 5060),
                transport = s.optString("transport", "udp"),
                voicemail = o.optString("voicemail"),
                phonebook = o.optString("phonebook"),
                functions = (0 until (f?.length() ?: 0)).map { f!!.getJSONObject(it) }
                    .map { Function(it.optString("name").removePrefix("★ "), it.optString("code")) },
                colleagues = (0 until (c?.length() ?: 0)).map { c!!.getJSONObject(it) }
                    .map { Colleague(it.optString("name"), it.optString("extension")) },
                pushEnabled = o.optJSONObject("push")?.optBoolean("enabled") ?: false,
                fcm = o.optJSONObject("push")?.optJSONObject("fcm")?.let {
                    FcmConfig(it.optString("project_id"), it.optString("app_id"), it.optString("api_key"), it.optString("sender_id"))
                }?.takeIf { it.projectId.isNotEmpty() && it.appId.isNotEmpty() && it.apiKey.isNotEmpty() && it.senderId.isNotEmpty() },
                source = source,
                raw = json,
                direct = o.optJSONObject("direct")?.let { d ->
                    val sip = d.optJSONObject("sip")
                    val probe = d.optJSONObject("probe")
                    if (sip != null && probe != null)
                        DirectConfig(sip.optString("domain"), sip.optInt("port", 5060),
                            sip.optString("transport", "udp"),
                            d.optString("phonebook"), probe.optString("host"), probe.optInt("port", 0))
                    else null
                },
            )
        }
    }
}

/** De link uit de QR-code: verco-telefoon://setup?s=<centrale>&u=<gebruiker>&p=<wachtwoord> */
data class SetupLink(val server: String, val user: String, val password: String) {
    fun configUrl(): String {
        val e = { v: String -> URLEncoder.encode(v, "UTF-8") }
        return server.trimEnd('/') + "/prov/vt?u=" + e(user) + "&p=" + e(password)
    }

    companion object {
        fun parse(text: String): SetupLink? {
            val uri = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() != "verco-telefoon" || uri.host?.lowercase() != "setup") return null
            val s = uri.getQueryParameter("s") ?: return null
            val u = uri.getQueryParameter("u") ?: return null
            val p = uri.getQueryParameter("p") ?: return null
            if (u.isEmpty() || p.isEmpty()) return null
            return SetupLink(s, u, p)
        }
    }
}

/** Bewaart de instellingen (alleen leesbaar voor deze app) en haalt ze op via de QR-link. */
class AccountStore(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = context.getSharedPreferences("verco", Context.MODE_PRIVATE)
    val config = MutableStateFlow<ProvisioningConfig?>(null)
    /** De instellingen die de app nú gebruikt: rechtstreeks met de centrale als dat kan (kantoor-wifi of VPN),
     *  anders via het tussenstation. Wisselt automatisch mee als het netwerk verandert. */
    val active = MutableStateFlow<ProvisioningConfig?>(null)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    /** 'Niet storen' van deze gebruiker (via de centrale, /prov/cti). */
    val dnd = MutableStateFlow(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var chooseJob: Job? = null

    /** Basis-URL van de centrale (webkant) + app-inlog, voor /prov/cti-aanroepen. */
    private fun ctiUrl(path: String, extra: String = ""): String? {
        val cfg = config.value ?: return null
        val base = cfg.source.trimEnd('/')
        val e = { v: String -> URLEncoder.encode(v, "UTF-8") }
        return "$base/prov/$path?u=${e(cfg.sipUser)}&p=${e(cfg.sipPassword)}$extra"
    }

    /** Huidige 'niet storen' ophalen bij de centrale. */
    suspend fun refreshDnd() {
        val url = ctiUrl("cti") ?: return
        runCatching {
            val json = withContext(Dispatchers.IO) { fetch(url) }
            dnd.value = JSONObject(json).optJSONObject("status")?.optBoolean("dnd") ?: false
        }
    }

    /** 'Niet storen' aan/uit zetten bij de centrale. */
    suspend fun setDnd(on: Boolean) {
        val url = ctiUrl("cti/dnd", "&on=${if (on) 1 else 0}") ?: return
        dnd.value = on   // meteen tonen; wordt bevestigd door het antwoord
        runCatching {
            val json = withContext(Dispatchers.IO) { fetch(url) }
            dnd.value = JSONObject(json).optBoolean("dnd", on)
        }
    }

    /**
     * Collega-status via de centrale (/prov/cti). Betrouwbaarder/sneller dan de SIP-presence,
     * die na een gesprek lang grijs kan blijven. Geeft een map toestelnummer -> status.
     */
    suspend fun colleaguePresence(): Map<String, PhoneManager.Presence>? {
        val url = ctiUrl("cti") ?: return null
        return runCatching {
            val json = withContext(Dispatchers.IO) { fetch(url) }
            val o = JSONObject(json)
            o.optJSONObject("status")?.let { dnd.value = it.optBoolean("dnd", dnd.value) }
            val arr = o.optJSONArray("colleagues") ?: return@runCatching emptyMap<String, PhoneManager.Presence>()
            val m = HashMap<String, PhoneManager.Presence>()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                m[c.optString("ext")] = when (c.optString("status")) {
                    "dnd" -> PhoneManager.Presence.DND
                    "ingesprek" -> PhoneManager.Presence.BUSY
                    else -> PhoneManager.Presence.AVAILABLE
                }
            }
            m
        }.getOrNull()
    }

    init {
        val raw = prefs.getString("config", null)
        val src = prefs.getString("source", null)
        if (raw != null && src != null) config.value = runCatching { ProvisioningConfig.parse(raw, src) }.getOrNull()
        active.value = config.value
        recompute()
        watchNetwork()
    }

    /** Opnieuw bepalen of de app rechtstreeks met de centrale kan of via het tussenstation moet. */
    fun recompute() {
        chooseJob?.cancel()
        chooseJob = scope.launch {
            val cfg = config.value
            active.value = if (cfg == null) null else chooseEffective(cfg)
        }
    }

    private suspend fun chooseEffective(cfg: ProvisioningConfig): ProvisioningConfig {
        val d = cfg.direct ?: return cfg          // geen tussenstation: niets te kiezen
        return if (reachable(d.probeHost, d.probePort))
            cfg.copy(domain = d.domain, port = d.port, transport = d.transport, phonebook = d.phonebook)   // rechtstreeks (wifi/VPN)
        else cfg                                   // via het tussenstation
    }

    private suspend fun reachable(host: String, port: Int, timeoutMs: Int = 1500): Boolean = withContext(Dispatchers.IO) {
        if (host.isBlank() || port <= 0) return@withContext false
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true } }.getOrDefault(false)
    }

    private fun watchNetwork() {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = recompute()
            override fun onLost(network: Network) = recompute()
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    suspend fun setup(text: String) {
        val link = SetupLink.parse(text)
        if (link == null) {
            error.value = "Dit is geen QR-code van de telefooncentrale."
            return
        }
        setup(link)
    }

    suspend fun setup(link: SetupLink, quiet: Boolean = false) {
        busy.value = true
        if (!quiet) error.value = null
        try {
            val json = withContext(Dispatchers.IO) { fetch(link.configUrl()) }
            val cfg = ProvisioningConfig.parse(json, link.server)
            prefs.edit().putString("config", json).putString("source", link.server).apply()
            config.value = cfg
            recompute()
        } catch (e: Exception) {
            if (!quiet) error.value = when (e) {
                is java.net.ConnectException, is java.net.SocketTimeoutException, is java.net.UnknownHostException ->
                    "De centrale is niet bereikbaar. Zit je op de wifi van kantoor?"
                else -> e.message ?: "Instellen mislukt"
            }
        } finally {
            busy.value = false
        }
    }

    /** Stil verversen bij dezelfde centrale. */
    suspend fun refresh() {
        val c = config.value ?: return
        setup(SetupLink(c.source, c.sipUser, c.sipPassword), quiet = true)
    }

    fun logout() {
        prefs.edit().clear().apply()
        config.value = null
        active.value = null
    }

    private fun fetch(url: String): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 10000
        con.readTimeout = 15000
        con.setRequestProperty("Accept", "application/json")
        val code = con.responseCode
        val body = (if (code in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code != 200) {
            val msg = runCatching { JSONObject(body).optString("error") }.getOrNull()
            throw IllegalStateException(if (!msg.isNullOrEmpty()) msg else "De centrale antwoordt met fout $code.")
        }
        return body
    }
}
