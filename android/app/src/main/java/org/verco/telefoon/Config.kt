package org.verco.telefoon

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Instellingen die de centrale geeft op /prov/vt (versie 1). */
data class Colleague(val name: String, val ext: String)
data class Function(val name: String, val code: String)

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
    val source: String,
    val raw: String,
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
                source = source,
                raw = json,
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
    private val prefs = context.getSharedPreferences("verco", Context.MODE_PRIVATE)
    val config = MutableStateFlow<ProvisioningConfig?>(null)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init {
        val raw = prefs.getString("config", null)
        val src = prefs.getString("source", null)
        if (raw != null && src != null) config.value = runCatching { ProvisioningConfig.parse(raw, src) }.getOrNull()
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
