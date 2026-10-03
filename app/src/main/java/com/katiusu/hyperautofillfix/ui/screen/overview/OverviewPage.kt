package com.katiusu.hyperautofillfix.ui.screen.overview

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katiusu.hyperautofillfix.BuildConfig
import com.katiusu.hyperautofillfix.R
import com.katiusu.hyperautofillfix.data.LogEvent
import com.katiusu.hyperautofillfix.data.LogLevel
import com.katiusu.hyperautofillfix.ui.icons.CheckCircleOutlineIcon
import com.katiusu.hyperautofillfix.ui.icons.ErrorOutlineIcon
import com.katiusu.hyperautofillfix.ui.icons.RemoveCircleOutlineIcon
import com.katiusu.hyperautofillfix.ui.util.BlurredBar
import com.katiusu.hyperautofillfix.ui.util.blurSource
import com.katiusu.hyperautofillfix.ui.util.isInDarkTheme
import com.katiusu.hyperautofillfix.ui.util.pageScrollModifiers
import com.katiusu.hyperautofillfix.ui.util.rememberBlurState
import com.katiusu.hyperautofillfix.ui.util.shouldShowSplitPane
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 概览页：只回答三个问题 —— 现在是什么状态、拦了多少、模块在不在。
 *
 * 顶部三块完全按示例主页的样式：状态方块（带装饰大图标，三档语义色）、
 * 自动填充服务名方块、操作按钮区，三者各自独立，互不挤在一张卡里。
 */
@Composable
fun OverviewPageView(
    events: List<LogEvent>,
    refreshKey: Int,
    isBlurEnabled: Boolean,
    extraBottomPadding: Dp,
    onOpenLogs: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else scheme.surface

    var rawService by remember { mutableStateOf<String?>(null) }
    var localRefresh by remember { mutableIntStateOf(0) }
    // 切回本页（refreshKey 变化）或点了重新读取时都重新查一次系统设置。
    LaunchedEffect(refreshKey, localRefresh) { rawService = readAutofillService(context) }

    val blocked = remember(events) { events.count { it.level == LogLevel.BLOCK } }
    val allowed = remember(events) { events.count { it.level == LogLevel.ALLOW } }
    val processes = remember(events) {
        events.filter { it.hook == "ModuleInit" }.map { it.pkg }.distinct()
    }
    val lastEvent = remember(events) { events.maxOfOrNull { it.time } }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    val raw = rawService
    val miuiService = raw != null && (raw.contains("com.miui") || raw.contains("com.xiaomi"))
    val unset = raw.isNullOrBlank()
    val failed = unset || miuiService
    val stateText = when {
        unset -> stringResource(R.string.overview_state_unset)
        miuiService -> stringResource(R.string.overview_state_miui)
        else -> stringResource(R.string.overview_state_ok)
    }

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, scrollBehavior) {
                TopAppBar(
                    title = stringResource(R.string.tab_overview),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { localRefresh++ }) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                    },
                )
            }
        },
        // 顶栏自己处理顶部安全区，这里只留左右，否则内容会被顶栏挤下去两次。
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.blurSource(if (isBlurEnabled) hazeState else null)) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(showTopAppBar = true, topAppBarScrollBehavior = scrollBehavior),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding,
                ),
            ) {
                item(key = "cards") {
                    val isWideScreen = shouldShowSplitPane()
                    val darkTheme = isInDarkTheme()
                    val dynamicColor = MiuixTheme.isDynamicColor

                    // 三档语义色，与示例主页同一套：异常（被改回小米）/ 提醒（没设服务）/ 正常。
                    val statusColor = when {
                        miuiService -> when {
                            dynamicColor -> scheme.errorContainer
                            darkTheme -> Color(0xFF3D1C1C)
                            else -> Color(0xFFFDE8E8)
                        }
                        unset -> when {
                            dynamicColor -> scheme.secondaryContainer
                            darkTheme -> Color(0xFF3D3520)
                            else -> Color(0xFFFDF6E3)
                        }
                        else -> when {
                            dynamicColor -> scheme.secondaryContainer
                            darkTheme -> Color(0xFF1A3825)
                            else -> Color(0xFFDFFAE4)
                        }
                    }
                    val iconTint = when {
                        miuiService -> if (dynamicColor) scheme.error.copy(alpha = 0.8f) else Color(0xFFDC3545)
                        unset -> if (dynamicColor) scheme.primary.copy(alpha = 0.8f) else Color(0xFFE0A800)
                        else -> if (dynamicColor) scheme.primary.copy(alpha = 0.8f) else Color(0xFF36D167)
                    }
                    // 异常 = 叉号；没设服务 = 圈中横线；正常 = 对号。
                    val statusIcon: ImageVector = when {
                        miuiService -> ErrorOutlineIcon
                        unset -> RemoveCircleOutlineIcon
                        else -> CheckCircleOutlineIcon
                    }
                    val versionText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

                    if (isWideScreen) {
                        // 宽屏：状态 / 服务名 / 操作 三卡片横向三等分。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(top = 12.dp)
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusCard(
                                titleText = stringResource(R.string.overview_state_title),
                                versionText = stateText,
                                statusText = versionText,
                                imageVector = statusIcon,
                                iconTint = iconTint,
                                statusColor = statusColor,
                                onClick = { openAutofillSettings(context) },
                                compact = true,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            ServiceCard(
                                raw = raw,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            ActionButtons(
                                stacked = true,
                                onOpenSettings = { openAutofillSettings(context) },
                                onRefresh = { localRefresh++ },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    } else {
                        // 窄屏：状态与服务名各占一个方块，两个按钮单独一张卡放在下面。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatusCard(
                                titleText = stringResource(R.string.overview_state_title),
                                versionText = stateText,
                                statusText = versionText,
                                imageVector = statusIcon,
                                iconTint = iconTint,
                                statusColor = statusColor,
                                onClick = { openAutofillSettings(context) },
                                compact = true,
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                            )
                            ServiceCard(
                                raw = raw,
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                            )
                        }
                        ActionButtons(
                            stacked = false,
                            onOpenSettings = { openAutofillSettings(context) },
                            onRefresh = { localRefresh++ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(top = 12.dp),
                        )
                    }
                }

                item(key = "stats") {
                    SmallTitle(text = stringResource(R.string.overview_stats_title))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        Column {
                            BasicComponent(
                                title = stringResource(R.string.overview_blocked),
                                summary = stringResource(R.string.overview_blocked_summary),
                                endActions = { ValueText(blocked.toString(), scheme.primary) },
                            )
                            BasicComponent(
                                title = stringResource(R.string.overview_allowed),
                                summary = stringResource(R.string.overview_allowed_summary),
                                endActions = { ValueText(allowed.toString(), scheme.onSurfaceVariantActions) },
                            )
                            BasicComponent(
                                title = stringResource(R.string.overview_last_event),
                                endActions = {
                                    ValueText(
                                        lastEvent?.let { timeFormat.format(Date(it)) }
                                            ?: stringResource(R.string.overview_none),
                                        scheme.onSurfaceVariantActions,
                                    )
                                },
                            )
                            BasicComponent(
                                title = stringResource(R.string.overview_view_logs),
                                summary = stringResource(R.string.overview_view_logs_summary),
                                onClick = onOpenLogs,
                            )
                        }
                    }
                }

                item(key = "processes") {
                    SmallTitle(text = stringResource(R.string.overview_processes_title))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        Column {
                            if (processes.isEmpty()) {
                                BasicComponent(
                                    title = stringResource(R.string.overview_processes_empty),
                                    summary = stringResource(R.string.overview_processes_empty_summary),
                                )
                            } else {
                                BasicComponent(
                                    title = stringResource(R.string.overview_processes_count),
                                    summary = stringResource(
                                        R.string.overview_processes_count_summary,
                                        processes.size,
                                    ),
                                )
                                processes.forEach { pkg ->
                                    BasicComponent(title = pkg)
                                }
                            }
                        }
                    }
                }

                item(key = "layers") {
                    SmallTitle(text = stringResource(R.string.overview_layers_title))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        Column {
                            BasicComponent(
                                title = stringResource(R.string.overview_layer_1),
                                summary = stringResource(R.string.overview_layer_1_summary),
                            )
                            BasicComponent(
                                title = stringResource(R.string.overview_layer_2),
                                summary = stringResource(R.string.overview_layer_2_summary),
                            )
                            BasicComponent(
                                title = stringResource(R.string.overview_layer_3),
                                summary = stringResource(R.string.overview_layer_3_summary),
                            )
                            BasicComponent(
                                title = stringResource(R.string.overview_layer_4),
                                summary = stringResource(R.string.overview_layer_4_summary),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 状态方块：照抄示例主页的状态卡 —— 右下角一枚超大的装饰图标，
 * 左上角三行文字（标题 / 结论 / 版本），整块可点，按下去有倾斜反馈。
 */
@Composable
private fun StatusCard(
    titleText: String,
    versionText: String,
    statusText: String,
    imageVector: ImageVector,
    iconTint: Color,
    statusColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val iconSize = if (compact) 100.dp else 170.dp
    val iconOffsetX = if (compact) 22.dp else 38.dp
    val iconOffsetY = if (compact) 26.dp else 45.dp
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = statusColor),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(iconOffsetX, iconOffsetY),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(iconSize),
                    imageVector = imageVector,
                    tint = iconTint,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(all = 16.dp),
            ) {
                MiuixText(
                    modifier = Modifier.fillMaxWidth(),
                    text = titleText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                MiuixText(
                    modifier = Modifier.fillMaxWidth(),
                    text = versionText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                MiuixText(
                    modifier = Modifier.fillMaxWidth(),
                    text = statusText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** 自动填充服务名方块：当前系统里真正生效的那个服务，等宽字体显示原始值。 */
@Composable
private fun ServiceCard(raw: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            MiuixText(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.overview_service_title),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(4.dp))
            MiuixText(
                modifier = Modifier.fillMaxWidth(),
                text = raw ?: stringResource(R.string.overview_state_raw_missing),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 两个按钮单独一块：宽屏竖排，窄屏横排各占一半。 */
@Composable
private fun ActionButtons(
    stacked: Boolean,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, insideMargin = PaddingValues(16.dp)) {
        if (stacked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OpenSettingsButton(onOpenSettings, Modifier.fillMaxWidth())
                RefreshButton(onRefresh, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OpenSettingsButton(onOpenSettings, Modifier.weight(1f))
                RefreshButton(onRefresh, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OpenSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        MiuixText(
            text = stringResource(R.string.overview_action_open),
            style = MiuixTheme.textStyles.button,
        )
    }
}

@Composable
private fun RefreshButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        text = stringResource(R.string.action_refresh),
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColorsPrimary(),
    )
}

@Composable
private fun ValueText(text: String, color: Color) {
    MiuixText(text = text, color = color, style = MiuixTheme.textStyles.body2)
}

/** 读取系统当前的自动填充服务（`Settings.Secure.autofill_service`）。 */
private fun readAutofillService(context: Context): String? = try {
    Settings.Secure.getString(context.contentResolver, "autofill_service")
} catch (_: Exception) {
    null
}

/**
 * 依次尝试跳到「密码与账户」相关页面：HyperOS 把它藏在账号页里，
 * 因此从最贴近的组件名开始逐级回退，最终一定能落到系统设置首页。
 */
private fun openAutofillSettings(context: Context) {
    val candidates = listOf(
        Intent().setComponent(
            ComponentName(
                "com.android.settings",
                "com.android.settings.Settings\$AccountDashboardActivity",
            ),
        ),
        Intent("android.settings.CREDENTIAL_PROVIDER"),
        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE),
        Intent(Settings.ACTION_SETTINGS),
    )
    candidates.forEach { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // 换下一个候选
        }
    }
}
