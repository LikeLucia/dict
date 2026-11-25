package kirakira.dokidoki.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kirakira.dokidoki.ui.theme.DictTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassMotion
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.glass.glassPressScale
import top.yukonga.miuix.kmp.glass.glassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** 轨道高度：沿用 main 原轨道的 38dp，换材质不改版面。 */
private val TrackHeight = 38.dp

/**
 * 本地移植的 GlassSegmentedTabRow 可滚动轨道。
 *
 * 外观按 miuix 0.9.5-beta04 的 `GlassSegmentedTabRowDefaults`：
 * 一条连续悬浮胶囊轨道 + 内缩 [GlassSegmentedTabRowDefaults.TrackPadding] 的指示器，
 * 不再给每个标签画单独的 `squircleBorder`。
 *
 * 布局与交互保留横向滚动：标签按实际文字宽度排布，整条内容可以横滑；
 * 指示器由左右两个 `Animatable` 分别驱动。切换标签时前缘快、后缘慢，
 * 形成 beta04 的拉伸回弹；滚动时指示器跟着内容移动。
 */
@Composable
fun GlassScrollableSegmentedTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return

    val p = DictTheme.palette
    // 浅色轨道上用纯黑标签；暗色主题保留正文色，避免黑字落在深色轨道上不可读。
    val labelColor = if (p.isDark) p.text else Color.Black
    val selected = selectedIndex.coerceIn(0, labels.lastIndex)
    val interactionSources = remember(labels) { List(labels.size) { MutableInteractionSource() } }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragCenterX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val backdrop = LocalBarBackdrop.current
    val trackShape = GlassSegmentedTabRowDefaults.shape(TrackHeight)
    val trackColor = GlassSegmentedTabRowDefaults.restingTrackColor()
    val trackStroke = GlassSegmentedTabRowDefaults.stroke()
    val trackPadding = GlassSegmentedTabRowDefaults.TrackPadding
    val indicatorShape = GlassSegmentedTabRowDefaults.shape(TrackHeight - trackPadding * 2f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(TrackHeight)
            .glassShadow(trackShape, GlassShadows.Float)
            .clip(trackShape)
            .drawBehind { drawRect(trackColor) }
            .then(
                if (backdrop != null) {
                    backdrop.glassTabModifier(trackShape)
                } else {
                    Modifier.edgeStroke(trackShape, trackStroke)
                },
            ),
    ) {
        val density = LocalDensity.current
        val innerHeightPx = with(density) { (TrackHeight - trackPadding * 2f).toPx() }
        val indicatorFill by animateColorAsState(
            targetValue = if (pressedIndex >= 0) GlassSegmentedTabRowDefaults.indicatorPressedColor() else GlassSegmentedTabRowDefaults.indicatorColor(),
            animationSpec = if (pressedIndex >= 0) GlassMotion.navPressEnter() else GlassMotion.navPressExit(),
            label = "filterTrackIndicatorFill",
        )
        val indicatorScale by animateFloatAsState(
            targetValue = if (pressedIndex >= 0) glassPressScale(innerHeightPx, density.density) else 1f,
            animationSpec = if (pressedIndex >= 0) GlassMotion.pressDown() else GlassMotion.pressUp(),
            label = "filterTrackIndicatorPress",
        )
        val textMeasurer = rememberTextMeasurer()
        val selectedStyle = MiuixTheme.textStyles.subtitle
        val normalStyle = MiuixTheme.textStyles.body2
        val labelWidths = remember(labels, textMeasurer, selectedStyle, normalStyle, density) {
            labels.map { label ->
                max(
                    textMeasurer.measure(
                        text = AnnotatedString(label),
                        style = selectedStyle,
                        maxLines = 1,
                        softWrap = false,
                    ).size.width,
                    textMeasurer.measure(
                        text = AnnotatedString(label),
                        style = normalStyle,
                        maxLines = 1,
                        softWrap = false,
                    ).size.width,
                ).toFloat()
            }
        }

        val gapPx = with(density) { GlassSegmentedTabRowDefaults.TabGap.toPx() }
        val tabPaddingPx = with(density) { GlassSegmentedTabRowDefaults.TabPaddingHorizontal.toPx() }
        val trackPaddingPx = with(density) { trackPadding.toPx() }
        // 内外两层：外层是整条轨道，内层已经按 TrackPadding 内缩，所以这里的坐标从 0 开始。
        val viewportWidth = constraints.maxWidth.toFloat() - trackPaddingPx * 2f
        val naturalWidth = labelWidths.sum() +
            tabPaddingPx * 2f * labels.size +
            gapPx * (labels.size - 1)
        val contentWidth = max(viewportWidth, naturalWidth)
        val extraPerTab = (contentWidth - naturalWidth) / labels.size
        val widths = labelWidths.map { it + tabPaddingPx * 2f + extraPerTab }
        val starts = FloatArray(labels.size).also { result ->
            var x = 0f
            widths.forEachIndexed { index, width ->
                result[index] = x
                x += width + gapPx
            }
        }

        val targetScroll = remember(selected, contentWidth, viewportWidth, widths) {
            centeredScroll(starts, widths, selected, viewportWidth, contentWidth)
        }
        val scrollState = rememberScrollState(initial = targetScroll)
        LaunchedEffect(selected, contentWidth, viewportWidth, widths) {
            if (contentWidth <= viewportWidth) return@LaunchedEffect
            val target = centeredScroll(starts, widths, selected, viewportWidth, contentWidth)
            if (scrollState.value != target) scrollState.animateScrollTo(target)
        }

        val indicatorLeft = remember { Animatable(starts[selected]) }
        val indicatorRight = remember { Animatable(starts[selected] + widths[selected]) }
        var indicatorInitialized by remember { mutableStateOf(false) }
        LaunchedEffect(pressedIndex, selected, widths, dragging) {
            if (dragging) return@LaunchedEffect
            val targetIndex = if (pressedIndex >= 0) pressedIndex else selected
            val targetLeft = starts[targetIndex]
            val targetRight = targetLeft + widths[targetIndex]
            if (!indicatorInitialized) {
                indicatorLeft.snapTo(targetLeft)
                indicatorRight.snapTo(targetRight)
                indicatorInitialized = true
            } else {
                launch {
                    indicatorLeft.animateTo(
                        targetValue = targetLeft,
                        animationSpec = GlassMotion.edgeSpring(targetLeft <= indicatorLeft.value),
                    )
                }
                launch {
                    indicatorRight.animateTo(
                        targetValue = targetRight,
                        animationSpec = GlassMotion.edgeSpring(targetLeft > indicatorLeft.value),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(trackPadding),
        ) {
            // 指示器在标签下面，单独一层；宽度和位置都在 layout 阶段读取动画值。
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        // 移植 miuix 的 navigationIndicatorBounds：
                        // 每帧先把左右边缘夹在轨道内，再减去横滑偏移。
                        val bounds = navigationIndicatorBounds(
                            left = indicatorLeft.value,
                            right = indicatorRight.value,
                            containerWidth = contentWidth,
                            inset = 0f,
                        )
                        val start = (bounds.x - scrollState.value).roundToInt()
                        val end = (bounds.y - scrollState.value).roundToInt()
                        val width = (end - start).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width),
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.placeRelative(start, 0)
                        }
                    }
                    .graphicsLayer {
                        val scale = indicatorScale
                        val insetPx = (1f - scale) * size.height
                        scaleX = if (size.width > 0f) ((size.width - insetPx) / size.width).coerceIn(0f, 1f) else 1f
                        scaleY = scale
                    }
                    .clip(indicatorShape)
                    .drawBehind { drawRect(indicatorFill) },
            )

            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    // 内容不足一屏时不启用横滑；到首尾由 ScrollState 夹住，
                    // overscrollEffect 置空避免两端出现越界回弹。
                    .horizontalScroll(
                        state = scrollState,
                        overscrollEffect = null,
                        enabled = contentWidth > viewportWidth,
                    )
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(GlassSegmentedTabRowDefaults.TabGap),
            ) {
                labels.forEachIndexed { index, label ->
                    FilterTabItem(
                        label = label,
                        selected = index == selected,
                        width = with(density) { widths[index].toDp() },
                        interactionSource = interactionSources[index],
                        selectedStyle = selectedStyle,
                        normalStyle = normalStyle,
                        color = labelColor,
                        indicatorShape = indicatorShape,
                        onClick = { onSelected(index) },
                        onPressedChange = { pressed ->
                            if (pressed) pressedIndex = index
                            else if (pressedIndex == index) pressedIndex = -1
                        },
                    )
                }
            }

            // 透明拖动层：只覆盖当前指示器，避免和轨道其他区域的横滑抢手势。
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val bounds = navigationIndicatorBounds(
                            left = indicatorLeft.value,
                            right = indicatorRight.value,
                            containerWidth = contentWidth,
                            inset = 0f,
                        )
                        val start = (bounds.x - scrollState.value).roundToInt()
                        val end = (bounds.y - scrollState.value).roundToInt()
                        val width = (end - start).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width),
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.placeRelative(start, 0)
                        }
                    }
                    .pointerInput(tabs.size, widths, contentWidth, selected) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                dragIndex = selected
                                dragCenterX = starts[selected] + widths[selected] / 2f
                                pressedIndex = selected
                            },
                            onDragEnd = {
                                val target = indexAtContentX(dragCenterX, starts, widths)
                                dragging = false
                                dragIndex = -1
                                pressedIndex = -1
                                onSelectState(target)
                            },
                            onDragCancel = {
                                dragging = false
                                dragIndex = -1
                                pressedIndex = -1
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragCenterX = (dragCenterX + dragAmount.x).coerceIn(0f, contentWidth)
                                val next = indexAtContentX(dragCenterX, starts, widths)
                                val target = navigationDragTarget(
                                    left = starts[next],
                                    width = widths[next],
                                    minLeft = 0f,
                                    maxRight = contentWidth,
                                    delta = dragAmount.x,
                                    changedItem = next != dragIndex,
                                )
                                scope.launch { indicatorLeft.animateTo(target.left, target.leftSpring) }
                                scope.launch { indicatorRight.animateTo(target.right, target.rightSpring) }
                                dragIndex = next
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun FilterTabItem(
    label: String,
    selected: Boolean,
    width: Dp,
    interactionSource: MutableInteractionSource,
    selectedStyle: TextStyle,
    normalStyle: TextStyle,
    color: Color,
    indicatorShape: top.yukonga.miuix.kmp.material.GlassShape,
    onClick: () -> Unit,
    onPressedChange: (Boolean) -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed) { onPressedChange(pressed) }
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.6f else 1f,
        animationSpec = GlassMotion.navContentFloat(),
        label = "filterTabPressAlpha",
    )
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(indicatorShape)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .graphicsLayer { alpha = pressAlpha }
            .padding(horizontal = GlassSegmentedTabRowDefaults.TabPaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = if (selected) selectedStyle else normalStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun indexAtContentX(
    x: Float,
    starts: FloatArray,
    widths: List<Float>,
): Int {
    if (starts.isEmpty()) return 0
    var result = 0
    for (index in starts.indices) {
        if (x >= starts[index]) result = index
    }
    return result.coerceIn(0, starts.lastIndex)
}

private data class NavigationDragTarget(
    val left: Float,
    val right: Float,
    val leftSpring: SpringSpec<Float>,
    val rightSpring: SpringSpec<Float>,
)

private fun navigationDragTarget(
    left: Float,
    width: Float,
    minLeft: Float,
    maxRight: Float,
    delta: Float,
    changedItem: Boolean,
): NavigationDragTarget {
    val start = left.coerceIn(minLeft, (maxRight - width).coerceAtLeast(minLeft))
    val end = start + width
    val trail = if (changedItem) 0f else (abs(delta) * 4f).coerceAtMost(60f)
    val movingRight = delta > 0f
    return NavigationDragTarget(
        left = if (delta > 0f && end < maxRight) start - trail else start,
        right = if (delta < 0f && start > minLeft) end + trail else end,
        leftSpring = if (!changedItem) GlassMotion.navDragFollow() else GlassMotion.edgeSpring(!movingRight),
        rightSpring = if (!changedItem) GlassMotion.navDragFollow() else GlassMotion.edgeSpring(movingRight),
    )
}

private fun navigationIndicatorBounds(
    left: Float,
    right: Float,
    containerWidth: Float,
    inset: Float,
): Offset {
    val min = inset.coerceIn(0f, containerWidth.coerceAtLeast(0f) / 2f)
    val max = (containerWidth - inset).coerceAtLeast(min)
    val start = left.coerceIn(min, max)
    return Offset(start, right.coerceIn(start, max))
}

private fun centeredScroll(
    starts: FloatArray,
    widths: List<Float>,
    index: Int,
    viewportWidth: Float,
    contentWidth: Float,
): Int {
    if (starts.isEmpty() || widths.isEmpty()) return 0
    val safeIndex = index.coerceIn(0, starts.lastIndex)
    val maxScroll = (contentWidth - viewportWidth).coerceAtLeast(0f)
    val center = starts[safeIndex] + widths[safeIndex] / 2f - viewportWidth / 2f
    return center.roundToInt().coerceIn(0, maxScroll.roundToInt())
}
