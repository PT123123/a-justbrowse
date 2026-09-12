package com.justbrowse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justbrowse.data.suggestions.DefaultSites
import com.justbrowse.ui.theme.LocalIsDarkTheme
import kotlin.math.roundToInt

/**
 * 夸克式主屏（v4）：
 * 居中 Logo + 名称；搜索卡支持「关键词气泡」（输入实时分词、点击选中、退格/× 整块删除）；
 * 下方「滑动定位轨道」拖动圆点精确控制光标位置；4 列快捷图标网格直达常用站点。
 * 规格设计令牌：search-bg #F2F2F7、bubble #EAF2FF/#2E6FEA、圆角 16/10/22、底部栏 56pt。
 */

data class SearchToken(
    val text: String,
    val isSeparator: Boolean,
    val start: Int,
    val wordIndex: Int
)

private val SEPARATOR_REGEX = Regex("(\\s+|[，。！？、；：,.;:!?·…—]+)")

internal fun tokenizeSearch(value: String): List<SearchToken> {
    val out = mutableListOf<SearchToken>()
    var last = 0
    var wi = -1
    SEPARATOR_REGEX.findAll(value).forEach { m ->
        if (m.range.first > last) {
            wi++
            out += SearchToken(value.substring(last, m.range.first), false, last, wi)
        }
        out += SearchToken(m.value, true, m.range.first, -1)
        last = m.range.last + 1
    }
    if (last < value.length) {
        wi++
        out += SearchToken(value.substring(last), false, last, wi)
    }
    return out
}

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
    var tfValue by remember { mutableStateOf(TextFieldValue("")) }
    val tokens = remember(tfValue.text) { tokenizeSearch(tfValue.text) }
    var activeWi by remember { mutableStateOf(-1) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val bubbleScroll = rememberScrollState()

    // 暗色与否由主题统一提供（LocalIsDarkTheme），不要靠背景亮度去猜 —— 动态取色下不可靠
    val isDark = LocalIsDarkTheme.current
    val accent = if (isDark) Color(0xFF5B8DEF) else Color(0xFF2E6FEA)
    val bubbleBg = if (isDark) Color(0xFF20304F) else Color(0xFFEAF2FF)
    val bubbleText = if (isDark) Color(0xFF8FB6FF) else Color(0xFF2E6FEA)
    val searchBg = MaterialTheme.colorScheme.surfaceVariant
    val searchStroke = if (isDark) Color(0xFF2E323B) else Color(0xFFE5E5EA)
    val textSub = MaterialTheme.colorScheme.onSurfaceVariant

    fun deleteWord(wi: Int) {
        val idx = tokens.indexOfFirst { it.wordIndex == wi }
        if (idx < 0) return
        val keep = tokens.toMutableList()
        keep.removeAt(idx)
        if (keep.getOrNull(idx)?.isSeparator == true) keep.removeAt(idx)
        else if (keep.getOrNull(idx - 1)?.isSeparator == true) keep.removeAt(idx - 1)
        val newText = keep.joinToString("") { it.text }
        tfValue = TextFieldValue(newText, selection = TextRange(newText.length))
        activeWi = -1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 76.dp)          // 给底部停靠栏让位
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

        // ===== 搜索卡：气泡区 + 透明输入 + 光标 =====
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
        ) {
            // 真实输入层（文本透明，仅光标可见）
            BasicTextField(
                value = tfValue,
                onValueChange = { new ->
                    tfValue = new
                    activeWi = -1
                },
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace && activeWi >= 0
                        ) {
                            deleteWord(activeWi)
                            true
                        } else {
                            false
                        }
                    }
                    .padding(start = 40.dp, end = 78.dp),
                textStyle = TextStyle(color = Color.Transparent, fontSize = 15.sp),
                cursorBrush = if (activeWi >= 0) SolidColor(Color.Transparent) else SolidColor(accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false
                ),
                keyboardActions = KeyboardActions(onGo = { onOpenUrl(tfValue.text) })
            )

            // 装饰层（气泡 + 图标，位于输入层之上）
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = textSub,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(9.dp))

                // 气泡区（单行横向滚动）
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(bubbleScroll)
                        .height(34.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tfValue.text.isEmpty() && !focused) {
                        Text(
                            text = "搜索或输入网址",
                            fontSize = 15.sp,
                            color = textSub
                        )
                    }
                    tokens.forEach { token ->
                        if (token.isSeparator) {
                            Text(
                                text = token.text,
                                fontSize = 14.5.sp,
                                color = textSub
                            )
                        } else {
                            val selected = activeWi == token.wordIndex
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bubbleBg)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (selected) accent else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        activeWi = if (selected) -1 else token.wordIndex
                                        focusRequester.requestFocus()
                                    }
                                    .padding(start = 9.dp, top = 4.dp, end = 7.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = token.text,
                                    fontSize = 14.5.sp,
                                    color = bubbleText,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (selected) {
                                    Spacer(Modifier.width(3.dp))
                                    IconButton(
                                        onClick = { deleteWord(token.wordIndex) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "删除",
                                            tint = bubbleText,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(5.dp))
                        }
                    }
                }

                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.Mic, contentDescription = null, tint = textSub, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = textSub, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
            }
        }

        // ===== 滑动定位轨道（聚焦且有内容时显示） =====
        if (focused && tfValue.text.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            CaretTrack(
                textLength = tfValue.text.length,
                selection = tfValue.selection.start.coerceIn(0, tfValue.text.length),
                onSeek = { pos ->
                    tfValue = tfValue.copy(selection = TextRange(pos, pos))
                },
                accent = accent,
                rail = if (isDark) Color(0xFF2E323B) else Color(0xFFE2E5EA),
                textSub = textSub,
                modifier = Modifier.fillMaxWidth()
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
            text = "试试输入「北京 天气」——词会自动变成气泡，退格整块删除\n拖动下方圆点可精确定位光标",
            fontSize = 11.sp,
            color = if (isDark) Color(0xFF6B7280) else Color(0xFFB0B6BF),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )
    }
}

/**
 * 滑动定位光标（规格 §3）：
 * 轨道可视区间 [padLeft, padRight] = [14dp, 宽-14dp]；
 * ratio = clamp((x - left - pad) / (宽 - 2*pad)) → pos = round(ratio * L)。
 */
@Composable
private fun CaretTrack(
    textLength: Int,
    selection: Int,
    onSeek: (Int) -> Unit,
    accent: Color,
    rail: Color,
    textSub: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val padPx = with(density) { 14.dp.toPx() }
    var widthPx by remember { mutableStateOf(0f) }

    fun posFromX(x: Float): Int {
        val usable = (widthPx - 2 * padPx).coerceAtLeast(1f)
        val r = ((x - padPx) / usable).coerceIn(0f, 1f)
        return (r * textLength).roundToInt()
    }

    val ratio = if (textLength > 0) selection.toFloat() / textLength else 0f
    val thumbCenter = padPx + ratio * (widthPx - 2 * padPx)
    val fillWidth = if (widthPx > 0) thumbCenter else padPx

    Box(
        modifier = modifier
            .height(30.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(textLength) {
                detectDragGestures(
                    onDragStart = { onSeek(posFromX(it.x)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onSeek(posFromX(change.position.x))
                    }
                )
            }
            .pointerInput(textLength) {
                detectTapGestures { onSeek(posFromX(it.x)) }
            }
    ) {
        // 轨道线
        Box(
            Modifier
                .padding(start = 14.dp, end = 54.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(rail)
        )
        // 已滑过部分
        Box(
            Modifier
                .padding(start = 14.dp)
                .width(with(density) { fillWidth.dp } - 14.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = 0.35f))
        )
        // 拇指圆点：22dp 白圆 + 蓝描边 + 阴影
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((thumbCenter - with(density) { 11.dp.toPx() }).roundToInt(), 0) }
                .size(22.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.55f))
            )
        }
        // 位置指示
        Text(
            text = "$selection / $textLength",
            fontSize = 10.5.sp,
            color = textSub,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
        )
    }
}
