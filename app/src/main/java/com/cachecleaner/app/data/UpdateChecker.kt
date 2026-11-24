package com.cachecleaner.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val downloadUrl: String)

/** Asks GitHub for the latest public release of this app. */
object UpdateChecker {

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/premkajiwala-hub/cache-cleaner/releases/latest"

    /** Returns the newer release, or null if up to date / offline / anything fails. Blocking. */
    fun check(currentVersion: String): UpdateInfo? {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CacheCleaner-Android")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val latest = json.getString("tag_name").removePrefix("v")
            if (!isNewer(latest, currentVersion)) return null

            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }
            return UpdateInfo(latest, apkUrl ?: json.getString("html_url"))
        } finally {
            connection.disconnect()
        }
    }

    /** Compares dotted versions numerically: 1.10 > 1.9, 1.1 == 1.1.0. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
