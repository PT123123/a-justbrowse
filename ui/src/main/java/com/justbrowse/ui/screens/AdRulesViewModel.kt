package com.justbrowse.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdRulesViewModel @Inject constructor() : ViewModel() {
    private val _rules = MutableStateFlow<List<String>>(emptyList())
    val rules: StateFlow<List<String>> = _rules.asStateFlow()

    fun addRule(rule: String) {
        viewModelScope.launch {
            _rules.value = _rules.value + rule
        }
    }

    fun removeRule(rule: String) {
        viewModelScope.launch {
            _rules.value = _rules.value - rule
        }
    }
}
