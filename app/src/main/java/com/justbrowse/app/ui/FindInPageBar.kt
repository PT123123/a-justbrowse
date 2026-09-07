package com.justbrowse.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Find in page") },
            singleLine = true
        )
        IconButton(onClick = { /* previous match — WebView.findAsync 单次，需要自行实现前后 */ }) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
        }
        IconButton(onClick = { /* next match */ }) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}
