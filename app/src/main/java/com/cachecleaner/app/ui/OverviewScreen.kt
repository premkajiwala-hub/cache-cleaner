package com.cachecleaner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cachecleaner.app.UiState
import com.cachecleaner.app.data.AppEntry
import com.cachecleaner.app.shizuku.ShizukuManager
import com.cachecleaner.app.shizuku.ShizukuStatus

private const val DATA_HOG_THRESHOLD = 1_000_000_000L // 1 GB

@Composable
fun OverviewScreen(
    state: UiState,
    contentPadding: PaddingValues,
    onClearAll: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val dataHogs = state.apps
        .filter { it.dataBytes >= DATA_HOG_THRESHOLD }
        .sortedByDescending { it.dataBytes }
        .take(5)

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.hasUsageAccess || state.shizuku != ShizukuStatus.READY) {
            item { SetupCard(state, onRequestShizukuPermission) }
        }
        state.error?.let { msg ->
            item { MessageCard(msg, isError = true, onDismiss = onDismissMessage) }
        }
        state.lastResult?.let { result ->
            item {
                val context = LocalContext.current
                MessageCard(
                    text = if (result.freedBytes > 0) {
                        "Freed ${context.formatSize(result.freedBytes)} from ${result.appsCleaned} apps"
                    } else {
                        "Caches were already empty — nothing to free"
                    },
                    isError = false,
                    onDismiss = onDismissMessage,
                )
            }
        }
        item { CacheCard(state, onClearAll) }
        state.storage?.let { item { StorageCard(state) } }
        if (dataHogs.isNotEmpty()) {
            item { DataHogsCard(dataHogs) }
        }
    }
}

@Composable
private fun CacheCard(state: UiState, onClearAll: () -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Total cache",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                if (state.apps.isEmpty() && state.scanning) "…" else context.formatSize(state.totalCacheBytes),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "across ${state.appsWithCache} of ${state.apps.size} apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClearAll,
                enabled = state.canClear && state.apps.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.clearing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Clearing…")
                } else {
                    Text("Clear all caches", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Only cache is removed. Logins, chats, photos, game worlds and other app data stay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StorageCard(state: UiState) {
    val context = LocalContext.current
    val storage = state.storage ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Phone storage", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { storage.usedBytes.toFloat() / storage.totalBytes.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${context.formatSize(storage.usedBytes)} used · ${context.formatSize(storage.freeBytes)} free " +
                    "of ${context.formatSize(storage.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Used by apps", style = MaterialTheme.typography.labelLarge)
            SizeRow("App files", context.formatSize(state.totalAppBytes))
            SizeRow("Data", context.formatSize(state.totalDataBytes))
            SizeRow("Cache", context.formatSize(state.totalCacheBytes))
        }
    }
}

@Composable
private fun SizeRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DataHogsCard(hogs: List<AppEntry>) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Biggest space users", style = MaterialTheme.typography.titleMedium)
            Text(
                "This is app data, not cache, so clearing cache won't shrink it. " +
                    "Open the app to delete old media (e.g. WhatsApp → Settings → Storage and data → Manage storage).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hogs.forEach { app ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.packageName, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${context.formatSize(app.dataBytes)} data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { if (!context.launchApp(app.packageName)) context.openAppInfo(app.packageName) }) {
                        Text("Open")
                    }
                    TextButton(onClick = { context.openAppInfo(app.packageName) }) { Text("Info") }
                }
            }
        }
    }
}

@Composable
private fun SetupCard(state: UiState, onRequestShizukuPermission: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("One-time setup", style = MaterialTheme.typography.titleMedium)

            SetupStep(
                done = state.hasUsageAccess,
                title = "1. Usage access",
                body = "Lets the app read each app's storage sizes. Find Cache Cleaner in the list and turn it on.",
                action = "Grant",
                onAction = { context.openUsageAccessSettings() },
            )

            val (body, action, onAction) = when (state.shizuku) {
                ShizukuStatus.NOT_INSTALLED -> Triple(
                    "Install Shizuku (free) — it gives this app permission to clear other apps' caches without root.",
                    "Get Shizuku",
                    {
                        context.openUrl("https://play.google.com/store/apps/details?id=${ShizukuManager.SHIZUKU_PACKAGE}")
                    },
                )
                ShizukuStatus.NOT_RUNNING -> Triple(
                    "Shizuku is installed but stopped (it stops on every reboot). Open it and tap Start under " +
                        "\"Start via Wireless debugging\".",
                    "Open Shizuku",
                    { context.launchApp(ShizukuManager.SHIZUKU_PACKAGE); Unit },
                )
                ShizukuStatus.NO_PERMISSION -> Triple(
                    "Shizuku is running. Allow Cache Cleaner to use it.",
                    "Allow",
                    onRequestShizukuPermission,
                )
                ShizukuStatus.READY -> Triple("Shizuku is running and allowed.", null, {})
            }
            SetupStep(
                done = state.shizuku == ShizukuStatus.READY,
                title = "2. Shizuku",
                body = body,
                action = action,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun SetupStep(done: Boolean, title: String, body: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!done && action != null) {
                FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 6.dp)) { Text(action) }
            }
        }
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Filled.Warning else Icons.Filled.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
        }
    }
}
