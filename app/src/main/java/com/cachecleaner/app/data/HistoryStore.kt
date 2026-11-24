package com.cachecleaner.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Keeps a small on-device log of past clean-ups (SharedPreferences, newest first). */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<CleanupRecord> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                CleanupRecord(
                    timestamp = o.getLong("t"),
                    freedBytes = o.getLong("freed"),
                    appsCleaned = o.getInt("apps"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(record: CleanupRecord): List<CleanupRecord> {
        val updated = (listOf(record) + load()).take(MAX_RECORDS)
        val array = JSONArray()
        updated.forEach {
            array.put(
                JSONObject()
                    .put("t", it.timestamp)
                    .put("freed", it.freedBytes)
                    .put("apps", it.appsCleaned),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
        return updated
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "records"
        const val MAX_RECORDS = 200
    }
}
