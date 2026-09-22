package com.justbrowse.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * 独立空间的应用内 PIN 闸门：首次进入设置 PIN，之后进入需验证 PIN。
 *
 * 只在设备没有设置锁屏凭据（PIN/图案/密码）时才会出现 —— 有锁屏时进入独立空间
 * 直接走系统验证（锁屏密码 / 生物识别），不需要应用内自设凭证。
 */
@Composable
fun SpaceGateDialog(
    isSetup: Boolean,
    error: String?,
    onSetupPin: (String) -> Unit,
    onUnlockPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    val title = if (isSetup) "设置独立空间 PIN" else "进入独立空间"
    val hint = if (isSetup) "设置至少 4 位 PIN" else "请输入独立空间 PIN"
    val buttonLabel = if (isSetup) "进入" else "解锁"

    fun submit() {
        if (pin.isBlank()) return
        if (isSetup) onSetupPin(pin) else onUnlockPin(pin)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = if (isSetup) {
                        "独立空间会单独保留浏览历史、Cookie、书签和密码，并与主空间完全隔离。" +
                            "当前设备没有设置锁屏密码，请先设置一个独立空间进入 PIN。"
                    } else {
                        "独立空间的浏览数据与主空间完全隔离，验证后即可进入。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text(hint) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit) { Text(buttonLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
