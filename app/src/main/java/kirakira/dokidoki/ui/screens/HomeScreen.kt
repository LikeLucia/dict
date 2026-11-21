package kirakira.dokidoki.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kirakira.dokidoki.data.DictFile
import kirakira.dokidoki.data.DictUiState
import kirakira.dokidoki.data.FileStatus
import kirakira.dokidoki.data.ProcessMode
import kirakira.dokidoki.data.ProcessResult
import kirakira.dokidoki.data.QueueFile
import kirakira.dokidoki.ui.components.DictCard
import kirakira.dokidoki.ui.components.FixedTopScrollPage
import kirakira.dokidoki.ui.components.SectionHeader
import kirakira.dokidoki.ui.components.SegmentSlider
import kirakira.dokidoki.ui.components.StatusBadge
import kirakira.dokidoki.ui.components.dashedBorder
import kirakira.dokidoki.ui.components.rememberMiuixIndication
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.StarOutline
import top.yukonga.miuix.kmp.icon.glass.File

@Composable
fun HomeScreen(
    state: DictUiState,
    onSelectMode: (ProcessMode) -> Unit,
    onStart: () -> Unit,
    onImport: () -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllQueue by rememberSaveable { mutableStateOf(false) }
    FixedTopScrollPage(modifier = modifier, bottomPadding = 190.dp, topBar = { HomeHeader() }) {
        Spacer(Modifier.height(12.dp))
        HomeStats(state)
        Spacer(Modifier.height(14.dp))
        ImportCard(onImport)
        Spacer(Modifier.height(14.dp))
        HomeStatusCard(state)
        val showQueueBlock = state.showQueue && state.queueFiles.isNotEmpty()
        AnimatedVisibility(
            visible = showQueueBlock,
            enter = fadeIn(tween(180)) + expandVertically(
                animationSpec = tween(220),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(tween(120)) + shrinkVertically(
                animationSpec = tween(180),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Column {
                val queueAction = when {
                    state.queueFiles.size <= 3 -> null
                    showAllQueue -> "收起"
                    else -> "显示全部"
                }
                SectionHeader(
                    "排队中",
                    action = queueAction,
                    onAction = if (queueAction == null) null else ({ showAllQueue = !showAllQueue }),
                )
                QueueCard(
                    queue = state.queueFiles,
                    showAll = showAllQueue,
                )
            }
        }
        SectionHeader("处理方式")
        ModeSegments(state.processMode, onSelectMode)
        Spacer(Modifier.height(16.dp))
        AuxQuickRow(state.auxType.label)
        SectionHeader("最近使用")
        val recent = remember(state.files) {
            state.files.asSequence().filter { it.status != FileStatus.PENDING }.take(3).toList()
        }
        RecentCard(recent, onOpenFile)
    }
}

@Composable
private fun HomeHeader() {
    val p = DictTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("kirakira", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = p.text, lineHeight = 34.sp)
        Spacer(Modifier.width(14.dp))
        Text("词库离线处理", fontSize = 13.sp, color = p.text2, modifier = Modifier.padding(top = 7.dp))
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(p.tint)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("本机完成 · 不上传", fontSize = 10.sp, color = p.onTint)
        }
    }
}

@Composable
private fun HomeStats(state: DictUiState) {
    val p = DictTheme.palette
    val processedCount = remember(state.files) { state.files.count { it.hasOutput } }
    val stats = listOf(
        "${state.files.size} 个" to "词库总数",
        "$processedCount 个" to "已处理",
        formatNumber(state.processedPinyin) to "累计补拼音",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.forEach { (value, label) ->
            DictCard(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                color = p.card2,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 10.sp, color = p.text2)
                }
            }
        }
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit) {
    val p = DictTheme.palette
    val indication = rememberMiuixIndication()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .dashedBorder(p.dash, 1.3.dp, 20.dp)
            .clickable(
                interactionSource = interaction,
                indication = indication,
            ) { onImport() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MiuixIcons.Glass.Add, contentDescription = null, modifier = Modifier.size(17.dp), tint = p.onTint)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("导入词库", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = p.text)
                Spacer(Modifier.height(4.dp))
                Text("Rime YAML · TXT · TSV · ZIP", fontSize = 11.sp, color = p.text2)
            }
        }
    }
}

@Composable
private fun HomeStatusCard(state: DictUiState) {
    val p = DictTheme.palette
    val result = state.result
    val active = state.processing
    val progress = if (active) state.progress.coerceIn(0f, 1f) else 0f
    val percent = (progress * 100).toInt()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "processing-progress",
    )
    DictCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(96.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(p.tint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (active) MiuixIcons.Glass.File else MiuixIcons.Glass.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = p.onTint,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(if (active) "正在处理" else "待导入词库", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
                Spacer(Modifier.weight(1f))
                Text("${formatNumber(if (active) result.totalLines else 0)} 行", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.accent)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(p.card2),
            ) {
                if (animatedProgress > 0.001f) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = animatedProgress,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = p.accent,
                            backgroundColor = p.card2,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (active) {
                        if (state.estimatedSeconds > 0) "预计用时 ${formatEstimatedDuration(state.estimatedSeconds)}" else "预计用时计算中…"
                    } else "导入词库后自动排队处理",
                    fontSize = 10.5.sp,
                    color = p.text2,
                )
                Spacer(Modifier.weight(1f))
                Text("$percent%", fontSize = 10.5.sp, color = p.accent)
            }
        }
    }
}

@Composable
private fun ModeSegments(selected: ProcessMode, onSelect: (ProcessMode) -> Unit) {
    val modes = ProcessMode.entries
    SegmentSlider(
        items = modes.map { it.title },
        selectedIndex = modes.indexOf(selected).coerceAtLeast(0),
        onSelect = { index -> onSelect(modes[index]) },
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

@Composable
private fun AuxQuickRow(value: String) {
    val p = DictTheme.palette
    DictCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(54.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 13.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(p.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MiuixIcons.Glass.StarOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = p.onTint)
            }
            Spacer(Modifier.width(11.dp))
            Text("辅助码", fontSize = 14.5.sp, color = p.text)
            Spacer(Modifier.weight(1f))
            Text(value, fontSize = 13.sp, color = p.accent)
        }
    }
}

@Composable
private fun QueueCard(queue: List<QueueFile>, showAll: Boolean) {
    val visible = if (showAll) queue else queue.take(3)
    DictCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height((maxOf(visible.size, 1) * 52).dp)
                ) {
        Column(Modifier.fillMaxSize()) {
            visible.forEachIndexed { index, file ->
                key(file.id) {
                    QueueRow(file.name, index + 1, file.processing, index == visible.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun QueueRow(name: String, position: Int, processing: Boolean, last: Boolean) {
    val p = DictTheme.palette
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.tint),
                contentAlignment = Alignment.Center,
            ) {
                Text(position.toString(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = p.onTint)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = p.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .height(21.dp)
                    .clip(RoundedCornerShape(10.5.dp))
                    .background(p.card2)
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (processing) "处理中" else "排队中", fontSize = 10.5.sp, color = p.text2)
            }
        }
        if (!last) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 48.dp, end = 18.dp)
                    .fillMaxWidth()
                    .height(0.7.dp)
                    .background(p.divider),
            )
        }
    }
}

@Composable
private fun RecentCard(files: List<DictFile>, onOpenFile: (String) -> Unit) {
    val p = DictTheme.palette
    DictCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height((maxOf(files.size, 1) * 54).dp)
                ) {
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无处理记录", fontSize = 12.5.sp, color = p.text3)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                files.forEachIndexed { index, file ->
                    key(file.id ?: index) {
                        RecentRow(
                            name = file.name,
                            meta = file.meta,
                            status = file.status,
                            last = index == files.lastIndex,
                            onClick = { file.id?.let(onOpenFile) },
                        )
                    }
                }
            }
        }
    }
}

private fun recentBadge(status: FileStatus): Pair<String, Boolean>? = when (status) {
    FileStatus.EXPORTED -> "已导出" to true
    FileStatus.CANCELLED -> "已取消" to false
    FileStatus.AUX_DONE, FileStatus.AUX_STRIPPED, FileStatus.PINYIN_DONE -> "处理完成" to true
    FileStatus.FAILED -> "处理失败" to false
    FileStatus.PENDING -> null
}

@Composable
private fun RecentRow(name: String, meta: String, status: FileStatus, last: Boolean, onClick: () -> Unit) {
    val p = DictTheme.palette
    val indication = rememberMiuixIndication()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(
                interactionSource = interaction,
                indication = indication,
            ) { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Column {
                Text(name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = p.text)
                Spacer(Modifier.height(4.dp))
                Text(meta, fontSize = 11.sp, color = p.text2)
            }
            Spacer(Modifier.weight(1f))
            recentBadge(status)?.let { (label, highlighted) ->
                StatusBadge(label, highlighted)
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(width = 9.dp, height = 15.dp),
                tint = p.text3,
            )
        }
        if (!last) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 54.dp, end = 41.dp)
                    .fillMaxWidth()
                    .height(0.7.dp)
                    .background(p.divider),
            )
        }
    }
}

internal fun formatEstimatedDuration(seconds: Int): String = when {
    seconds <= 0 -> "0s"
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m${seconds % 60}s"
}

fun formatNumber(value: Int): String {
    val s = value.toString()
    val sb = StringBuilder()
    s.forEachIndexed { index, c ->
        if (index > 0 && (s.length - index) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString()
}