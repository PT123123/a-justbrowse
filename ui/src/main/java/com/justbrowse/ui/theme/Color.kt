package com.justbrowse.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Brand colors for JustBrowse
val BrowserBlue = Color(0xFF1A73E8)
val BrowserBlueDark = Color(0xFF0B57D0)
val SurfaceLight = Color(0xFFFFFBFE)
val SurfaceDark = Color(0xFF121212)

/**
 * 暗色主题的次级表面（地址栏胶囊、标签卡片底色等）。
 * 与 [SurfaceDark] 拉开一点层次，避免整屏一个色。
 */
val DarkSurfaceVariant = Color(0xFF26262B)

// ===== 纯黑（AMOLED）变体 =====
/** 纯黑底色：OLED 屏像素可完全熄灭，最省电、对比最强 */
val SurfaceAmoled = Color(0xFF000000)
/** 纯黑下的次级表面，仅比底色亮一点点，保住卡片边界 */
val AmoledSurfaceVariant = Color(0xFF141414)
