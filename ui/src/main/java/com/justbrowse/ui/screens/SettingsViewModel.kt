package com.justbrowse.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.data.prefs.AppSettings
import com.justbrowse.data.prefs.SearchEngine
import com.justbrowse.data.prefs.SettingsDataStore
import com.justbrowse.data.prefs.ThemeMode
import com.justbrowse.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { settingsDataStore.setSearchEngine(engine) }
    }

    fun setAdBlocking(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAdBlocking(enabled) }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setJavaScriptEnabled(enabled) }
    }

    fun setLoadImages(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLoadImages(enabled) }
    }

    fun setDoNotTrack(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDoNotTrack(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDynamicColor(enabled) }
    }

    fun setTextZoom(zoom: Int) {
        viewModelScope.launch { settingsDataStore.setTextZoom(zoom) }
    }

    fun clearBrowsingData() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }
}
