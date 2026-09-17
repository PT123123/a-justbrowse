package com.justbrowse.domain.repository

import com.justbrowse.domain.model.PasswordEntry
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {
    /** 全部条目（含明文密码，仅用于密码管理页/自动填充，勿打日志） */
    fun observeAll(): Flow<List<PasswordEntry>>

    /** 按站点源查匹配凭证（自动填充用） */
    suspend fun findByOrigin(origin: String): List<PasswordEntry>

    /** 关键词搜索（origin/title/username） */
    suspend fun search(query: String): List<PasswordEntry>

    suspend fun upsert(entry: PasswordEntry)

    suspend fun delete(id: String)
}
