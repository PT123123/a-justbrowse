package com.justbrowse.data.repository

import com.justbrowse.data.crypto.PasswordCrypto
import com.justbrowse.data.db.PasswordDao
import com.justbrowse.data.db.PasswordEntity
import com.justbrowse.data.di.DefaultSpaceDb
import com.justbrowse.data.di.PrivateSpaceDb
import com.justbrowse.domain.model.PasswordEntry
import com.justbrowse.domain.model.SpaceId
import com.justbrowse.domain.repository.PasswordRepository
import com.justbrowse.domain.space.SpaceController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordRepositoryImpl @Inject constructor(
    @DefaultSpaceDb private val mainDao: PasswordDao,
    @PrivateSpaceDb private val privateDao: PasswordDao,
    private val spaceController: SpaceController,
    private val crypto: PasswordCrypto
) : PasswordRepository {

    private fun daoFor(space: SpaceId): PasswordDao =
        if (space == SpaceId.PRIVATE) privateDao else mainDao

    override fun observeAll(): Flow<List<PasswordEntry>> =
        spaceController.currentSpace
            .flatMapLatest { daoFor(it).observeAll() }
            .map { list -> list.map { it.toDomain() } }

    override suspend fun findByOrigin(origin: String): List<PasswordEntry> =
        daoFor(spaceController.currentSpace.value).getByOrigin(origin).map { it.toDomain() }

    override suspend fun search(query: String): List<PasswordEntry> =
        daoFor(spaceController.currentSpace.value).search(query).map { it.toDomain() }

    override suspend fun upsert(entry: PasswordEntry) {
        val dao = daoFor(spaceController.currentSpace.value)
        val now = System.currentTimeMillis()
        // 更新时若密码为空（用户未改动密码框），沿用库中已有密文
        val existing = dao.getById(entry.id)
        val (cipher, iv) = if (entry.password.isEmpty() && existing != null) {
            existing.passwordCipher to existing.passwordIv
        } else {
            crypto.encrypt(entry.password)
        }
        dao.upsert(
            PasswordEntity(
                id = entry.id.ifEmpty { UUID.randomUUID().toString() },
                origin = entry.origin,
                title = entry.title,
                username = entry.username,
                passwordCipher = cipher,
                passwordIv = iv,
                createdAt = if (entry.createdAt != 0L) entry.createdAt else now,
                updatedAt = now
            )
        )
    }

    override suspend fun delete(id: String) =
        daoFor(spaceController.currentSpace.value).delete(id)

    private fun PasswordEntity.toDomain() = PasswordEntry(
        id = id,
        origin = origin,
        title = title,
        username = username,
        password = try {
            crypto.decrypt(passwordCipher, passwordIv)
        } catch (e: Exception) {
            // Keystore 密钥失效（如清除凭据）时解密失败，返回空串而非崩溃
            ""
        },
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}