package org.verco.telefoon

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/** Belgeschiedenis (laatste 100), op de telefoon. */
data class RecentCall(val kind: String, val number: String, val name: String, val time: Long, val duration: Int)

class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("verco_recent", Context.MODE_PRIVATE)
    val calls = MutableStateFlow<List<RecentCall>>(emptyList())
    val unseenMissed = MutableStateFlow(0)

    init {
        calls.value = runCatching {
            val a = JSONArray(prefs.getString("list", "[]"))
            (0 until a.length()).map { a.getJSONObject(it) }.map {
                RecentCall(it.getString("k"), it.getString("n"), it.optString("m"), it.getLong("t"), it.optInt("d"))
            }
        }.getOrDefault(emptyList())
    }

    fun add(c: RecentCall) {
        if (c.number.isEmpty()) return
        calls.value = (listOf(c) + calls.value).take(100)
        if (c.kind == "missed") unseenMissed.value += 1
        save()
    }

    fun clear() {
        calls.value = emptyList(); unseenMissed.value = 0; save()
    }

    private fun save() {
        val a = JSONArray()
        calls.value.forEach {
            a.put(JSONObject().put("k", it.kind).put("n", it.number).put("m", it.name).put("t", it.time).put("d", it.duration))
        }
        prefs.edit().putString("list", a.toString()).apply()
    }
}

/** Telefoonboek van de centrale (vCard via de CardDAV-link). */
data class Contact(val name: String, val numbers: List<String>)

object Phonebook {
    suspend fun load(url: String): List<Contact> = withContext(Dispatchers.IO) {
        if (url.isEmpty()) return@withContext emptyList()
        val text = runCatching { URL(url).readText() }.getOrDefault("")
        parse(text)
    }

    fun parse(text: String): List<Contact> {
        val out = mutableListOf<Contact>()
        var name = ""
        var nums = mutableListOf<String>()
        text.replace("\r\n ", "").replace("\n ", "").lines().map { it.trim() }.forEach { line ->
            when {
                line == "BEGIN:VCARD" -> { name = ""; nums = mutableListOf() }
                line.startsWith("FN:") -> name = line.substring(3).replace("\\,", ",").replace("\\;", ";")
                line.startsWith("TEL") && line.contains(":") -> nums.add(line.substringAfter(":"))
                line == "END:VCARD" && name.isNotEmpty() && !name.startsWith("★") -> out.add(Contact(name, nums.toList()))
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }
}
