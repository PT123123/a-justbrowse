package com.justbrowse.data.repository

import com.justbrowse.data.crypto.PasswordCrypto
import com.justbrowse.data.db.PasswordDao
import com.justbrowse.data.db.PasswordEntity
import com.justbrowse.domain.model.PasswordEntry
import com.justbrowse.domain.repository.PasswordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordRepositoryImpl @Inject constructor(
    private val passwordDao: PasswordDao,
    private val crypto: PasswordCrypto
) : PasswordRepository {

    override fun observeAll(): Flow<List<PasswordEntry>> =
        passwordDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findByOrigin(origin: String): List<PasswordEntry> =
        passwordDao.getByOrigin(origin).map { it.toDomain() }

    override suspend fun search(query: String): List<PasswordEntry> =
        passwordDao.search(query).map { it.toDomain() }

    override suspend fun upsert(entry: PasswordEntry) {
        val now = System.currentTimeMillis()
        // 更新时若密码为空（用户未改动密码框），沿用库中已有密文
        val existing = passwordDao.getById(entry.id)
        val (cipher, iv) = if (entry.password.isEmpty() && existing != null) {
            existing.passwordCipher to existing.passwordIv
        } else {
            crypto.encrypt(entry.password)
        }
        passwordDao.upsert(
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

    override suspend fun delete(id: String) = passwordDao.delete(id)

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
