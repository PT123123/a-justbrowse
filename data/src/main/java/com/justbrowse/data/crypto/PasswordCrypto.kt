package com.justbrowse.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密码字段加解密：Android Keystore AES-256-GCM。
 *
 * - 密钥生成后存于系统 Keystore，不可导出；应用数据被整体提取也无法解密。
 * - 每次加密由 Keystore 生成随机 IV，密文/IV 均以 Base64 存储。
 * - 密文、IV、口令永不写日志。
 */
@Singleton
class PasswordCrypto @Inject constructor() {

    private companion object {
        const val KEY_ALIAS = "justbrowse_pwd_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * 加密明文密码，返回 (Base64 密文, Base64 IV)。
     *
     * 注意：Keystore 密钥默认要求「随机化加密」，即禁止调用方自带 IV
     * （否则抛 InvalidAlgorithmParameterException: Caller-provided IV not permitted）。
     * 因此这里不传 GCMParameterSpec，由 Keystore 生成随机 IV，
     * 加密后从 cipher.iv 取出与密文一起存库，解密时再作为参数传入。
     */
    fun encrypt(plain: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipherBytes, Base64.NO_WRAP) to
            Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    /** 解密 Base64 密文 + Base64 IV；失败抛出异常由调用方处理 */
    fun decrypt(cipherB64: String, ivB64: String): String {
        val cipherBytes = Base64.decode(cipherB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }
}
