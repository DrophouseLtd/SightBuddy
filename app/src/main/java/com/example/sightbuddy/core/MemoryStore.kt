package com.example.sightbuddy.core

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One message of a saved chat. [kind] is the TranscriptEntry kind's name. */
data class MemoryEntry(val kind: String, val text: String)

/** A saved chat. */
data class Memory(
    val id: String,
    val name: String,
    val feature: String,
    val createdAt: Long,
    val entries: List<MemoryEntry>,
)

/**
 * Saved chats ("Memories"): one small JSON file each, in the app's private
 * storage. Never uploaded, and left out of the cloud backup (they can hold
 * letters and labels read aloud); a phone-to-phone transfer on Android 12 and
 * later still carries them. See res/xml/data_extraction_rules.xml.
 */
class MemoryStore(context: Context) {

    private val dir = File(context.filesDir, "memories").apply { mkdirs() }

    private val _memories = MutableStateFlow(load())

    /** Newest first. */
    val memories = _memories.asStateFlow()

    fun names(): List<String> = _memories.value.map { it.name }

    fun save(name: String, feature: String, entries: List<MemoryEntry>): Memory {
        val memory = Memory(UUID.randomUUID().toString(), name, feature, System.currentTimeMillis(), entries)
        write(memory)
        _memories.value = listOf(memory) + _memories.value
        return memory
    }

    fun rename(id: String, name: String) {
        val memory = _memories.value.firstOrNull { it.id == id } ?: return
        val renamed = memory.copy(name = name)
        write(renamed)
        _memories.value = _memories.value.map { if (it.id == id) renamed else it }
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        _memories.value = _memories.value.filterNot { it.id == id }
    }

    private fun write(memory: Memory) {
        val json = JSONObject()
            .put("id", memory.id)
            .put("name", memory.name)
            .put("feature", memory.feature)
            .put("createdAt", memory.createdAt)
            .put("entries", JSONArray().apply {
                memory.entries.forEach { put(JSONObject().put("kind", it.kind).put("text", it.text)) }
            })
        // Write then rename, so a crash mid-write never leaves half a file.
        val tmp = File(dir, "${memory.id}.json.tmp")
        val target = File(dir, "${memory.id}.json")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(target)) {
            // Should not happen in app storage; a copy still saves the chat.
            Log.w("MemoryStore", "Could not move ${tmp.name} into place; copying it")
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun load(): List<Memory> =
        (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
            .mapNotNull { file ->
                try {
                    val json = JSONObject(file.readText())
                    val entries = json.getJSONArray("entries")
                    Memory(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        feature = json.optString("feature"),
                        createdAt = json.getLong("createdAt"),
                        entries = (0 until entries.length()).map {
                            val e = entries.getJSONObject(it)
                            MemoryEntry(e.getString("kind"), e.getString("text"))
                        },
                    )
                } catch (e: Exception) {
                    Log.w("MemoryStore", "Skipping unreadable ${file.name}", e)
                    null
                }
            }
            .sortedByDescending { it.createdAt }
}

/** Rules for chat names: tidy what the model returns, and keep names distinct. */
object MemoryNames {

    /** Names this similar (1.0 = identical) count as the same name. */
    const val SIMILAR = 0.8

    private const val MAX_LENGTH = 48

    /** A model's reply as a name: first line, no quotes, labels or final full stop. */
    fun clean(raw: String): String {
        var name = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
        name = name.removePrefix("Title:").removePrefix("title:").trim()
        name = name.trim('"', '\'', '*', '“', '”', '‘', '’', '#', ' ').removeSuffix(".").trim()
        if (name.length > MAX_LENGTH) name = name.take(MAX_LENGTH).substringBeforeLast(' ').trim()
        return name
    }

    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() && y.isEmpty()) return 1.0
        val longest = maxOf(x.length, y.length)
        return 1.0 - levenshtein(x, y).toDouble() / longest
    }

    /** True when [name] is identical or at least [SIMILAR] alike to one already [taken]. */
    fun clashes(name: String, taken: Collection<String>): Boolean =
        taken.any { similarity(name, it) >= SIMILAR }

    /** Last resort when the model keeps repeating itself: "Name 2", "Name 3"... */
    fun numbered(name: String, taken: Collection<String>): String {
        val lower = taken.map { normalize(it) }.toSet()
        var n = 2
        while (normalize("$name $n") in lower) n++
        return "$name $n"
    }

    private fun normalize(s: String) =
        s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.split(' ').filter { it.isNotEmpty() }.joinToString(" ")

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
