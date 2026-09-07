package com.justbrowse.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.justbrowse.domain.model.UserScript

@Composable
fun ScriptEditorDialog(
    script: UserScript,
    onDismiss: () -> Unit,
    onSave: (UserScript) -> Unit
) {
    var source by remember { mutableStateOf(script.source) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit: ${script.name}") },
        text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth().height(400.dp).padding(top = 8.dp),
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            TextButton(onClick = {
                // 重新解析元数据
                val updated = com.justbrowse.core.scripts.UserScriptParser.parse(
                    source = source,
                    id = script.id,
                    updatedAt = System.currentTimeMillis()
                )
                onSave(updated.copy(enabled = script.enabled))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
