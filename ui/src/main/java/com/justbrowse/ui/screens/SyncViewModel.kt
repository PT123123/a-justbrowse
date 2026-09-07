package com.justbrowse.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.core.sync.RealSyncController
import com.justbrowse.core.sync.SyncState
import com.justbrowse.core.sync.SyncController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncController: SyncController
) : ViewModel() {

    val state: StateFlow<SyncState> = syncController.state

    var serverPairingCode: String = ""
        private set

    fun startServer() {
        viewModelScope.launch {
            // 生成配对码（实际应由 controller 生成）
            serverPairingCode = (100000 + (Math.random() * 900000).toInt()).toString()
            syncController.startServer(8765)
        }
    }

    fun stopServer() {
        viewModelScope.launch { syncController.stopServer() }
    }

    fun startDiscovery() {
        viewModelScope.launch { syncController.startDiscovery() }
    }

    fun stopDiscovery() {
        viewModelScope.launch { syncController.stopDiscovery() }
    }

    fun connect(host: String, port: Int, pairingCode: String) {
        viewModelScope.launch { syncController.connect(host, port, pairingCode) }
    }

    fun pullAndMerge() {
        viewModelScope.launch { syncController.pullAndMerge() }
    }

    fun push() {
        viewModelScope.launch { syncController.push() }
    }

    fun reset() {
        // 重置状态
    }
}
