package com.justbrowse.data.repository

import com.justbrowse.domain.model.UserScript
import com.justbrowse.domain.repository.ScriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M0 占位实现：内存存储，无持久化。
 * M1+ 替换为 Room-backed 实现。
 */
@Singleton
class ScriptRepositoryImpl @Inject constructor() : ScriptRepository {

    private val scripts = MutableStateFlow<List<UserScript>>(emptyList())

    override fun observeAll(): Flow<List<UserScript>> = scripts

    override fun observeEnabled(): Flow<List<UserScript>> =
        scripts.map { list -> list.filter { it.enabled } }

    override suspend fun getById(id: String): UserScript? =
        scripts.value.firstOrNull { it.id == id }

    override suspend fun save(script: UserScript) {
        val current = scripts.value.toMutableList()
        val idx = current.indexOfFirst { it.id == script.id }
        if (idx >= 0) current[idx] = script else current.add(script)
        scripts.value = current
    }

    override suspend fun delete(id: String) {
        scripts.value = scripts.value.filterNot { it.id == id }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        scripts.value = scripts.value.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
    }
}
