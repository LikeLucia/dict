package kirakira.dokidoki.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.glass.File
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
 * 分类标签：用 miuix 0.9.5-beta03 的 [TabRowWithContour]。
 *
 * 它的画法正好对应要的外观，三块都由组件自己用 squircleBackground 绘制：
 * - **整条轨道**：`colors.backgroundColor(false)` 画在整行上，是白色胶囊；
 * - **选中项**：`colors.backgroundColor(true)` 画在选中那一格上，是灰色圆角胶囊；
 * - **未选中项**：`TabItemWithContour` 里只有 squircleClip 与 selectable，
 *   没有任何背景，所以文字后面是空的，透出轨道底色。
 *
 * 选 [TabRowWithContour] 而不是 [TabRow]，是因为 [TabRow] 的轨道底色是裸的
 * `background(color)`，画出来是矩形；这里要胶囊形轨道。
 *
 * 标签等宽（`calculateTabWidth` 在 minWidth/maxWidth 之间按可用宽度均分），
 * 11 个标签算出来超过一屏，所以可以横向滑动；选中项由
 * `TabRowConfig.scrollToSelected` 自动滚到视口中央。
 */
@Composable
private fun FilterChips(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val p = DictTheme.palette
    TabRowWithContour(
        tabs = labels,
        selectedTabIndex = selected,
        onTabSelected = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = TabRowDefaults.tabRowColors(
            // 整条轨道的底色（未选中项透出它，自己不带背景）：组件画成胶囊。
            backgroundColor = p.card,
            contentColor = p.text2,
            // 选中项的圆角灰色胶囊：同样是组件用 squircleBackground 画，
            // 半径 = cornerRadius（14dp），标签高 28dp，正好是一颗完整胶囊。
            selectedBackgroundColor = if (p.isDark) {
                Color.White.copy(alpha = 0.12f)
            } else {
                Color.Black.copy(alpha = 0.10f)
            },
            selectedContentColor = p.text,
        ),
        minWidth = TabRowDefaults.TabRowWithContourMinWidth,
        maxWidth = TabRowDefaults.TabRowWithContourMaxWidth,
        height = FilterTrackHeight,
        cornerRadius = FilterTrackHeight / 2 - 5.dp,
        itemSpacing = 2.dp,
    )
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