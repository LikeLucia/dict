package kirakira.dokidoki.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kirakira.dokidoki.data.AmbiguousFileGroup
import kirakira.dokidoki.data.DictFile
import kirakira.dokidoki.data.FileSortMode
import kirakira.dokidoki.data.ProcessResult
import kirakira.dokidoki.data.ambiguityGroups
import kirakira.dokidoki.ui.components.DictCard
import kirakira.dokidoki.ui.components.DictTopBar
import kirakira.dokidoki.ui.components.DropdownAnchor
import kirakira.dokidoki.ui.components.captureDropdownAnchor
import kirakira.dokidoki.ui.components.FixedTopScrollPage
import kirakira.dokidoki.ui.components.IconBubble
import kirakira.dokidoki.ui.components.rememberMiuixIndication
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.glass.ConvertFile
import top.yukonga.miuix.kmp.icon.glass.DigitalStatistics
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.icon.glass.Report
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportScreen(
    result: ProcessResult,
    /** 当前词库列表：用于在会话记录缺少时间时回退到词库里的修改时间。 */
    files: List<DictFile>,
    /** 多音字卡片的排序方式，与词库页互不影响。 */
    sortMode: FileSortMode,
    sortDescending: Boolean,
    onOpenFile: (AmbiguousFileGroup) -> Unit,
    onOpenSortMenu: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    FixedTopScrollPage(modifier = modifier, topBar = { DictTopBar("处理报告") }) {
        SmallTitle("统计", modifier = Modifier.padding(start = 4.dp))
        StatsGrid(result)
        SmallTitle("报告详情", modifier = Modifier.padding(start = 4.dp))
        PolyphonicCard(
            result = result,
            files = files,
            sortMode = sortMode,
            sortDescending = sortDescending,
            onOpenFile = onOpenFile,
            onOpenSortMenu = onOpenSortMenu,
        )
    }
}

@Composable
private fun StatsGrid(result: ProcessResult) {
    val colors = MiuixTheme.colorScheme
    val stats = listOf(
        Triple(formatNumber(result.totalLines), "处理行数", MiuixIcons.Glass.DigitalStatistics),
        Triple(formatNumber(result.pinyinAdded), "补拼音", MiuixIcons.Glass.File),
        Triple(formatNumber(result.auxReplaced), "替换辅助码", MiuixIcons.Glass.ConvertFile),
        Triple(result.ambiguousCount.toString(), "多音字行数", MiuixIcons.Glass.Report),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (col in 0 until 2) {
                    val (value, label, icon) = stats[row * 2 + col]
                    DictCard(
                        modifier = Modifier.weight(1f).height(96.dp),
                        color = colors.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBubble(icon, size = 34.dp, iconSize = 18.dp, background = colors.tertiaryContainer, tint = colors.onTertiaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(value, fontSize = 17.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                                Spacer(Modifier.height(1.dp))
                                Text(label, fontSize = 10.5.sp, lineHeight = 12.sp, color = colors.onBackgroundVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 多音字分组所属文件的修改时间：yy-M-dd hh:mm:ss（月不补零、日补零）；0 表示未知，返回 null 由组件省略摘要。 */
private fun formatGroupModified(millis: Long): String? {
    if (millis <= 0L) return null
    return SimpleDateFormat("yy-M-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}

@Composable
private fun PolyphonicCard(
    result: ProcessResult,
    files: List<DictFile>,
    sortMode: FileSortMode,
    sortDescending: Boolean,
    onOpenFile: (AmbiguousFileGroup) -> Unit,
    onOpenSortMenu: (Rect) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var searchVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val sortAnchor = remember { DropdownAnchor() }
    val entryCount = result.ambiguousCount
    val groups = remember(result) { result.ambiguityGroups() }
    val libraryTimes = remember(files) { files.mapNotNull { f -> f.id?.let { it to f.modifiedAt } }.toMap() }
    // 修改时间优先取会话记录，缺失时回退到词库列表。
    fun modifiedOf(group: AmbiguousFileGroup): Long =
        group.modifiedAt.takeIf { it > 0L } ?: libraryTimes[group.fileId] ?: 0L
    val q = query.trim()
    // 先按搜索词过滤，再按排序方式与方向排列。
    val filtered = if (q.isEmpty()) groups else groups.filter { it.fileName.contains(q, ignoreCase = true) }
    val shown = remember(filtered, sortMode, sortDescending, libraryTimes) {
        val sorted = when (sortMode) {
            FileSortMode.NAME -> filtered.sortedBy { it.fileName.lowercase() }
            FileSortMode.SIZE -> filtered.sortedBy { it.entryCount }
            FileSortMode.MODIFIED -> filtered.sortedBy { modifiedOf(it) }
        }
        if (sortDescending) sorted.asReversed() else sorted
    }
    val colors = MiuixTheme.colorScheme
    val indication = rememberMiuixIndication()
    LaunchedEffect(groups.isEmpty()) {
        if (groups.isEmpty()) {
            searchVisible = false
            query = ""
            focusManager.clearFocus()
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        if (!searchVisible || groups.isEmpty()) {
            BasicComponent(
                modifier = Modifier.height(62.dp),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
                title = if (groups.isEmpty()) "多音字详情" else "多音字详情 · ${entryCount} 行",
                onClick = null,
                endActions = {
                    if (groups.isNotEmpty()) {
                        // 放大镜在左，排序按钮在右。
                        Icon(
                            MiuixIcons.Basic.Search,
                            contentDescription = "搜索文件",
                            tint = colors.onBackgroundVariant,
                            modifier = Modifier.clip(CircleShape)
                                .clickable(interactionSource = null, indication = indication) { searchVisible = true }
                                .size(32.dp).padding(8.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Row(
                            modifier = Modifier
                                .captureDropdownAnchor(sortAnchor)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(interactionSource = null, indication = indication) {
                                    onOpenSortMenu(sortAnchor.bounds())
                                }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                sortMode.label + if (sortDescending) " ↓" else " ↑",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                MiuixIcons.Basic.ArrowUpDown,
                                contentDescription = "排序",
                                modifier = Modifier.size(12.dp),
                                tint = colors.primary,
                            )
                        }
                    }
                },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(62.dp).padding(start = 18.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).height(40.dp),
                    insideMargin = DpSize(16.dp, 10.dp),
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = colors.surfaceContainerHigh,
                        labelColor = colors.onBackgroundVariant,
                        borderColor = colors.primary,
                    ),
                    label = "搜文件名",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    cornerRadius = 12.dp,
                    textStyle = MiuixTheme.textStyles.main.copy(fontSize = 13.5.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "取消",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable(interactionSource = null, indication = indication) {
                            searchVisible = false
                            query = ""
                            focusManager.clearFocus()
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 2.dp))
        if (groups.isEmpty()) {
            BasicComponent(title = "本次处理未发现多音字行", titleColor = BasicComponentDefaults.summaryColor())
            return@Card
        }
        shown.forEachIndexed { index, group ->
            BasicComponent(
                title = group.fileName,
                // 文件下方显示源文件的修改时间：yy-MM-d hh:mm:ss。
                summary = formatGroupModified(modifiedOf(group)),
                onClick = { onOpenFile(group) },
                onClickLabel = "查看多音字",
                startAction = {
                    Icon(MiuixIcons.Glass.File, null, Modifier.size(18.dp), tint = colors.onTertiaryContainer)
                },
                endActions = {
                    Text("${group.entryCount} 行", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Icon(MiuixIcons.Basic.ArrowRight, null, Modifier.padding(start = 8.dp).size(13.dp), tint = colors.onSurfaceContainerHigh)
                },
            )
            if (index != shown.lastIndex) HorizontalDivider(Modifier.padding(start = 46.dp))
        }
        when {
            q.isNotEmpty() && shown.isEmpty() -> Text("没有匹配的文件", fontSize = 10.sp, color = colors.onBackgroundVariant, modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 12.dp))
            q.isNotEmpty() -> Text("找到 ${shown.size} 个文件 · ${shown.sumOf { it.entryCount }} 行", fontSize = 10.sp, color = colors.onBackgroundVariant, modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 12.dp))
            groups.size > 1 -> Text("共 ${entryCount} 行 · 涉及 ${groups.size} 个文件", fontSize = 10.sp, color = colors.onBackgroundVariant, modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 12.dp))
        }
    }
}



