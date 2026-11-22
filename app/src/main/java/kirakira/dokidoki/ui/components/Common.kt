package kirakira.dokidoki.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupLayoutPosition
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.popupClipReveal
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.icon.glass.BookOutline
import top.yukonga.miuix.kmp.icon.glass.DigitalStatistics
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.Report
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.utils.MiuixIndication
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun Modifier.dashedBorder(color: Color, width: Dp = 1.3.dp, radius: Dp = 20.dp, dash: Dp = 7.dp, gap: Dp = 5.dp): Modifier =
    drawWithCache {
        val corner = CornerRadius(radius.toPx())
        val stroke = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
        )
        onDrawBehind { drawRoundRect(color = color, cornerRadius = corner, style = stroke) }
    }

@Composable
fun rememberMiuixIndication(): MiuixIndication {
    val p = DictTheme.palette
    return remember(p.isDark) {
        MiuixIndication(if (p.isDark) Color.White else Color.Black)
    }
}

@Composable
fun DictCard(
    modifier: Modifier = Modifier,
    color: Color = DictTheme.palette.card,
    radius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color),
        content = content,
    )
}

@Composable
fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    background: Color = DictTheme.palette.tint,
    tint: Color = DictTheme.palette.onTint,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val p = DictTheme.palette
    val indication = rememberMiuixIndication()
    val actionInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = p.text2)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                fontSize = 12.5.sp,
                color = p.accent,
                modifier = Modifier.then(
                    if (onAction != null) {
                        Modifier.clickable(
                            interactionSource = actionInteraction,
                            indication = indication,
                        ) { onAction() }
                    } else {
                        Modifier
                    },
                ),
            )
        }
    }
}

/** The page transition moves the whole page, but only its body scrolls. */
@Composable
fun FixedTopPage(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    CompositionLocalProvider(LocalDictTopBarScrollBehavior provides scrollBehavior) {
        Column(
            modifier
                .fillMaxSize()
                .padding(top = 4.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            topBar()
            content()
        }
    }
}

@Composable
fun FixedTopScrollPage(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 150.dp,
    topBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    FixedTopPage(modifier = modifier, topBar = topBar) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomPadding),
            content = content,
        )
    }
}

internal val LocalDictTopBarScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }

@Composable
fun DictTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    badge: String? = null,
    searchActive: Boolean = false,
    onSearchClick: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    actionSize: Dp = 48.dp,
    actionIconSize: Dp = 28.dp,
    trailingActionOffsetY: Dp = 0.dp,
) {
    val p = DictTheme.palette
    TopAppBar(
        title = title,
        modifier = modifier.fillMaxWidth(),
        color = p.bg,
        titleColor = p.text,
        largeTitleColor = p.text,
        defaultWindowInsetsPadding = false,
        titlePadding = 24.dp,
        navigationIconPadding = 24.dp,
        actionIconPadding = 16.dp,
        scrollBehavior = LocalDictTopBarScrollBehavior.current,
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(actionSize),
                    backgroundColor = p.card,
                    cornerRadius = actionSize / 2,
                    minHeight = actionSize,
                    minWidth = actionSize,
                ) {
                    Icon(
                        MiuixIcons.Basic.ArrowRight,
                        contentDescription = "返回",
                        modifier = Modifier.size(actionIconSize).rotate(180f),
                        tint = p.text,
                    )
                }
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .then(if (onSearchClick != null) Modifier.weight(1f, fill = false) else Modifier)
                            .widthIn(max = 84.dp)
                            .heightIn(min = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.tint)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            badge,
                            fontSize = 13.sp,
                            color = p.onTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onSearchClick != null) {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.offset(y = trailingActionOffsetY).size(actionSize),
                        backgroundColor = if (searchActive) p.tint else p.card,
                        cornerRadius = actionSize / 2,
                        minHeight = actionSize,
                        minWidth = actionSize,
                    ) {
                        Icon(
                            MiuixIcons.Basic.Search,
                            contentDescription = if (searchActive) "关闭搜索" else "搜索",
                            modifier = Modifier.size(actionIconSize),
                            tint = if (searchActive) p.onTint else p.text2,
                        )
                    }
                }
                if (onSelectAll != null) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(onClick = onSelectAll),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "全选",
                            fontSize = 13.sp,
                            color = p.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun PrimaryButton(
    label: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val p = DictTheme.palette
    val indication = rememberMiuixIndication()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(60.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.accent, p.accent2)))
            .clickable(
                interactionSource = interaction,
                indication = indication,
                enabled = enabled,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (sub == null) {
                Text(label, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = p.onAccent)
            } else {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.onAccent)
                Spacer(Modifier.height(1.dp))
                Text(sub, fontSize = 10.5.sp, color = p.onAccent.copy(alpha = 0.82f))
            }
        }
    }
}

@Composable
fun DialogAction(
    label: String,
    color: Color,
    enabled: Boolean = true,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    val indication = rememberMiuixIndication()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = indication,
                enabled = enabled,
            ) { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
fun SegmentSlider(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = DictTheme.palette
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.card2),
    ) {
        val density = LocalDensity.current
        val inset = 4.dp
        val itemWidth = (maxWidth - inset * 2) / items.size
        val position = remember { Animatable(selectedIndex.toFloat()) }
        LaunchedEffect(selectedIndex) {
            val target = selectedIndex.toFloat()
            if (position.value != target) position.animateTo(target, tween(200, easing = LinearEasing))
        }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        with(density) { (inset + itemWidth * position.value).toPx() }.roundToInt(),
                        with(density) { inset.toPx() }.roundToInt(),
                    )
                }
                .width(itemWidth)
                .height(maxHeight - inset * 2)
                .clip(RoundedCornerShape(8.dp))
                .background(p.card),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(inset),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) p.accent else p.text2,
                    animationSpec = tween(160),
                    label = "segment-text",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val p = DictTheme.palette
    Box(
        modifier = modifier
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) p.tint else p.card2)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 10.sp, color = if (highlighted) p.onTint else p.text2)
    }
}

@Composable
fun DictBottomBar(selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = DictTheme.palette
    val items = remember {
        listOf(
            GlassNavigationItem(MiuixIcons.Glass.HomeOutline, "首页"),
            GlassNavigationItem(MiuixIcons.Glass.BookOutline, "词库"),
            GlassNavigationItem(MiuixIcons.Glass.DigitalStatistics, "报告"),
            GlassNavigationItem(MiuixIcons.Glass.SettingsOutline, "设置"),
        )
    }
    val barHeight = 54.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .height(barHeight),
        contentAlignment = Alignment.Center,
    ) {
        GlassNavigationBar(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            backdrop = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            defaultWindowInsetsPadding = false,
            shadow = GlassShadows.Float,
            height = barHeight,
            selectedColor = p.text,
            unselectedColor = p.text,
            indicatorColor = if (p.isDark) {
                Color.White.copy(alpha = 0.12f)
            } else {
                Color.Black.copy(alpha = 0.10f)
            },
            indicatorPressedColor = if (p.isDark) {
                Color.White.copy(alpha = 0.18f)
            } else {
                Color.Black.copy(alpha = 0.16f)
            },
        )
    }
}

/** Keep live layout coordinates; compute clipped root bounds only when opening the menu. */
internal class DropdownAnchor {
    private var coordinates: LayoutCoordinates? = null

    fun update(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    fun bounds(): Rect = coordinates?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero
}

internal fun Modifier.captureDropdownAnchor(anchor: DropdownAnchor): Modifier = onPlaced(anchor::update)

data class DropdownSpec(
    val anchor: Rect,
    val items: List<String>,
    val selectedIndex: Int,
    val key: String = "",
    val alignToAnchorRight: Boolean = false,
    val endInset: Dp = 0.dp,
    /** Positive overlaps the anchor row; negative leaves a gap. */
    val verticalInset: Dp = (-6).dp,
)

@Composable
fun DropdownOverlay(
    spec: DropdownSpec,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val p = DictTheme.palette
    val density = LocalDensity.current
    val fractionAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(0f) }
    val dimAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { fractionAnim.animateTo(1f, ListPopupDefaults.FractionAnimationSpec) }
        launch { alphaAnim.animateTo(1f, ListPopupDefaults.AlphaEnterAnimationSpec) }
        launch { dimAnim.animateTo(1f, ListPopupDefaults.DimEnterAnimationSpec) }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(p.scrim.copy(alpha = p.scrim.alpha * dimAnim.value)) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
    ) {
        val panelWidth = 196.dp
        val panelHeight = (spec.items.size * 52 + 12).dp
        val anchorRight = with(density) { spec.anchor.right.toDp() }
        val maxPanelX = (maxWidth - panelWidth - 12.dp).coerceAtLeast(12.dp)
        val desiredX = if (spec.alignToAnchorRight) {
            anchorRight - panelWidth - spec.endInset
        } else {
            maxWidth - 16.dp - panelWidth
        }
        val panelX = desiredX.coerceIn(12.dp, maxPanelX)
        val anchorTop = with(density) { spec.anchor.top.toDp() }
        val anchorBottom = with(density) { spec.anchor.bottom.toDp() }
        // 按页面剩余空间决定展开方向：下方放得下就向下，放不下才翻到上方（上方更大时）。
        val spaceBelow = maxHeight - anchorBottom - 12.dp
        val spaceAbove = anchorTop - 12.dp
        val needed = panelHeight - spec.verticalInset
        val showAbove = spaceBelow < needed && spaceAbove > spaceBelow
        val desiredY = if (showAbove) {
            anchorTop - panelHeight + spec.verticalInset
        } else {
            anchorBottom - spec.verticalInset
        }
        val panelY = desiredY.coerceIn(
            12.dp,
            (maxHeight - panelHeight - 12.dp).coerceAtLeast(12.dp),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(with(density) { panelX.toPx() }.roundToInt(), with(density) { panelY.toPx() }.roundToInt()) }
                .width(196.dp)
                .height(panelHeight)
                .graphicsLayer {
                    val progress = fractionAnim.value
                    val scale = 0.15f + 0.85f * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = alphaAnim.value
                    transformOrigin = TransformOrigin(1f, if (showAbove) 1f else 0f)
                }
                .popupClipReveal(
                    fractionProgress = { fractionAnim.value },
                    popupLayoutPosition = PopupLayoutPosition(
                        showBelow = !showAbove,
                        showAbove = showAbove,
                        isRightAligned = true,
                    ),
                    cornerRadius = 18.dp,
                    squircleEnabled = false,
                )
                .clip(RoundedCornerShape(18.dp))
                .background(p.popup)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                spec.items.forEachIndexed { index, item ->
                val itemInteraction = remember { MutableInteractionSource() }
                val itemPressed = itemInteraction.collectIsPressedAsState().value
                val itemMask by animateColorAsState(
                    targetValue = if (itemPressed) Color.Black.copy(alpha = 0.10f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 120),
                    label = "dropdown-item-mask",
                )
                val topPad = if (index == 0) 6.dp else 0.dp
                val bottomPad = if (index == spec.items.lastIndex) 6.dp else 0.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp + topPad + bottomPad)
                        .background(itemMask)
                        .clickable(
                            interactionSource = itemInteraction,
                            indication = null,
                        ) { onSelect(index) }
                        .padding(top = topPad, bottom = bottomPad),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item,
                        fontSize = 15.sp,
                        fontWeight = if (index == spec.selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == spec.selectedIndex) p.accent else p.text,
                        modifier = Modifier
                            .padding(start = 18.dp)
                            .weight(1f),
                    )
                    if (index == spec.selectedIndex) {
                        Icon(
                            MiuixIcons.Basic.Check,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 18.dp)
                                .size(16.dp),
                            tint = p.accent,
                        )
                    }
                }
            }
        }
    }
}
}