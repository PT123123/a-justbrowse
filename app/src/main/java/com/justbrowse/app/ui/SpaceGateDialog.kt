package com.justbrowse.app.ui

import androidx.biometric.BiometricManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * 独立空间闸门：首次进入设置 PIN，之后进入需验证 PIN 或使用系统生物识别。
 */
@Composable
fun SpaceGateDialog(
    gate: BrowserViewModel.SpaceGate,
    error: String?,
    onSetupPin: (String) -> Unit,
    onUnlockPin: (String) -> Unit,
    onBiometricUnlock: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }

    val isSetup = gate == BrowserViewModel.SpaceGate.SETUP_PIN
    val title = if (isSetup) "设置独立空间 PIN" else "进入独立空间"
    val hint = if (isSetup) "设置至少 4 位 PIN" else "请输入独立空间 PIN"
    val buttonLabel = if (isSetup) "进入" else "解锁"

    val biometricSupported = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

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
                        "独立空间会单独保留浏览历史、Cookie、书签和密码，并与主空间完全隔离。请先设置一个进入密码。"
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
                if (!isSetup && biometricSupported) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBiometricUnlock, modifier = Modifier.fillMaxWidth()) {
                        Text("使用系统指纹/面部识别解锁")
                    }
                }
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