package com.justbrowse.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onDismissSuggestions: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingUrl by remember(url) { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
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
                    }),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        if (isEditing && editingUrl.isNotEmpty()) {
                            IconButton(onClick = {
                                editingUrl = ""
                                onUrlChange("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                if (showSuggestions && suggestions.isNotEmpty() && isFocused) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .padding(top = 56.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        LazyColumn {
                            items(suggestions, key = { it.id }) { entry ->
                                SuggestionItem(
                                    entry = entry,
                                    query = if (isEditing) editingUrl else url,
                                    onClick = {
                                        onSuggestionClick(entry.url)
                                        isEditing = false
                                    }
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
private fun SuggestionItem(
    entry: HistoryEntry,
    query: String,
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
    }
}
