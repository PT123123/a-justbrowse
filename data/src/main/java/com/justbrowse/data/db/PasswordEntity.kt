package com.justbrowse.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 密码表。passwordCipher/passwordIv 为 Keystore AES-GCM 加密后的
 * Base64 密文与 IV，数据库中不存明文密码。
 */
@Entity(tableName = "passwords", indices = [Index(value = ["origin"])])
data class PasswordEntity(
    @PrimaryKey val id: String,
    val origin: String,
    val title: String,
    val username: String,
    val passwordCipher: String,
    val passwordIv: String,
    val createdAt: Long,
    val updatedAt: Long
)
