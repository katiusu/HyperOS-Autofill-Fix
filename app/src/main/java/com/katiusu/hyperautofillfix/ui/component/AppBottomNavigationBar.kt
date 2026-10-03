package com.katiusu.hyperautofillfix.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.katiusu.hyperautofillfix.ui.component.liquid.IosLiquidGlassNavigationBar
import com.katiusu.hyperautofillfix.ui.util.isInDarkTheme
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 三档底栏，由设置页的「悬浮底栏 / 液态玻璃底栏」两个开关决定：
 * 0 贴底普通、1 悬浮毛玻璃胶囊、2 iOS 液态玻璃。
 */
@Composable
internal fun BottomNavigationBar(
    mode: Int,
    items: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    backdrop: LayerBackdrop?,
    blurActive: Boolean,
    onItemSelected: (Int) -> Unit,
) {
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val containerColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer

    when (mode) {
        2 -> {
            val navigationItems = remember(items, icons) {
                List(items.size) { index -> NavigationItem(items[index], icons[index]) }
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IosLiquidGlassNavigationBar(
                    items = navigationItems,
                    selectedIndex = selectedIndex,
                    onItemClick = onItemSelected,
                    backdrop = backdrop,
                    isBlurActive = blurActive,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .widthIn(max = 440.dp),
                )
            }
        }

        1 -> {
            val barShape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
            FloatingNavigationBar(
                modifier = if (blurActive) {
                    Modifier.textureBlur(
                        backdrop = backdrop!!,
                        shape = barShape,
                        blurRadius = 25f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)),
                            ),
                        ),
                        highlight = if (isInDarkTheme()) {
                            Highlight.GlassStrokeMiddleDark
                        } else {
                            Highlight.GlassStrokeMiddleLight
                        },
                    )
                } else {
                    Modifier
                },
                color = containerColor,
            ) {
                items.forEachIndexed { index, label ->
                    FloatingNavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { onItemSelected(index) },
                        icon = icons[index],
                        label = label,
                        enabled = true,
                    )
                }
            }
        }

        else -> {
            Box(
                modifier = Modifier
                    .then(
                        if (blurActive) {
                            Modifier.textureBlur(
                                backdrop = backdrop!!,
                                shape = RectangleShape,
                                blurRadius = 25f,
                                colors = BlurDefaults.blurColors(
                                    blendColors = listOf(
                                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.5f)),
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .background(barColor)
                    // 吃掉底栏区域的点击，避免穿透到 pager 内容。
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                NavigationBar(color = barColor) {
                    items.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { onItemSelected(index) },
                            icon = icons[index],
                            label = label,
                            enabled = true,
                        )
                    }
                }
            }
        }
    }
}
