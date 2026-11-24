package com.cachecleaner.app.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

enum class ShizukuStatus {
    /** Shizuku app is not installed. */
    NOT_INSTALLED,

    /** Installed but its service isn't started (normal after every reboot). */
    NOT_RUNNING,

    /** Running, but this app hasn't been allowed to use it yet. */
    NO_PERMISSION,

    /** Ready to run commands. */
    READY,
}

data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String)

/**
 * Normal apps can't clear other apps' caches. Shizuku runs commands with the
 * same rights as `adb shell`, which is allowed to (`pm trim-caches`).
 */
object ShizukuManager {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val PERMISSION_REQUEST_CODE = 4242

    /**
     * Asking Android to free an impossible amount of space makes it delete
     * every app's cache. App data, logins and files are not touched.
     */
    const val CLEAR_ALL_CACHES_COMMAND = "pm trim-caches 999G"

    fun status(context: Context): ShizukuStatus {
        val running = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
        if (!running) {
            return if (isInstalled(context)) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
        }
        return try {
            if (!Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            ) {
                ShizukuStatus.READY
            } else {
                ShizukuStatus.NO_PERMISSION
            }
        } catch (e: Throwable) {
            ShizukuStatus.NOT_RUNNING
        }
    }

    fun requestPermission() {
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    // Shizuku.newProcess is hidden in API 13, but remains the simplest way to run a
    // one-off shell command; the alternative (a bound UserService) is overkill here.
    private val newProcess: Method by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
    }

    /** Runs [command] through `sh -c` with shell privileges. Blocking; call off the main thread. */
    fun exec(command: String): ExecResult {
        val process = try {
            newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        } catch (e: InvocationTargetException) {
            throw IllegalStateException(e.targetException.message ?: "Shizuku failed to start the command", e)
        }
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return ExecResult(exitCode, stdout.trim(), stderr.trim())
    }
}
