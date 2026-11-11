package com.invictus.abhyas.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.invictus.abhyas.ui.settings.ExamSettingsScreen
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
import com.invictus.abhyas.ui.ncert.ChapterListScreen
import com.invictus.abhyas.ui.ncert.ReaderScreen
import com.invictus.abhyas.ui.pdf.PdfReportScreen
import com.invictus.abhyas.ui.pdf.PdfReviewScreen
import com.invictus.abhyas.ui.pdf.PdfStudioScreen
import com.invictus.abhyas.ui.pyq.PyqStoreScreen
import com.invictus.abhyas.ui.screens.HomeScreen
import com.invictus.abhyas.ui.library.BrowseScreen
import com.invictus.abhyas.ui.library.LibraryScreen
import com.invictus.abhyas.ui.library.PapersScreen
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

private const val ROUTE_SETTINGS = "settings"

private val TOP_LEVEL_ROUTES = setOf(Tab.Home.route, Tab.Library.route, Tab.Progress.route)

private val NAV_BAR_ROUTES = TOP_LEVEL_ROUTES + setOf("setup", "pdfstudio", "chapters/{bookId}", "ncertpractice", "pyq", "papers")

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
    var practiceDialog by remember { mutableStateOf<String?>(null) }
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
    when (practiceDialog) {
        "setup" -> SetupScreen(
            app,
            onStarted = { practiceDialog = null; navController.navigate("session/$it") },
            onDismiss = { practiceDialog = null },
        )
        "ncert" -> NcertPracticeScreen(
            app,
            onStarted = { practiceDialog = null; navController.navigate("session/$it") },
            onOpenSettings = { practiceDialog = null; navController.navigate(ROUTE_SETTINGS) },
            onDismiss = { practiceDialog = null },
        )
    }

    Scaffold(
        // Screens own the status-bar inset (their top bar draws behind it); only sides/bottom here.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            if (currentRoute in NAV_BAR_ROUTES) {
                val selectedIndex = when (currentRoute) {
                    Tab.Home.route -> 0
                    Tab.Library.route, "chapters/{bookId}", "pyq" -> 1
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
                    onCustomPractice = { practiceDialog = "setup" },
                    onNcertPractice = { practiceDialog = "ncert" },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding).swipeBetweenTabs(
                enabled = isTopLevel,
                currentIndex = Tab.entries.indexOfFirst { it.route == currentRoute },
                count = Tab.entries.size,
                onGo = { index ->
                    navController.navigate(Tab.entries[index].route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            ),
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    app,
                    onOpenSession = { navController.navigate("session/$it") },
                    onImportBank = { navController.navigate(Tab.Library.route) },
                    onOpenPyq = { navController.navigate("pyq") },
                    onOpenResult = { navController.navigate("result/$it") },
                )
            }
            composable(Tab.Library.route) {
                LibraryScreen(
                    app,
                    onBrowse = { navController.navigate("browse") },
                    onViewAll = { navController.navigate("papers") },
                    onOpenPyq = { navController.navigate("pyq") },
                    onOpenBook = { navController.navigate("chapters/$it") },
                    onOpenSession = { navController.navigate("session/$it") },
                )
            }
            composable("papers") {
                PapersScreen(app, onOpenSession = { navController.navigate("session/$it") })
            }
            composable("pyq") {
                PyqStoreScreen(
                    app,
                    onOpenSettings = { navController.navigate("settings/data") },
                    onOpenStudio = { navController.navigate("pdfstudio") },
                )
            }
            composable("chapters/{bookId}", arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("bookId").orEmpty()
                ChapterListScreen(
                    app, id,
                    onOpenChapter = { from, to -> navController.navigate("reader/$id?page=$from&from=$from&to=$to") },
                    onOpenFullBook = { navController.navigate("reader/$id") },
                )
            }
            composable(
                "reader/{bookId}?page={page}&para={para}&from={from}&to={to}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("page") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("para") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("from") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("to") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { entry ->
                ReaderScreen(
                    app,
                    entry.arguments?.getString("bookId").orEmpty(),
                    entry.arguments?.getInt("page") ?: 0,
                    entry.arguments?.getInt("para") ?: -1,
                    entry.arguments?.getInt("from") ?: 0,
                    entry.arguments?.getInt("to") ?: 0,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("browse") { BrowseScreen(app) }
            composable("pdfstudio") { PdfStudioScreen(app, onOpenJob = { navController.navigate("pdf/$it") }) }
            composable("pdf/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                PdfReviewScreen(app, id, onBack = { navController.popBackStack() }, onReport = { navController.navigate("pdf/$id/report") })
            }
            composable("pdf/{id}/report", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PdfReportScreen(app, entry.arguments?.getLong("id") ?: 0L, onBack = { navController.popBackStack() })
            }
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
                    app,
                    onBack = { navController.popBackStack() },
                    onOpenExam = { navController.navigate("settings/exam") },
                    onOpenAppearance = { navController.navigate("settings/appearance") },
                    onOpenAi = { navController.navigate("settings/ai") },
                    onOpenData = { navController.navigate("settings/data") },
                    onOpenAbout = { navController.navigate("settings/about") },
                )
            }
            composable("settings/exam") { ExamSettingsScreen(app, onBack = { navController.popBackStack() }) }
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

/**
 * Swipe sideways on a main tab to move to the next or previous tab (Home - Library - Progress).
 * Horizontal scrollers inside a screen (like the book shelf) consume their own drags first, so they still scroll.
 */
private fun Modifier.swipeBetweenTabs(enabled: Boolean, currentIndex: Int, count: Int, onGo: (Int) -> Unit): Modifier =
    if (!enabled || currentIndex < 0) this else this.pointerInput(currentIndex) {
        val threshold = 80.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragCancel = { total = 0f },
            onDragEnd = {
                when {
                    total < -threshold && currentIndex < count - 1 -> onGo(currentIndex + 1)
                    total > threshold && currentIndex > 0 -> onGo(currentIndex - 1)
                }
                total = 0f
            },
        ) { _, dx -> total += dx }
    }
