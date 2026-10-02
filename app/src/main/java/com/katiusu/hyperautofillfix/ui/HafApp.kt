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
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.katiusu.hyperautofillfix.data.LogStore
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val PAGE_OVERVIEW = 0
private const val PAGE_LOGS = 1
private const val PAGE_SETTINGS = 2

/**
 * 底栏模糊半径。backdrop 只提供原语、没有任何尺寸 Defaults，
 * 这里取与 2.1.0（miuix-blur 版）接近的强度，保证只换渲染器、不换观感。
 */
private val NAV_BLUR_RADIUS = 14.dp

/** 叠在模糊之上、内容之下的容器色透明度：太高会盖掉模糊，太低在明暗主题下都可能让图标读不清。 */
private const val NAV_TINT_ALPHA = 0.55f

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
 * 底栏有两种形态，由设置里的「悬浮底栏」切换（见 [GlassNavigationBar] / [PlainNavigationBar]）：
 * - **悬浮毛玻璃**（默认）：页面内容先录进 [Backdrop]，底栏再用 `drawBackdrop` 对这一层做模糊
 *   （AndroidLiquidGlass / Backdrop 的模糊渲染比 miuix-blur 干净）。没有边缘折射、也没有高光描边，
 *   所以它是毛玻璃，不是液态玻璃；底栏形状与圆角仍由 Miuix 的 `FloatingNavigationBar` 决定。
 * 模糊走 RenderEffect（API 31+，见 [isRenderEffectSupported]），低于门槛时不建 backdrop、
 * 底栏退回不透明配色。
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

    // 能力检测：模糊走 RenderEffect（API 31+）；贴底的普通底栏是不透明的，根本用不到 backdrop。
    // 任一条件不满足就既不创建 backdrop、也不挂 layerBackdrop，省掉每帧一次的图层录制。
    // isRenderEffectSupported() 在 Android 上只是一次 Build.VERSION.SDK_INT 比较，用不着 remember。
    val useGlassBar = isRenderEffectSupported() && state.floatingNavBar
    // backdrop 2.0.0 的 rememberLayerBackdrop 把 onDraw 当作 remember 的 key：
    // 传内联 lambda 会在每次重组时重建图层、重置录制定位，所以这里 remember 成稳定引用。
    val backdropDraw: ContentDrawScope.() -> Unit = remember(scheme.surface) {
        {
            // 先铺一层不透明底色再录内容：内容有透明区域时，模糊会把颜色扩散进透明区
            drawRect(scheme.surface)
            drawContent()
        }
    }
    // 录制侧要用具体类型 LayerBackdrop：Modifier.layerBackdrop() 只接收它；
    // 绘制侧的 drawBackdrop() 收的是 Backdrop 接口，LayerBackdrop 本身就是它的实现。
    val backdrop: LayerBackdrop? = if (useGlassBar) {
        rememberLayerBackdrop(onDraw = backdropDraw)
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
            // 两种底栏二选一（设置 → 外观 → 悬浮底栏）：
            // 开 = 悬浮毛玻璃胶囊；关 = 贴底普通底栏（2.0.0 的形态）。
            if (state.floatingNavBar) {
                GlassNavigationBar(
                    backdrop = backdrop,
                    currentPage = currentPage,
                    onSelect = { goToPage(it) },
                )
            } else {
                PlainNavigationBar(
                    currentPage = currentPage,
                    onSelect = { goToPage(it) },
                )
            }
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
 * 悬浮毛玻璃底栏：贴着 [FloatingNavigationBar] 的圆角形状做背景模糊。
 *
 * [FloatingNavigationBar] 内部先画 squircle 背景、再应用调用方传入的 modifier，
 * 所以这里传进去的玻璃恰好画在背景之上、图标之下；把 color 设为透明即可让玻璃透出。
 * 模糊用 AndroidLiquidGlass（Backdrop）的 `vibrancy() + blur()`：边缘处理与降采样比 miuix-blur 干净，
 * 但仍然只是"模糊 + 一点边缘高光"，没有折射——所以这里是毛玻璃，不是液态玻璃。
 * 不支持 RenderEffect 的机器回退到主题的 surfaceContainer，保证图标始终可读。
 */
@Composable
private fun GlassNavigationBar(
    backdrop: Backdrop?,
    currentPage: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 圆角、阴影一律取组件自带的 Defaults（不自己编尺寸）；模糊形状必须和底栏完全一致，
    // 否则玻璃边缘会和底栏圆角错位。
    val cornerRadius = FloatingToolbarDefaults.CornerRadius
    val shape = RoundedCornerShape(cornerRadius)

    FloatingNavigationBar(
        modifier = if (backdrop != null) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    // vibrancy 提饱和，避免模糊后的内容发灰；blur 的半径按 dp 换算成像素。
                    vibrancy()
                    blur(NAV_BLUR_RADIUS.toPx())
                },
                // 一点边缘高光，让玻璃有厚度感；它不是折射，不构成"液态玻璃"。
                highlight = { Highlight.Default },
                // 阴影交给 Miuix 的 shadowElevation，避免两层阴影叠加。
                shadow = null,
                // 模糊之上、内容之下的半透明容器色：纯模糊在明暗主题下都可能让图标读不清。
                onDrawSurface = { drawRect(scheme.surfaceContainer.copy(alpha = NAV_TINT_ALPHA)) },
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

/**
 * 贴底的普通底栏：直接用 Miuix 的 [NavigationBar]，也就是 2.0.0 的形态 ——
 * 不透明底色 + 顶部分隔线，每项**图标 + 文字**（`NavigationBarDisplayMode.IconAndText`），
 * inset 由组件自己处理（`defaultWindowInsetsPadding = true`）。
 *
 * 与悬浮版的差别只在"底栏这一块"：内容仍然由各页的 `contentBottomPadding` 让开，
 * 因为这个底栏是不透明的，内容从它下面滚过看不见、观感与 2.0.0 一致。
 */
@Composable
private fun PlainNavigationBar(
    currentPage: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBar {
        DESTINATIONS.forEachIndexed { index, destination ->
            // NavigationBarItem 是 RowScope 的扩展，只能写在 NavigationBar 的 content 里。
            NavigationBarItem(
                selected = currentPage == index,
                onClick = { onSelect(index) },
                icon = destination.icon,
                label = destination.label,
            )
        }
    }
}
