package com.invictus.abhyas.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.prefs.SettingsStore
import com.invictus.abhyas.ui.screens.CreateScreen
import com.invictus.abhyas.ui.screens.HomeScreen
import com.invictus.abhyas.ui.screens.LibraryScreen
import com.invictus.abhyas.ui.screens.ProgressScreen
import com.invictus.abhyas.ui.screens.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Library("library", "Library", Icons.Outlined.LibraryBooks),
    Create("create", "Create", Icons.Outlined.Add),
    Progress("progress", "Progress", Icons.Outlined.Insights),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AbhyasRoot(database: AbhyasDatabase, settingsStore: SettingsStore) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { tab ->
                    ShortNavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Home.route) { HomeScreen(database) }
            composable(Tab.Library.route) { LibraryScreen(database) }
            composable(Tab.Create.route) { CreateScreen() }
            composable(Tab.Progress.route) { ProgressScreen() }
            composable(Tab.Settings.route) { SettingsScreen(settingsStore) }
        }
    }
}
