package com.katiusu.hyperautofillfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.katiusu.hyperautofillfix.data.LogEvent
import com.katiusu.hyperautofillfix.data.LogLevel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FILTER_ALL = 0
private const val FILTER_BLOCK = 1
private const val FILTER_ALLOW = 2
private const val FILTER_INFO = 3

private val FILTER_LABELS = listOf("全部", "拦截", "放行", "信息")

/**
 * 日志页：搜索 + 级别筛选 + 事件列表。
 * 直观 / 原文两种视图由外壳顶栏的按钮切换，当前模式在这里用文字同时说明，不靠图标单独表达。
 */
@Composable
fun LogsScreen(
    events: List<LogEvent>,
    rawView: Boolean,
    showAllowed: Boolean,
    scrollBehavior: ScrollBehavior,
    contentBottomPadding: Dp,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    val focusManager = LocalFocusManager.current

    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    val filtered = remember(events, query, filter, showAllowed) {
        val keyword = query.trim()
        events.filter { event ->
            val levelOk = when (filter) {
                FILTER_BLOCK -> event.level == LogLevel.BLOCK
                FILTER_ALLOW -> event.level == LogLevel.ALLOW
                FILTER_INFO -> event.level == LogLevel.INFO
                else -> true
            }
            val allowedOk = showAllowed || event.level != LogLevel.ALLOW
            val keywordOk = keyword.isEmpty() ||
                event.pkg.contains(keyword, ignoreCase = true) ||
                event.hook.contains(keyword, ignoreCase = true) ||
                (event.value?.contains(keyword, ignoreCase = true) == true)
            levelOk && allowedOk && keywordOk
        }
    }
    val blockedCount = remember(filtered) { filtered.count { it.level == LogLevel.BLOCK } }
    val suggestions = remember(events) { events.map { it.pkg }.distinct().take(6) }

    LaunchedEffect(filtered.firstOrNull()?.id) {
        if (filtered.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // 悬浮玻璃底栏不占布局高度，这里让滚动内容在末端自己让开
        contentPadding = PaddingValues(bottom = contentBottomPadding),
    ) {
        item(key = "search") {
            SearchBar(
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = { query = it },
                        // 回车不是"退出搜索"：Miuix 的 InputField 在「收起且仍有焦点」时会主动
                        // onQueryChange("") 清空 query（SearchBar.kt 的 LaunchedEffect(expanded)），
                        // 而本页的列表过滤依赖 query —— 一旦收起，关键词和筛选结果会一起消失。
                        // 因此这里只收键盘，保持展开（在 Miuix 里 expanded 就是"正在搜索"）。
                        onSearch = { focusManager.clearFocus() },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = "搜索进程 / 层次 / 写入值",
                    )
                },
                onExpandedChange = { searchExpanded = it },
                expanded = searchExpanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
            ) {
                suggestions.forEach { pkg ->
                    BasicComponent(
                        title = pkg,
                        summary = "只看这个进程的事件",
                        // 同一个坑：收起会被 Miuix 清掉 query，所以选完保持展开，退出搜索交给 Back。
                        onClick = { query = pkg },
                    )
                }
            }
        }

        item(key = "filter") {
            TabRow(
                tabs = FILTER_LABELS,
                selectedTabIndex = filter,
                onTabSelected = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 4.dp),
            )
        }

        item(key = "summary") {
            Text(
                text = buildString {
                    append("共 ${filtered.size} 条")
                    if (blockedCount > 0) append(" · 拦截 $blockedCount 条")
                    append(if (rawView) " · 当前显示 logcat 原文" else " · 当前显示直观排版")
                },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            )
        }

        if (filtered.isEmpty()) {
            // 空状态不是设置分组，用页面画布上的说明文字表达，避免出现一张孤立的 Card
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(0.6f)
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            !showAllowed && filter == FILTER_ALLOW ->
                                "「记录放行事件」已在设置里关闭，放行事件不会被记录，因此这里为空。"

                            events.isEmpty() ->
                                "还没有日志。启用模块并重启后，系统管家每次尝试改写都会在这里出现。"

                            else ->
                                "没有符合当前筛选条件的事件，换个级别或清空搜索词试试。"
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // 日志是同一类记录的流水，不再给每条记录套一张 Card（那是设计语言里点名的失败做法），
            // 而是用行 + 分割线的平坦列表，行结构由 BasicComponent 负责
            itemsIndexed(filtered, key = { _, event -> event.id }) { index, event ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                EventRow(
                    event = event,
                    rawView = rawView,
                    timeText = timeFormat.format(Date(event.time)),
                )
            }
        }
    }
}

@Composable
private fun EventRow(event: LogEvent, rawView: Boolean, timeText: String) {
    val scheme = MiuixTheme.colorScheme

    if (rawView) {
        // 原文视图：唯一使用等宽字体的地方，方便与 `adb logcat -s HyperOSAutofillFix` 对照
        Text(
            text = event.raw,
            style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
            color = scheme.onSurfaceVariantSummary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    } else {
        BasicComponent(
            title = levelTitle(event.level),
            summary = buildString {
                append(timeText)
                if (event.level != LogLevel.INFO) {
                    append(" · ").append(event.hook)
                }
                append(" · ").append(event.pkg)
            },
            // 级别标识放在组件自带的 leading 槽位，颜色 + 文字双重编码
            startAction = { LevelDot(event.level) },
            bottomAction = event.value?.let { value ->
                {
                    Text(
                        text = if (event.level == LogLevel.INFO) value else "写入值：$value",
                        style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            },
        )
    }
}

/** 事件级别的小圆点，只做视觉辅助，级别本身由标题文字表达。 */
@Composable
private fun LevelDot(level: LogLevel) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(levelColor(level)),
    )
}

@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    // 拦截是本模块正常工作的结果，不是错误状态，所以用主色而不是 error
    LogLevel.BLOCK -> MiuixTheme.colorScheme.primary
    LogLevel.ALLOW -> MiuixTheme.colorScheme.onSurfaceVariantActions
    LogLevel.INFO -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

private fun levelTitle(level: LogLevel): String = when (level) {
    LogLevel.BLOCK -> "已拦截改写"
    LogLevel.ALLOW -> "放行合法写入"
    LogLevel.INFO -> "模块信息"
}
