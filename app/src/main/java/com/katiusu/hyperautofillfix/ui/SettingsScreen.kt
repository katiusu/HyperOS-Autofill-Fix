package com.katiusu.hyperautofillfix.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.katiusu.hyperautofillfix.BuildConfig
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 主题下拉的可选项，下标与 [ThemeMode] 常量一一对应。 */
private val THEME_OPTIONS = listOf("跟随系统", "浅色", "深色", "动态取色")

/** 项目仓库地址，展示在「关于」分组里，点一下用浏览器打开。 */
private const val GITHUB_URL = "https://github.com/katiusu/HyperOS-Autofill-Fix"
private const val GITHUB_URL_LABEL = "github.com/katiusu/HyperOS-Autofill-Fix"

@Composable
fun SettingsScreen(
    state: UiPrefsState,
    eventCount: Int,
    scrollBehavior: ScrollBehavior,
    contentBottomPadding: Dp,
    onCopyLogs: () -> Unit,
    onRequestClear: () -> Unit,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // 悬浮玻璃底栏不占布局高度，这里让滚动内容在末端自己让开
        contentPadding = PaddingValues(bottom = contentBottomPadding),
    ) {
        item(key = "appearance") {
            SmallTitle(text = "外观")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                OverlayDropdownPreference(
                    title = "主题",
                    summary = "选中后立即生效；动态取色在 Android 12+ 生效，不支持时自动回退",
                    items = THEME_OPTIONS,
                    selectedIndex = state.themeMode.coerceIn(0, THEME_OPTIONS.lastIndex),
                    onSelectedIndexChange = { state.themeMode = it },
                )
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = "开：悬浮的毛玻璃胶囊，内容从它后面穿过；" +
                        "关：贴底普通底栏（2.0.0 的样式，图标带文字标签）",
                    checked = state.floatingNavBar,
                    onCheckedChange = { state.floatingNavBar = it },
                )
            }
        }

        item(key = "logs") {
            SmallTitle(text = "日志")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                SwitchPreference(
                    title = "记录放行事件",
                    summary = "除拦截外，也记录第三方服务自身的合法写入",
                    checked = state.showAllowed,
                    onCheckedChange = { state.showAllowed = it },
                )
                SwitchPreference(
                    title = "默认显示原文",
                    summary = "打开日志页时使用 logcat 原文视图，可随时在顶栏切换",
                    checked = state.rawView,
                    onCheckedChange = { state.rawView = it },
                )
                ArrowPreference(
                    title = "复制最近日志",
                    summary = "把最近 300 条复制到剪贴板（当前共 $eventCount 条）",
                    onClick = onCopyLogs,
                )
            }
        }

        item(key = "danger") {
            SmallTitle(text = "数据")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                ArrowPreference(
                    title = "清空日志",
                    summary = "删除本机保存的全部事件记录，无法恢复",
                    // 破坏性动作：标题走 error 语义角色（颜色之外还有“无法恢复”的文案，不靠颜色单独表达）
                    titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
                    onClick = onRequestClear,
                )
            }
        }

        item(key = "about") {
            SmallTitle(text = "关于")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                BasicComponent(
                    title = "版本",
                    endActions = {
                        Text(
                            text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                )
                ArrowPreference(
                    title = "GitHub 仓库",
                    summary = GITHUB_URL_LABEL,
                    onClick = { openUrl(context, GITHUB_URL) },
                )
                BasicComponent(title = "Hook 入口", summary = "com.katiusu.hyperautofillfix.MainHook")
                BasicComponent(title = "日志标签", summary = "HyperOSAutofillFix")
                BasicComponent(
                    title = "生效方式",
                    summary = "在 LSPosed 中启用并勾选 android、com.android.providers.settings、com.android.settings、com.miui.securitycenter，然后重启手机",
                )
            }
        }

        item(key = "privacy") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Text(
                    text = "拦截事件通过显式广播从被 Hook 的进程送到本应用，只在 /data/data/${BuildConfig.APPLICATION_ID} 下落盘保存，不联网、不上传。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 用系统浏览器打开链接；设备上没有任何能处理 http 的应用时静默忽略。 */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Throwable) {
        // 没有浏览器可用
    }
}
