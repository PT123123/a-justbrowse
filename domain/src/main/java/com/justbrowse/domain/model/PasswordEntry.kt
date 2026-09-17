package com.justbrowse.domain.model

/**
 * 密码条目。password 为明文，仅存在于内存中；
 * 数据库内只存 Keystore 加密后的密文（见 data 层 PasswordCrypto）。
 */
data class PasswordEntry(
    val id: String,
    /** 站点源，如 "github.com"，按域匹配用于自动填充 */
    val origin: String,
    val title: String,
    val username: String,
    val password: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
