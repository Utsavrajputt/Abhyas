@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import com.invictus.abhyas.data.pyq.GitHubLinks
import com.invictus.abhyas.data.pyq.IndexLink
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.prefs.UserSettings
import com.invictus.abhyas.ui.ai.AiSettingsSection
import com.invictus.abhyas.ui.components.AbhyasTopBar
import com.invictus.abhyas.ui.components.BackNavigationIcon
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ExamPicker
import kotlinx.coroutines.launch

/** Header + body frame shared by every Settings sub-screen (xmd-style flat top bar with a back arrow). */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            AbhyasTopBar(
                title = { Text(title) },
                navigationIcon = { BackNavigationIcon(onBack = onBack) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** Root of Settings: xmd-style category rows (Appearance, AI, NCERT & Data) and About. */
@Composable
fun SettingsRootScreen(
    app: AbhyasApp,
    onBack: () -> Unit,
    onOpenExam: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    SettingsScaffold(title = "Settings", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column {
            SettingsSectionCard {
                CategoryRow(
                    icon = AppIcons.School,
                    title = "Exam",
                    subtitle = "NEET or JEE Main - Home, Library and papers follow it",
                    isFirst = true,
                    onClick = onOpenExam,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = AppIcons.Palette,
                    title = "Appearance",
                    subtitle = "Theme color, dark and AMOLED mode",
                    onClick = onOpenAppearance,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = AppIcons.AutoAwesome,
                    title = "AI",
                    subtitle = "Optional Gemini features, API key and model",
                    onClick = onOpenAi,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = AppIcons.Database,
                    title = "Sources",
                    subtitle = "GitHub links for books and papers",
                    isLast = true,
                    onClick = onOpenData,
                )
            }
            }
            Column(modifier = Modifier.padding(top = 16.dp)) {
                SettingsSectionCard {
                    CategoryRow(
                        icon = AppIcons.Info,
                        title = "About",
                        subtitle = "Version, credits, and licenses",
                        isFirst = true,
                        isLast = true,
                        onClick = onOpenAbout,
                    )
                }
            }
        }
    }
}

/** Optional AI: master switch, then key / model / connection test while it is on. */
@Composable
fun AiSettingsScreen(app: AbhyasApp, onBack: () -> Unit) {
    val store = app.settingsStore
    val settings by store.settings.collectAsState(initial = UserSettings())
    val scope = rememberCoroutineScope()

    SettingsScaffold(title = "AI", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionCard {
                SwitchSettingRow(
                    title = "Enable AI features",
                    subtitle = "Off by default. Practice, scoring and reading never need AI. AI content is always shown as unverified.",
                    checked = settings.aiEnabled,
                    onCheckedChange = { scope.launch { store.setAiEnabled(it) } },
                )
            }
            if (settings.aiEnabled) {
                SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AiSettingsSection(app, settings.aiModel)
                    }
                }
            }
        }
    }
}

/** Sources: GitHub links for books and papers. */
@Composable
fun DataSettingsScreen(app: AbhyasApp, onBack: () -> Unit) {
    val store = app.settingsStore
    val settings by store.settings.collectAsState(initial = UserSettings())
    val scope = rememberCoroutineScope()
    val links by store.indexLinks.collectAsState(initial = emptyList())
    var linkUrl by remember { mutableStateOf("") }
    var linkBusy by remember { mutableStateOf(false) }
    var linkMessage by remember { mutableStateOf<String?>(null) }
    var linkError by remember { mutableStateOf(false) }

    SettingsScaffold(title = "Sources", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "GitHub links",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    BodyMuted(
                        "Add as many index.json links (or GitHub repos) as you like. Books and papers from every link " +
                            "show up in Packages > Online, and everything stays offline after downloading.",
                    )
                    links.forEach { link ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(link.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    link.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { scope.launch { store.setIndexLinks(links.filter { it.id != link.id }) } }) { Text("Remove") }
                        }
                    }
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it; linkMessage = null },
                        label = { Text("index.json link or GitHub repo (https)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    linkMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (linkError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val url = GitHubLinks.normalize(linkUrl)
                                scope.launch {
                                    linkBusy = true
                                    linkError = true
                                    try {
                                        if (links.any { it.url == url }) {
                                            linkMessage = "This link is already added"
                                        } else {
                                            val parsed = app.pyqRepository.fetchPackageIndex(url)
                                            if (parsed.entries.isEmpty()) {
                                                linkMessage = parsed.errors.firstOrNull() ?: "No packages found at this link"
                                            } else {
                                                val name = parsed.name ?: url.removePrefix("https://").substringBefore('/')
                                                store.setIndexLinks(links + IndexLink(java.util.UUID.randomUUID().toString(), name, url))
                                                linkUrl = ""
                                                linkError = false
                                                linkMessage = "Added $name: ${parsed.entries.size} packages"
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        linkMessage = e.message ?: "Could not read this link"
                                    } finally {
                                        linkBusy = false
                                    }
                                }
                            },
                            enabled = linkUrl.isNotBlank() && !linkBusy,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Add link") }
                        if (linkBusy) LoadingIndicator(modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}

/** Exam focus: the whole app (Home, Library, PYQ store, practice defaults) follows this choice. */
@Composable
fun ExamSettingsScreen(app: AbhyasApp, onBack: () -> Unit) {
    val store = app.settingsStore
    val settings by store.settings.collectAsState(initial = UserSettings())
    val scope = rememberCoroutineScope()

    SettingsScaffold(title = "Exam", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExamPicker(selected = settings.exam, onSelect = { scope.launch { store.setExam(it) } })
            BodyMuted(
                "Home, the Library, the previous-year papers store and the default filters in Browse and " +
                    "Custom practice show only the selected exam. Progress keeps showing all your attempts.",
            )
            DailyPracticeCard(app)
        }
    }
}

/** Daily practice options: on/off, how many questions, whether numericals are mixed in. */
@Composable
private fun DailyPracticeCard(app: AbhyasApp) {
    val store = app.settingsStore
    val settings by store.settings.collectAsState(initial = UserSettings())
    val scope = rememberCoroutineScope()
    val counts = listOf(10, 20, 30, 50)
    SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        SwitchSettingRow(
            title = "Daily practice",
            subtitle = "A new random set on Home every day, with a streak",
            checked = settings.dailyEnabled,
            onCheckedChange = { scope.launch { store.setDailyEnabled(it) } },
        )
        if (settings.dailyEnabled) {
            SwitchSettingRow(
                title = "Include numericals",
                subtitle = "Off: theory questions only",
                checked = settings.dailyNumerical,
                onCheckedChange = { scope.launch { store.setDailyNumerical(it) } },
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Questions per day", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                com.invictus.abhyas.ui.screens.ConnectedToggleGroup(
                    items = counts.map { it.toString() },
                    selected = counts.indexOf(settings.dailyCount),
                ) { i -> scope.launch { store.setDailyCount(counts[i]) } }
            }
        }
    }
}
