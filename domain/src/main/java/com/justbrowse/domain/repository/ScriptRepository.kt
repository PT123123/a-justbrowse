package com.justbrowse.domain.repository

import com.justbrowse.domain.model.UserScript
import kotlinx.coroutines.flow.Flow

interface ScriptRepository {
    fun observeAll(): Flow<List<UserScript>>
    fun observeEnabled(): Flow<List<UserScript>>
    suspend fun getById(id: String): UserScript?
    suspend fun save(script: UserScript)
    suspend fun delete(id: String)
    suspend fun setEnabled(id: String, enabled: Boolean)
}
