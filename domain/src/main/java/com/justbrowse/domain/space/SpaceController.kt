package com.justbrowse.domain.space

import com.justbrowse.domain.model.SpaceId
import kotlinx.coroutines.flow.StateFlow

/**
 * 独立空间的状态与切换。
 *
 * 独立空间一旦配置，进入前必须验证：设备有锁屏凭据时走系统验证（锁屏密码 /
 * 生物识别），没有锁屏凭据时才退回应用内 PIN。应用每次冷启动都回到
 * [SpaceId.MAIN]，需要再次进入时才唤起验证 —— 保证残留的独立空间会话不会在下次
 * 打开应用时绕过验证直接显示。
 */
interface SpaceController {
    val currentSpace: StateFlow<SpaceId>

    /** 独立空间是否已设置应用内 PIN（只有设备无锁屏凭据、走 PIN 兜底时才会用到） */
    val privateConfigured: StateFlow<Boolean>

    /**
     * 无锁屏凭据时的兜底：设置应用内 PIN 并立即进入。
     * [pin] 长度校验由 UI 负责；错误空串会抛 [IllegalArgumentException]。
     */
    suspend fun configureAndEnterPrivateSpace(pin: String)

    /**
     * 验证并进入独立空间。
     * - [pin] 非空：按应用内 PIN 校验，密码错误返回 false（不进入）。
     * - [pin] 为 null：视为已通过系统验证（锁屏密码 / 生物识别），直接进入。
     */
    suspend fun unlockAndEnterPrivateSpace(pin: String?): Boolean

    /** 返回主空间（独立空间保持已配置，下次进入仍需验证） */
    suspend fun switchToMain()
}