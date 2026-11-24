package com.cachecleaner.app.data

/**
 * Storage used by one installed app, split the same way Android's
 * Settings → Apps → Storage usage screen splits it.
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    /** "App" in Settings: APK, libraries, compiled code. */
    val appBytes: Long,
    /** "Data" in Settings: databases, media, files (cache excluded). */
    val dataBytes: Long,
    /** "Cache" in Settings: internal + external cache dirs. */
    val cacheBytes: Long,
) {
    /** "Total" in Settings. */
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

data class DeviceStorage(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = totalBytes - freeBytes
}

data class CleanupRecord(
    val timestamp: Long,
    val freedBytes: Long,
    val appsCleaned: Int,
)

enum class SortBy(val label: String) {
    CACHE("Cache"),
    DATA("Data"),
    TOTAL("Total"),
    NAME("Name"),
}
