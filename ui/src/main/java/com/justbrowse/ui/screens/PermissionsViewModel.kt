package com.justbrowse.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SitePermission(
    val domain: String,
    val camera: Boolean = false,
    val microphone: Boolean = false,
    val location: Boolean = false
)

@HiltViewModel
class PermissionsViewModel @Inject constructor() : ViewModel() {
    private val _permissions = MutableStateFlow<List<SitePermission>>(emptyList())
    val permissions: StateFlow<List<SitePermission>> = _permissions.asStateFlow()

    fun loadPermissions() {
        viewModelScope.launch {
            // TODO: Load from persistent storage
            _permissions.value = emptyList()
        }
    }
}
