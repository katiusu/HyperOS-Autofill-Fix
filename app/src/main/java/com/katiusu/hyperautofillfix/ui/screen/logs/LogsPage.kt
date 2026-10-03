package com.katiusu.hyperautofillfix.ui.screen.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katiusu.hyperautofillfix.R
import com.katiusu.hyperautofillfix.data.LogEvent
import com.katiusu.hyperautofillfix.data.LogLevel
import com.katiusu.hyperautofillfix.data.LogStore
import com.katiusu.hyperautofillfix.prefs.ConfigState
import com.katiusu.hyperautofillfix.ui.screen.settings.AppOptionKeys
import com.katiusu.hyperautofillfix.ui.util.BlurredBar
import com.katiusu.hyperautofillfix.ui.util.blurSource
import com.katiusu.hyperautofillfix.ui.util.pageScrollModifiers
import com.katiusu.hyperautofillfix.ui.util.rememberBlurState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FILTER_ALL = 0
private const val FILTER_BLOCK = 1
private const val FILTER_ALLOW = 2
private const val FILTER_INFO = 3

private val FILTER_LABELS = listOf(
    R.string.logs_filter_all,
    R.string.logs_filter_block,
    R.string.logs_filter_allow,
    R.string.logs_filter_info,
)

/**
 * 日志页：一条事件一行（同一类流水不套 Card），可切直观排版 / logcat 原文、按级别筛选、按关键词搜索。
 *
 * 「记录放行事件」「默认显示原文」两个开关直接读配置管道，设置页改完这里立刻跟着变。
 */
@Composable
fun LogsPageView(
    events: List<LogEvent>,
    isBlurEnabled: Boolean,
    extraBottomPadding: Dp,
    onOpenSettings: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else scheme.surface
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val rawView = ConfigState.bool(AppOptionKeys.RAW_VIEW, false)
    val showAllowed = ConfigState.bool(AppOptionKeys.SHOW_ALLOWED, true)

    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filtered = remember(events, filter, showAllowed, query) {
        events.filter { event ->
            val levelMatch = when (filter) {
                FILTER_BLOCK -> event.level == LogLevel.BLOCK
                FILTER_ALLOW -> event.level == LogLevel.ALLOW
                FILTER_INFO -> event.level == LogLevel.INFO
                else -> true
            }
            val allowedMatch = showAllowed || event.level != LogLevel.ALLOW
            val queryMatch = query.isBlank() ||
                event.pkg.contains(query, ignoreCase = true) ||
                event.hook.contains(query, ignoreCase = true) ||
                (event.value?.contains(query, ignoreCase = true) == true)
            levelMatch && allowedMatch && queryMatch
        }
    }
    val suggestions = remember(events) { events.map { it.pkg }.distinct().take(6) }
    val blockedCount = remember(events) { events.count { it.level == LogLevel.BLOCK } }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    val summaryText = buildString {
        append(stringResource(R.string.logs_summary_total, filtered.size))
        if (blockedCount > 0) {
            append(" · ").append(stringResource(R.string.logs_summary_blocked, blockedCount))
        }
        append(" · ")
        append(stringResource(if (rawView) R.string.logs_summary_raw else R.string.logs_summary_pretty))
    }

    val emptyText = when {
        !showAllowed && filter == FILTER_ALLOW -> stringResource(R.string.logs_empty_no_allowed)
        events.isEmpty() -> stringResource(R.string.logs_empty_no_events)
        else -> stringResource(R.string.logs_empty_filtered)
    }

    // 换了筛选条件或清空搜索后，把列表拉回顶部，否则会停在旧位置看起来像"没变化"。
    LaunchedEffect(filtered.firstOrNull()?.id) {
        if (filtered.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, scrollBehavior) {
                TopAppBar(
                    title = stringResource(R.string.tab_logs),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = { ConfigState.set(AppOptionKeys.RAW_VIEW, !rawView) },
                        ) {
                            Icon(
                                imageVector = if (rawView) MiuixIcons.ListView else MiuixIcons.File,
                                contentDescription = stringResource(
                                    if (rawView) R.string.logs_toggle_pretty else R.string.logs_toggle_raw,
                                ),
                            )
                        }
                        if (events.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = stringResource(R.string.logs_clear),
                                )
                            }
                        }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.blurSource(if (isBlurEnabled) hazeState else null)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(showTopAppBar = true, topAppBarScrollBehavior = scrollBehavior),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding,
                ),
            ) {
                item(key = "search") {
                    SearchBar(
                        inputField = {
                            InputField(
                                query = query,
                                onQueryChange = { query = it },
                                // 回车不是「退出搜索」：Miuix 的 InputField 在「收起且仍有焦点」时会主动
                                // onQueryChange("") 清空 query（SearchBar 内部的 LaunchedEffect(expanded)），
                                // 而本页的过滤依赖 query —— 一旦收起，关键词和筛选结果会一起消失。
                                // 所以这里只收键盘、保持展开（在 Miuix 里 expanded 就是「正在搜索」）。
                                onSearch = { focusManager.clearFocus() },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = stringResource(R.string.logs_search_label),
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 4.dp),
                    ) {
                        suggestions.forEach { pkg ->
                            BasicComponent(
                                title = pkg,
                                summary = stringResource(R.string.logs_suggestion_summary),
                                onClick = { query = pkg },
                            )
                        }
                    }
                }

                item(key = "filter") {
                    TabRow(
                        tabs = FILTER_LABELS.map { stringResource(it) },
                        selectedTabIndex = filter,
                        onTabSelected = { filter = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp, bottom = 4.dp),
                    )
                }

                item(key = "summary") {
                    MiuixText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        text = summaryText,
                        style = MiuixTheme.textStyles.footnote2,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }

                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier
                                .fillParentMaxHeight(0.6f)
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            MiuixText(
                                modifier = Modifier.fillMaxWidth(),
                                text = emptyText,
                                fontSize = 15.sp,
                                color = scheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                            )
                            if (!showAllowed && filter == FILTER_ALLOW) {
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    text = stringResource(R.string.tab_settings),
                                    onClick = onOpenSettings,
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            }
                        }
                    }
                } else {
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
    }

    if (showClearConfirm) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.clear_logs_dialog_title),
            summary = stringResource(R.string.clear_logs_dialog_summary),
            onDismissRequest = { showClearConfirm = false },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.clear_logs_action),
                    onClick = {
                        LogStore.clear()
                        showClearConfirm = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun EventRow(
    event: LogEvent,
    rawView: Boolean,
    timeText: String,
) {
    val summary = buildString {
        append(timeText)
        if (event.hook.isNotBlank() && event.level != LogLevel.INFO) {
            append(" · ").append(event.hook)
        }
        if (event.pkg.isNotBlank()) {
            append(" · ").append(event.pkg)
        }
    }
    val detail = if (rawView) {
        event.raw
    } else {
        event.value?.let { value ->
            if (event.level == LogLevel.INFO) value else stringResource(R.string.logs_value_prefix, value)
        }
    }
    BasicComponent(
        title = stringResource(levelTitle(event.level)),
        summary = summary,
        startAction = { LevelDot(event.level) },
        bottomAction = detail?.let {
            {
                MiuixText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    text = it,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
    )
}

@Composable
private fun LevelDot(level: LogLevel) {
    Box(
        modifier = Modifier
            .padding(end = 10.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(levelColor(level)),
    )
}

/** 拦截是模块的正常工作结果，不是错误，所以用 primary 而不是 error。 */
@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.BLOCK -> MiuixTheme.colorScheme.primary
    LogLevel.ALLOW -> MiuixTheme.colorScheme.onSurfaceVariantActions
    LogLevel.INFO -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

private fun levelTitle(level: LogLevel): Int = when (level) {
    LogLevel.BLOCK -> R.string.logs_level_block
    LogLevel.ALLOW -> R.string.logs_level_allow
    LogLevel.INFO -> R.string.logs_level_info
}
