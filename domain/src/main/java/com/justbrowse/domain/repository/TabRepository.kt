package com.justbrowse.domain.repository

import com.justbrowse.domain.model.Tab
import kotlinx.coroutines.flow.Flow

interface TabRepository {
    fun observeTabs(): Flow<List<Tab>>
    fun observeActiveTab(): Flow<Tab?>
    suspend fun getTab(tabId: String): Tab?
    suspend fun saveTab(tab: Tab)
    suspend fun deleteTab(tabId: String)
    suspend fun setActiveTab(tabId: String)
    suspend fun count(): Int
}
