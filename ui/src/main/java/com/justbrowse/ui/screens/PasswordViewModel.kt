package com.justbrowse.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.domain.model.PasswordEntry
import com.justbrowse.domain.repository.PasswordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val passwordRepository: PasswordRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    /** 列表：有关键词时搜索，否则展示全部 */
    val entries: StateFlow<List<PasswordEntry>> = kotlinx.coroutines.flow.combine(
        passwordRepository.observeAll(),
        searchQuery
    ) { all, query ->
        if (query.isBlank()) all
        else all.filter {
            it.origin.contains(query, ignoreCase = true) ||
                it.title.contains(query, ignoreCase = true) ||
                it.username.contains(query, ignoreCase = true)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun search(query: String) {
        searchQuery.value = query
    }

    fun save(existing: PasswordEntry?, origin: String, title: String, username: String, password: String) {
        viewModelScope.launch {
            passwordRepository.upsert(
                PasswordEntry(
                    id = existing?.id ?: "",
                    origin = origin,
                    title = title.ifEmpty { origin },
                    username = username,
                    // 编辑时密码留空 = 沿用原密码（Repository 内处理）
                    password = password,
                    createdAt = existing?.createdAt ?: 0L,
                    updatedAt = 0L
                )
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { passwordRepository.delete(id) }
    }
}
