package com.justbrowse.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.core.scripts.UserScriptParser
import com.justbrowse.domain.model.UserScript
import com.justbrowse.domain.repository.ScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ScriptViewModel @Inject constructor(
    private val scriptRepository: ScriptRepository
) : ViewModel() {

    val scripts: StateFlow<List<UserScript>> = scriptRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun importFromText(text: String) {
        val script = UserScriptParser.parse(
            source = text,
            id = UUID.randomUUID().toString(),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            scriptRepository.save(script)
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            val text = fetchUrl(url)
            if (text != null) {
                val script = UserScriptParser.parse(
                    source = text,
                    id = UUID.randomUUID().toString(),
                    updatedAt = System.currentTimeMillis()
                )
                scriptRepository.save(script)
            }
        }
    }

    private suspend fun fetchUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun save(script: UserScript) {
        viewModelScope.launch { scriptRepository.save(script) }
    }

    fun delete(id: String) {
        viewModelScope.launch { scriptRepository.delete(id) }
    }

    fun toggleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { scriptRepository.setEnabled(id, enabled) }
    }
}
