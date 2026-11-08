package com.invictus.abhyas.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.ui.components.FloatingNavigation
import com.invictus.abhyas.ui.components.LocalBackAction
import com.invictus.abhyas.ui.components.LocalShellActions
import com.invictus.abhyas.ui.components.PillTab
import com.invictus.abhyas.ui.components.ShellActions
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.settings.AboutScreen
import com.invictus.abhyas.ui.settings.AiSettingsScreen
import com.invictus.abhyas.ui.settings.AppearanceSettingsScreen
import com.invictus.abhyas.ui.settings.DataSettingsScreen
import com.invictus.abhyas.ui.settings.LibrariesScreen
import com.invictus.abhyas.ui.settings.SettingsRootScreen
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

/** The three pill tabs. Create is the separate round + button; Settings lives in the top bar. */
private enum class Tab(val route: String, val label: String, val activeWidth: Int) {
    Home("home", "Home", 96),
    Library("library", "Library", 108),
    Progress("progress", "Progress", 116),
}

private const val ROUTE_CREATE = "create"
private const val ROUTE_SETTINGS = "settings"

private val TOP_LEVEL_ROUTES = setOf(Tab.Home.route, Tab.Library.route, Tab.Progress.route, ROUTE_CREATE)

private val NAV_BAR_ROUTES = TOP_LEVEL_ROUTES + setOf("browse", "setup", "pdfstudio", "ncert", "ncertpractice")

private fun Tab.icon() = when (this) {
    Tab.Home -> AppIcons.Home
    Tab.Library -> AppIcons.LibraryBooks
    Tab.Progress -> AppIcons.Insights
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AbhyasRoot(app: AbhyasApp) {
    val database = app.database
    val settingsStore = app.settingsStore
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val settings by app.settingsStore.settings.collectAsState(initial = UserSettings())
    val systemDark = isSystemInDarkTheme()
    val isDark = settings.darkMode ?: systemDark
    val scope = rememberCoroutineScope()
    val pillTabs = remember {
        Tab.entries.map { PillTab(it.label, it.icon(), it.activeWidth.dp) }
    }
    val isTopLevel = currentRoute in TOP_LEVEL_ROUTES
    val shellActions = remember(isDark) {
        ShellActions(
            isDark = isDark,
            onOpenSettings = {
                navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
            },
            onToggleDark = { scope.launch { app.settingsStore.setDarkMode(!isDark) } },
        )
    }
    val backAction: (() -> Unit)? = if (isTopLevel) null else ({ navController.popBackStack(); Unit })

    CompositionLocalProvider(
        LocalShellActions provides (if (isTopLevel) shellActions else null),
        LocalBackAction provides backAction,
    ) {
    Scaffold(
        bottomBar = {
            if (currentRoute in NAV_BAR_ROUTES) {
                val selectedIndex = when (currentRoute) {
                    Tab.Home.route -> 0
                    Tab.Library.route, "browse", "ncert" -> 1
                    Tab.Progress.route -> 2
                    else -> null
                }
                FloatingNavigation(
                    tabs = pillTabs,
                    selectedIndex = selectedIndex,
                    onTabSelected = { index ->
                        navController.navigate(Tab.entries[index].route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCreate = {
                        navController.navigate(ROUTE_CREATE) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
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
                NcertPracticeScreen(
                    app,
                    onStarted = { navController.navigate("session/$it") { popUpTo("ncertpractice") { inclusive = true } } },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                )
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
            composable(ROUTE_CREATE) {
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
            composable(ROUTE_SETTINGS) {
                SettingsRootScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAppearance = { navController.navigate("settings/appearance") },
                    onOpenAi = { navController.navigate("settings/ai") },
                    onOpenData = { navController.navigate("settings/data") },
                    onOpenAbout = { navController.navigate("settings/about") },
                )
            }
            composable("settings/appearance") { AppearanceSettingsScreen(app, onBack = { navController.popBackStack() }) }
            composable("settings/ai") { AiSettingsScreen(app, onBack = { navController.popBackStack() }) }
            composable("settings/data") { DataSettingsScreen(app, onBack = { navController.popBackStack() }) }
            composable("settings/about") {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLibraries = { navController.navigate("settings/libraries") },
                )
            }
            composable("settings/libraries") { LibrariesScreen(onBack = { navController.popBackStack() }) }
        }
    }
    }
}
