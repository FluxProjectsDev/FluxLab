package com.febricahyaa.fluxlab

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private val Gold = Color(0xFFE0B85B)
private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF332A12),
    secondary = Color(0xFFC9B98D),
    background = Color(0xFF0E0D0A),
    surface = Color(0xFF17140E),
    surfaceVariant = Color(0xFF242016),
    onBackground = Color(0xFFF1EBDD),
    onSurface = Color(0xFFF1EBDD),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF705B14),
    secondary = Color(0xFF6A5E3D),
    background = Color(0xFFFFF9EE),
    surface = Color(0xFFFFF9EE),
    surfaceVariant = Color(0xFFF2E9D2),
)

private data class Destination(val route: String, val label: Int, val icon: ImageVector)

private val destinations = listOf(
    Destination("overview", R.string.overview, Icons.Default.Dashboard),
    Destination("tests", R.string.tests, Icons.Default.Science),
    Destination("sessions", R.string.sessions, Icons.Default.Assessment),
    Destination("reports", R.string.reports, Icons.Default.Description),
    Destination("settings", R.string.settings, Icons.Default.Settings),
)

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
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 700.dp) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    destinations.forEach { destination ->
                        NavigationRailItem(
                            selected = current == destination.route,
                            onClick = { navigate(destination.route) },
                            icon = { Icon(destination.icon, stringResource(destination.label)) },
                            label = { androidx.compose.material3.Text(stringResource(destination.label)) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { FluxLabNavHost(navigation, model) }
            }
        } else {
            Scaffold(
                bottomBar = {
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
                },
            ) { padding -> Box(Modifier.padding(padding)) { FluxLabNavHost(navigation, model) } }
        }
    }
}

@Composable
private fun FluxLabNavHost(navigation: androidx.navigation.NavHostController, model: AppViewModel) {
    NavHost(navigation, startDestination = "overview") {
        composable("overview") { OverviewScreen(model) }
        composable("tests") { TestsScreen(model) }
        composable("sessions") { SessionsScreen(model) }
        composable("reports") { ReportsScreen(model) }
        composable("settings") { SettingsScreen(model) }
    }
}
