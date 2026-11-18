package com.welivethisway.orusage.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.welivethisway.orusage.ui.screens.BreakdownScreen
import com.welivethisway.orusage.ui.screens.DashboardScreen
import com.welivethisway.orusage.ui.screens.KeysScreen
import com.welivethisway.orusage.ui.screens.MistralScreen
import com.welivethisway.orusage.ui.screens.SetupScreen
import com.welivethisway.orusage.ui.screens.SettingsScreen
import com.welivethisway.orusage.ui.theme.ORUsageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ORUsageTheme {
                AppRoot()
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("Overview", Icons.Filled.Home),
    KEYS("Keys", Icons.Filled.Lock),
    BREAKDOWN("Breakdown", Icons.Filled.List),
    MISTRAL("Mistral", Icons.AutoMirrored.Filled.Send),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun AppRoot(vm: AppViewModel = viewModel(
    factory = AppViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
)) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD.name) }
    // Set when the user chooses "Mistral only" from setup: no OpenRouter key, so its screens
    // would be empty. Rather than show three blank tabs, only offer the ones with data.
    var mistralOnly by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    // Errors and confirmations both land in one snackbar so the UI never grows two competing banners.
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHost.showSnackbar(it)
            vm.dismissError()
        }
    }
    LaunchedEffect(state.banner) {
        state.banner?.let {
            snackbarHost.showSnackbar(it)
            vm.dismissBanner()
        }
    }

    val openRouterConnected = state.isConnected
    if (!openRouterConnected && !state.isMistralConnected && !mistralOnly) {
        SetupScreen(vm = vm, state = state, onMistralOnly = {
            mistralOnly = true
            tab = Tab.MISTRAL.name
        })
        return
    }

    val tabs: List<Tab> = if (openRouterConnected) Tab.entries.toList()
    else listOf(Tab.MISTRAL, Tab.SETTINGS)
    val current = tabs.firstOrNull { it.name == tab } ?: tabs.first()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { entry ->
                    NavigationBarItem(
                        selected = current == entry,
                        onClick = { tab = entry.name },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        when (current) {
            Tab.DASHBOARD -> DashboardScreen(vm, state, modifier)
            Tab.KEYS -> KeysScreen(vm, state, modifier)
            Tab.BREAKDOWN -> BreakdownScreen(vm, state, modifier)
            Tab.MISTRAL -> MistralScreen(vm, state, modifier)
            Tab.SETTINGS -> SettingsScreen(vm, state, modifier)
        }
    }
}
