package com.justbrowse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justbrowse.ui.theme.LocalIsDarkTheme

/**
 * 夸克式主屏（v4）：
 * 居中 Logo + 名称；搜索卡为普通纯文本输入（输入即所见，无气泡装饰）；
 * 下方 4 列快捷图标网格直达常用站点。
 */
private data class HomeTile(val title: String, val url: String, val colors: List<Color>)

private val HOME_TILES = listOf(
    HomeTile("百度", "https://www.baidu.com", listOf(Color(0xFF4A8DFF), Color(0xFF1D4ED8))),
    HomeTile("哔哩哔哩", "https://www.bilibili.com", listOf(Color(0xFFFB8FB0), Color(0xFFE0487E))),
    HomeTile("GitHub", "https://github.com", listOf(Color(0xFF4B5563), Color(0xFF161B22))),
    HomeTile("知乎", "https://www.zhihu.com", listOf(Color(0xFF3F9BFF), Color(0xFF005FCC))),
    HomeTile("微博", "https://weibo.com", listOf(Color(0xFFFF7A6B), Color(0xFFE6162D))),
    HomeTile("淘宝", "https://www.taobao.com", listOf(Color(0xFFFFA24D), Color(0xFFFF5000))),
    HomeTile("抖音", "https://www.douyin.com", listOf(Color(0xFF3A4149), Color(0xFF101418))),
    HomeTile("豆瓣", "https://www.douban.com", listOf(Color(0xFF3FC47E), Color(0xFF00A31A)))
)

@Composable
fun HomeScreen(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tfValue by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 暗色与否由主题统一提供（LocalIsDarkTheme），不要靠背景亮度去猜 —— 动态取色下不可靠
    val isDark = LocalIsDarkTheme.current
    val accent = if (isDark) Color(0xFF5B8DEF) else Color(0xFF2E6FEA)
    val searchBg = MaterialTheme.colorScheme.surfaceVariant
    val searchStroke = if (isDark) Color(0xFF2E323B) else Color(0xFFE5E5EA)
    val textSub = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            // 系统栏与底部停靠栏的安全区已由 BrowserScreen 的内容区统一留出，这里不再重复处理
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1.25f))

        // ===== Logo + 名称 =====
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4A8DFF), Color(0xFF0B57D0))))
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "J",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "JustBrowse",
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp
            )
        }
        Spacer(Modifier.height(30.dp))

        // ===== 搜索卡：普通纯文本输入，所见即所得 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (focused) MaterialTheme.colorScheme.surface else searchBg)
                .border(
                    width = if (focused) 1.5.dp else 1.dp,
                    color = if (focused) accent else searchStroke,
                    shape = RoundedCornerShape(16.dp)
                )
                // 点击搜索卡空白处也聚焦输入框
                .pointerInput(Unit) {
                    detectTapGestures { focusRequester.requestFocus() }
                }
        ) {
            BasicTextField(
                value = tfValue,
                onValueChange = { tfValue = it },
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(color = textSub, fontSize = 15.sp),
                cursorBrush = SolidColor(accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false
                ),
                keyboardActions = KeyboardActions(onGo = { onOpenUrl(tfValue) }),
                decorationBox = { inner ->
                    Row(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = textSub,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (tfValue.isEmpty()) {
                                Text(
                                    text = "搜索或输入网址",
                                    fontSize = 15.sp,
                                    color = textSub
                                )
                            }
                            inner()
                        }
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = textSub, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = textSub, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                }
            )
        }

        Spacer(Modifier.height(36.dp))

        // ===== 4 列快捷图标 =====
        val rows = HOME_TILES.chunked(4)
        Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            rows.forEach { rowTiles ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rowTiles.forEachIndexed { i, tile ->
                        if (i > 0) Spacer(Modifier.weight(1f))
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenUrl(tile.url) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Brush.linearGradient(tile.colors))
                                    .shadow(5.dp, RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tile.title.take(1),
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = tile.title,
                                fontSize = 12.sp,
                                color = textSub
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "输入网址或搜索关键词，回车开始浏览",
            fontSize = 11.sp,
            color = if (isDark) Color(0xFF6B7280) else Color(0xFFB0B6BF),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )
    }
}