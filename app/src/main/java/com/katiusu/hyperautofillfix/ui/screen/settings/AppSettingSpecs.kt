package com.katiusu.hyperautofillfix.ui.screen.settings

import com.katiusu.hyperautofillfix.AppSettings
import com.katiusu.hyperautofillfix.R
import com.katiusu.hyperautofillfix.prefs.OptionSpec
import com.katiusu.hyperautofillfix.prefs.OptionType
import com.katiusu.hyperautofillfix.ui.component.pref.HookSection

/**
 * 本应用设置项用到的配置键。
 *
 * 前六个键同时是 Hook 侧读的键名（`PrefsStore` 的 `settings` 文件里不带前缀），
 * 因此改名会同时影响设置页与本机记录，不要随意改动。
 */
object AppOptionKeys {
    const val SHOW_ALLOWED = "show_allowed"
    const val RAW_VIEW = "raw_view"
    const val COPY_LOGS = "copy_logs"
    const val EXPORT_SETTINGS = "export_settings"
    const val IMPORT_SETTINGS = "import_settings"
    const val CLEAR_LOGS = "clear_logs"
}

private val THEME_VALUES = listOf(
    AppSettings.THEME_SYSTEM,
    AppSettings.THEME_LIGHT,
    AppSettings.THEME_DARK,
    AppSettings.THEME_MONET_SYSTEM,
    AppSettings.THEME_MONET_LIGHT,
    AppSettings.THEME_MONET_DARK,
)

private val THEME_LABELS = listOf(
    R.string.theme_system,
    R.string.theme_light,
    R.string.theme_dark,
    R.string.theme_monet_system,
    R.string.theme_monet_light,
    R.string.theme_monet_dark,
)

/**
 * 设置页的分区声明。加一个设置项只需要在下面加一条 [OptionSpec]：
 * 渲染、持久化、全局搜索都由配置管道接管。
 *
 * 外观三项（主题 / 底栏形态 / 背景模糊）是外壳级设置，写入的是同一份 `settings`，
 * `MainActivity` 直接订阅它们，所以改完立刻生效、不需要重建 Activity。
 */
fun appSettingSections(): List<HookSection> = listOf(
    HookSection(
        titleRes = R.string.settings_interface,
        specs = listOf(
            OptionSpec(
                key = AppSettings.KEY_THEME_MODE,
                type = OptionType.DROPDOWN,
                titleRes = R.string.theme_mode,
                summaryRes = R.string.theme_mode_summary,
                defaultString = AppSettings.THEME_SYSTEM,
                entryResIds = THEME_LABELS,
                entryValues = THEME_VALUES,
            ),
            OptionSpec(
                key = AppSettings.KEY_FLOATING_NAVBAR,
                type = OptionType.SWITCH,
                titleRes = R.string.floating_navbar,
                summaryRes = R.string.floating_navbar_summary,
                defaultBoolean = true,
            ),
            OptionSpec(
                key = AppSettings.KEY_LIQUID_GLASS,
                type = OptionType.SWITCH,
                titleRes = R.string.liquid_glass,
                summaryRes = R.string.liquid_glass_summary,
                dependsOn = AppSettings.KEY_FLOATING_NAVBAR,
            ),
            OptionSpec(
                key = AppSettings.KEY_BLUR_ENABLED,
                type = OptionType.SWITCH,
                titleRes = R.string.blur_enabled,
                summaryRes = R.string.blur_enabled_summary,
                defaultBoolean = true,
            ),
        ),
    ),
    HookSection(
        titleRes = R.string.settings_logs,
        specs = listOf(
            OptionSpec(
                key = AppOptionKeys.SHOW_ALLOWED,
                type = OptionType.SWITCH,
                titleRes = R.string.show_allowed,
                summaryRes = R.string.show_allowed_summary,
                defaultBoolean = true,
            ),
            OptionSpec(
                key = AppOptionKeys.RAW_VIEW,
                type = OptionType.SWITCH,
                titleRes = R.string.raw_view,
                summaryRes = R.string.raw_view_summary,
            ),
            OptionSpec(
                key = AppOptionKeys.COPY_LOGS,
                type = OptionType.ARROW,
                titleRes = R.string.copy_logs,
                summaryRes = R.string.copy_logs_summary,
            ),
        ),
    ),
    HookSection(
        titleRes = R.string.settings_data,
        specs = listOf(
            OptionSpec(
                key = AppOptionKeys.EXPORT_SETTINGS,
                type = OptionType.ARROW,
                titleRes = R.string.export_settings,
                summaryRes = R.string.export_settings_summary,
            ),
            OptionSpec(
                key = AppOptionKeys.IMPORT_SETTINGS,
                type = OptionType.ARROW,
                titleRes = R.string.import_settings,
                summaryRes = R.string.import_settings_summary,
            ),
            OptionSpec(
                key = AppOptionKeys.CLEAR_LOGS,
                type = OptionType.ARROW,
                titleRes = R.string.clear_logs,
                summaryRes = R.string.clear_logs_summary,
                dangerous = true,
            ),
        ),
    ),
)
