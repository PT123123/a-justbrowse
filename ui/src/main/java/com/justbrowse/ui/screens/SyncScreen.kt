package com.justbrowse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.text.KeyboardOptions
import com.justbrowse.core.sync.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit = {},
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val includeVault by viewModel.includeVault.collectAsState()
    val vaultPassphrase by viewModel.vaultPassphrase.collectAsState()
    var pairingCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("局域网同步") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态卡片
            SyncStatusCard(state = state)

            // 密码同步（可选，口令派生密钥加密传输）
            VaultCard(
                includeVault = includeVault,
                passphrase = vaultPassphrase,
                onToggle = viewModel::setIncludeVault,
                onPassphraseChange = viewModel::setVaultPassphrase,
                onGenerate = viewModel::generatePassphrase
            )

            // 操作按钮
            when (state) {
                is SyncState.StartingServer -> {
                    CircularProgressIndicator()
                    Text("正在启动服务…")
                }
                is SyncState.Idle -> {
                    Button(
                        onClick = { viewModel.startServer() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("作为发送端启动")
                    }
                    Button(
                        onClick = { viewModel.startDiscovery() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("搜索设备（接收）")
                    }
                }
                is SyncState.ServerRunning -> {
                    Text(
                        "服务运行中，端口 ${(state as SyncState.ServerRunning).port}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "配对码：${viewModel.serverPairingCode}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(onClick = { viewModel.stopServer() }) {
                        Text("停止服务")
                    }
                }
                is SyncState.Discovering -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text("正在局域网内搜索设备…")
                    }
                }
                is SyncState.PeerFound -> {
                    val peer = state as SyncState.PeerFound
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("发现设备", style = MaterialTheme.typography.labelLarge)
                            Text(peer.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${peer.host}:${peer.port}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it },
                        label = { Text("配对码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.connect(peer.host, peer.port, pairingCode) },
                        enabled = pairingCode.length == 6,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("连接")
                    }
                }
                is SyncState.Connecting -> {
                    CircularProgressIndicator()
                    Text("正在连接…")
                }
                is SyncState.Connected -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text("已连接", style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = { viewModel.pullAndMerge() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("拉取并合并")
                    }
                    Button(
                        onClick = { viewModel.push() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("推送到远端")
                    }
                }
                is SyncState.Syncing -> {
                    CircularProgressIndicator()
                    Text("正在同步：${(state as SyncState.Syncing).direction}…")
                }
                is SyncState.Error -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text("出错了：${(state as SyncState.Error).error}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.reset() }) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultCard(
    includeVault: Boolean,
    passphrase: String,
    onToggle: (Boolean) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onGenerate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("同步密码", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "密码经口令加密后传输，两端需相同口令",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = includeVault, onCheckedChange = onToggle)
            }
            if (includeVault) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = { Text("密码同步口令") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = onGenerate) { Text("生成") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SyncStatusCard(state: SyncState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (state) {
                    is SyncState.Idle -> Icons.Default.Sync
                    is SyncState.StartingServer -> Icons.Default.Refresh
                    is SyncState.ServerRunning -> Icons.Default.Computer
                    is SyncState.Discovering -> Icons.Default.Refresh
                    is SyncState.PeerFound -> Icons.Default.Phone
                    is SyncState.Connecting -> Icons.Default.Refresh
                    is SyncState.Connected -> Icons.Default.CheckCircle
                    is SyncState.Syncing -> Icons.Default.Sync
                    is SyncState.Error -> Icons.Default.Error
                },
                contentDescription = null,
                tint = when (state) {
                    is SyncState.Connected -> MaterialTheme.colorScheme.primary
                    is SyncState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(end = 12.dp)
            )
            Column {
                Text(
                    text = when (state) {
                        is SyncState.Idle -> "等待同步"
                        is SyncState.StartingServer -> "正在启动服务…"
                        is SyncState.ServerRunning -> "服务运行中"
                        is SyncState.Discovering -> "搜索中…"
                        is SyncState.PeerFound -> "已发现设备"
                        is SyncState.Connecting -> "正在连接…"
                        is SyncState.Connected -> "已连接"
                        is SyncState.Syncing -> "同步中…"
                        is SyncState.Error -> "出错了"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = when (state) {
                        is SyncState.Idle -> "请选择一种模式开始"
                        is SyncState.StartingServer -> "请稍候…"
                        is SyncState.ServerRunning -> "端口 ${(state as SyncState.ServerRunning).port}"
                        is SyncState.Discovering -> "正在寻找同一局域网下的设备"
                        is SyncState.PeerFound -> "${(state as SyncState.PeerFound).host}:${(state as SyncState.PeerFound).port}"
                        is SyncState.Connecting -> "正在校验配对码…"
                        is SyncState.Connected -> "可以开始同步数据了"
                        is SyncState.Syncing -> "正在传输…"
                        is SyncState.Error -> (state as SyncState.Error).error
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
