package com.justbrowse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.material3.Text
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
    var pairingCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态卡片
            SyncStatusCard(state = state)

            // 操作按钮
            when (state) {
                is SyncState.StartingServer -> {
                    CircularProgressIndicator()
                    Text("Starting server...")
                }
                is SyncState.Idle -> {
                    Button(
                        onClick = { viewModel.startServer() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Start as Server (Send)")
                    }
                    Button(
                        onClick = { viewModel.startDiscovery() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Discover Devices (Receive)")
                    }
                }
                is SyncState.ServerRunning -> {
                    Text(
                        "Server running on port ${(state as SyncState.ServerRunning).port}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Pairing code: ${viewModel.serverPairingCode}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(onClick = { viewModel.stopServer() }) {
                        Text("Stop Server")
                    }
                }
                is SyncState.Discovering -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text("Discovering devices on local network...")
                    }
                }
                is SyncState.PeerFound -> {
                    val peer = state as SyncState.PeerFound
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Device Found", style = MaterialTheme.typography.labelLarge)
                            Text(peer.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${peer.host}:${peer.port}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it },
                        label = { Text("Pairing Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.connect(peer.host, peer.port, pairingCode) },
                        enabled = pairingCode.length == 6,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect")
                    }
                }
                is SyncState.Connecting -> {
                    CircularProgressIndicator()
                    Text("Connecting...")
                }
                is SyncState.Connected -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text("Connected!", style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = { viewModel.pullAndMerge() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Pull & Merge")
                    }
                    Button(
                        onClick = { viewModel.push() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Push to Remote")
                    }
                }
                is SyncState.Syncing -> {
                    CircularProgressIndicator()
                    Text("Syncing: ${(state as SyncState.Syncing).direction}...")
                }
                is SyncState.Error -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text("Error: ${(state as SyncState.Error).error}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.reset() }) {
                        Text("Retry")
                    }
                }
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
                        is SyncState.Idle -> "Ready to sync"
                        is SyncState.StartingServer -> "Starting server..."
                        is SyncState.ServerRunning -> "Server running"
                        is SyncState.Discovering -> "Discovering..."
                        is SyncState.PeerFound -> "Device found"
                        is SyncState.Connecting -> "Connecting..."
                        is SyncState.Connected -> "Connected"
                        is SyncState.Syncing -> "Syncing..."
                        is SyncState.Error -> "Error"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = when (state) {
                        is SyncState.Idle -> "Choose a mode to start"
                        is SyncState.StartingServer -> "Please wait..."
                        is SyncState.ServerRunning -> "Port ${(state as SyncState.ServerRunning).port}"
                        is SyncState.Discovering -> "Looking for devices"
                        is SyncState.PeerFound -> "${(state as SyncState.PeerFound).host}:${(state as SyncState.PeerFound).port}"
                        is SyncState.Connecting -> "Verifying pairing code..."
                        is SyncState.Connected -> "Ready to sync data"
                        is SyncState.Syncing -> "Transferring..."
                        is SyncState.Error -> (state as SyncState.Error).error
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
