package com.katiusu.hyperautofillfix.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.katiusu.hyperautofillfix.data.LogEvent
import com.katiusu.hyperautofillfix.data.LogLevel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val AUTOFILL_SERVICE = "autofill_service"

/**
 * 概览页：只回答三个问题——现在是什么状态、拦了多少、模块在不在。
 * 分组一律是 SmallTitle + Card，行一律用 BasicComponent / 预置组件，不手搓行样式。
 */
@Composable
fun OverviewScreen(
    events: List<LogEvent>,
    scrollBehavior: ScrollBehavior,
    contentBottomPadding: Dp,
    onOpenLogs: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MiuixTheme.colorScheme
    val listState = rememberLazyListState()

    var service by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshKey) {
        service = readAutofillService(context)
    }

    val blocked = remember(events) { events.count { it.level == LogLevel.BLOCK } }
    val allowed = remember(events) { events.count { it.level == LogLevel.ALLOW } }
    val processes = remember(events) {
        events.filter { it.hook == "ModuleInit" }.map { it.pkg }.distinct()
    }
    val lastEvent = events.maxOfOrNull { it.time }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    val raw = service
    val miuiService = raw != null && (raw.contains("com.miui") || raw.contains("com.xiaomi"))
    val failed = raw.isNullOrBlank() || miuiService
    val stateText = when {
        raw.isNullOrBlank() -> "未设置自动填充服务"
        miuiService -> "已被改回小米自家服务"
        else -> "第三方服务正常工作"
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // 底栏是悬浮的：内容一直铺到屏幕底部，只在滚动末端用内边距把最后一条让到玻璃条上方
        contentPadding = PaddingValues(bottom = contentBottomPadding),
    ) {
        item(key = "state") {
            SmallTitle(text = "当前状态")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                insideMargin = PaddingValues(16.dp),
                // 真正失败的状态才用 error 容器；正常时回到 Card 的默认配色
                colors = if (failed) {
                    CardDefaults.defaultColors(
                        color = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer,
                    )
                } else {
                    CardDefaults.defaultColors()
                },
            ) {
                Text(text = stateText, style = MiuixTheme.textStyles.title4)
                Spacer(Modifier.size(6.dp))
                Text(
                    text = raw ?: "读取不到该设置项",
                    style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                    color = if (failed) scheme.onErrorContainer else scheme.onSurfaceContainerVariant,
                )
                Spacer(Modifier.size(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { openAutofillSettings(context) }) {
                        Text("打开密码与账户")
                    }
                    TextButton(text = "重新读取", onClick = { refreshKey++ })
                }
            }
        }

        item(key = "stats") {
            SmallTitle(text = "拦截统计")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                BasicComponent(
                    title = "已拦截改写",
                    summary = "系统管家想改回小米服务或清空，被本模块丢弃",
                    endActions = { Value(blocked.toString(), scheme.primary) },
                )
                BasicComponent(
                    title = "放行合法写入",
                    summary = "第三方服务自身或用户主动选择的写入",
                    endActions = { Value(allowed.toString(), scheme.onSurfaceVariantActions) },
                )
                BasicComponent(
                    title = "最近一条事件",
                    endActions = {
                        Value(
                            lastEvent?.let { timeFormat.format(Date(it)) } ?: "暂无",
                            scheme.onSurfaceVariantActions,
                        )
                    },
                )
                BasicComponent(
                    title = "查看运行日志",
                    summary = "直观排版与 logcat 原文可切换",
                    onClick = onOpenLogs,
                )
            }
        }

        item(key = "processes") {
            SmallTitle(text = "模块已载入的进程")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                if (processes.isEmpty()) {
                    BasicComponent(
                        title = "还没有收到模块上报",
                        summary = "在 LSPosed 中启用本模块、勾选四个作用域并重启手机后，这里会列出已生效的进程",
                    )
                } else {
                    BasicComponent(title = "已生效进程", summary = "共 ${processes.size} 个")
                    processes.forEach { pkg ->
                        BasicComponent(title = pkg)
                    }
                }
            }
        }

        item(key = "layers") {
            SmallTitle(text = "拦截层次")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                BasicComponent(
                    title = "Settings.Secure.putString",
                    summary = "写入方进程内直接拦截",
                )
                BasicComponent(
                    title = "SettingsProvider.call(PUT_secure)",
                    summary = "所有进程写入设置的汇聚点",
                )
                BasicComponent(
                    title = "SettingsState.insertSettingLocked",
                    summary = "设置存储底层兜底，覆盖绕过 call 的直写",
                )
                BasicComponent(
                    title = "SettingsProvider.insert / update",
                    summary = "旧式 ContentProvider 通道",
                )
            }
        }
    }
}

/** 行尾数值：沿用组件正文尺寸，只按语义角色换色。 */
@Composable
private fun Value(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = color,
    )
}

private fun readAutofillService(context: Context): String? = try {
    Settings.Secure.getString(context.contentResolver, AUTOFILL_SERVICE)
} catch (t: Throwable) {
    null
}

/**
 * 打开「语言与输入法 → 密码与账户」。自动填充服务的开关就在这一页里。
 *
 * HyperOS / MIUI 的入口是 `com.android.settings.Settings$AccountDashboardActivity`
 * （AOSP 里 `account_dashboard_title` = “Passwords & accounts”），所以优先显式指定组件；
 * 设备上没有这个入口时按 action 逐个退回，最后兜底到设置首页。
 */
private fun openAutofillSettings(context: Context) {
    val accountPage = Intent().setComponent(
        ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$AccountDashboardActivity",
        ),
    )
    val candidates = listOf(
        accountPage,
        // AOSP 14+ 同一页面的语义 action，部分 ROM 只保留了这个过滤器
        Intent("android.settings.CREDENTIAL_PROVIDER"),
        // 兜底：直接打开自动填充服务选择器，再退回设置首页
        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Throwable) {
            // 当前 ROM 没有这个入口，试下一个
        }
    }
}
