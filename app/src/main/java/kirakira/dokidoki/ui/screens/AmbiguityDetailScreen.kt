package kirakira.dokidoki.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kirakira.dokidoki.data.AmbiguousEntry
import kirakira.dokidoki.data.AmbiguousFileGroup
import kirakira.dokidoki.data.AmbiguousDetailPage
import kotlinx.coroutines.CancellationException
import kirakira.dokidoki.ui.components.DictTopBar
import kirakira.dokidoki.ui.components.LocalDictTopBarScrollBehavior
import kirakira.dokidoki.ui.theme.DictTheme
import kirakira.dokidoki.ui.components.FixedTopPage
import kirakira.dokidoki.ui.components.rememberMiuixIndication
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DETAIL_PAGE_SIZE = 200

@Composable
fun AmbiguityDetailScreen(
    group: AmbiguousFileGroup,
    isActive: Boolean,
    onBack: () -> Unit,
    onLoadPage: suspend (String, Int, Int, String) -> AmbiguousDetailPage,
    /** 替换模式点读音时调用：把该读音写回输出文件对应行。返回是否成功。 */
    onReplaceReading: suspend (line: Int, syllableIndex: Int, reading: String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    // 长按卡片进入替换模式：此时点读音是替换，而不是复制。
    var replaceMode by remember(group.fileId) { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    var wasActive by remember { mutableStateOf(isActive) }
    val activeState = rememberUpdatedState(isActive)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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
    val q = query.trim()
    var pageIndex by remember(group.fileId, q) { mutableIntStateOf(0) }
    var shown by remember(group.fileId, q, pageIndex) { mutableStateOf(emptyList<AmbiguousEntry>()) }
    val offsets = remember(group.fileId, q) { mutableStateListOf(0) }
    var nextOffset by remember(group.fileId, q, pageIndex) { mutableStateOf<Int?>(null) }
    var loading by remember(group.fileId, q, pageIndex) { mutableStateOf(true) }
    var loadError by remember(group.fileId, q, pageIndex) { mutableStateOf<String?>(null) }
    LaunchedEffect(group, q, pageIndex) {
        try {
            val offset = offsets[pageIndex]
            val loaded = if (group.summary != null) {
                if (q.isNotEmpty()) delay(150)
                onLoadPage(group.fileId, offset, DETAIL_PAGE_SIZE, q)
            } else {
                val numeric = q.isNotEmpty() && q.all(Char::isDigit)
                val exact = q.drop(1).takeIf { q.startsWith("l", true) && it.isNotEmpty() && it.all(Char::isDigit) }
                val rows = group.entries.filter { entry ->
                    entry.message.contains(q, true) || entry.pinyin.contains(q, true) ||
                        entry.readings.any { reading -> reading.character.contains(q) || reading.used.contains(q, true) ||
                            reading.others.any { it.contains(q, true) } } ||
                        (numeric && entry.line.toString().contains(q)) || entry.line.toString() == exact
                }
                AmbiguousDetailPage(rows.drop(offset).take(DETAIL_PAGE_SIZE),
                    (offset + DETAIL_PAGE_SIZE).takeIf { it < rows.size })
            }
            shown = loaded.entries
            nextOffset = loaded.nextOffset
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = "明细读取失败：" + (e.message ?: "文件已移除或不可读")
        } finally {
            loading = false
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(q, pageIndex) {
        if (listState.layoutInfo.totalItemsCount > 0) listState.scrollToItem(0)
    }
    val scope = rememberCoroutineScope()
    // 右下角翻页按钮的显示条件：不在顶部显示「回到顶部」，不在底部显示「翻到底部」。
    val notAtTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.visibleItemsInfo.isNotEmpty() &&
                (info.visibleItemsInfo.first().index > 0 || listState.firstVisibleItemScrollOffset > 0)
        }
    }
    val notAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
            last.index < info.totalItemsCount - 1 || last.offset + last.size > viewportHeight - 1
        }
    }
    val lastRowKey = "line:${group.fileId}:${shown.lastOrNull()?.line ?: 0}"

    FixedTopPage(
        modifier = modifier,
        topBar = {
            DictTopBar(
                title = "多音字详情",
                onBack = onBack,
                badge = group.entryCount.toString() + " 行",
                searchActive = searchVisible,
                onSearchClick = { toggleSearch() },
                actionSize = 40.dp,
                actionIconSize = 22.dp,
            )
            AnimatedVisibility(
                visible = replaceMode,
                enter = fadeIn(tween(120)) + expandVertically(tween(170)),
                exit = fadeOut(tween(90)) + shrinkVertically(tween(130)),
            ) {
                ReplaceModeBar(onExit = { replaceMode = false })
            }
            AnimatedVisibility(
                visible = searchVisible,
                enter = fadeIn(tween(120)) + expandVertically(tween(170)),
                exit = fadeOut(tween(90)) + shrinkVertically(tween(130)),
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    SearchField(query, { query = it }, focusRequester)
                }
            }
        },
    ) {
        // 必须在 FixedTopPage 的 content 内读取：ScrollBehavior 由 FixedTopPage 内部的
        // CompositionLocalProvider 提供，在函数开头读只会拿到默认值 null。
        val topBarScrollBehavior = LocalDictTopBarScrollBehavior.current
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
            ) {
                if (shown.isNotEmpty()) {
                    FileCardsBackdrop(
                        listState = listState,
                        fileId = group.fileId,
                        lastRowKey = lastRowKey,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    contentPadding = PaddingValues(0.dp),
                    overscrollEffect = null,
                ) {
                    item(key = "top-spacing", contentType = "top-spacing") {
                        Spacer(Modifier.height(16.dp))
                    }
                    if (shown.isEmpty()) {
                        item(key = "empty", contentType = "empty-state") {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(72.dp),
                                insideMargin = PaddingValues(0.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (loading) "读取中…" else loadError ?: "没有匹配的行", fontSize = 12.5.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                                }
                            }
                        }
                    } else {
                        item(key = "file:${group.fileId}", contentType = "file-header") {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Card(
                                        modifier = Modifier.size(30.dp),
                                        cornerRadius = 9.dp,
                                        insideMargin = PaddingValues(0.dp),
                                        colors = CardDefaults.defaultColors(
                                            color = MiuixTheme.colorScheme.tertiaryContainer,
                                            contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                                        ),
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(MiuixIcons.Glass.File, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = group.fileName,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = group.entryCount.toString() + " 行",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                        itemsIndexed(
                            items = shown,
                            key = { _, entry -> "line:${group.fileId}:${entry.line}" },
                            contentType = { _, _ -> "polyphonic-line" },
                        ) { index, entry ->
                            DetailEntryRow(
                                entry,
                                showDivider = index != shown.lastIndex,
                                replaceMode = replaceMode,
                                onLongPress = { replaceMode = true },
                                onReplaceReading = onReplaceReading,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                        if (pageIndex > 0 || nextOffset != null) {
                            item(key = "pagination:" + group.fileId + ":" + pageIndex, contentType = "pagination") {
                                // 左右两侧各占一半剩余宽度，中间的页码才会真正居中。
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (pageIndex > 0) {
                                            Text(
                                                "上一页",
                                                modifier = Modifier.clickable { pageIndex-- },
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Text(
                                        "第 " + (pageIndex + 1) + " 页",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                        val next = nextOffset
                                        if (next != null) {
                                            Text("下一页", modifier = Modifier.clickable {
                                                if (pageIndex + 1 == offsets.size) offsets.add(next)
                                                pageIndex++
                                            }, color = MiuixTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                DetailJumpButtons(
                    notAtTop = notAtTop,
                    notAtBottom = notAtBottom,
                    onJumpTop = {
                        scope.launch {
                            // 滚动全程每帧把顶栏压到展开位：程序化滚动期间顶栏状态会被自身
                            // 的测量与回弹改写，只在开始或结束时设一次都留不住。
                            holdTopBar(topBarScrollBehavior?.state, collapse = false) {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                    onJumpBottom = {
                        // 只滚到本页底部，不自动翻页：翻页交给底部「下一页」，避免滚动完立刻跳页。
                        scope.launch {
                            val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            val scrollable = listState.canScrollBackward || listState.canScrollForward
                            if (scrollable) {
                                holdTopBar(topBarScrollBehavior?.state, collapse = true) {
                                    listState.animateScrollToItem(lastIndex)
                                }
                            } else {
                                listState.animateScrollToItem(lastIndex)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // 按截图红圈：贴右侧、位于分页行上方。
                        .padding(end = 8.dp, bottom = 72.dp),
                )
            }
        }
    }
}
/**
 * 在 [block]（通常是滚动动画）执行期间，每帧把顶栏压到目标折叠态。
 *
 * 程序化滚动不派发 nestedScroll，顶栏不会自己跟随；而只在开始或结束时设一次
 * `heightOffset` 又会被顶栏自身的测量与回弹改写，所以这里逐帧重申目标值。
 * [block] 结束后再落一次最终状态，避免最后一帧被覆盖。
 */
private suspend fun holdTopBar(state: TopAppBarState?, collapse: Boolean, block: suspend () -> Unit) {
    if (state == null) {
        block()
        return
    }
    val target = { if (collapse) state.heightOffsetLimit else 0f }
    coroutineScope {
        val holder = launch {
            while (isActive) {
                state.heightOffset = target()
                withFrameNanos { }
            }
        }
        try {
            block()
        } finally {
            holder.cancel()
        }
    }
    // 落一次最终状态，避免最后一帧被顶栏自身改写。
    state.heightOffset = target()
    if (!collapse) state.contentOffset = 0f
}

/**
 * 程序化滚动后同步顶栏折叠状态。
 *
 * Miuix 顶栏的折叠量只由 ScrollBehavior 的 nestedScrollConnection 累加，
 * 而 animateScrollToItem 不会派发 nested scroll 事件，所以必须手动补齐，
 * 否则列表动了、顶栏高度不变，大标题会卡在原地。
 */
private suspend fun syncTopBar(state: TopAppBarState?, collapse: Boolean, animate: Boolean = true) {
    if (state == null) return
    val target = if (collapse) state.heightOffsetLimit else 0f
    if (state.heightOffset != target) {
        if (animate) {
            androidx.compose.animation.core.animate(
                initialValue = state.heightOffset,
                targetValue = target,
                animationSpec = tween(240),
            ) { value, _ ->
                state.heightOffset = value
            }
        } else {
            state.heightOffset = target
        }
    }
    if (!collapse) state.contentOffset = 0f
}

@Composable
private fun FileCardsBackdrop(
    listState: LazyListState,
    fileId: String,
    lastRowKey: String,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier.clipToBounds()) { constraints ->
        val info = listState.layoutInfo
        val viewportOffset = -info.viewportStartOffset
        val outset = 48.dp.roundToPx()
        val visibleItems = info.visibleItemsInfo.filter { item ->
            val key = item.key as? String ?: return@filter false
            key == "file:$fileId" || key.startsWith("line:$fileId:")
        }
        val top = visibleItems.firstOrNull { it.key == "file:$fileId" }?.let {
            it.offset + viewportOffset
        } ?: -outset
        val bottom = visibleItems.firstOrNull { it.key == lastRowKey }?.let {
            it.offset + it.size + viewportOffset
        } ?: (constraints.maxHeight + outset)
        val cardHeight = (bottom - top).coerceAtLeast(1)
        val card = subcompose(fileId) {
            Card(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                insideMargin = PaddingValues(0.dp),
            ) { }
        }.single().measure(Constraints.fixed(constraints.maxWidth, cardHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            card.placeRelative(0, top)
        }
    }
}

/** 右下角翻页按钮：上箭头回到顶部，下箭头滚到底部并翻页；背景与尺寸对齐顶栏的圆形动作按钮。 */
@Composable
private fun DetailJumpButtons(
    notAtTop: Boolean,
    notAtBottom: Boolean,
    onJumpTop: () -> Unit,
    onJumpBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = DictTheme.palette
    val indication = rememberMiuixIndication()
    val buttonSize = 40.dp
    val iconSize = 22.dp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = notAtTop,
            enter = fadeIn(tween(120)) + expandVertically(tween(170)),
            exit = fadeOut(tween(90)) + shrinkVertically(tween(130)),
        ) {
            JumpButton("回到顶部", -90f, buttonSize, iconSize, p.card, p.text2, indication, onJumpTop)
        }
        AnimatedVisibility(
            visible = notAtBottom,
            enter = fadeIn(tween(120)) + expandVertically(tween(170)),
            exit = fadeOut(tween(90)) + shrinkVertically(tween(130)),
        ) {
            JumpButton("翻到底部", 90f, buttonSize, iconSize, p.card, p.text2, indication, onJumpBottom)
        }
    }
}

@Composable
private fun JumpButton(
    description: String,
    rotation: Float,
    size: Dp,
    iconSize: Dp,
    background: Color,
    tint: Color,
    indication: Indication,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = null, indication = indication, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            MiuixIcons.Basic.ArrowRight,
            contentDescription = description,
            modifier = Modifier.size(iconSize).rotate(rotation),
            tint = tint,
        )
    }
}

@Composable
private fun DetailEntryRow(
    entry: AmbiguousEntry,
    showDivider: Boolean,
    replaceMode: Boolean,
    onLongPress: () -> Unit,
    onReplaceReading: suspend (line: Int, syllableIndex: Int, reading: String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val indication = rememberMiuixIndication()
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 替换模式下点读音即替换；普通模式保持复制。
    fun handleReading(reading: String, syllableIndex: Int) {
        if (!replaceMode) {
            copyDetailText(context, reading)
            return
        }
        scope.launch {
            val ok = onReplaceReading(entry.line, syllableIndex, reading)
            Toast.makeText(
                context,
                if (ok) "已替换为 " + reading else "替换失败：该行没有可写入的输出",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    Column(modifier.fillMaxWidth().combinedClickable(
        interactionSource = interaction,
        indication = indication,
        onClick = {},
        onLongClick = onLongPress,
    )) {
        Row(
            // 左右留白各 16dp。
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                // 行号徽章：圆角 8dp。
                modifier = Modifier.widthIn(min = 58.dp).wrapContentWidth(unbounded = true).heightIn(min = 32.dp)
                    .clickable(interactionSource = interaction, indication = indication) {
                        copyDetailText(context, entry.line.toString())
                    },
                cornerRadius = 8.dp,
                // 左右内边距 21dp。
                insideMargin = PaddingValues(horizontal = 21.dp),
                colors = CardDefaults.defaultColors(
                    color = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                ),
            ) {
                Box(
                    modifier = Modifier.heightIn(min = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "L${entry.line}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onTertiaryContainer,
                        maxLines = 1,
                    )
                }
            }
            // 行号与多音字之间间距 6dp。
            Spacer(Modifier.width(6.dp))
            Text(
                text = entry.message,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = colors.onBackground,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (entry.pinyin.isNotBlank()) {
            CopyableReading(
                value = entry.pinyin,
                label = "当前读音：" + entry.pinyin,
                modifier = Modifier.padding(start = 16.dp),
            )
        } else {
            Text(
                text = "当前行未记录有效读音",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 10.5.sp,
                color = colors.onBackgroundVariant,
            )
        }
        entry.readings.forEach { reading ->
            Text(
                text = reading.character + when {
                    reading.used.isBlank() -> " · 可选读音"
                    reading.toneUnspecified -> " · 可选读音（原行未标声调）"
                    else -> " · 其他读音"
                },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                fontSize = 10.5.sp,
                color = colors.onBackgroundVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                reading.others.forEach { other ->
                    CopyableReading(
                        value = other,
                        onClick = if (replaceMode) ({ handleReading(other, reading.index) }) else null,
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.7.dp, color = colors.dividerLine)
        }
    }
}

/** 替换模式提示条：说明当前点击行为，并提供退出入口。 */
@Composable
private fun ReplaceModeBar(onExit: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val indication = rememberMiuixIndication()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "替换拼音模式 · 点读音即替换原有拼音",
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = colors.primary,
        )
        Text(
            "退出",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(interactionSource = null, indication = indication, onClick = onExit)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CopyableReading(
    value: String,
    label: String = value,
    modifier: Modifier = Modifier,
    /** 为空时按默认行为复制；替换模式下由调用方传入替换动作。 */
    onClick: (() -> Unit)? = null,
    /** 文字内边距；卡片内传 0 水平内边距，使文字与左侧行号徽章对齐。 */
    contentPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val indication = rememberMiuixIndication()
    Text(
        label, fontSize = 11.5.sp, color = MiuixTheme.colorScheme.primary,
        modifier = modifier.clip(RoundedCornerShape(7.dp))
            .clickable(interactionSource = interaction, indication = indication) {
                onClick?.invoke() ?: copyDetailText(context, value)
            }
            .padding(contentPadding),
    )
}

private fun copyDetailText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("多音字详情", text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    val focusManager = LocalFocusManager.current
    val colors = MiuixTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp).focusRequester(focusRequester),
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = colors.surfaceContainerHigh,
            labelColor = colors.onBackgroundVariant,
            borderColor = colors.primary,
        ),
        cornerRadius = 26.dp,
        label = "搜行号 / 词语 / 读音",
        useLabelAsPlaceholder = true,
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
        singleLine = true,
    )
}

