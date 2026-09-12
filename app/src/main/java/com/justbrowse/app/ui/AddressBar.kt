package com.justbrowse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.justbrowse.data.prefs.SearchEngine
import com.justbrowse.data.suggestions.DefaultSites
import com.justbrowse.data.suggestions.SuggestedSite
import com.justbrowse.domain.model.HistoryEntry

/**
 * v3 搜索覆盖层：点击底部地址栏胶囊后全屏弹出。
 * 顶部输入框（带搜索引擎切换），下方为历史建议 / 常用网站列表。
 */
@Composable
fun SearchOverlay(
    initialUrl: String,
    suggestions: List<HistoryEntry>,
    showSuggestions: Boolean,
    searchEngine: SearchEngine,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onSearchEngineChange: (SearchEngine) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(initialUrl) }
    var showEngineMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }

                // 输入胶囊
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { showEngineMenu = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = searchEngine.label,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showEngineMenu,
                            onDismissRequest = { showEngineMenu = false }
                        ) {
                            SearchEngine.entries.forEach { engine ->
                                DropdownMenuItem(
                                    text = { Text(engine.label) },
                                    onClick = {
                                        onSearchEngineChange(engine)
                                        showEngineMenu = false
                                    }
                                )
                            }
                        }
                    }
                    BasicTextField(
                        value = editing,
                        onValueChange = {
                            editing = it
                            onUrlChange(it)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                        decorationBox = { inner ->
                            Box {
                                if (editing.isEmpty()) {
                                    Text(
                                        text = "搜索或输入网址",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (editing.isNotEmpty()) {
                        IconButton(onClick = {
                            editing = ""
                            onUrlChange("")
                        }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val hist = if (showSuggestions) suggestions else emptyList()
                val defaults = if (showSuggestions && suggestions.isEmpty() && editing.length >= 2) {
                    DefaultSites.search(editing, 4)
                } else {
                    emptyList()
                }

                if (hist.isEmpty() && defaults.isEmpty()) {
                    item {
                        Text(
                            text = if (editing.isBlank()) "输入网址或搜索关键词" else "没有找到相关结果",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                } else {
                    if (hist.isNotEmpty()) {
                        item {
                            SectionHeader("History")
                        }
                        items(hist, key = { it.id }) { entry ->
                            SuggestionHistoryItem(
                                entry = entry,
                                onClick = { onSuggestionClick(entry.url) }
                            )
                        }
                        item {
                            SuggestionFooter(onClick = onClearHistory)
                        }
                    }
                    if (defaults.isNotEmpty()) {
                        item {
                            SectionHeader("Sites")
                        }
                        items(defaults, key = { it.url }) { site ->
                            SuggestionSiteItem(
                                site = site,
                                onClick = { onSuggestionClick(site.url) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SuggestionHistoryItem(
    entry: HistoryEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = entry.title.ifEmpty { entry.url },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${entry.visitCount}x",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuggestionSiteItem(
    site: SuggestedSite,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Language,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = site.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = site.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SuggestionFooter(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Clear,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Clear browsing history",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
