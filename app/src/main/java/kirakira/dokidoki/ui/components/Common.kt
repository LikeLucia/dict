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
import top.yukonga.miuix.kmp.basic.Scaffold
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

/**
 * 与设置页一致的 Miuix Scaffold 骨架：顶栏固定在 Scaffold.topBar，正文在 content 内滚动。
 * 供需要自定义滚动容器（如 LazyColumn）的页面使用。
 */
@Composable
fun DictScaffoldPage(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    content: @Composable ColumnScope.(PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    CompositionLocalProvider(LocalDictTopBarScrollBehavior provides scrollBehavior) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            // Scaffold 的 topBar 槽位内部是 Box：直接展开多个子项会叠在同一原点，
            // 搜索框因此压住大标题和状态栏。这里统一包一层 Column，标题在上、搜索框排在下方。
            topBar = { Column(Modifier.fillMaxWidth()) { topBar() } },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = DictTheme.palette.bg,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 正文作为玻璃采样源，顶栏与底栏共用它。
                    .then(pageBackdropRecordModifier()),
            ) {
                content(padding)
            }
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
                .padding(bottom = bottomPadding)
                // 把正文录进玻璃采样层：顶栏与底栏才能采到滚动内容。
                // 只有前台页录，Pager 预组合的相邻页不写同一层。
                .then(pageBackdropRecordModifier()),
            content = content,
        )
    }
}

/** 当前页面的正文是否该录进玻璃采样层。 */
@Composable
internal fun pageBackdropRecordModifier(): Modifier {
    val backdrop = LocalBarBackdrop.current ?: return Modifier
    if (!LocalPageActive.current) return Modifier
    return backdrop.recordModifier()
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
    /** 标题栏下方的固定内容（如分类标签）：随顶栏一起固定，不参与正文滚动。 */
    bottomContent: @Composable () -> Unit = {},
) {
    val p = DictTheme.palette
    // 顶栏玻璃：与 c001apk 的 MiuixScaffold 同一思路——判空决定材质。
    // 有 backdrop 时顶栏底色透明，背后垫一层模糊；否则保持原来的纯色 p.bg。
    val backdrop = LocalBarBackdrop.current
    val barSurface: Modifier = if (backdrop == null) Modifier else Modifier.barBlur(backdrop)
    Box(Modifier.fillMaxWidth()) {
        // 模糊层铺满顶栏（含 bottomContent 的分类轨道区域），
        // 它只在 backdrop 非空时生效，其余情况是空 Modifier。
        Box(Modifier.matchParentSize().then(barSurface))
    TopAppBar(
        title = title,
        modifier = modifier.fillMaxWidth(),
        // 有玻璃时用透明底，让下面的模糊层透出来。
        color = if (backdrop != null) Color.Transparent else p.bg,
        titleColor = p.text,
        largeTitleColor = p.text,
        defaultWindowInsetsPadding = false,
        titlePadding = 24.dp,
        navigationIconPadding = 24.dp,
        actionIconPadding = 16.dp,
        bottomContent = bottomContent,
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
                    // 按下高亮为矩形：小圆角矩形底色，与圆形图标按钮区分。
                    val selectAllInteraction = remember { MutableInteractionSource() }
                    val selectAllPressed = selectAllInteraction.collectIsPressedAsState().value
                    val selectAllMask by animateColorAsState(
                        targetValue = if (selectAllPressed) p.text.copy(alpha = 0.10f) else Color.Transparent,
                        animationSpec = tween(durationMillis = 120),
                        label = "select-all-mask",
                    )
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            // 灰遮罩圆角 12dp。
                            .clip(RoundedCornerShape(12.dp))
                            .background(selectAllMask)
                            .clickable(
                                interactionSource = selectAllInteraction,
                                indication = null,
                                onClick = onSelectAll,
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "全选",
                            // 与顶栏标题（Miuix Title3 = 20sp）保持一致。
                            fontSize = 20.sp,
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
            // 判空双材质：非空走玻璃；为空时组件内部自动降级成 surfaceContainer 纯色。
            backdrop = LocalBarBackdrop.current?.rawBackdrop,
            // 材质与轨道统一：默认 puredThinGlass 在暗色下是灰黑色系，
            // 会让底栏整体发灰偏黑；改用只留模糊与极淡高光的中性材质。
            material = TrackGlassMaterial,
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
    /** 需要同时勾选多项时传入；非空时忽略 [selectedIndex]。 */
    val selectedIndexes: Set<Int>? = null,
    /** 在这些下标之前画一条分隔线，用于分组。 */
    val dividerBefore: Set<Int> = emptySet(),
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
        val dividerCount = spec.dividerBefore.count { it in 1..spec.items.lastIndex }
        // 每条分隔线占 0.7dp 线 + 上下各 6dp 间距。
        val panelHeight = (spec.items.size * 52 + 12 + dividerCount * 13).dp
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
        // 按整页可用区域判断上下：不扣状态栏，也不扣底部悬浮导航栏。
        val usableTop = 8.dp
        val usableBottom = (maxHeight - 8.dp).coerceAtLeast(usableTop + 1.dp)
        val spaceBelow = usableBottom - anchorBottom - 12.dp
        val spaceAbove = anchorTop - usableTop - 12.dp
        val needed = panelHeight - spec.verticalInset
        // 方向规则：默认向下；只有下方放不下、而上方放得下时才翻到上方；
        // 两边都放不下时取剩余空间更大的一侧。锚点在页面中部时上下都放得下，仍按默认向下，
        // 不会因为「上方剩余量恰好更大」就无端上翻。
        val showAbove = when {
            spaceBelow >= needed -> false
            spaceAbove >= needed -> true
            else -> spaceAbove > spaceBelow
        }
        val desiredY = if (showAbove) {
            anchorTop - panelHeight + spec.verticalInset
        } else {
            anchorBottom - spec.verticalInset
        }
        val panelY = desiredY.coerceIn(
            usableTop,
            (usableBottom - panelHeight).coerceAtLeast(usableTop),
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
                // 勾选状态：多选看 selectedIndexes，单选看 selectedIndex。
                val checked = spec.selectedIndexes?.contains(index) ?: (index == spec.selectedIndex)
                val topPad = if (index == 0) 6.dp else 0.dp
                val bottomPad = if (index == spec.items.lastIndex) 6.dp else 0.dp
                Column(Modifier.fillMaxWidth()) {
                    if (index in spec.dividerBefore && index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                .height(0.7.dp)
                                .background(p.divider),
                        )
                    }
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
                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                            color = if (checked) p.accent else p.text,
                            modifier = Modifier
                                .padding(start = 18.dp)
                                .weight(1f),
                        )
                        if (checked) {
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
}