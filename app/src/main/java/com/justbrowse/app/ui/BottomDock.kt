package com.justbrowse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * v3 底部停靠栏（夸克式）：
 * 一条长地址栏胶囊（内嵌返回/前进/刷新/收藏）+ 标签计数按钮 + 更多菜单。
 * 点击胶囊进入搜索覆盖层；点击标签按钮展开标签面板。
 */
@Composable
fun BottomDock(
    state: BrowserUiState,
    isBookmarked: Boolean,
    darkMode: Boolean,
    showMenu: Boolean,
    onCapsuleClick: () -> Unit,
    onTabClick: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStopLoading: () -> Unit,
    onToggleBookmark: () -> Unit,
    onMenuClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    onMenuHome: () -> Unit,
    onMenuRefresh: () -> Unit,
    onMenuFind: () -> Unit,
    onMenuDark: () -> Unit,
    onMenuBookmarks: () -> Unit,
    onMenuHistory: () -> Unit,
    onMenuSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHome = state.activeUrl.isEmpty()
    val isHttps = state.activeUrl.startsWith("https://")
    val shownUrl = state.activeUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ===== 长地址栏胶囊 =====
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onCapsuleClick)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = state.canGoBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onForward, enabled = state.canGoForward) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isHome) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Icon(
                        if (isHttps) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isHttps) "Secure" else "Not secure",
                        tint = if (isHttps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isHome) "搜索或输入网址" else shownUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isHome) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (state.isLoading) {
                    IconButton(onClick = onStopLoading) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Stop",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onReload) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                        tint = if (isBookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // ===== 标签按钮（带计数，点击展开标签面板） =====
            BadgedBox(
                badge = {
                    if (state.tabs.isNotEmpty()) {
                        Badge {
                            Text(text = "${state.tabs.size}")
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                IconButton(onClick = onTabClick) {
                    Icon(
                        Icons.Default.ViewWeek,
                        contentDescription = "Tabs",
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            // ===== 更多菜单 =====
            Box {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = onMenuDismiss) {
                    DropdownMenuItem(
                        text = { Text("Home") },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                        onClick = onMenuHome
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = onMenuRefresh
                    )
                    DropdownMenuItem(
                        text = { Text("Find in page") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        onClick = onMenuFind
                    )
                    DropdownMenuItem(
                        text = { Text(if (darkMode) "Dark mode ✓" else "Dark mode") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = if (darkMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        onClick = onMenuDark
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks") },
                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                        onClick = onMenuBookmarks
                    )
                    DropdownMenuItem(
                        text = { Text("History") },
                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                        onClick = onMenuHistory
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = onMenuSettings
                    )
                }
            }
        }
    }
}
