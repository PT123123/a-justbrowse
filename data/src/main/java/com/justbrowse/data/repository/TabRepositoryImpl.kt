package com.justbrowse.data.repository

import com.justbrowse.data.db.TabDao
import com.justbrowse.data.db.TabEntity
import com.justbrowse.domain.model.Tab
import com.justbrowse.domain.repository.TabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabRepositoryImpl @Inject constructor(
    private val tabDao: TabDao
) : TabRepository {

    override fun observeTabs(): Flow<List<Tab>> =
        tabDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActiveTab(): Flow<Tab?> =
        tabDao.observeActive().map { it?.toDomain() }

    override suspend fun getTab(tabId: String): Tab? =
        tabDao.getById(tabId)?.toDomain()

    override suspend fun saveTab(tab: Tab) {
        tabDao.upsert(tab.toEntity(tabDao.count()))
    }

    override suspend fun deleteTab(tabId: String) {
        tabDao.delete(tabId)
    }

    override suspend fun setActiveTab(tabId: String) {
        tabDao.clearActive()
        tabDao.setActive(tabId)
    }

    override suspend fun count(): Int = tabDao.count()

    private fun TabEntity.toDomain() = Tab(
        id = id,
        url = url,
        title = title,
        faviconUrl = faviconUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isActive = isActive
    )

    private fun Tab.toEntity(order: Int) = TabEntity(
        id = id,
        url = url,
        title = title,
        faviconUrl = faviconUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isActive = isActive,
        orderIndex = order
    )
}
