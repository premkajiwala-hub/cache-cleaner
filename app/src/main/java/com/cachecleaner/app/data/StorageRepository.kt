package com.cachecleaner.app.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager

/**
 * Reads per-app storage sizes through Android's StorageStatsManager.
 * Requires the user to grant "Usage access" to this app once.
 */
class StorageRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val statsManager = context.getSystemService(StorageStatsManager::class.java)

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun deviceStorage(): DeviceStorage? = try {
        DeviceStorage(
            totalBytes = statsManager.getTotalBytes(StorageManager.UUID_DEFAULT),
            freeBytes = statsManager.getFreeBytes(StorageManager.UUID_DEFAULT),
        )
    } catch (e: Exception) {
        null
    }

    /** Scans every installed app. Slow-ish (a few hundred ms); call off the main thread. */
    fun scanApps(): List<AppEntry> {
        val user = Process.myUserHandle()
        return installedApps().mapNotNull { info ->
            try {
                val stats = statsManager.queryStatsForPackage(info.storageUuid, info.packageName, user)
                val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                    info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                AppEntry(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    isSystem = isSystem,
                    appBytes = stats.appBytes,
                    // StorageStats.dataBytes includes the cache; Settings shows them apart.
                    dataBytes = (stats.dataBytes - stats.cacheBytes).coerceAtLeast(0),
                    cacheBytes = stats.cacheBytes,
                )
            } catch (e: Exception) {
                null // app uninstalled mid-scan, or stats unavailable for it
            }
        }
    }

    private fun installedApps(): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
}
