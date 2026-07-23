package com.febricahyaa.fluxlab

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class Destination(val route: String, val label: Int, val icon: ImageVector)

private val destinations = listOf(
    Destination("overview", R.string.overview, Icons.Default.Dashboard),
    Destination("tests", R.string.tests, Icons.Default.Science),
    Destination("sessions", R.string.sessions, Icons.Default.Assessment),
    Destination("reports", R.string.reports, Icons.Default.Description),
    Destination("settings", R.string.settings, Icons.Default.Settings),
)

internal fun isTopLevelRoute(route: String?): Boolean = destinations.any { it.route == route }

@Composable
fun FluxLabRoot() {
    val application = LocalContext.current.applicationContext as FluxLabApplication
    val model: AppViewModel = viewModel(factory = AppViewModel.Factory(application))
    val settings by model.settings.collectAsStateWithLifecycle()
    val dark = when (settings.theme) {
        ThemeSetting.SYSTEM -> isSystemInDarkTheme()
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (settings.colorStyle == ColorStyle.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) FluxDarkColorScheme else FluxLightColorScheme
    MaterialTheme(colorScheme = colors, typography = FluxTypography.material, shapes = FluxShapes.material) {
        FluxLabNavigation(model)
    }
}

@Composable
private fun FluxLabNavigation(model: AppViewModel) {
    val navigation = rememberNavController()
    val entry by navigation.currentBackStackEntryAsState()
    val current = entry?.destination?.route ?: "overview"
    val navigate: (String) -> Unit = { route ->
        navigation.navigate(route) {
            popUpTo(navigation.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val isTopLevelDestination = isTopLevelRoute(current)
    Scaffold(
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination.route,
                            onClick = { navigate(destination.route) },
                            icon = { Icon(destination.icon, stringResource(destination.label)) },
                            label = { androidx.compose.material3.Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { FluxLabNavHost(navigation, model) }
    }
}

@Composable
private fun FluxLabNavHost(navigation: androidx.navigation.NavHostController, model: AppViewModel) {
    NavHost(navigation, startDestination = "overview") {
        composable("overview") { OverviewScreen(model) { route -> navigation.navigate(route) } }
        composable("tests") { TestsScreen(model) { route -> navigation.navigate(route) } }
        composable("sessions") { SessionsScreen(model) { route -> navigation.navigate(route) } }
        composable("sessions/{sessionId}") { entry ->
            SessionDetailScreen(model, onBack = { navigation.popBackStack() }, sessionId = entry.arguments?.getString("sessionId"))
        }
        composable("reports") { ReportsScreen(model) }
        composable("settings") { SettingsScreen(model) }
        composable("benchmark/active") { ActiveBenchmarkScreen(model, onBack = { navigation.popBackStack() }) }
        composable("overview/cpu") { MetricDetailScreen(model, MetricDetailKind.CPU, onBack = { navigation.popBackStack() }) }
        composable("overview/gpu") { MetricDetailScreen(model, MetricDetailKind.GPU, onBack = { navigation.popBackStack() }) }
        composable("overview/memory") { MetricDetailScreen(model, MetricDetailKind.MEMORY, onBack = { navigation.popBackStack() }) }
        composable("overview/thermal") { MetricDetailScreen(model, MetricDetailKind.THERMAL, onBack = { navigation.popBackStack() }) }
        composable("overview/battery") { BatteryDetailScreen(model, onBack = { navigation.popBackStack() }) }
        composable("overview/storage") { StorageDetailScreen(model, onBack = { navigation.popBackStack() }) }
        composable("overview/flux") { MetricDetailScreen(model, MetricDetailKind.FLUX, onBack = { navigation.popBackStack() }) }
        composable("overview/synthesiscore") { MetricDetailScreen(model, MetricDetailKind.SYNTHESIS, onBack = { navigation.popBackStack() }) }
        composable("overview/profile") { MetricDetailScreen(model, MetricDetailKind.PROFILE, onBack = { navigation.popBackStack() }) }
        composable("overview/latest-session") { SessionDetailScreen(model, onBack = { navigation.popBackStack() }) }
    }
}
