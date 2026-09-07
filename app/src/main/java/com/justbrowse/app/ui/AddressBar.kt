package com.justbrowse.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.justbrowse.data.prefs.SearchEngine
import com.justbrowse.data.suggestions.DefaultSites
import com.justbrowse.data.suggestions.SuggestedSite
import com.justbrowse.domain.model.HistoryEntry

@Composable
fun AddressBar(
    url: String,
    title: String,
    progress: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    suggestions: List<HistoryEntry>,
    showSuggestions: Boolean,
    isBookmarked: Boolean,
    searchEngine: SearchEngine,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onDismissSuggestions: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSearchEngineChange: (SearchEngine) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingUrl by remember(url) { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var showSearchEngineMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Default sites shown when no history matches
    val defaultSites = DefaultSites.search(editingUrl.ifEmpty { url }, 4)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = onReload) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }
            if (isTablet) {
                IconButton(onClick = onHome) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = if (isEditing) editingUrl else url,
                    onValueChange = {
                        editingUrl = it
                        isEditing = true
                        onUrlChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            if (!focusState.isFocused) {
                                onDismissSuggestions()
                            }
                        },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Search or enter address",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        onSubmit()
                        isEditing = false
                        onDismissSuggestions()
                        focusManager.clearFocus()
                    }),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = {
                        if (!isEditing) {
                            if (url.startsWith("https://")) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Secure",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else if (url.startsWith("http://")) {
                                Icon(
                                    Icons.Default.LockOpen,
                                    contentDescription = "Not secure",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEditing && editingUrl.isNotEmpty()) {
                                IconButton(onClick = {
                                    editingUrl = ""
                                    onUrlChange("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            } else {
                                if (isTablet) {
                                    Box {
                                        IconButton(onClick = { showSearchEngineMenu = true }) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = searchEngine.label,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showSearchEngineMenu,
                                            onDismissRequest = { showSearchEngineMenu = false }
                                        ) {
                                            SearchEngine.entries.forEach { engine ->
                                                DropdownMenuItem(
                                                    text = { Text(engine.label) },
                                                    onClick = {
                                                        onSearchEngineChange(engine)
                                                        showSearchEngineMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = onToggleBookmark) {
                                        Icon(
                                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Box {
                                        IconButton(onClick = { showMoreMenu = true }) {
                                            Icon(Icons.Default.Menu, contentDescription = "More")
                                        }
                                        DropdownMenu(
                                            expanded = showMoreMenu,
                                            onDismissRequest = { showMoreMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Home") },
                                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                                                onClick = {
                                                    showMoreMenu = false
                                                    onHome()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (isBookmarked) "Remove bookmark" else "Add bookmark"
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showMoreMenu = false
                                                    onToggleBookmark()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Search engine") },
                                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                                                onClick = {
                                                    showMoreMenu = false
                                                    showSearchEngineMenu = true
                                                }
                                            )
                                            if (showSearchEngineMenu) {
                                                SearchEngine.entries.forEach { engine ->
                                                    DropdownMenuItem(
                                                        text = { Text(engine.label) },
                                                        onClick = {
                                                            onSearchEngineChange(engine)
                                                            showSearchEngineMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                )

                if (showSuggestions && suggestions.isNotEmpty() && isFocused) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(top = 56.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        LazyColumn {
                            items(suggestions, key = { it.id }) { entry ->
                                SuggestionHistoryItem(
                                    entry = entry,
                                    onClick = {
                                        onSuggestionClick(entry.url)
                                        isEditing = false
                                        focusManager.clearFocus()
                                    }
                                )
                            }
                            // Show default sites when no history matches
                            if (suggestions.isEmpty() && isFocused && editingUrl.length >= 2) {
                                items(defaultSites, key = { it.url }) { site ->
                                    SuggestionSiteItem(
                                        site = site,
                                        onClick = {
                                            onSuggestionClick(site.url)
                                            isEditing = false
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                            item {
                                SuggestionFooter(
                                    onClearHistory = onClearHistory
                                )
                            }
                        }
                    }
                }
            }

            IconButton(onClick = { /* TODO: open menu */ }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        }

        if (isLoading && progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.padding(end = 12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
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
            text = "\${entry.visitCount}x",
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Language,
            contentDescription = null,
            modifier = Modifier.padding(end = 12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
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
private fun SuggestionFooter(
    onClearHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClearHistory)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Clear,
            contentDescription = null,
            modifier = Modifier.padding(end = 12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Clear browsing history",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
