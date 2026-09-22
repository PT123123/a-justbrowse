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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    val crashLogSummary by viewModel.crashLogSummary.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showTextZoomDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
            item { SectionHeader("外观") }
            item {
                SettingsItem(
                    title = "主题",
                    subtitle = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色 · ${settings.darkThemeVariant.label}"
                    },
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                SwitchSettingsItem(
                    title = "动态取色",
                    subtitle = "使用系统强调色（Android 12+）",
                    checked = settings.useDynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }

            item { SectionHeader("通用") }
            item {
                SettingsItem(
                    title = "搜索引擎",
                    subtitle = settings.searchEngine.label,
                    onClick = { showSearchDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = "字号缩放",
                    subtitle = "网页文字大小 ${settings.textZoom}%",
                    onClick = { showTextZoomDialog = true }
                )
            }
            item {
                SwitchSettingsItem(
                    title = "广告拦截",
                    subtitle = "拦截广告与跟踪请求",
                    checked = settings.adBlockingEnabled,
                    onCheckedChange = viewModel::setAdBlocking
                )
            }
            item {
                SwitchSettingsItem(
                    title = "JavaScript 脚本",
                    subtitle = "启用网页脚本执行",
                    checked = settings.javaScriptEnabled,
                    onCheckedChange = viewModel::setJavaScriptEnabled
                )
            }
            item {
                SwitchSettingsItem(
                    title = "加载图片",
                    subtitle = "自动加载网页图片",
                    checked = settings.loadImages,
                    onCheckedChange = viewModel::setLoadImages
                )
            }

            item { SectionHeader("隐私") }
            item {
                SwitchSettingsItem(
                    title = "请勿跟踪",
                    subtitle = "向网站发送 DNT 请求头",
                    checked = settings.doNotTrack,
                    onCheckedChange = viewModel::setDoNotTrack
                )
            }
            item {
                SettingsItem(
                    title = "清除浏览数据",
                    subtitle = "历史记录、Cookie、缓存与网站数据",
                    onClick = { showClearDialog = true }
                )
            }

            item { SectionHeader("关于") }
            item {
                SettingsItem(
                    title = "版本",
                    subtitle = "JustBrowse 0.1.0",
                    onClick = {}
                )
            }
            item {
                SettingsItem(
                    title = "导出崩溃日志",
                    subtitle = crashLogSummary ?: "暂无记录（闪退时自动记录）",
                    onClick = viewModel::exportCrashLog
                )
            }
        }
    }

    // Theme picker dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("主题") },
            text = {
                Column {
                    ThemeOption("跟随系统", ThemeMode.SYSTEM, settings.themeMode) {
                        viewModel.setThemeMode(ThemeMode.SYSTEM); showThemeDialog = false
                    }
                    ThemeOption("浅色", ThemeMode.LIGHT, settings.themeMode) {
                        viewModel.setThemeMode(ThemeMode.LIGHT); showThemeDialog = false
                    }
                    ThemeOption("深色", ThemeMode.DARK, settings.themeMode) {
                        viewModel.setThemeMode(ThemeMode.DARK); showThemeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("取消") }
            }
        )
    }

    // Search engine picker dialog
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("搜索引擎") },
            text = {
                Column {
                    SearchEngine.entries.forEach { engine ->
                        ThemeOption(engine.label, engine.name, settings.searchEngine.name) {
                            viewModel.setSearchEngine(engine)
                            showSearchDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("取消") }
            }
        )
    }

    // Text zoom dialog
    if (showTextZoomDialog) {
        var zoom by remember { mutableFloatStateOf(settings.textZoom.toFloat()) }
        AlertDialog(
            onDismissRequest = { showTextZoomDialog = false },
            title = { Text("字号缩放") },
            text = {
                Column {
                    Text(
                        text = "${zoom.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 50f..200f,
                        steps = 14
                    )
                    Text(
                        text = "调整网页文字显示大小，对所有页面生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTextZoom(zoom.toInt())
                    showTextZoomDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTextZoomDialog = false }) { Text("取消") }
            }
        )
    }

    // Clear data confirmation
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除浏览数据") },
            text = { Text("将清除历史记录、Cookie、缓存与网站存储数据，继续？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBrowsingData()
                    showClearDialog = false
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
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
