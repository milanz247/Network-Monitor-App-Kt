package com.example.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.presentation.SettingsViewModel
import com.example.ui.components.SettingsCard
import com.example.ui.components.SettingsSubScreenHeader
import com.example.ui.theme.LocalExtendedColors

@Composable
fun BackupResetScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val extended = LocalExtendedColors.current
    val context = LocalContext.current
    val lastBackupResult by viewModel.lastBackupResult.collectAsState()
    val resetPreviewCount by viewModel.resetPreviewCount.collectAsState()
    val shareIntent by viewModel.shareIntent.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(it) }
    }

    LaunchedEffect(shareIntent) {
        shareIntent?.let {
            context.startActivity(Intent.createChooser(it, "Share usage summary"))
            viewModel.clearShareIntent()
        }
    }

    if (resetPreviewCount != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearResetPreview() },
            title = { Text("Clear all history?") },
            text = { Text("This will permanently delete $resetPreviewCount row(s) across every table: usage, speed, downtime, signal and connection logs. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmResetAll() }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearResetPreview() }) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
    ) {
        item { SettingsSubScreenHeader(title = "Backup, Reset & Share", onBack = onBack) }

        item {
            SettingsCard {
                Text("Backup & Restore", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Exports every table to a JSON file you choose the location for, or restores from one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Button(
                    onClick = { exportLauncher.launch("net_monitor_backup.json") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export backup") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Import backup") }
                if (lastBackupResult != null) {
                    Text(
                        text = lastBackupResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            SettingsCard {
                Text("Share usage summary", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Generates a shareable image card summarizing this month's usage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Button(
                    onClick = { viewModel.generateShareCard() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Share this month's summary") }
            }
        }

        item {
            SettingsCard {
                Text("Reset history", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Permanently deletes all usage and diagnostics history. You'll see exactly how many rows before anything is deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Button(
                    onClick = { viewModel.previewResetAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear all history...") }
            }
        }
    }
}
