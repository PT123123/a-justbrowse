package com.justbrowse.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.core.sync.SyncState
import com.justbrowse.core.sync.SyncController
import com.justbrowse.core.sync.VaultCipher
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

    /** ===== 密码 vault 同步（默认关闭，口令不持久化） ===== */

    private val _includeVault = MutableStateFlow(false)
    val includeVault: StateFlow<Boolean> = _includeVault.asStateFlow()

    private val _vaultPassphrase = MutableStateFlow("")
    val vaultPassphrase: StateFlow<String> = _vaultPassphrase.asStateFlow()

    fun setIncludeVault(enabled: Boolean) {
        _includeVault.value = enabled
    }

    fun setVaultPassphrase(value: String) {
        _vaultPassphrase.value = value
    }

    /** 生成 8 位随机口令（发送端展示给对端输入） */
    fun generatePassphrase() {
        _vaultPassphrase.value = VaultCipher.generatePassphrase()
    }

    /** 推送/拉取/起服务前，把当前开关与口令下发给控制器（内存持有） */
    private fun applyVaultConfig() {
        val passphrase = _vaultPassphrase.value.takeIf { _includeVault.value && it.isNotEmpty() }
        syncController.configureVault(
            include = passphrase != null,
            passphrase = passphrase?.toCharArray()
        )
    }

    fun startServer() {
        viewModelScope.launch {
            // 生成配对码（实际应由 controller 生成）
            serverPairingCode = (100000 + (Math.random() * 900000).toInt()).toString()
            applyVaultConfig()
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
        viewModelScope.launch {
            applyVaultConfig()
            syncController.pullAndMerge()
        }
    }

    fun push() {
        viewModelScope.launch {
            applyVaultConfig()
            syncController.push()
        }
    }

    fun reset() {
        // 重置状态
    }
}
