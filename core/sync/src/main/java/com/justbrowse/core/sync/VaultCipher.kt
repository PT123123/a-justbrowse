package com.justbrowse.core.sync

import android.util.Base64
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 密码 vault 加解密。
 *
 * 格式：口令 --PBKDF2WithHmacSHA256(随机 16B salt, 150000 次)--> 256bit 密钥
 *       --AES-256-GCM(随机 12B IV, 128bit tag)--> 密文(Base64)
 * 明文为 PasswordSync 列表的 JSON 字节。
 *
 * 安全约定：仅内存操作；盐与 IV 每次导出重新随机；口令/密文/明文不写日志。
 */
object VaultCipher {

    private const val FORMAT_VERSION = 1
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val KEY_LENGTH_BITS = 256
    private const val TAG_LENGTH_BITS = 128
    private const val KDF_ITERATIONS = 150_000

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    /** 加密密码列表为 vault blob（每次调用重新随机 salt/IV） */
    fun encrypt(entries: List<PasswordSync>, passphrase: CharArray, senderDeviceName: String): VaultBlob {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(passphrase, salt, KDF_ITERATIONS)

        val plain = json.encodeToString(
            ListSerializer(PasswordSync.serializer()),
            entries
        ).toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(plain)

        return VaultBlob(
            formatVersion = FORMAT_VERSION,
            kdfSalt = Base64.encodeToString(salt, Base64.NO_WRAP),
            kdfIterations = KDF_ITERATIONS,
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            itemCount = entries.size,
            senderDeviceName = senderDeviceName
        )
    }

    /** 解密 vault blob；口令错误/数据损坏抛 GeneralSecurityException 或序列化异常 */
    fun decrypt(blob: VaultBlob, passphrase: CharArray): List<PasswordSync> {
        val salt = Base64.decode(blob.kdfSalt, Base64.NO_WRAP)
        val iv = Base64.decode(blob.iv, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(blob.ciphertext, Base64.NO_WRAP)
        val key = deriveKey(passphrase, salt, blob.kdfIterations)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val plain = cipher.doFinal(cipherBytes)

        return json.decodeFromString(
            ListSerializer(PasswordSync.serializer()),
            String(plain, Charsets.UTF_8)
        )
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    /** 生成 8 位随机口令（大小写字母 + 数字，无易混淆字符） */
    fun generatePassphrase(length: Int = 8): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        return (1..length).map { alphabet[random.nextInt(alphabet.length)] }
            .joinToString("")
    }
}
