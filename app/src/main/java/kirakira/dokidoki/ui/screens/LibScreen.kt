package kirakira.dokidoki.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.sp
import kirakira.dokidoki.data.DictFile
import kirakira.dokidoki.data.FileStatus
import kirakira.dokidoki.data.DictUiState
import kirakira.dokidoki.data.FileSortMode
import kirakira.dokidoki.ui.components.DictCard
import kirakira.dokidoki.ui.components.DictTopBar
import kirakira.dokidoki.ui.components.DropdownAnchor
import kirakira.dokidoki.ui.components.captureDropdownAnchor
import kirakira.dokidoki.ui.components.DictScaffoldPage
import kirakira.dokidoki.ui.components.StatusBadge
import kirakira.dokidoki.ui.components.rememberMiuixIndication
import kirakira.dokidoki.ui.theme.DictTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassFloatingToolbar
import top.yukonga.miuix.kmp.glass.GlassFloatingToolbarDefaults
import top.yukonga.miuix.kmp.glass.GlassMotion
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.glass.glassPressScale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// One lazy item contains the filter chips and sort row, before the first file.
private const val LIBRARY_ITEMS_START = 1

@Composable
fun LibScreen(
    state: DictUiState,
    isActive: Boolean,
    onEnterSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onFocusHandled: () -> Unit,
    onSortDescending: (Boolean) -> Unit,
    onOpenSortMenu: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = DictTheme.palette
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableIntStateOf(0) }
    // 标签 = 全部 + 文件格式 + 处理标记；统一在一行里横向滑动。
    val filterLabels = remember { LIBRARY_FILTERS.map { it.label } }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    var wasActive by remember { mutableStateOf(isActive) }
    val activeState = rememberUpdatedState(isActive)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    fun toggleSearch() {
        if (searchVisible) {
            searchVisible = false
            query = ""
            focusManager.clearFocus()
            keyboard?.hide()
        } else {
            searchVisible = true
            searchFocusRequest += 1
        }
    }
    LaunchedEffect(searchFocusRequest) {
        if (searchFocusRequest == 0) return@LaunchedEffect
        delay(40)
        if (activeState.value && searchVisible) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(isActive) {
        if (isActive) {
            wasActive = true
        } else if (wasActive) {
            focusManager.clearFocus()
            keyboard?.hide()
            wasActive = false
        }
    }
    LaunchedEffect(state.selectionMode) {
        if (state.selectionMode) {
            searchVisible = false
            query = ""
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    val orderedFiles = remember(state.files, state.sortMode) {
        when (state.sortMode) {
            FileSortMode.MODIFIED -> state.files.sortedBy { it.modifiedAt }
            FileSortMode.SIZE -> state.files.sortedBy { it.sizeBytes }
            // Compute the lowercase sort key once per file, not for each comparison.
            FileSortMode.NAME -> state.files.map { it to it.name.lowercase() }
                .sortedBy { it.second }
                .map { it.first }
        }
    }
    val visibleFiles = remember(orderedFiles, query, selectedFilter, state.sortDescending) {
        val noQuery = query.isBlank()
        val spec = LIBRARY_FILTERS.getOrNull(selectedFilter) ?: LIBRARY_FILTERS.first()
        val filtered = if (noQuery && spec === LIBRARY_FILTERS.first()) orderedFiles else orderedFiles.filter { file ->
            (noQuery || file.name.contains(query, ignoreCase = true)) && spec.matches(file)
        }
        if (state.sortDescending) filtered.asReversed() else filtered
    }
    // 只在收到新的定位请求时清一次筛选；否则用户自己选的标签会被反复重置。
    var handledFocusRequest by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(state.libraryFocusId, state.libraryFocusRequest, isActive, visibleFiles) {
        if (!isActive) return@LaunchedEffect
        val focusId = state.libraryFocusId ?: return@LaunchedEffect
        if (state.libraryFocusRequest == handledFocusRequest) return@LaunchedEffect
        if (query.isNotEmpty() || selectedFilter != 0) {
            // 先恢复成完整列表，下一轮再解析行号。
            query = ""
            selectedFilter = 0
            return@LaunchedEffect
        }
        handledFocusRequest = state.libraryFocusRequest
        val targetIndex = visibleFiles.indexOfFirst { it.id == focusId }
        if (targetIndex >= 0) {
            listState.requestScrollToItem(LIBRARY_ITEMS_START + targetIndex)
            highlightedId = focusId
        }
        onFocusHandled()
    }
    LaunchedEffect(highlightedId) {
        val focusId = highlightedId ?: return@LaunchedEffect
        delay(1600)
        if (highlightedId == focusId) highlightedId = null
    }
    DictScaffoldPage(
        modifier = modifier,
        topBar = {
            DictTopBar(
                title = if (state.selectionMode) "已选 ${state.selectedFileIds.size} 项" else "词库",
                badge = if (state.selectionMode) null else "${state.files.size} 个",
                searchActive = searchVisible,
                onSearchClick = if (state.selectionMode) null else ({ toggleSearch() }),
                onSelectAll = if (state.selectionMode) onSelectAll else null,
                actionSize = 40.dp,
                actionIconSize = 22.dp,
                // 搜索框与分类标签同处顶栏的 bottomContent 槽位：
                // 展开搜索时搜索框紧贴标题下方，分类标签顺延到它下面，两者位置互换。
                bottomContent = {
                    Column {
                        AnimatedVisibility(
                            visible = searchVisible,
                            enter = fadeIn(tween(120)) + expandVertically(tween(170)),
                            exit = fadeOut(tween(90)) + shrinkVertically(tween(130)),
                        ) {
                            Column {
                                Spacer(Modifier.height(12.dp))
                                SearchBar(
                                    query = query,
                                    onQueryChange = { query = it },
                                    focusRequester = focusRequester,
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                        FilterChips(
                            labels = filterLabels,
                            selected = selectedFilter,
                            onSelect = { selectedFilter = it },
                        )
                    }
                },
            )
        },
    ) { _ ->
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 190.dp),
        ) {
            item(key = "controls") {
                SortHeader(
                    label = state.sortMode.label + if (state.sortDescending) " ↓" else " ↑",
                    onOpen = onOpenSortMenu,
                )
            }
            if (visibleFiles.isEmpty()) {
                item(key = "empty") { EmptyLibraryCard() }
            } else {
                itemsIndexed(
                    items = visibleFiles,
                    key = { index, file -> file.id?.let { "file:$it" } ?: "untracked:$index" },
                    contentType = { _, _ -> "file" },
                ) { index, file ->
                    LibRow(
                        file = file,
                        first = index == 0,
                        last = index == visibleFiles.lastIndex,
                        selectionMode = state.selectionMode,
                        selected = file.id != null && file.id in state.selectedFileIds,
                        highlighted = file.id != null && file.id == highlightedId,
                        onEnterSelection = onEnterSelection,
                        onToggleSelection = onToggleSelection,
                        onSelectAll = onSelectAll,
                    )
                }
            }
            // 选择模式不显示底部提示：整条摘要只在浏览时出现。
            if (!state.selectionMode) {
                item(key = "summary") {
                    val totalSize = remember(visibleFiles) { formatTotalSize(visibleFiles) }
                    Text(
                        "共 ${visibleFiles.size} 个词库 · 总计 $totalSize",
                        fontSize = 10.5.sp,
                        color = p.text3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortHeader(
    label: String,
    onOpen: (Rect) -> Unit,
) {
    val p = DictTheme.palette
    val anchor = remember { DropdownAnchor() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("全部词库", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = p.text2)
        Spacer(Modifier.weight(1f))
        // 按下灰遮罩圆角 8dp。
        val sortInteraction = remember { MutableInteractionSource() }
        val sortPressed = sortInteraction.collectIsPressedAsState().value
        val sortMask by animateColorAsState(
            targetValue = if (sortPressed) p.text.copy(alpha = 0.10f) else Color.Transparent,
            animationSpec = tween(durationMillis = 120),
            label = "sort-mask",
        )
        Row(
            modifier = Modifier
                .captureDropdownAnchor(anchor)
                .clip(RoundedCornerShape(8.dp))
                .background(sortMask)
                .clickable(
                    interactionSource = sortInteraction,
                    indication = null,
                ) { onOpen(anchor.bounds()) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("排序：$label", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = p.accent)
            Spacer(Modifier.width(3.dp))
            Icon(
                MiuixIcons.Basic.ArrowUpDown,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = p.accent,
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    val colors = MiuixTheme.colorScheme
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(52.dp)
            .focusRequester(focusRequester),
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = colors.surfaceContainerHigh,
            labelColor = colors.onBackgroundVariant,
            borderColor = colors.primary,
        ),
        cornerRadius = 26.dp,
        label = "搜索词库名",
        useLabelAsPlaceholder = true,
        singleLine = true,
        textStyle = MiuixTheme.textStyles.main.copy(fontSize = 14.sp),
        leadingIcon = {
            Icon(
                MiuixIcons.Basic.Search,
                contentDescription = null,
                modifier = Modifier.padding(start = 16.dp, end = 10.dp).size(17.dp),
                tint = colors.onBackgroundVariant,
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

/** 词库页的一个筛选标签：文案 + 命中条件。 */
private class LibraryFilter(val label: String, val matches: (DictFile) -> Boolean)

private val LIBRARY_FILTERS = listOf(
    LibraryFilter("全部") { true },
    LibraryFilter("YAML") { it.name.endsWith(".yaml", true) || it.name.endsWith(".yml", true) },
    LibraryFilter("TXT") { it.name.endsWith(".txt", true) },
    LibraryFilter("TSV") { it.name.endsWith(".tsv", true) },
    LibraryFilter("ZIP") { it.name.endsWith(".zip", true) },
    // 处理标记分类。
    LibraryFilter("拼和辅") { it.status == FileStatus.PINYIN_AUX_DONE },
    LibraryFilter("拼音") { it.status == FileStatus.PINYIN_DONE },
    LibraryFilter("辅助码") { it.status == FileStatus.AUX_DONE },
    LibraryFilter("删辅助码") { it.status == FileStatus.AUX_STRIPPED },
    LibraryFilter("多音字") { it.polyphonicHandled },
    LibraryFilter("取消") { it.status == FileStatus.CANCELLED },
)

/** 轨道高度：沿用本页原来的 38dp，换材质不改版面。 */
private val FilterTrackHeight = 38.dp

/**
 * 分类标签：miuix 0.9.5-beta03 的玻璃轨道 + 玻璃指示器。
 *
 * 结构照 [top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow]：整条轨道是一块玻璃面板
 * （puredThinGlass 材质、bloom 描边、Float 投影），选中项不是另一块玻璃，而是轨道内
 * 随选中位置滑动的指示器胶囊——beta03 的指示器本来就是「玻璃上的一层着色」，颜色与
 * 按下反馈都取 [GlassSegmentedTabRowDefaults]。
 *
 * 指示器部分完全照 [GlassSegmentedTabRow]：左右两条边各一个 [Animatable]，
 * 切换时前缘用 [GlassMotion.navIndicator]、后缘用 [GlassMotion.navIndicatorTrail]，
 * 途中胶囊被拉长、到位回弹收拢；按下先预览、松手才切换；按下时按 glassPressScale 缩放。
 *
 * 与官方组件的差别只有一处：官方要求等宽且不滚动，这里标签有 12 个、宽度不等，
 * 所以仍用 LazyRow 横向滑动——指示器的两条边用「内容坐标」做动画，
 * 滚动偏移在布局阶段直接减掉，于是切换有弹簧、滚动仍逐帧贴合，快速滑动不丢帧。
 */
@Composable
private fun FilterChips(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val p = DictTheme.palette
    val density = LocalDensity.current
    val chipListState = rememberLazyListState()
    val spacingPx = with(density) { 2.dp.toPx() }
    // 只记录每个标签的宽度（宽度不随滚动变化），内容坐标由累加得出，天然稳定。
    var chipWidths by remember(labels.size) { mutableStateOf(FloatArray(labels.size)) }
    fun contentLeft(index: Int): Float {
        var x = 0f
        for (i in 0 until index.coerceAtMost(chipWidths.size)) {
            x += chipWidths[i] + spacingPx
        }
        return x
    }
    // 滚动偏移：首个可见项的内容坐标减去它在视口里的偏移（与 miuix TabRow 同一思路）。
    fun scrollOffset(): Float {
        val info = chipListState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
        return contentLeft(first.index) - first.offset
    }

    // 采样层：标签浮在顶栏自己的不透明底色上，所以只把这块底色记进 layer。
    // 玻璃画在 layer 之上，不会采到自己。
    val backdrop = rememberLayerBackdrop()

    // 指示器完全照 GlassSegmentedTabRow 的做法：
    // 左右两条边各是一个 Animatable，切换时用 GlassMotion.edgeSpring 分别驱动——
    // 朝移动方向的那条边是前缘（navIndicator，更快），另一条是后缘（navIndicatorTrail），
    // 所以胶囊在途中会被拉长，到位后回弹收拢。
    val indicatorLeft = remember { Animatable(0f) }
    val indicatorRight = remember { Animatable(0f) }
    // 按下先预览：指示器跟着按住的那颗走，松手才真正切换。
    var pressedIndex by remember { mutableIntStateOf(-1) }
    val indicatorIndex = if (pressedIndex >= 0) pressedIndex else selected
    val indicatorPressed = pressedIndex >= 0
    var indicatorSettled by remember { mutableStateOf(false) }

    val indicatorColor = GlassSegmentedTabRowDefaults.indicatorColor()
    val indicatorPressedColor = GlassSegmentedTabRowDefaults.indicatorPressedColor()
    val indicatorFill by animateColorAsState(
        targetValue = if (indicatorPressed) indicatorPressedColor else indicatorColor,
        animationSpec = if (indicatorPressed) GlassMotion.navPressEnter() else GlassMotion.navPressExit(),
        label = "filter-indicator-fill",
    )
    val innerHeightPx = with(density) { (FilterTrackHeight - GlassSegmentedTabRowDefaults.TrackPadding * 2).toPx() }
    val indicatorScale by animateFloatAsState(
        targetValue = if (indicatorPressed) glassPressScale(innerHeightPx, density.density) else 1f,
        animationSpec = if (indicatorPressed) GlassMotion.pressDown() else GlassMotion.pressUp(),
        label = "filter-indicator-press",
    )

    // 目标位置用「内容坐标」，滚动不参与动画：滚动偏移在布局阶段直接减掉，
    // 所以快速滑动时指示器逐帧贴着标签走，不会因为弹簧而滞后。
    // key 只取这一颗标签的位置，宽度测量刷新时不会把动画反复重启。
    val targetLeft = contentLeft(indicatorIndex)
    val targetWidth = chipWidths.getOrNull(indicatorIndex) ?: 0f
    LaunchedEffect(targetLeft, targetWidth) {
        if (targetWidth <= 0f) return@LaunchedEffect
        val targetRight = targetLeft + targetWidth
        if (!indicatorSettled) {
            // 首帧直接落位，避免从 0 滑进来。
            indicatorLeft.snapTo(targetLeft)
            indicatorRight.snapTo(targetRight)
            indicatorSettled = true
            return@LaunchedEffect
        }
        val movingRight = targetLeft > indicatorLeft.value
        launch { indicatorLeft.animateTo(targetLeft, GlassMotion.edgeSpring(leading = !movingRight)) }
        launch { indicatorRight.animateTo(targetRight, GlassMotion.edgeSpring(leading = movingRight)) }
    }

    // 选中居中：思路照 miuix TabRow 的 scrollToSelected——换标签时把选中项滚到视口中央。
    // 区别是这里标签不等宽，所以居中偏移要用「该标签自己的宽度」算：
    // 让内容坐标里的标签中心落在视口中心，即 offset = -(视口宽 - 标签宽) / 2。
    // scrollToItem 的 offset 是该项相对视口起点的偏移，负值表示推到视口左外侧。
    // 首尾项由 LazyListState 自动夹到合法滚动范围，所以不会滚出边界。
    // 居中滚动是程序发起的，不该被「滑动不等于切换」的守卫当成用户拖动。
    // 否则切换后的动画期间（约 300ms）点其他标签会被直接忽略。
    var centeringScroll by remember { mutableStateOf(false) }
    // 首次居中要等宽度测量完：chipWidths 在首帧还是全 0，视口也要等 LazyRow 布局完。
    // 用 snapshotFlow 等这两个条件就绪，而不是把就绪状态塞进 LaunchedEffect 的 key——
    // 那样每次宽度刷新都会重跑一遍，首次会连续居中两次。
    var centeredOnce by remember { mutableStateOf(false) }
    // snapshotFlow 里要读「当前」选中项，但 effect 的 key 是 Unit（只跑一次），
    // 所以用 rememberUpdatedState 拿到最新值，避免闭包捕获首帧的 selected。
    val latestSelected by rememberUpdatedState(selected)
    LaunchedEffect(Unit) {
        snapshotFlow {
            val width = chipWidths.getOrNull(latestSelected) ?: 0f
            val info = chipListState.layoutInfo
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            Triple(width, viewport, chipWidths.all { it > 0f })
        }.first { (width, viewport, ready) -> ready && width > 0f && viewport > 0f }
        // 首帧直接落位，避免一进页面就看到轨道自己滑一下。
        val index = latestSelected
        val width = chipWidths.getOrNull(index) ?: return@LaunchedEffect
        val info = chipListState.layoutInfo
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        chipListState.scrollToItem(index, -((viewport - width) / 2f).roundToInt())
        centeredOnce = true
    }
    // 之后每次换标签都动画居中；只跟 selected，按下预览不触发滚动。
    // centeredOnce 一起进 key：首次居中完成后这个 effect 才会真正开始工作。
    LaunchedEffect(selected, centeredOnce) {
        if (!centeredOnce) return@LaunchedEffect
        val width = chipWidths.getOrNull(selected) ?: return@LaunchedEffect
        if (width <= 0f) return@LaunchedEffect
        val info = chipListState.layoutInfo
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        if (viewport <= 0f) return@LaunchedEffect
        centeringScroll = true
        try {
            chipListState.animateScrollToItem(selected, -((viewport - width) / 2f).roundToInt())
        } finally {
            centeringScroll = false
        }
    }

    Box {
        // 玻璃采样的内容：与标签背后实际可见的顶栏底色一致。
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(backdrop)
                .background(p.bg),
        )
        GlassFloatingToolbar(
            modifier = Modifier.fillMaxWidth(),
            backdrop = backdrop,
            fill = p.card,
            // 轨道材质：puredThinGlass 默认 20dp 模糊，这里改成 0dp——
            // 不做背景模糊，只保留材质的三层颜色层、bloom 描边与投影。
            // 0dp 时 miuix 的 blur() 会直接返回，不建 RenderEffect，也就没有采样开销。
            material = GlassFloatingToolbarDefaults.material().copy(blurRadius = 0.dp),
            cornerRadius = FilterTrackHeight / 2,
            // 左右留白从 24dp 收到 12dp：轨道整体变长 24dp，比下方列表卡片更宽。
            outSidePadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shadow = GlassShadows.Float,
        ) {
            // 玻璃材质叠在采样层上时，轨道内部不要再画不透明底色，
            // 否则玻璃被盖住；这里只保留布局与裁切。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FilterTrackHeight)
                    // 溢出部分由容器平口裁掉。
                    .clipToBounds(),
            ) {
                // 指示器照 GlassSegmentedTabRow 的结构：外层节点铺满轨道、只负责定位，
                // 内层才是真正的胶囊（尺寸、缩放、圆角、填色都只作用在它身上）。
                // 两条边的动画值在这里直接减掉滚动偏移，所以切换有弹簧、滚动逐帧贴合。
                val trackPad = GlassSegmentedTabRowDefaults.TrackPadding
                val innerHeight = FilterTrackHeight - trackPad * 2
                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val start = (indicatorLeft.value - scrollOffset()).roundToInt()
                            val end = (indicatorRight.value - scrollOffset()).roundToInt()
                            val width = (end - start).coerceAtLeast(0)
                            val height = (constraints.maxHeight - trackPad.roundToPx() * 2).coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = width,
                                    maxWidth = width,
                                    minHeight = height,
                                    maxHeight = height,
                                ),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.placeRelative(IntOffset(start, trackPad.roundToPx()))
                            }
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 与 beta03 指示器一致：按下时整体缩小，宽度按高度差值等比收缩。
                            .graphicsLayer {
                                val scale = indicatorScale
                                val insetPx = (1f - scale) * size.height
                                scaleX = if (size.width > 0f) {
                                    ((size.width - insetPx) / size.width).coerceIn(0f, 1f)
                                } else {
                                    1f
                                }
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(innerHeight / 2))
                            .background(indicatorFill),
                    )
                }
                LazyRow(
                    state = chipListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    overscrollEffect = null,
                ) {
                    itemsIndexed(labels) { index, label ->
                        val isSelected = index == selected
                        val chipInteraction = remember { MutableInteractionSource() }
                        val chipPressed by chipInteraction.collectIsPressedAsState()
                        // 按下先让指示器跟过去预览，松手才切换（与 GlassSegmentedTabRow 一致）。
                        // 松开或手势取消都要复位，否则指示器会停在被按过的那颗上。
                        LaunchedEffect(chipPressed) {
                            if (chipPressed) {
                                if (centeringScroll || !chipListState.isScrollInProgress) pressedIndex = index
                            } else if (pressedIndex == index) {
                                pressedIndex = -1
                            }
                        }
                        val textColor by animateColorAsState(
                            targetValue = p.text,
                            animationSpec = tween(160),
                            label = "filter-chip-text",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .wrapContentWidth()
                                .clip(RoundedCornerShape(FilterTrackHeight / 2))
                                .clickable(
                                    interactionSource = chipInteraction,
                                    indication = null,
                                ) {
                                    // 滑动不等于切换：滚动中忽略点击。
                                    if (centeringScroll || !chipListState.isScrollInProgress) onSelect(index)
                                }
                                // 测量必须放在 padding 之前：挂在 padding 之后只会量到文字宽度，
                                // 左右各 16dp 的内边距被漏掉，累加后胶囊会一路偏左。
                                .onGloballyPositioned { coords ->
                                    val w = coords.size.width.toFloat()
                                    if (index in chipWidths.indices && chipWidths[index] != w) {
                                        val copy = chipWidths.copyOf()
                                        copy[index] = w
                                        chipWidths = copy
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard() {
    DictCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(54.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有导入词库", fontSize = 12.5.sp, color = DictTheme.palette.text3)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibRow(
    file: DictFile,
    first: Boolean,
    last: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    onEnterSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    val p = DictTheme.palette
    val indication = rememberMiuixIndication()
    val interaction = remember { MutableInteractionSource() }
    // Keep the joined card appearance without one GPU layer as tall as the entire list.
    val cap = remember(first, last) {
        val top = if (first) 20.dp else 0.dp
        val bottom = if (last) 20.dp else 0.dp
        if (first || last) Modifier.clip(RoundedCornerShape(top, top, bottom, bottom)) else Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(54.dp)
            .then(cap)
            .background(p.card)
            .then(
                when {
                    highlighted -> Modifier.background(p.tint)
                    selected -> Modifier.background(p.card2)
                    else -> Modifier
                },
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = indication,
                onClick = {
                    if (selectionMode && file.id != null) onToggleSelection(file.id)
                },
                onLongClick = {
                    if (selectionMode) onSelectAll()
                    else file.id?.let(onEnterSelection)
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) p.accent else p.card2)
                        .border(1.3.dp, if (selected) p.accent else p.text3, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            MiuixIcons.Basic.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = p.onAccent,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(p.tint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MiuixIcons.Glass.File, contentDescription = null, modifier = Modifier.size(18.dp), tint = p.onTint)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(file.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                // 只显示处理时间：未处理过的文件不再回退到源文件修改时间，
                // 否则列表里出现的是文件本身的时间，而不是处理词库的时间。
                val processedTime = file.processedAt.takeIf { it > 0L }
                val processed = remember(processedTime) { processedTime?.let { formatModified(it) } }
                Text(
                    if (processed != null) processed + " · " + file.meta else file.meta,
                    fontSize = 11.sp,
                    color = p.text2,
                    maxLines = 1,
                )
            }
            if (!selectionMode) {
                Spacer(Modifier.width(8.dp))
                // 手动替换过多音字拼音的文件额外带一个标记，不覆盖原有处理状态。
                if (file.polyphonicHandled) {
                    PolyphonicBadge()
                    Spacer(Modifier.width(6.dp))
                }
                // 空文案的状态（如旧记录里的已导出）不渲染徽章。
                if (file.status.label.isNotEmpty()) {
                    StatusBadge(file.status.label, file.status.highlighted)
                }
            }
        }
        if (!last && !selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 54.dp)
                    .fillMaxWidth(0.78f)
                    .height(0.7.dp)
                    .background(p.divider),
            )
        }
    }
}
/** 已手动替换过多音字拼音的标记。 */
@Composable
private fun PolyphonicBadge() {
    val p = DictTheme.palette
    // 与 StatusBadge 保持一致：同样的高度、圆角、tint 背景与内边距。
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.tint)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("多音字", fontSize = 10.sp, color = p.onTint)
    }
}

/** 列表时间文案：yy-MM-dd HH:mm:ss（入参为处理时间毫秒时间戳，0 表示还没处理过）。 */
private fun formatModified(millis: Long): String? {
    if (millis <= 0L) return null
    return SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}

private fun formatTotalSize(files: List<DictFile>): String {
    val total = files.sumOf { it.sizeBytes }
    return if (total < 1024 * 1024) String.format("%.1f KB", total / 1024.0) else String.format("%.1f MB", total / (1024.0 * 1024.0))
}