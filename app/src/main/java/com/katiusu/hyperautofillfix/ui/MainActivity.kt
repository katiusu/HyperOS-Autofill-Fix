package com.katiusu.hyperautofillfix.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.katiusu.hyperautofillfix.AppSettings
import com.katiusu.hyperautofillfix.R
import com.katiusu.hyperautofillfix.data.LogStore
import com.katiusu.hyperautofillfix.prefs.ConfigState
import com.katiusu.hyperautofillfix.ui.component.BottomNavigationBar
import com.katiusu.hyperautofillfix.ui.screen.about.AboutPageContent
import com.katiusu.hyperautofillfix.ui.screen.logs.LogsPageView
import com.katiusu.hyperautofillfix.ui.screen.overview.OverviewPageView
import com.katiusu.hyperautofillfix.ui.screen.settings.SettingsPageView
import com.katiusu.hyperautofillfix.ui.theme.AppTheme
import com.katiusu.hyperautofillfix.ui.util.applyWindowBackground
import com.katiusu.hyperautofillfix.ui.util.rememberBlurBackdrop
import com.katiusu.hyperautofillfix.ui.util.shouldExpandNavigationRail
import com.katiusu.hyperautofillfix.ui.util.shouldShowSplitPane
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/**
 * 唯一的 Activity：外壳（底栏/侧栏 + 四页 pager）在这里，页面内容在 ui/screen 下。
 *
 * 外壳级设置（主题、底栏形态、背景模糊）全部住在 [ConfigState]（物理存储见
 * [com.katiusu.hyperautofillfix.prefs.PrefsStore]），设置页写、这里读，不需要任何回传回调。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        // 2.x 把主题存成 Int，必须先迁移成 ColorSchemeMode 的枚举名，再让配置管道读取。
        AppSettings.migrateLegacy(this)
        ConfigState.init(this)
        setContent {
            val themeModeName = ConfigState.string(AppSettings.KEY_THEME_MODE, AppSettings.THEME_SYSTEM)
            val isFloatingNavbar = ConfigState.bool(AppSettings.KEY_FLOATING_NAVBAR, true)
            val isLiquidGlass = ConfigState.bool(AppSettings.KEY_LIQUID_GLASS, false)
            val isBlurEnabled = ConfigState.bool(AppSettings.KEY_BLUR_ENABLED, true)
            val themeMode = AppSettings.themeModeOf(themeModeName)

            // 启动窗口背景必须与 Compose 内容同色，否则冷启动会闪一下另一种底色。
            SideEffect { applyWindowBackground(themeModeName) }

            AppTheme(themeMode = themeMode) {
                MainScreen(
                    isFloatingNavbar = isFloatingNavbar,
                    isLiquidGlass = isLiquidGlass,
                    isBlurEnabled = isBlurEnabled,
                    themeMode = themeMode,
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    isFloatingNavbar: Boolean,
    isLiquidGlass: Boolean,
    isBlurEnabled: Boolean,
    themeMode: ColorSchemeMode,
) {
    val events by LogStore.events.collectAsState()
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { 4 })
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isNavigating by remember { mutableStateOf(false) }
    var navJob by remember { mutableStateOf<Job?>(null) }
    var overviewRefreshKey by remember { mutableIntStateOf(0) }

    val items = listOf(
        stringResource(R.string.tab_overview),
        stringResource(R.string.tab_logs),
        stringResource(R.string.tab_settings),
        stringResource(R.string.tab_about),
    )
    val icons = listOf(MiuixIcons.Home, MiuixIcons.ListView, MiuixIcons.Settings, MiuixIcons.Info)

    // pager 的横滑被关掉（userScrollEnabled = false），currentPage 只会在弹簧动画结束时变。
    LaunchedEffect(pagerState.currentPage) {
        if (!isNavigating && selectedIndex != pagerState.currentPage) {
            selectedIndex = pagerState.currentPage
            overviewRefreshKey++
        }
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    // 设备不支持运行时着色器时 rememberBlurBackdrop() 返回 null，此时所有毛玻璃自动降级为纯色。
    val backdrop = rememberBlurBackdrop()
    val blurActive = isBlurEnabled && backdrop != null
    val navBarMode = if (!isFloatingNavbar) 0 else if (!isLiquidGlass) 1 else 2
    val useNavigationRail = shouldShowSplitPane() && navBarMode == 0
    val railState = rememberNavigationRailState()
    val expandRail = shouldExpandNavigationRail()
    LaunchedEffect(expandRail) {
        if (expandRail) railState.expand() else railState.collapse()
    }

    fun select(index: Int) {
        if (index == selectedIndex) return
        if (index == 0) overviewRefreshKey++
        navJob?.cancel()
        selectedIndex = index
        isNavigating = true
        navJob = scope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.springAnimateToPage(index)
            } finally {
                // 被后一次点击取消时不要动选中态，交给新的那次收尾。
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != index) selectedIndex = pagerState.currentPage
                }
            }
        }
    }

    // 从任意页返回都回概览，避免误触返回直接退出应用。
    BackHandler(enabled = selectedIndex != 0) { select(0) }

    val pagerContent: @Composable (Dp, PaddingValues) -> Unit = { navBarHeight, pagerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .background(surfaceColor),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                contentPadding = pagerPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .pagerGestureOverride(
                        pagerState = pagerState,
                        mode = PagerInterceptionMode.CrossAxisInterceptor,
                    ),
                userScrollEnabled = false,
                pageNestedScrollConnection = PagerGestureNestedScrollConnection,
            ) { page ->
                when (page) {
                    0 -> OverviewPageView(
                        events = events,
                        refreshKey = overviewRefreshKey,
                        isBlurEnabled = isBlurEnabled,
                        extraBottomPadding = navBarHeight,
                        onOpenLogs = { select(1) },
                    )

                    1 -> LogsPageView(
                        events = events,
                        isBlurEnabled = isBlurEnabled,
                        extraBottomPadding = navBarHeight,
                        onOpenSettings = { select(2) },
                    )

                    2 -> SettingsPageView(
                        isBlurEnabled = isBlurEnabled,
                        extraBottomPadding = navBarHeight,
                    )

                    else -> AboutPageContent(
                        themeMode = themeMode,
                        isBlurEnabled = isBlurEnabled,
                        extraBottomPadding = navBarHeight,
                    )
                }
            }
        }
    }

    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (useNavigationRail) {
        var railWidthPx by remember { mutableIntStateOf(0) }
        val railWidth = with(LocalDensity.current) { railWidthPx.toDp() }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(
                        WindowInsets.systemBars
                            .add(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Start),
                    ),
            ) {
                pagerContent(navigationBarBottom, PaddingValues(start = railWidth))
            }
            NavigationRail(
                modifier = Modifier
                    .onSizeChanged { railWidthPx = it.width }
                    .then(
                        if (blurActive) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RectangleShape,
                                blurRadius = 25f,
                                colors = BlurDefaults.blurColors(
                                    blendColors = listOf(BlendColorEntry(color = surfaceColor.copy(alpha = 0.5f))),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    ),
                color = if (blurActive) Color.Transparent else surfaceColor,
                state = railState,
            ) {
                items.forEachIndexed { index, label ->
                    NavigationRailItem(
                        selected = selectedIndex == index,
                        onClick = { select(index) },
                        icon = icons[index],
                        label = label,
                    )
                }
            }
        }
    } else {
        Scaffold(
            popupHost = { },
            bottomBar = {
                BottomNavigationBar(
                    mode = navBarMode,
                    items = items,
                    icons = icons,
                    selectedIndex = selectedIndex,
                    backdrop = backdrop,
                    blurActive = blurActive,
                    onItemSelected = { select(it) },
                )
            },
        ) { globalPadding ->
            pagerContent(globalPadding.calculateBottomPadding(), PaddingValues(0.dp))
        }
    }
}
