package kirakira.dokidoki.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kirakira.dokidoki.data.DictFile
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
import top.yukonga.miuix.kmp.glass.GlassFloatingToolbar
import top.yukonga.miuix.kmp.glass.GlassMotion
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
        val filtered = if (noQuery && selectedFilter == 0) orderedFiles else orderedFiles.filter { file ->
            val name = file.name
            (noQuery || name.contains(query, ignoreCase = true)) && when (selectedFilter) {
                1 -> name.endsWith(".yaml", true) || name.endsWith(".yml", true)
                2 -> name.endsWith(".txt", true)
                3 -> name.endsWith(".tsv", true)
                4 -> name.endsWith(".zip", true)
                else -> true
            }
        }
        if (state.sortDescending) filtered.asReversed() else filtered
    }
    LaunchedEffect(state.libraryFocusId, state.libraryFocusRequest, isActive, query, selectedFilter, visibleFiles) {
        if (!isActive) return@LaunchedEffect
        val focusId = state.libraryFocusId ?: return@LaunchedEffect
        if (query.isNotEmpty() || selectedFilter != 0) {
            // Re-enter the effect with the unfiltered list before resolving the row index.
            query = ""
            selectedFilter = 0
            return@LaunchedEffect
        }
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
        Row(
            modifier = Modifier.captureDropdownAnchor(anchor).clickable { onOpen(anchor.bounds()) },
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

/**
 * 分类标签：与底部导航栏同款的 miuix 玻璃胶囊（GlassFloatingToolbar）。
 * 指示器沿用悬浮底栏的实现：左右两条边各用一条弹簧（GlassMotion.edgeSpring），
 * 移动时前缘领先、后缘跟随，胶囊在途中被拉长，到位后回弹收拢；
 * 文字配色也照底栏来——选中与否都读正文色，由指示器胶囊本身区分选中项。
 */
@Composable
private fun FilterChips(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val p = DictTheme.palette
    val labels = listOf("全部", "YAML", "TXT", "TSV", "ZIP")
    val slots = labels.size
    val indicatorLeft = remember { Animatable(0f) }
    val indicatorRight = remember { Animatable(0f) }
    var trackWidth by remember { mutableIntStateOf(0) }
    val slotWidth = if (slots > 0 && trackWidth > 0) trackWidth.toFloat() / slots else 0f
    LaunchedEffect(selected, slotWidth) {
        if (slotWidth <= 0f) return@LaunchedEffect
        val newLeft = selected * slotWidth
        val newRight = newLeft + slotWidth
        if (indicatorRight.value <= indicatorLeft.value) {
            // 首次布局直接落位，不从 0 号槽滑入。
            indicatorLeft.snapTo(newLeft)
            indicatorRight.snapTo(newRight)
        } else {
            // 与 GlassNavigationBar 一致：向左移动时前缘用 navIndicator，向右时后缘用 navIndicatorTrail。
            launch {
                indicatorLeft.animateTo(newLeft, GlassMotion.edgeSpring(newLeft <= indicatorLeft.value))
            }
            launch {
                indicatorRight.animateTo(newRight, GlassMotion.edgeSpring(newLeft > indicatorLeft.value))
            }
        }
    }
    // 与底栏一致：深色白色淡填充，浅色黑色淡填充。
    val indicatorColor = if (p.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    GlassFloatingToolbar(
        modifier = Modifier.fillMaxWidth(),
        backdrop = null,
        fill = p.card,
        cornerRadius = 26.dp,
        outSidePadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
        shadow = GlassShadows.Float,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 胶囊高度 38dp。
                .height(38.dp)
                .onSizeChanged { trackWidth = it.width }
                // 指示器画在等宽槽位坐标系里，位置完全由槽宽和左右两条边决定。
                .drawBehind {
                    val left = indicatorLeft.value
                    val right = indicatorRight.value
                    if (right <= left) return@drawBehind
                    val height = size.height
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, height),
                        cornerRadius = CornerRadius(height / 2f),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                val textColor by animateColorAsState(
                    targetValue = p.text,
                    animationSpec = tween(160),
                    label = "filter-chip-text",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        // 关掉 clickable 自带的按压指示（ripple）：它是一层没有过渡的高亮，
                        // 会和下面那条带弹簧动画的指示器胶囊叠成两层。选中态只由指示器胶囊表达。
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                val modified = remember(file.modifiedAt) { formatModified(file.modifiedAt) }
                Text(
                    if (modified != null) modified + " · " + file.meta else file.meta,
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
                StatusBadge(file.status.label, file.status.highlighted)
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
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.card2)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("多音字", fontSize = 10.sp, color = p.text2)
    }
}

/** 词库修改时间：yy-MM-dd HH:mm:ss（modifiedAt 为毫秒时间戳，0 表示未知）。 */
private fun formatModified(millis: Long): String? {
    if (millis <= 0L) return null
    return SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}

private fun formatTotalSize(files: List<DictFile>): String {
    val total = files.sumOf { it.sizeBytes }
    return if (total < 1024 * 1024) String.format("%.1f KB", total / 1024.0) else String.format("%.1f MB", total / (1024.0 * 1024.0))
}