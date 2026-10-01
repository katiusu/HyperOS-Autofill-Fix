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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.katiusu.hyperautofillfix.data.LogStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
 *
 * 底栏是悬浮的液态玻璃条：页面内容先录进 [LayerBackdrop]，底栏再对这一层做 [textureBlur]，
 * 因此滚动时内容会从玻璃后面穿过。RuntimeShader 只有 API 33+ 才有，低版本自动降级为不透明底栏。
 */
@Composable
fun HafApp(state: UiPrefsState) {
    val events by LogStore.events.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val scrollBehavior = MiuixScrollBehavior()
    val scheme = MiuixTheme.colorScheme

    val pagerState = rememberPagerState(pageCount = { DESTINATIONS.size })
    val currentPage = pagerState.currentPage
    var showClearConfirm by remember { mutableStateOf(false) }

    // 能力检测：API 33 以下没有 RuntimeShader，textureBlur 会静默失效。
    // 这种情况下既不创建 backdrop、也不挂 layerBackdrop，省掉每帧一次的图层录制；底栏退回不透明配色。
    // isRuntimeShaderSupported() 在 Android 上只是一次 Build.VERSION.SDK_INT 比较，用不着 remember。
    val glassSupported = isRuntimeShaderSupported()
    val backdrop: LayerBackdrop? = if (glassSupported) {
        // 先铺一层不透明底色再录内容：内容有透明区域时，模糊会把颜色扩散进透明区（官方文档点名的问题）
        rememberLayerBackdrop {
            drawRect(scheme.surface)
            drawContent()
        }
    } else {
        null
    }

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
            GlassNavigationBar(
                backdrop = backdrop,
                currentPage = currentPage,
                onSelect = { goToPage(it) },
            )
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        // 只让开顶栏：内容要一直铺到屏幕底部，才能从玻璃底栏后面穿过。
        // 底栏高度改由页面的滚动内容内边距让开，否则滚动到底时最后一条会被压在底栏下。
        val contentBottomPadding = innerPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
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
                                contentBottomPadding = contentBottomPadding,
                                onOpenLogs = { goToPage(PAGE_LOGS) },
                            )

                            PAGE_LOGS -> LogsScreen(
                                events = events,
                                rawView = state.rawView,
                                showAllowed = state.showAllowed,
                                scrollBehavior = scrollBehavior,
                                contentBottomPadding = contentBottomPadding,
                            )

                            else -> SettingsScreen(
                                state = state,
                                eventCount = events.size,
                                scrollBehavior = scrollBehavior,
                                contentBottomPadding = contentBottomPadding,
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

/**
 * 液态玻璃底栏：贴着 [FloatingNavigationBar] 的圆角形状做背景模糊。
 *
 * [FloatingNavigationBar] 内部先画 squircle 背景、再应用调用方传入的 modifier，
 * 所以这里传进去的 [textureBlur] 恰好画在背景之上、图标之下；把 color 设为透明即可让玻璃透出。
 * 不支持 blur 的机器回退到主题的 surfaceContainer，保证图标始终可读。
 */
@Composable
private fun GlassNavigationBar(
    backdrop: LayerBackdrop?,
    currentPage: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 圆角、阴影一律取组件自带的 Defaults（不自己编尺寸）；模糊形状必须和底栏完全一致，
    // 否则玻璃边缘会和底栏圆角错位。
    val cornerRadius = FloatingToolbarDefaults.CornerRadius
    val shape = RoundedCornerShape(cornerRadius)
    // 模糊之上再叠一层半透明 surface：纯模糊在明暗主题和花哨壁纸下都可能让图标看不清
    val glassColors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(color = scheme.surface.copy(alpha = 0.6f))),
    )

    FloatingNavigationBar(
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = shape,
                blurRadius = BlurDefaults.BlurRadius,
                colors = glassColors,
            )
        } else {
            Modifier
        },
        color = if (backdrop != null) Color.Transparent else scheme.surfaceContainer,
        cornerRadius = cornerRadius,
        shadowElevation = FloatingNavigationBarDefaults.ShadowElevation,
    ) {
        DESTINATIONS.forEachIndexed { index, destination ->
            FloatingNavigationBarItem(
                selected = currentPage == index,
                onClick = { onSelect(index) },
                icon = destination.icon,
                label = destination.label,
            )
        }
    }
}
