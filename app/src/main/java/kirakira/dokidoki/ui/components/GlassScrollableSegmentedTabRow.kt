// Uses miuix GlassSegmentedTabRow itself for the indicator; only labels are scrolled here.
package kirakira.dokidoki.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassFloatingToolbar
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 方案 2：指示器由 miuix 的 [GlassSegmentedTabRow] 原组件绘制。
 *
 * 这里不复制也不自绘指示器：外层仍用 [GlassFloatingToolbar] 提供悬浮玻璃，
 * 中间叠一层设为透明的 [GlassSegmentedTabRow] 只负责画指示器和跑它自己的
 * Animatable / edgeSpring 动画；最上层是负责横向滚动的标签 Row。
 * 这样指示器绘制和动画都来自 miuix，标签布局由这里控制。
 */
@Composable
fun GlassScrollableSegmentedTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    height: Dp = GlassSegmentedTabRowDefaults.Height,
    material: GlassMaterial? = GlassSegmentedTabRowDefaults.material(),
    stroke: EdgeStroke? = GlassSegmentedTabRowDefaults.stroke(),
    shadow: GlassShadow? = GlassShadows.Float,
    trackColor: Color = GlassSegmentedTabRowDefaults.restingTrackColor(),
    indicatorColor: Color = GlassSegmentedTabRowDefaults.indicatorColor(),
    selectedContentColor: Color = MiuixTheme.colorScheme.onSurface,
    contentColor: Color = MiuixTheme.colorScheme.onSurface,
) {
    if (tabs.isEmpty()) return

    GlassFloatingToolbar(
        modifier = modifier.fillMaxWidth(),
        backdrop = backdrop,
        cornerRadius = height / 2,
        outSidePadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        material = material ?: GlassSegmentedTabRowDefaults.material(),
        fill = trackColor,
        stroke = stroke,
        shadow = shadow,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(height),
        ) {
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val selectedStyle = MiuixTheme.textStyles.subtitle
            val normalStyle = MiuixTheme.textStyles.body2
            val labelWidths = remember(tabs, textMeasurer, selectedStyle, normalStyle, density) {
                tabs.map { label ->
                    max(
                        textMeasurer.measure(AnnotatedString(label), selectedStyle, maxLines = 1, softWrap = false).size.width,
                        textMeasurer.measure(AnnotatedString(label), normalStyle, maxLines = 1, softWrap = false).size.width,
                    ).toFloat()
                }
            }
            val gapPx = with(density) { GlassSegmentedTabRowDefaults.TabGap.toPx() }
            val tabPaddingPx = with(density) { GlassSegmentedTabRowDefaults.TabPaddingHorizontal.toPx() }
            val trackPaddingPx = with(density) { GlassSegmentedTabRowDefaults.TrackPadding.toPx() }
            val viewportWidth = constraints.maxWidth.toFloat() - trackPaddingPx * 2f
            val maxLabelWidth = labelWidths.maxOrNull() ?: 0f
            val baseTabWidth = maxLabelWidth + tabPaddingPx * 2f
            val naturalWidth = baseTabWidth * tabs.size + gapPx * (tabs.size - 1)
            val contentWidth = max(viewportWidth, naturalWidth)
            val extraPerTab = (contentWidth - naturalWidth) / tabs.size
            val tabWidthPx = baseTabWidth + extraPerTab
            val tabWidth = with(density) { tabWidthPx.toDp() }
            val starts = FloatArray(tabs.size) { index -> index * (tabWidthPx + gapPx) }
            val index = selectedIndex.coerceIn(0, tabs.lastIndex)
            val targetScroll = remember(index, contentWidth, viewportWidth, tabWidthPx) {
                centeredScroll(starts, tabWidthPx, index, viewportWidth, contentWidth)
            }
            val scrollState = rememberScrollState(initial = targetScroll)
            LaunchedEffect(index, contentWidth, viewportWidth, tabWidthPx) {
                if (contentWidth <= viewportWidth) return@LaunchedEffect
                val target = centeredScroll(starts, tabWidthPx, index, viewportWidth, contentWidth)
                if (scrollState.value != target) scrollState.animateScrollTo(target)
            }

            // 透明背景层：真正的指示器由 miuix GlassSegmentedTabRow 绘制。
            // 宽度补上 TrackPadding，让它的 3dp 内缩与下面标签 Row 对齐。
            val backdropForIndicator = rememberLayerBackdrop()
            GlassSegmentedTabRow(
                tabs = tabs,
                selectedIndex = index,
                onSelect = {},
                backdrop = backdropForIndicator,
                modifier = Modifier
                    .width(with(density) { (contentWidth + trackPaddingPx * 2f).toDp() })
                    .graphicsLayer { translationX = -scrollState.value.toFloat() },
                height = height,
                surfaceAlpha = 0f,
                trackColor = Color.Transparent,
                stroke = null,
                shadow = null,
                indicatorColor = indicatorColor,
                selectedContentColor = Color.Transparent,
                contentColor = Color.Transparent,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(GlassSegmentedTabRowDefaults.TrackPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .horizontalScroll(
                            state = scrollState,
                            overscrollEffect = null,
                            enabled = contentWidth > viewportWidth,
                        )
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(GlassSegmentedTabRowDefaults.TabGap),
                ) {
                    tabs.forEachIndexed { position, label ->
                        val selected = position == index
                        Box(
                            modifier = Modifier
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clip(GlassSegmentedTabRowDefaults.shape(height - GlassSegmentedTabRowDefaults.TrackPadding * 2f))
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    interactionSource = null,
                                    indication = null,
                                    onClick = { onSelect(position) },
                                )
                                .padding(horizontal = GlassSegmentedTabRowDefaults.TabPaddingHorizontal),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) selectedContentColor else contentColor,
                                style = if (selected) MiuixTheme.textStyles.subtitle else MiuixTheme.textStyles.body2,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun centeredScroll(
    starts: FloatArray,
    tabWidth: Float,
    index: Int,
    viewportWidth: Float,
    contentWidth: Float,
): Int {
    if (starts.isEmpty()) return 0
    val safeIndex = index.coerceIn(0, starts.lastIndex)
    val maxScroll = (contentWidth - viewportWidth).coerceAtLeast(0f)
    val center = starts[safeIndex] + tabWidth / 2f - viewportWidth / 2f
    return center.roundToInt().coerceIn(0, maxScroll.roundToInt())
}
