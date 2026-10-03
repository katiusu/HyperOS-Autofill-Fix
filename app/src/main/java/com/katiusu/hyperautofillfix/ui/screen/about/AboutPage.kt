package com.katiusu.hyperautofillfix.ui.screen.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.katiusu.hyperautofillfix.BuildConfig
import com.katiusu.hyperautofillfix.R
import com.katiusu.hyperautofillfix.ui.component.effect.BgEffectBackground
import com.katiusu.hyperautofillfix.ui.util.BlurredBar
import com.katiusu.hyperautofillfix.ui.util.blurSource
import com.katiusu.hyperautofillfix.ui.util.pageScrollModifiers
import com.katiusu.hyperautofillfix.ui.util.rememberBlurState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val GITHUB_URL = "https://github.com/katiusu/HyperOS-Autofill-Fix"
private const val HOOK_ENTRY = "com.katiusu.hyperautofillfix.MainHook"
private const val LOG_TAG = "HyperOSAutofillFix"

/** 折叠头部的高度（滚动进度以它为分母）与 logo 尺寸。 */
private val HEADER_HEIGHT = 240.dp
private val LOGO_SIZE = 84.dp

/**
 * 关于页：折叠头部（图标 + 应用名 + 版本）+ 模块信息 / 隐私说明 / 界面依赖。
 *
 * 顶栏标题随滚动渐显：头部还在屏幕上时不重复显示标题，滚过去之后才出现。
 */
@Composable
fun AboutPageContent(
    themeMode: ColorSchemeMode,
    isBlurEnabled: Boolean,
    extraBottomPadding: Dp,
) {
    val context = LocalContext.current
    val scheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else scheme.surface
    val listState = rememberLazyListState()

    val density = LocalDensity.current
    val headerHeightPx = with(density) { HEADER_HEIGHT.toPx() }
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / headerHeightPx).coerceIn(0f, 1f)
            }
        }
    }
    val headerAlpha = (1f - scrollProgress / 0.7f).coerceIn(0f, 1f)

    val logoSizePx = with(density) { LOGO_SIZE.roundToPx() }
    val appIcon = remember(context, logoSizePx) {
        runCatching {
            context.packageManager.getApplicationIcon(context.applicationInfo)
                .toBitmap(width = logoSizePx, height = logoSizePx)
                .asImageBitmap()
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, scrollBehavior) {
                TopAppBar(
                    title = if (scrollProgress > 0.6f) stringResource(R.string.app_name) else "",
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.blurSource(if (isBlurEnabled) hazeState else null)) {
            // 示例骨架的动态背景特效：随滚动进度淡出（API 33 以下自动退化为纯色）。
            BgEffectBackground(
                dynamicBackground = true,
                isFullSize = true,
                modifier = Modifier.fillMaxSize(),
                alpha = { 1f - scrollProgress },
            ) {
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
                    item(key = "header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(HEADER_HEIGHT)
                                .alpha(headerAlpha),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (appIcon != null) {
                                Image(
                                    bitmap = appIcon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(LOGO_SIZE)
                                        .squircleClip(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            MiuixText(
                                text = stringResource(R.string.app_name),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            MiuixText(
                                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MiuixTheme.textStyles.footnote2,
                                color = scheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    item(key = "info") {
                        SmallTitle(text = stringResource(R.string.about_info_title))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            Column {
                                BasicComponent(
                                    title = stringResource(R.string.about_version),
                                    endActions = {
                                        ValueText("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                                    },
                                )
                                BasicComponent(
                                    title = stringResource(R.string.theme_mode),
                                    endActions = { ValueText(stringResource(themeModeLabelRes(themeMode))) },
                                )
                                ArrowPreference(
                                    title = stringResource(R.string.about_source_code),
                                    summary = stringResource(R.string.about_source_code_summary),
                                    onClick = { openUrl(context, GITHUB_URL) },
                                )
                                BasicComponent(
                                    title = stringResource(R.string.about_hook_entry),
                                    endActions = { ValueText(HOOK_ENTRY) },
                                )
                                BasicComponent(
                                    title = stringResource(R.string.about_log_tag),
                                    endActions = { ValueText(LOG_TAG) },
                                )
                                BasicComponent(
                                    title = stringResource(R.string.about_scope),
                                    summary = stringResource(R.string.about_scope_summary),
                                )
                            }
                        }
                    }

                    item(key = "privacy") {
                        SmallTitle(text = stringResource(R.string.about_privacy_title))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            MiuixText(
                                modifier = Modifier.padding(16.dp),
                                text = stringResource(R.string.about_privacy, BuildConfig.APPLICATION_ID),
                                style = MiuixTheme.textStyles.footnote2,
                                color = scheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    item(key = "dependencies") {
                        SmallTitle(text = stringResource(R.string.about_dependencies_title))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            Column {
                                BasicComponent(
                                    title = stringResource(R.string.about_dependency_miuix),
                                    summary = "Miuix 0.9.4",
                                )
                                BasicComponent(
                                    title = stringResource(R.string.about_dependency_haze),
                                    summary = "Haze 1.7.3",
                                )
                                BasicComponent(
                                    title = stringResource(R.string.about_dependency_compose),
                                    summary = "Compose BOM 2026.09.00",
                                )
                            }
                        }
                    }

                    item(key = "copyright") {
                        MiuixText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .padding(bottom = 16.dp),
                            text = stringResource(R.string.copyright),
                            style = MiuixTheme.textStyles.footnote2,
                            color = scheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueText(text: String) {
    MiuixText(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.body2,
    )
}

private fun themeModeLabelRes(mode: ColorSchemeMode): Int = when (mode) {
    ColorSchemeMode.System -> R.string.theme_system
    ColorSchemeMode.Light -> R.string.theme_light
    ColorSchemeMode.Dark -> R.string.theme_dark
    ColorSchemeMode.MonetSystem -> R.string.theme_monet_system
    ColorSchemeMode.MonetLight -> R.string.theme_monet_light
    ColorSchemeMode.MonetDark -> R.string.theme_monet_dark
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        // 设备上没有浏览器时静默失败（与旧版一致）
    }
}
