package com.justbrowse.data.repository

import com.justbrowse.data.db.TabDao
import com.justbrowse.data.db.TabEntity
import com.justbrowse.data.di.DefaultSpaceDb
import com.justbrowse.data.di.PrivateSpaceDb
import com.justbrowse.domain.model.SpaceId
import com.justbrowse.domain.model.Tab
import com.justbrowse.domain.repository.TabRepository
import com.justbrowse.domain.space.SpaceController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabRepositoryImpl @Inject constructor(
    @DefaultSpaceDb private val mainDao: TabDao,
    @PrivateSpaceDb private val privateDao: TabDao,
    private val spaceController: SpaceController
) : TabRepository {

    private fun daoFor(space: SpaceId): TabDao =
        if (space == SpaceId.PRIVATE) privateDao else mainDao

    override fun observeTabs(): Flow<List<Tab>> =
        spaceController.currentSpace
            .flatMapLatest { daoFor(it).observeAll() }
            .map { list -> list.map { it.toDomain() } }

    override fun observeActiveTab(): Flow<Tab?> =
        spaceController.currentSpace
            .flatMapLatest { daoFor(it).observeActive() }
            .map { it?.toDomain() }

    override suspend fun getTab(tabId: String): Tab? =
        daoFor(spaceController.currentSpace.value).getById(tabId)?.toDomain()

    override suspend fun saveTab(tab: Tab) {
        val dao = daoFor(spaceController.currentSpace.value)
        dao.upsert(tab.toEntity(dao.count()))
    }

    override suspend fun deleteTab(tabId: String) {
        daoFor(spaceController.currentSpace.value).delete(tabId)
    }

    override suspend fun setActiveTab(tabId: String) {
        val dao = daoFor(spaceController.currentSpace.value)
        dao.clearActive()
        dao.setActive(tabId)
    }

    override suspend fun count(): Int = daoFor(spaceController.currentSpace.value).count()

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