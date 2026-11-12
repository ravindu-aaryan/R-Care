package com.refluxcare.app.meditation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class MeditationSound(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val isCustom: Boolean = false
)

object MeditationSounds {
    const val SILENCE = "silence"
    internal const val CUSTOM_PREFIX = "custom:"

    private const val TAG = "MeditationSounds"
    private const val PREFS = "meditation_sounds"
    private const val KEY_CUSTOM = "custom_tracks"

    val builtIn: List<MeditationSound> = listOf(
        MeditationSound(SILENCE, "Silence", "Just the timer and bell", "🤫"),
        MeditationSound("rain", "Rain", "Soft, steady rainfall", "🌧️"),
        MeditationSound("ocean", "Ocean waves", "Slow waves rolling onto the shore", "🌊"),
        MeditationSound("bowls", "Singing bowls", "Resonant bowls struck every few breaths", "🔔"),
        MeditationSound("om", "Om drone", "A warm, low chanting drone", "🕉️"),
        MeditationSound("brown", "Brown noise", "Deep, soft, steady noise", "🟤"),
        MeditationSound("chimes", "Wind chimes", "Gentle chimes in a light breeze", "🎐"),
        MeditationSound("forest", "Forest", "Rustling breeze and distant birdsong", "🌲"),
        MeditationSound("binaural", "Deep calm hum", "Theta binaural tone, best with headphones", "🎧")
    )

    fun customTracks(context: Context): List<MeditationSound> =
        load(context).map { (uri, name) ->
            MeditationSound(
                id = CUSTOM_PREFIX + uri,
                name = name,
                description = "Your music",
                emoji = "🎵",
                isCustom = true
            )
        }

    @Synchronized
    fun addCustom(context: Context, uri: Uri, name: String) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Log.w(TAG, "Could not take persistable permission for $uri", e)
        }
        val uriString = uri.toString()
        val cleanName = name.trim().ifBlank { "My track" }
        val entries = load(context).filterNot { it.first == uriString } + (uriString to cleanName)
        save(context, entries)
    }

    @Synchronized
    fun removeCustom(context: Context, id: String) {
        val uriString = id.removePrefix(CUSTOM_PREFIX)
        val entries = load(context)
        if (entries.none { it.first == uriString }) return
        save(context, entries.filterNot { it.first == uriString })
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not release permission for $uriString", e)
        }
    }

    fun all(context: Context): List<MeditationSound> = builtIn + customTracks(context)

    fun find(context: Context, id: String): MeditationSound? {
        builtIn.firstOrNull { it.id == id }?.let { return it }
        if (!id.startsWith(CUSTOM_PREFIX)) return null
        return customTracks(context).firstOrNull { it.id == id }
    }

    private fun load(context: Context): List<Pair<String, String>> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Pair<String, String>>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val uri = o.optString("uri", "")
                if (uri.isBlank()) continue
                out.add(uri to o.optString("name", "My track"))
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "Corrupt custom track list", e)
            emptyList()
        }
    }

    private fun save(context: Context, entries: List<Pair<String, String>>) {
        val arr = JSONArray()
        for ((uri, name) in entries) {
            arr.put(JSONObject().put("uri", uri).put("name", name))
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM, arr.toString())
            .apply()
    }
}
