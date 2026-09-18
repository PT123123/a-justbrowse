package com.justbrowse.domain.model

/**
 * 应用空间：主空间与独立空间。
 * 独立空间对浏览数据（cookie / 站点存储 / 历史 / 书签 / 密码 / 标签）完全隔离，
 * 但油猴脚本与全局设置共享。
 */
enum class SpaceId {
    MAIN,
    PRIVATE;

    val displayName: String
        get() = when (this) {
            MAIN -> "主空间"
            PRIVATE -> "独立空间"
        }

    /** 独立空间使用的 WebView profile 名称；主空间用系统默认 profile（null）。 */
    val webViewProfileName: String?
        get() = when (this) {
            MAIN -> null
            PRIVATE -> "justbrowse_private_space"
        }
}