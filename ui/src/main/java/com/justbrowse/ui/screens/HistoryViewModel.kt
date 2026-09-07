package com.justbrowse.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.domain.model.HistoryEntry
import com.justbrowse.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val entries: StateFlow<List<HistoryEntry>> = historyRepository
        .observeByDate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchResults = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val searchResults: StateFlow<List<HistoryEntry>> = _searchResults.asStateFlow()

    fun search(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = historyRepository.search(query)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { historyRepository.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { historyRepository.clearAll() }
    }
}
