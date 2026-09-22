package com.justbrowse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 主菜单：从底部上滑的堆叠式面板。
 *
 * 上半部分是常用操作的图标宫格（含开关状态高亮），下半部分是导航类入口列表。
 * 开关项（朗读 / 适应屏幕 / 阅读模式 / 深色模式 / 独立空间）通过 [active] 高亮当前状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuSheet(
    canGoForward: Boolean,
    darkMode: Boolean,
    fitScreen: Boolean,
    readingMode: Boolean,
    readingAloud: Boolean,
    inPrivateSpace: Boolean,
    onDismiss: () -> Unit,
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    onForward: () -> Unit,
    onFind: () -> Unit,
    onReadAloud: () -> Unit,
    onFitScreen: () -> Unit,
    onReadingMode: () -> Unit,
    onDark: () -> Unit,
    onVideoSniff: () -> Unit,
    onSpace: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onScripts: () -> Unit,
    onSync: () -> Unit,
    onPasswords: () -> Unit,
    onSettings: () -> Unit
) {
    val actions = listOf(
        MenuAction("主页", Icons.Default.Home, onClick = onHome),
        MenuAction("刷新", Icons.Default.Refresh, onClick = onRefresh),
        MenuAction("前进", Icons.AutoMirrored.Filled.ArrowForward, enabled = canGoForward, onClick = onForward),
        MenuAction("页内查找", Icons.Default.Search, onClick = onFind),
        MenuAction("网页朗读", Icons.Default.RecordVoiceOver, active = readingAloud, onClick = onReadAloud),
        MenuAction(
            if (fitScreen) "适应屏幕" else "电脑版",
            Icons.Default.FitScreen,
            active = fitScreen,
            onClick = onFitScreen
        ),
        MenuAction("阅读模式", Icons.AutoMirrored.Filled.Article, active = readingMode, onClick = onReadingMode),
        MenuAction("深色模式", Icons.Default.DarkMode, active = darkMode, onClick = onDark),
        MenuAction("视频嗅探", Icons.Default.OndemandVideo, onClick = onVideoSniff),
        MenuAction("独立空间", Icons.Default.Lock, active = inPrivateSpace, onClick = onSpace)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = "菜单",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
            )

            // 宫格固定 4 列：手动分行而不是用 LazyVerticalGrid，避免与外层滚动嵌套冲突
            actions.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    row.forEach { action ->
                        MenuTile(action = action, modifier = Modifier.weight(1f))
                    }
                    repeat(4 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            MenuRow(Icons.Default.Bookmark, "书签", onBookmarks)
            MenuRow(Icons.Default.History, "历史记录", onHistory)
            MenuRow(Icons.Default.Download, "下载管理", onDownloads)
            MenuRow(Icons.Default.Extension, "油猴脚本", onScripts)
            MenuRow(Icons.Default.Sync, "局域网同步", onSync)
            MenuRow(Icons.Default.Key, "密码管理器", onPasswords)
            MenuRow(Icons.Default.Settings, "设置", onSettings)
        }
    }
}

private data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun MenuTile(action: MenuAction, modifier: Modifier = Modifier) {
    val contentColor = when {
        !action.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        action.active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (action.active) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = action.enabled, onClick = action.onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
