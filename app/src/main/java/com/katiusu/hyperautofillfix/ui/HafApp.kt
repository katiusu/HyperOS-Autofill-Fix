package com.katiusu.hyperautofillfix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.katiusu.hyperautofillfix.data.LogStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.window.WindowDialog

private const val PAGE_OVERVIEW = 0
private const val PAGE_LOGS = 1
private const val PAGE_SETTINGS = 2

private data class Destination(val icon: ImageVector, val label: String, val largeTitle: String)

private val DESTINATIONS = listOf(
    Destination(MiuixIcons.Home, "概览", "自动填充修复"),
    Destination(MiuixIcons.ListView, "日志", "运行日志"),
    Destination(MiuixIcons.Settings, "设置", "设置"),
)

/**
 * 应用外壳：唯一的 Scaffold（提供 bars、snackbar 与弹窗宿主）+ HorizontalPager 承载三页。
 * 跨页面的状态（主题、日志视图、清空确认）都提升到这里，页面本身只接收值和回调。
 */
@Composable
fun HafApp(state: UiPrefsState) {
    val events by LogStore.events.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val scrollBehavior = MiuixScrollBehavior()

    val pagerState = rememberPagerState(pageCount = { DESTINATIONS.size })
    val currentPage = pagerState.currentPage
    var showClearConfirm by remember { mutableStateOf(false) }

    fun toast(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun goToPage(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    BackHandler(enabled = currentPage != PAGE_OVERVIEW) { goToPage(PAGE_OVERVIEW) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = DESTINATIONS[currentPage].label,
                largeTitle = DESTINATIONS[currentPage].largeTitle,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (currentPage == PAGE_LOGS) {
                        IconButton(onClick = { state.rawView = !state.rawView }) {
                            Icon(
                                imageVector = if (state.rawView) MiuixIcons.ListView else MiuixIcons.File,
                                contentDescription = if (state.rawView) {
                                    "切换到直观视图"
                                } else {
                                    "切换到 logcat 原文"
                                },
                            )
                        }
                        if (events.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(MiuixIcons.Delete, contentDescription = "清空日志")
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                DESTINATIONS.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = currentPage == index,
                        onClick = { goToPage(index) },
                        icon = destination.icon,
                        label = destination.label,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { index ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // 平板 / 折叠屏等宽窗口下限制正文宽度，避免卡片横跨整个屏幕；
                    // 600dp 沿用 Miuix 官方 Example 的窄/宽断点族，窄屏时不起作用
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 600.dp),
                    ) {
                        when (index) {
                            PAGE_OVERVIEW -> OverviewScreen(
                                events = events,
                                scrollBehavior = scrollBehavior,
                                onOpenLogs = { goToPage(PAGE_LOGS) },
                            )

                            PAGE_LOGS -> LogsScreen(
                                events = events,
                                rawView = state.rawView,
                                showAllowed = state.showAllowed,
                                scrollBehavior = scrollBehavior,
                            )

                            else -> SettingsScreen(
                                state = state,
                                eventCount = events.size,
                                scrollBehavior = scrollBehavior,
                                onCopyLogs = {
                                    val picked = events.take(300)
                                    clipboard.setText(
                                        AnnotatedString(picked.joinToString("\n") { it.raw }),
                                    )
                                    toast("已复制 ${picked.size} 条日志")
                                },
                                onRequestClear = { showClearConfirm = true },
                            )
                        }
                    }
                }
            }

            // 破坏性操作从两个页面都能触发，所以用不依赖页面边界的 WindowDialog
            WindowDialog(
                show = showClearConfirm,
                title = "清空日志？",
                summary = "会删除本机保存的 ${events.size} 条事件记录，且无法恢复。",
                onDismissRequest = { showClearConfirm = false },
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = "取消",
                        onClick = { showClearConfirm = false },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = "清空",
                        onClick = {
                            LogStore.clear()
                            showClearConfirm = false
                            toast("日志已清空")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
