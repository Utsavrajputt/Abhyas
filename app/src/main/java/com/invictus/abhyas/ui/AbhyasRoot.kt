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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.ui.screens.CreateScreen
import com.invictus.abhyas.ui.screens.HomeScreen
import com.invictus.abhyas.ui.library.BrowseScreen
import com.invictus.abhyas.ui.library.LibraryScreen
import com.invictus.abhyas.ui.result.ResultScreen
import com.invictus.abhyas.ui.session.SessionScreen
import com.invictus.abhyas.ui.setup.SetupScreen
import com.invictus.abhyas.ui.progress.ProgressScreen
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
fun AbhyasRoot(app: AbhyasApp) {
    val database = app.database
    val settingsStore = app.settingsStore
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            val showBar = Tab.entries.any { it.route == currentRoute } || currentRoute == "browse" || currentRoute == "setup"
            if (showBar) ShortNavigationBar {
                Tab.entries.forEach { tab ->
                    ShortNavigationBarItem(
                        selected = currentRoute == tab.route || (tab == Tab.Library && currentRoute == "browse"),
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
            composable(Tab.Home.route) {
                HomeScreen(
                    app,
                    onOpenSession = { navController.navigate("session/$it") },
                    onImportBank = { navController.navigate(Tab.Library.route) },
                )
            }
            composable(Tab.Library.route) { LibraryScreen(app, onBrowse = { navController.navigate("browse") }) }
            composable("browse") { BrowseScreen(app) }
            composable(Tab.Create.route) {
                CreateScreen(
                    onCustomPractice = { navController.navigate("setup") },
                    onImportBank = { navController.navigate(Tab.Library.route) },
                )
            }
            composable("setup") { SetupScreen(app, onStarted = { navController.navigate("session/$it") { popUpTo("setup") { inclusive = true } } }) }
            composable("session/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                SessionScreen(
                    app, id,
                    onFinished = {
                        navController.navigate("result/$id") { popUpTo("session/{id}") { inclusive = true } }
                    },
                    onExit = { navController.popBackStack() },
                )
            }
            composable("result/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                ResultScreen(app, id, onDone = { navController.popBackStack() })
            }
            composable(Tab.Progress.route) {
                ProgressScreen(
                    app,
                    onOpenResult = { navController.navigate("result/$it") },
                    onOpenSession = { navController.navigate("session/$it") },
                )
            }
            composable(Tab.Settings.route) { SettingsScreen(settingsStore) }
        }
    }
}
