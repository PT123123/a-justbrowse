package com.justbrowse.data.space

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.justbrowse.domain.model.SpaceId
import com.justbrowse.domain.space.SpaceController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.spaceDataStore: DataStore<Preferences> by preferencesDataStore(name = "space")

/**
 * [SpaceController] 的 DataStore 实现。
 *
 * 进入独立空间默认走系统验证（锁屏密码 / 生物识别，见 UI 层），本类只负责
 * 「设备没有锁屏凭据」时的 PIN 兜底凭证：以「随机盐 + PBKDF2(SHA-256) 哈希」形式保存，
 * 口令明文永不落盘；当前处于哪个空间只在内存中记录 —— 每次冷启动都回到主空间，
 * 保证进入前必然触发验证。
 */
@Singleton
class SpaceControllerImpl @Inject constructor(
    @ApplicationContext context: Context
) : SpaceController {

    private val dataStore = context.spaceDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val PRIVATE_CONFIGURED = booleanPreferencesKey("private_configured")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
    }

    private val _currentSpace = MutableStateFlow(SpaceId.MAIN)
    override val currentSpace: StateFlow<SpaceId> = _currentSpace.asStateFlow()

    override val privateConfigured: StateFlow<Boolean> =
        dataStore.data
            .map { it[Keys.PRIVATE_CONFIGURED] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun configureAndEnterPrivateSpace(pin: String) {
        require(pin.isNotEmpty()) { "PIN 不能为空" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        dataStore.edit {
            it[Keys.PIN_SALT] = Base64.getEncoder().encodeToString(salt)
            it[Keys.PIN_HASH] = Base64.getEncoder().encodeToString(hash)
            it[Keys.PRIVATE_CONFIGURED] = true
        }
        _currentSpace.value = SpaceId.PRIVATE
    }

    override suspend fun unlockAndEnterPrivateSpace(pin: String?): Boolean {
        if (pin == null) {
            // 系统验证（锁屏密码 / 生物识别）已由 UI 层完成
            _currentSpace.value = SpaceId.PRIVATE
            return true
        }
        val prefs = dataStore.data.first()
        val saltB64 = prefs[Keys.PIN_SALT] ?: return false
        val hashB64 = prefs[Keys.PIN_HASH] ?: return false
        val expected = Base64.getDecoder().decode(hashB64)
        val actual = pbkdf2(pin, Base64.getDecoder().decode(saltB64))
        val ok = constantTimeEquals(actual, expected)
        if (ok) _currentSpace.value = SpaceId.PRIVATE
        return ok
    }

    override suspend fun switchToMain() {
        _currentSpace.value = SpaceId.MAIN
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 210_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}