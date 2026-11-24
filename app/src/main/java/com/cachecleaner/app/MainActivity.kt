package com.cachecleaner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cachecleaner.app.ui.AppsScreen
import com.cachecleaner.app.ui.HistoryScreen
import com.cachecleaner.app.ui.OverviewScreen
import com.cachecleaner.app.ui.theme.CacheCleanerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CacheCleanerTheme { CacheCleanerApp() }
        }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Filled.Home),
    APPS("Apps", Icons.AutoMirrored.Filled.List),
    HISTORY("History", Icons.Filled.DateRange),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheCleanerApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            Box {
                TopAppBar(
                    title = { Text(if (tab == 0) "Cache Cleaner" else Tab.entries[tab].title) },
                    actions = {
                        IconButton(onClick = { vm.refreshStatus(); vm.scan() }, enabled = !state.scanning && !state.clearing) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Rescan")
                        }
                    },
                )
                if (state.scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                }
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        when (Tab.entries[tab]) {
            Tab.OVERVIEW -> OverviewScreen(
                state = state,
                contentPadding = padding,
                onClearAll = vm::clearAllCaches,
                onRequestShizukuPermission = vm::requestShizukuPermission,
                onDismissMessage = vm::dismissMessages,
            )
            Tab.APPS -> AppsScreen(state, padding)
            Tab.HISTORY -> HistoryScreen(state, padding, vm::clearHistory)
        }
    }
}
