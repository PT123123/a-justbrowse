package com.justbrowse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.justbrowse.data.prefs.SearchEngine
import com.justbrowse.data.prefs.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item { SectionHeader("Appearance") }
            item {
                SettingsItem(
                    title = "Theme",
                    subtitle = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> "Follow system"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    },
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                SwitchSettingsItem(
                    title = "Dynamic Colors",
                    subtitle = "Use system accent colors (Android 12+)",
                    checked = settings.useDynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }
            item {
                SwitchSettingsItem(
                    title = "Force Dark Mode",
                    subtitle = "Invert page colors for night browsing",
                    checked = settings.forceDarkMode,
                    onCheckedChange = viewModel::setForceDarkMode
                )
            }

            item { SectionHeader("General") }
            item {
                SettingsItem(
                    title = "Search Engine",
                    subtitle = settings.searchEngine.label,
                    onClick = { showSearchDialog = true }
                )
            }
            item {
                SwitchSettingsItem(
                    title = "Ad Blocking",
                    subtitle = "Block ads and trackers",
                    checked = settings.adBlockingEnabled,
                    onCheckedChange = viewModel::setAdBlocking
                )
            }
            item {
                SwitchSettingsItem(
                    title = "JavaScript",
                    subtitle = "Enable JavaScript on pages",
                    checked = settings.javaScriptEnabled,
                    onCheckedChange = viewModel::setJavaScriptEnabled
                )
            }
            item {
                SwitchSettingsItem(
                    title = "Load Images",
                    subtitle = "Automatically load images",
                    checked = settings.loadImages,
                    onCheckedChange = viewModel::setLoadImages
                )
            }

            item { SectionHeader("Privacy") }
            item {
                SwitchSettingsItem(
                    title = "Do Not Track",
                    subtitle = "Send DNT header to websites",
                    checked = settings.doNotTrack,
                    onCheckedChange = viewModel::setDoNotTrack
                )
            }
            item {
                SettingsItem(
                    title = "Clear Browsing Data",
                    subtitle = "History, cookies, cache",
                    onClick = { showClearDialog = true }
                )
            }

            item { SectionHeader("About") }
            item {
                SettingsItem(
                    title = "Version",
                    subtitle = "JustBrowse 0.1.0 (M2)",
                    onClick = {}
                )
            }
        }
    }

    // Theme picker dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeOption("Follow system", ThemeMode.SYSTEM, settings.themeMode) {
                        viewModel.setThemeMode(ThemeMode.SYSTEM); showThemeDialog = false
                    }
                    ThemeOption("Light", ThemeMode.LIGHT, settings.themeMode) {
                        viewModel.setThemeMode(ThemeMode.LIGHT); showThemeDialog = false
                    }
                    ThemeOption("Dark", ThemeMode.DARK, settings.themeMode) {
                        viewModel.setThemeMode(ThemeMode.DARK); showThemeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Search engine picker dialog
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Engine") },
            text = {
                Column {
                    SearchEngine.values().forEach { engine ->
                        ThemeOption(engine.label, engine.name, settings.searchEngine.name) {
                            viewModel.setSearchEngine(engine)
                            showSearchDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear data confirmation
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Browsing Data") },
            text = { Text("This will clear your browsing history. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBrowsingData()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun <T> ThemeOption(label: String, value: T, current: T, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = value == current, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun SwitchSettingsItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
