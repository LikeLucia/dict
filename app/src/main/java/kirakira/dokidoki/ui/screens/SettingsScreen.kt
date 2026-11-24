package kirakira.dokidoki.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kirakira.dokidoki.data.AuxType
import kirakira.dokidoki.data.DictUiState
import kirakira.dokidoki.data.InputEncoding
import kirakira.dokidoki.data.OutputDelimiter
import kirakira.dokidoki.data.ProcessMode
import kirakira.dokidoki.data.ThemeMode
import kirakira.dokidoki.data.ToneMode
import kirakira.dokidoki.ui.components.DialogAction
import kirakira.dokidoki.ui.components.DictCard
import kirakira.dokidoki.ui.components.FixedTopScrollPage
import kirakira.dokidoki.ui.components.DictTopBar
import kirakira.dokidoki.ui.components.DropdownAnchor
import kirakira.dokidoki.ui.components.captureDropdownAnchor
import kirakira.dokidoki.ui.components.rememberMiuixIndication
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown

enum class SettingsMenu { THEME, AUX_TYPE, TONE, OUTPUT_DELIMITER, INPUT_ENCODING, EXPORT_CONFLICT }

@Composable
fun SettingsScreen(
    state: DictUiState,
    openedMenu: SettingsMenu?,
    onOpenMenu: (SettingsMenu, Rect) -> Unit,
    onSelectMode: (ProcessMode) -> Unit,
    onSelectAux: (AuxType) -> Unit,
    onReplaceAux: (Boolean) -> Unit,
    onReplacePinyin: (Boolean) -> Unit,
    onSetColumns: (Int, Int) -> Unit,
    onSetWeight: (Int) -> Unit,
    onPickExportDirectory: () -> Unit,
    onAbout: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = DictTheme.palette
    val auxAnchor = remember { DropdownAnchor() }
    val toneAnchor = remember { DropdownAnchor() }
    val delimiterAnchor = remember { DropdownAnchor() }
    val encodingAnchor = remember { DropdownAnchor() }
    val exportConflictAnchor = remember { DropdownAnchor() }
    var columnsDialog by remember { mutableStateOf(false) }
    var weightDialog by remember { mutableStateOf(false) }

    FixedTopScrollPage(modifier = modifier, topBar = { DictTopBar("处理设置") }) {
        Spacer(Modifier.height(16.dp))

        // 「主题设置」入口：右侧不显示当前值，点击进入二级页。
        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
        ) {
            SettingsRow(
                title = "主题设置",
                // 右侧不显示当前主题名；value 传空串只为保留右向导航箭头。
                value = "",
                upDownArrow = false,
                last = true,
                onClick = onOpenThemeSettings,
            )
        }

        Spacer(Modifier.height(14.dp))
        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(224.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                ProcessMode.entries.forEachIndexed { index, mode ->
                    RadioRow(
                        title = mode.title,
                        selected = state.processMode == mode,
                        last = index == ProcessMode.entries.lastIndex,
                        onClick = { onSelectMode(mode) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(260.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                SettingsRow(
                    title = "辅助码类型",
                    value = state.auxType.label,
                    valueColor = p.accent,
                    holdDownState = openedMenu == SettingsMenu.AUX_TYPE,
                    modifier = Modifier.captureDropdownAnchor(auxAnchor),
                    onClick = {
                        onOpenMenu(SettingsMenu.AUX_TYPE, auxAnchor.bounds())
                    },
                )
                SettingsRow(
                    title = "辅助码分隔符",
                    value = ";",
                )
                SettingsRow(
                    title = "替换已有辅助码",
                    switchOn = state.replaceAux,
                    onSwitch = onReplaceAux,
                )
                SettingsRow(
                    title = "替换已有拼音",
                    switchOn = state.replacePinyin,
                    onSwitch = onReplacePinyin,
                )
                SettingsRow(
                    title = "补充权重",
                    value = state.weight.toString(),
                    upDownArrow = false,
                    last = true,
                    onClick = { weightDialog = true },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(208.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                SettingsRow(
                    title = "拼音声调",
                    value = state.tone.label,
                    valueColor = p.accent,
                    holdDownState = openedMenu == SettingsMenu.TONE,
                    modifier = Modifier.captureDropdownAnchor(toneAnchor),
                    onClick = {
                        onOpenMenu(SettingsMenu.TONE, toneAnchor.bounds())
                    },
                )
                SettingsRow(
                    title = "输出分隔符",
                    value = state.outputDelimiter.label,
                    valueColor = p.accent,
                    holdDownState = openedMenu == SettingsMenu.OUTPUT_DELIMITER,
                    modifier = Modifier.captureDropdownAnchor(delimiterAnchor),
                    onClick = {
                        onOpenMenu(SettingsMenu.OUTPUT_DELIMITER, delimiterAnchor.bounds())
                    },
                )
                SettingsRow(
                    title = "输入编码",
                    value = state.inputEncoding.label,
                    valueColor = p.accent,
                    holdDownState = openedMenu == SettingsMenu.INPUT_ENCODING,
                    modifier = Modifier.captureDropdownAnchor(encodingAnchor),
                    onClick = {
                        onOpenMenu(SettingsMenu.INPUT_ENCODING, encodingAnchor.bounds())
                    },
                )
                SettingsRow(
                    title = "词语列 / 拼音列",
                    value = state.wordColumn.toString() + " / " + state.pinyinColumn,
                    upDownArrow = false,
                    last = true,
                    onClick = { columnsDialog = true },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(104.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                SettingsRow(
                    title = "导出路径",
                    value = state.exportDirectoryName,
                    valueColor = p.accent,
                    upDownArrow = false,
                    onClick = onPickExportDirectory,
                )
                SettingsRow(
                    title = "同名文件",
                    value = state.exportConflictMode.label,
                    valueColor = p.accent,
                    holdDownState = openedMenu == SettingsMenu.EXPORT_CONFLICT,
                    modifier = Modifier.captureDropdownAnchor(exportConflictAnchor),
                    last = true,
                    onClick = {
                        onOpenMenu(SettingsMenu.EXPORT_CONFLICT, exportConflictAnchor.bounds())
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
        ) {
            SettingsRow(
                title = "关于",
                value = "版本信息",
                upDownArrow = false,
                last = true,
                miuixIndication = true,
                onClick = { onAbout() },
            )
        }
    }

    if (weightDialog) {
        WeightDialog(
            weight = state.weight,
            onDismiss = { weightDialog = false },
            onConfirm = {
                weightDialog = false
                onSetWeight(it)
            },
        )
    }

    if (columnsDialog) {
        ColumnsDialog(
            word = state.wordColumn,
            pinyin = state.pinyinColumn,
            onDismiss = { columnsDialog = false },
            onConfirm = { w, py ->
                columnsDialog = false
                onSetColumns(w, py)
            },
        )
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    value: String? = null,
    valueColor: Color = DictTheme.palette.text2,
    switchOn: Boolean? = null,
    holdDownState: Boolean = false,
    upDownArrow: Boolean = true,
    last: Boolean = false,
    miuixIndication: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onSwitch: ((Boolean) -> Unit)? = null,
) {
    val p = DictTheme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val mask by animateColorAsState(
        targetValue = if (holdDownState || pressed) Color.Black.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "settings-row-mask",
    )
    val indication = if (miuixIndication) rememberMiuixIndication() else null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                ) { onClick() } else Modifier,
            )
            .background(mask),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 16.sp, color = p.text)
            Spacer(Modifier.weight(1f))
            if (switchOn != null) {
                DictSwitch(switchOn, onSwitch ?: {})
            } else if (value != null) {
                Text(
                    value,
                    fontSize = 15.sp,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 190.dp),
                )
                if (onClick != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (upDownArrow) MiuixIcons.Basic.ArrowUpDown else MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        modifier = if (upDownArrow) Modifier.size(13.dp) else Modifier.size(width = 9.dp, height = 13.dp),
                        tint = p.text3,
                    )
                }
            }
        }
        if (!last) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 24.dp)
                    .fillMaxWidth()
                    .height(0.7.dp)
                    .background(p.divider),
            )
        }
    }
}

@Composable
internal fun RadioRow(
    title: String,
    selected: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val p = DictTheme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val indication = rememberMiuixIndication()
    val selectedColor by animateColorAsState(
        targetValue = if (selected) p.accent else p.text3,
        animationSpec = tween(durationMillis = 160),
        label = "radio-border",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "radio-dot",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
            ) { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = p.text,
                )
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(1.6.dp, selectedColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                            alpha = dotScale
                        }
                        .clip(CircleShape)
                        .background(p.accent),
                )
            }
        }
        if (!last) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 34.dp)
                    .fillMaxWidth()
                    .height(0.7.dp)
                    .background(p.divider),
            )
        }
    }
}

@Composable
internal fun DictSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = DictTheme.palette
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.switchColors(
            checkedTrackColor = p.switchOn,
            uncheckedTrackColor = p.switchOff,
            checkedThumbColor = p.onAccent,
            uncheckedThumbColor = p.onAccent,
        ),
    )
}

@Composable
private fun ColumnsDialog(word: Int, pinyin: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val p = DictTheme.palette
    var wordText by remember(word) { mutableStateOf(word.toString()) }
    var pinyinText by remember(pinyin) { mutableStateOf(pinyin.toString()) }
    val w = wordText.toIntOrNull()
    val py = pinyinText.toIntOrNull()
    val valid = w != null && py != null && w in 1..128 && py in 1..128 && w != py
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(p.popup)
                .padding(20.dp),
        ) {
            Text("词语列 / 拼音列", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.text)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("词语列", fontSize = 12.sp, color = p.text2)
                    Spacer(Modifier.height(6.dp))
                    NumberField(wordText) { wordText = it }
                }
                Column(Modifier.weight(1f)) {
                    Text("拼音列", fontSize = 12.sp, color = p.text2)
                    Spacer(Modifier.height(6.dp))
                    NumberField(pinyinText) { pinyinText = it }
                }
            }
            if (!valid) {
                Text("请输入 1–128 范围内的两个不同列号", fontSize = 11.sp, color = p.accent)
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                DialogAction("取消", p.text2, onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                DialogAction(
                    label = "确定",
                    color = if (valid) p.accent else p.text3,
                    enabled = valid,
                    bold = true,
                ) { onConfirm(w!!, py!!) }
            }
        }
    }
}

@Composable
private fun WeightDialog(weight: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val p = DictTheme.palette
    var text by remember(weight) { mutableStateOf(weight.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in 1..999999
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(p.popup)
                .padding(20.dp),
        ) {
            Text("补充权重", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.text)
            Spacer(Modifier.height(16.dp))
            NumberField(text, maxDigits = 6) { text = it }
            if (!valid) {
                Spacer(Modifier.height(8.dp))
                Text("请输入 1–999999 的整数", fontSize = 11.sp, color = p.accent)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                DialogAction("取消", p.text2, onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                DialogAction(
                    label = "确定",
                    color = if (valid) p.accent else p.text3,
                    enabled = valid,
                    bold = true,
                ) { onConfirm(value!!) }
            }
        }
    }
}

@Composable
private fun NumberField(value: String, maxDigits: Int = 3, onChange: (String) -> Unit) {
    val p = DictTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.card2)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { text -> if (text.all { it.isDigit() } && text.length <= maxDigits) onChange(text) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = p.text),
            cursorBrush = SolidColor(p.accent),
        )
    }
}
