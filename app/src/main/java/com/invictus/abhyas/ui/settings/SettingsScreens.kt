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
                    title = "NCERT & data",
                    subtitle = "NCERT book index and PYQ index URLs",
                    isLast = true,
                    onClick = onOpenData,
                )
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

/** NCERT book index URL (the same value the NCERT screen uses to download books). */
@Composable
fun DataSettingsScreen(app: AbhyasApp, onBack: () -> Unit) {
    val store = app.settingsStore
    val settings by store.settings.collectAsState(initial = UserSettings())
    val scope = rememberCoroutineScope()
    var url by remember(settings.ncertIndexUrl) { mutableStateOf(settings.ncertIndexUrl) }
    var pyqUrl by remember(settings.pyqIndexUrl) { mutableStateOf(settings.pyqIndexUrl) }
    val links by store.indexLinks.collectAsState(initial = emptyList())
    var linkUrl by remember { mutableStateOf("") }
    var linkBusy by remember { mutableStateOf(false) }
    var linkMessage by remember { mutableStateOf<String?>(null) }
    var linkError by remember { mutableStateOf(false) }

    SettingsScaffold(title = "NCERT & data", onBack = onBack) {
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
                        "NCERT book index",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    BodyMuted("HTTPS address of a hosted book index. Its books show up in Packages > Online; everything stays offline after downloading.")
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Index URL (https)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { scope.launch { store.setNcertIndexUrl(url) } },
                            enabled = url.trim() != settings.ncertIndexUrl,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Save") }
                        OutlinedButton(
                            onClick = { url = ""; scope.launch { store.setNcertIndexUrl("") } },
                            enabled = settings.ncertIndexUrl.isNotEmpty() || url.isNotEmpty(),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Clear") }
                    }
                }
            }

            SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "PYQ index (previous year papers)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    BodyMuted("HTTPS address of an index.json listing yearly question-bank packs (build it with tools/build_index.py). Its papers show up in Packages > Online (package icon in Library).")
                    OutlinedTextField(
                        value = pyqUrl,
                        onValueChange = { pyqUrl = it },
                        label = { Text("PYQ index URL (https)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { scope.launch { store.setPyqIndexUrl(pyqUrl) } },
                            enabled = pyqUrl.trim() != settings.pyqIndexUrl,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Save") }
                        OutlinedButton(
                            onClick = { pyqUrl = ""; scope.launch { store.setPyqIndexUrl("") } },
                            enabled = settings.pyqIndexUrl.isNotEmpty() || pyqUrl.isNotEmpty(),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Clear") }
                    }
                }
            }

            SettingsSectionCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Other links",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    BodyMuted(
                        "Add as many index.json links as you like, for example one you publish on GitHub. " +
                            "The app reads the name and the packages from the link itself; they show up in Packages > Online.",
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
        }
    }
}
