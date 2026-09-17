package com.justbrowse.ui.screens

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * 密码管理器的锁屏验证门（透明 Activity）。
 *
 * 为什么单独建这个 Activity：系统 BiometricPrompt 接口要求宿主是
 * FragmentActivity，而主浏览器 MainActivity 是 ComponentActivity
 * （Compose 的 BackHandler 依赖它的 OnBackPressedDispatcher），不能改基类。
 * 所以由本透明 Activity 专门承载系统验证流程，结果经 setResult 返回。
 *
 * 验证方式（全部走系统接口）：
 * - Android 11+（API 30）：仅锁屏凭据（PIN/图案/密码），与用户需求一致；
 * - Android 9/10（API 28/29）：锁屏凭据或生物识别（该组合在旧版本上映射为
 *   setDeviceCredentialAllowed）；
 * - 设备未设置锁屏凭据时无法验证，直接放行并在 extra 中注明。
 *
 * PC 端说明：局域网同步预留的 PC 客户端将来应使用各自系统的登录口令校验
 * API（Windows Hello / PAM 等），与本 Activity 无关。
 */
class PasswordAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_NO_LOCK = "no_lock"
        const val EXTRA_ERROR = "error_message"
    }

    private lateinit var executor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = ContextCompat.getMainExecutor(this)

        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceSecure != true) {
            // 无锁屏凭据：无从验证，放行但注明
            setResult(RESULT_OK, intent.putExtra(EXTRA_NO_LOCK, true))
            finish()
            return
        }

        // 提示框文案
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁密码管理器")
            .setSubtitle("验证锁屏密码以查看保存的账号密码")
            .setAllowedAuthenticators(allowedAuthenticators())
            .setConfirmationRequired(false)
            .build()

        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    setResult(RESULT_OK)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    setResult(RESULT_CANCELED, intent.putExtra(EXTRA_ERROR, errString.toString()))
                    finish()
                }
            }
        )
        prompt.authenticate(promptInfo)
    }

    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 仅锁屏凭据（PIN/图案/密码）
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            // API 29 及以下：凭据需与生物识别组合；无生物识别时系统自动回落到凭据输入
            BiometricManager.Authenticators.DEVICE_CREDENTIAL or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
}
