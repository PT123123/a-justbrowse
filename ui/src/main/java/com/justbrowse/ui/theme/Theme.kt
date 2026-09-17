package com.justbrowse.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.justbrowse.data.prefs.DarkThemeVariant
import com.justbrowse.data.prefs.ThemeMode

/**
 * 当前是否处于暗色主题。
 *
 * 由 [JustBrowseTheme] 统一提供，供底层组件（主屏、标签面板等）直接读取，
 * 不要再靠「背景色亮度」之类的启发式去猜主题 —— 动态取色下那种猜法并不可靠。
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** 把 [ThemeMode] 解析为最终是否暗色（SYSTEM 时跟随系统）。 */
@Composable
fun resolveDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/** 默认深色：以 #121212 打底 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF0B2A5B),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD3E3FF),
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = SurfaceDark,
    onBackground = Color(0xFFE5E2E6),
    surface = SurfaceDark,
    onSurface = Color(0xFFE5E2E6),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA9A9B2),
    outline = Color(0xFF3C3C44),
    outlineVariant = Color(0xFF2E2E35)
)

/** 纯黑（AMOLED）：背景换成 #000000，配色基调与默认深色保持一致 */
private val AmoledColorScheme = DarkColorScheme.copy(
    background = SurfaceAmoled,
    surface = SurfaceAmoled,
    surfaceVariant = AmoledSurfaceVariant,
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1A1A1A),
    // 弹窗（AlertDialog）、下拉菜单（DropdownMenu）用的是 surfaceContainer* 系列，
    // 不一起压暗的话，纯黑模式下会冒出几块灰底浮层。
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun JustBrowseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkThemeVariant: DarkThemeVariant = DarkThemeVariant.DEFAULT,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = resolveDarkTheme(themeMode)

    val colorScheme = when {
        // 纯黑是用户明确点选的配色，优先级高于动态取色 ——
        // 否则系统强调色会把 #000000 背景冲掉，这个变体就白选了。
        darkTheme && darkThemeVariant == DarkThemeVariant.AMOLED -> AmoledColorScheme
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
