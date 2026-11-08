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
import com.invictus.abhyas.ui.ncert.NcertPracticeScreen
import com.invictus.abhyas.ui.ncert.NcertScreen
import com.invictus.abhyas.ui.ncert.ReaderScreen
import com.invictus.abhyas.ui.pdf.PdfReportScreen
import com.invictus.abhyas.ui.pdf.PdfReviewScreen
import com.invictus.abhyas.ui.pdf.PdfStudioScreen
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
            val showBar = Tab.entries.any { it.route == currentRoute } || currentRoute == "browse" || currentRoute == "setup" || currentRoute == "pdfstudio" || currentRoute == "ncert" || currentRoute == "ncertpractice"
            if (showBar) ShortNavigationBar {
                Tab.entries.forEach { tab ->
                    ShortNavigationBarItem(
                        selected = currentRoute == tab.route || (tab == Tab.Library && currentRoute == "browse") || (tab == Tab.Create && currentRoute == "pdfstudio") || (tab == Tab.Library && currentRoute == "ncert") || (tab == Tab.Create && currentRoute == "ncertpractice"),
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
            composable(Tab.Library.route) {
                LibraryScreen(app, onBrowse = { navController.navigate("browse") }, onOpenNcert = { navController.navigate("ncert") })
            }
            composable("ncert") {
                NcertScreen(app, onOpenBook = { navController.navigate("reader/$it") }, onPractice = { navController.navigate("ncertpractice") })
            }
            composable("ncertpractice") {
                NcertPracticeScreen(app, onStarted = { navController.navigate("session/$it") { popUpTo("ncertpractice") { inclusive = true } } })
            }
            composable(
                "reader/{bookId}?page={page}&para={para}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("page") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("para") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                ReaderScreen(
                    app,
                    entry.arguments?.getString("bookId").orEmpty(),
                    entry.arguments?.getInt("page") ?: 0,
                    entry.arguments?.getInt("para") ?: -1,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("browse") { BrowseScreen(app) }
            composable(Tab.Create.route) {
                CreateScreen(
                    onCustomPractice = { navController.navigate("setup") },
                    onImportBank = { navController.navigate(Tab.Library.route) },
                    onImportPdf = { navController.navigate("pdfstudio") },
                    onNcertPractice = { navController.navigate("ncertpractice") },
                )
            }
            composable("pdfstudio") { PdfStudioScreen(app, onOpenJob = { navController.navigate("pdf/$it") }) }
            composable("pdf/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                PdfReviewScreen(app, id, onBack = { navController.popBackStack() }, onReport = { navController.navigate("pdf/$id/report") })
            }
            composable("pdf/{id}/report", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PdfReportScreen(app, entry.arguments?.getLong("id") ?: 0L, onBack = { navController.popBackStack() })
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
                ResultScreen(app, id, onDone = { navController.popBackStack() }, onOpenNcert = { b, p, q -> navController.navigate("reader/$b?page=$p&para=$q") })
            }
            composable(Tab.Progress.route) {
                ProgressScreen(
                    app,
                    onOpenResult = { navController.navigate("result/$it") },
                    onOpenSession = { navController.navigate("session/$it") },
                )
            }
            composable(Tab.Settings.route) { SettingsScreen(app) }
        }
    }
}
