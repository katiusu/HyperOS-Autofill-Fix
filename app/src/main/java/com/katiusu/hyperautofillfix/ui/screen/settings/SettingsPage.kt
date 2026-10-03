package com.katiusu.hyperautofillfix.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.katiusu.hyperautofillfix.R
import com.katiusu.hyperautofillfix.data.LogStore
import com.katiusu.hyperautofillfix.prefs.ConfigBackup
import com.katiusu.hyperautofillfix.ui.component.pref.HookOptionsPage
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val JSON_MIME = "application/json"
private const val EXPORT_FILE_NAME = "hyperautofillfix-settings.json"

/** 复制到剪贴板的最大条数（与旧版一致）。 */
private const val COPY_LIMIT = 300

/**
 * 设置页：外观（主题 / 底栏形态 / 模糊）、日志、数据。
 *
 * 页面本身只做渲染与副作用接线 —— 每一项的声明、默认值与可见性都在 [appSettingSections] 里，
 * 点击箭头卡片时统一走 [HookOptionsPage] 的 `onArrowClick` 回调（复制 / 导出 / 导入 / 清空）。
 */
@Composable
fun SettingsPageView(
    isBlurEnabled: Boolean,
    extraBottomPadding: Dp,
) {
    val context = LocalContext.current
    val sections = remember { appSettingSections() }
    var showClearConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JSON_MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(ConfigBackup.exportJson().toByteArray())
            }
            true
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (ok) R.string.export_success else R.string.export_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val ok = json != null && ConfigBackup.importJson(json)
        Toast.makeText(
            context,
            if (ok) R.string.import_success else R.string.import_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    HookOptionsPage(
        title = stringResource(R.string.tab_settings),
        sections = sections,
        isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding,
        onArrowClick = { spec ->
            when (spec.key) {
                AppOptionKeys.COPY_LOGS -> copyRecentLogs(context)
                AppOptionKeys.EXPORT_SETTINGS -> exportLauncher.launch(EXPORT_FILE_NAME)
                AppOptionKeys.IMPORT_SETTINGS -> importLauncher.launch(JSON_MIME)
                AppOptionKeys.CLEAR_LOGS -> showClearConfirm = true
            }
        },
    )

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

/**
 * 把最近 [COPY_LIMIT] 条日志抄进剪贴板：每条形如
 * `03-08 12:00:00 [block] com.miui.securitycenter/Settings.Secure.putString = null`，换行后跟 logcat 原文。
 */
private fun copyRecentLogs(context: Context) {
    val recent = LogStore.events.value.take(COPY_LIMIT)
    if (recent.isEmpty()) {
        Toast.makeText(context, R.string.copy_logs_empty, Toast.LENGTH_SHORT).show()
        return
    }
    val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    val text = recent.joinToString(separator = "\n") { event ->
        buildString {
            append(timeFormat.format(Date(event.time)))
            append(" [").append(event.level.key).append("] ")
            append(event.pkg)
            if (event.hook.isNotBlank()) append('/').append(event.hook)
            event.value?.let { append(" = ").append(it) }
            append('\n').append(event.raw)
        }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("HyperOSAutofillFix", text))
    Toast.makeText(
        context,
        context.getString(R.string.copy_logs_done, recent.size),
        Toast.LENGTH_SHORT,
    ).show()
}
