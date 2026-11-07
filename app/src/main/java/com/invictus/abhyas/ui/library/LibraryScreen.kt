@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.ui.screens.BodyMuted
import com.invictus.abhyas.ui.screens.ConnectedToggleGroup
import com.invictus.abhyas.ui.screens.ExpressiveScreen
import com.invictus.abhyas.ui.screens.SectionTitle

private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

@Composable
fun LibraryScreen(app: AbhyasApp, onBrowse: () -> Unit) {
    val vm: LibraryViewModel = viewModel(factory = appViewModelFactory(app))
    val banks by vm.banks.collectAsState()
    val importState by vm.importState.collectAsState()
    var examFilter by remember { mutableStateOf(0) }
    var pendingRemove by remember { mutableStateOf<BankEntity?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importZip(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.importFolder(uri)
    }

    ExpressiveScreen("Library", "Question banks, offline") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { zipPicker.launch(ZIP_TYPES) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null)
                Text("  Import zip")
            }
            FilledTonalButton(
                onClick = { folderPicker.launch(null) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Text("  Import folder")
            }
        }

        vm.demoPackages.forEach { dir ->
            OutlinedButton(
                onClick = { vm.installDemo(dir) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Install demo bank ($dir) - debug build only") }
        }

        val list = banks
        when {
            list == null || importState == ImportUi.Running -> Box(
                Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator(modifier = Modifier.size(72.dp)) }

            list.isEmpty() -> EmptyLibrary()

            else -> {
                ConnectedToggleGroup(listOf("All", "NEET", "JEE Main"), examFilter) { examFilter = it }
                val shown = when (examFilter) {
                    1 -> list.filter { it.exam == "NEET" }
                    2 -> list.filter { it.exam == "JEE_MAIN" }
                    else -> list
                }
                FilledTonalButton(
                    onClick = onBrowse,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Text("  Browse and search questions")
                }
                SectionTitle("Installed banks")
                if (shown.isEmpty()) BodyMuted("No banks for this exam.")
                shown.forEach { bank ->
                    BankCard(
                        bank = bank,
                        onUpdate = { zipPicker.launch(ZIP_TYPES) },
                        onRemove = { pendingRemove = bank },
                    )
                }
            }
        }
    }

    pendingRemove?.let { bank ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove ${bank.title}?") },
            text = {
                Text(
                    "Its questions, bookmarks and mistakes are deleted. " +
                        "Past test results are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.remove(bank.id); pendingRemove = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } },
        )
    }

    (importState as? ImportUi.Done)?.let { done ->
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(done.title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { done.lines.forEach { Text(it) } } },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("OK") } },
        )
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(MaterialShapes.Clover4Leaf.toShape())
                .background(MaterialTheme.colorScheme.tertiaryContainer),
        )
        Text("Nothing here yet", style = MaterialTheme.typography.titleLargeEmphasized)
        BodyMuted("Import a question-bank zip or folder (manifest.json + questions.json) to start.")
    }
}

@Composable
private fun BankCard(bank: BankEntity, onUpdate: () -> Unit, onRemove: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(bank.title, style = MaterialTheme.typography.titleMediumEmphasized)
            val parts = listOfNotNull(
                "v${bank.version}",
                "${bank.questionCount} questions",
                bank.exam.replace('_', ' '),
                bank.classLevel?.let { "Class $it" },
                bank.subject,
                bank.kind.lowercase(),
            )
            BodyMuted(parts.joinToString(" - "))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onUpdate, shapes = ButtonDefaults.shapes()) { Text("Update") }
                TextButton(onClick = onRemove, shapes = ButtonDefaults.shapes()) { Text("Remove") }
            }
        }
    }
}
