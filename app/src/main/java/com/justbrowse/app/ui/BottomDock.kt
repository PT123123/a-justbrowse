package com.justbrowse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
 * 一条长地址栏胶囊（内嵌返回/刷新/安全状态）+ 标签计数按钮 + 更多菜单按钮。
 * 点击胶囊进入搜索覆盖层；点击标签按钮展开标签面板；点击更多按钮弹出底部菜单面板。
 */
@Composable
fun BottomDock(
    state: BrowserUiState,
    onCapsuleClick: () -> Unit,
    onTabClick: () -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onStopLoading: () -> Unit,
    onMenuClick: () -> Unit,
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
                        contentDescription = "后退",
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
                        contentDescription = if (isHttps) "安全连接" else "不安全",
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
                            contentDescription = "停止加载",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onReload) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                        contentDescription = "标签页",
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            // ===== 更多菜单（面板由 BrowserScreen 的 MainMenuSheet 呈现） =====
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多")
            }
        }
    }
}
