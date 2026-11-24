package com.cachecleaner.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cachecleaner.app.data.AppEntry
import com.cachecleaner.app.data.CleanupRecord
import com.cachecleaner.app.data.DeviceStorage
import com.cachecleaner.app.data.HistoryStore
import com.cachecleaner.app.data.StorageRepository
import com.cachecleaner.app.shizuku.ShizukuManager
import com.cachecleaner.app.shizuku.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class CleanResult(
    val freedBytes: Long,
    val appsCleaned: Int,
)

data class UiState(
    val scanning: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val shizuku: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val apps: List<AppEntry> = emptyList(),
    val storage: DeviceStorage? = null,
    val clearing: Boolean = false,
    val lastResult: CleanResult? = null,
    val error: String? = null,
    val history: List<CleanupRecord> = emptyList(),
) {
    val totalCacheBytes: Long get() = apps.sumOf { it.cacheBytes }
    val totalDataBytes: Long get() = apps.sumOf { it.dataBytes }
    val totalAppBytes: Long get() = apps.sumOf { it.appBytes }
    val appsWithCache: Int get() = apps.count { it.cacheBytes > 0 }
    val canClear: Boolean get() = shizuku == ShizukuStatus.READY && hasUsageAccess && !clearing
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StorageRepository(app)
    private val historyStore = HistoryStore(app)

    private val _state = MutableStateFlow(UiState(history = historyStore.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshStatus() }
    private val binderDead = Shizuku.OnBinderDeadListener { refreshStatus() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshStatus() }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refreshStatus()
        scan()
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    /** Called when the screen comes back, e.g. after granting Usage access in Settings. */
    fun onResume() {
        val hadAccess = _state.value.hasUsageAccess
        refreshStatus()
        if (!hadAccess && _state.value.hasUsageAccess) scan()
    }

    fun refreshStatus() {
        _state.update {
            it.copy(
                hasUsageAccess = repo.hasUsageAccess(),
                shizuku = ShizukuManager.status(getApplication()),
            )
        }
    }

    fun requestShizukuPermission() {
        try {
            ShizukuManager.requestPermission()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Shizuku isn't running: ${e.message}") }
        }
    }

    fun scan() {
        if (!repo.hasUsageAccess()) {
            refreshStatus()
            return
        }
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            val (apps, storage) = withContext(Dispatchers.IO) { repo.scanApps() to repo.deviceStorage() }
            _state.update { it.copy(scanning = false, apps = apps, storage = storage) }
        }
    }

    fun clearAllCaches() {
        if (!_state.value.canClear) return
        viewModelScope.launch {
            scanJob?.join()
            _state.update { it.copy(clearing = true, error = null, lastResult = null) }
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val before = repo.scanApps().associate { it.packageName to it.cacheBytes }

                    val exec = ShizukuManager.exec(ShizukuManager.CLEAR_ALL_CACHES_COMMAND)
                    if (exec.exitCode != 0) {
                        val reason = exec.stderr.ifBlank { exec.stdout }.ifBlank { "exit code ${exec.exitCode}" }
                        error("Clearing failed: $reason")
                    }

                    val after = repo.scanApps()
                    var freed = 0L
                    var cleaned = 0
                    for (entry in after) {
                        val drop = (before[entry.packageName] ?: 0L) - entry.cacheBytes
                        if (drop > 0) {
                            freed += drop
                            cleaned++
                        }
                    }
                    Triple(after, repo.deviceStorage(), CleanResult(freed, cleaned))
                }
                val (apps, storage, result) = outcome
                val history = historyStore.add(
                    CleanupRecord(System.currentTimeMillis(), result.freedBytes, result.appsCleaned),
                )
                _state.update {
                    it.copy(
                        clearing = false,
                        apps = apps,
                        storage = storage,
                        lastResult = result,
                        history = history,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(clearing = false, error = e.message ?: e.toString()) }
                refreshStatus()
            }
        }
    }

    fun dismissMessages() {
        _state.update { it.copy(lastResult = null, error = null) }
    }

    fun clearHistory() {
        historyStore.clear()
        _state.update { it.copy(history = emptyList()) }
    }
}
