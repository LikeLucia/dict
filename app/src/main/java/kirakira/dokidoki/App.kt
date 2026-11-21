package kirakira.dokidoki

import android.net.Uri
import android.os.Build
import android.view.RoundedCorner
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.WindowCompat
import kirakira.dokidoki.data.AuxType
import kirakira.dokidoki.data.ambiguityGroups
import kirakira.dokidoki.data.DictUiState
import kirakira.dokidoki.data.ExportConflictMode
import kirakira.dokidoki.data.FileSortMode
import kirakira.dokidoki.data.InputEncoding
import kirakira.dokidoki.data.OutputDelimiter
import kirakira.dokidoki.data.ToneMode
import kirakira.dokidoki.data.MainViewModel
import kirakira.dokidoki.data.ThemeMode
import kirakira.dokidoki.ui.components.DictBottomBar
import kirakira.dokidoki.ui.components.DialogAction
import kirakira.dokidoki.ui.components.DropdownOverlay
import kirakira.dokidoki.ui.components.DropdownSpec
import kirakira.dokidoki.ui.components.PrimaryButton
import kirakira.dokidoki.ui.components.rememberMiuixIndication
import kirakira.dokidoki.ui.screens.AboutScreen
import kirakira.dokidoki.ui.screens.AmbiguityDetailScreen
import kirakira.dokidoki.ui.screens.HomeScreen
import kirakira.dokidoki.ui.screens.LibrariesScreen
import kirakira.dokidoki.ui.screens.LibScreen
import kirakira.dokidoki.ui.screens.ReportScreen
import kirakira.dokidoki.ui.screens.SettingsMenu
import kirakira.dokidoki.ui.screens.SettingsScreen
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

private const val MIUIX_DETAIL_DURATION = 500

/** Miuix nav 默认程序化过渡曲线（response 0.8 / damping 0.95），固定系数预计算。 */
private class MiuixPageEasing(
    response: Double = 0.8,
    damping: Double = 0.95,
) : Easing {
    private val r: Double
    private val w: Double
    private val c2: Double

    init {
        val omega = 2.0 * PI / response
        val k = omega * omega
        val c = damping * 4.0 * PI / response
        w = sqrt(4.0 * k - c * c) / 2.0
        r = -c / 2.0
        c2 = r / w
    }

    override fun transform(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        val t = fraction.toDouble()
        val decay = exp(r * t)
        return (decay * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat()
    }
}

private val MiuixPageMotionEasing = MiuixPageEasing()

// A nested host clips only after its ancestor has finished moving the whole page.
private val LocalAncestorDetailTransition = staticCompositionLocalOf<() -> Boolean> { { false } }

@Composable
fun DictApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    DictTheme(darkTheme = dark) {
        val p = DictTheme.palette
        val view = LocalView.current
        val context = LocalContext.current
        val packageInfo = remember(context) {
            lazy(LazyThreadSafetyMode.NONE) {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            }
        }
        LaunchedEffect(dark) {
            val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
        var dropdown by remember { mutableStateOf<DropdownSpec?>(null) }
        val currentMenu by remember {
            derivedStateOf {
                dropdown?.key?.let { key -> SettingsMenu.entries.firstOrNull { it.name == key } }
            }
        }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var detailFileId by remember { mutableStateOf<String?>(null) }
        var aboutOpen by remember { mutableStateOf(false) }
        var sourceLicensesOpen by remember { mutableStateOf(false) }
        val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.importFiles(context.contentResolver, uris)
        }
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) viewModel.exportTo(context.contentResolver, uri)
        }
        val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) viewModel.setExportDirectory(uri)
        }
        val doBatchExport: () -> Unit = {
            val payload = viewModel.requestExportFiles(state.selectedFileIds)
            if (payload == null) {
                Toast.makeText(context, "所选词库没有可导出的处理结果", Toast.LENGTH_SHORT).show()
            } else {
                if (!viewModel.exportPayloadToConfiguredPath(payload)) {
                    exportLauncher.launch(payload.name)
                }
            }
        }
        LaunchedEffect(state.autoExportPending) {
            if (state.autoExportPending) {
                val payload = viewModel.takeAutoExport()
                if (payload != null) {
                    if (!viewModel.exportPayloadToConfiguredPath(payload)) {
                        exportLauncher.launch(payload.name)
                    }
                }
            }
        }
        LaunchedEffect(state.message) {
            state.message?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
        LaunchedEffect(state.selectedTab) {
            if (state.selectedTab != 2) detailFileId = null
            if (state.selectedTab != 3) {
                aboutOpen = false
                sourceLicensesOpen = false
            }
        }
        LaunchedEffect(aboutOpen) {
            if (aboutOpen) sourceLicensesOpen = false
        }
        val detailGroup = detailFileId?.let { id ->
            state.result.ambiguityGroups().firstOrNull { it.fileId == id }
        }
        val detailVisible = detailGroup != null && state.selectedTab == 2

        BackHandler(enabled = dropdown != null) {
            dropdown = null
        }
        BackHandler(enabled = dropdown == null && state.selectionMode) { viewModel.clearSelection() }
        BackHandler(enabled = dropdown == null && !state.selectionMode && state.selectedTab != 0) { viewModel.selectTab(0) }
        BackHandler(enabled = detailVisible) { detailFileId = null }
        BackHandler(enabled = sourceLicensesOpen) { sourceLicensesOpen = false }
        BackHandler(enabled = !sourceLicensesOpen && aboutOpen) { aboutOpen = false }
        val aboutVisible = aboutOpen && state.selectedTab == 3
        Box(Modifier.fillMaxSize().background(p.bg)) {
            MiuixDetailHost(
                visible = aboutVisible,
                dismissImmediately = state.selectedTab != 3,
                base = {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxSize(),
                        ) {
                            MiuixDetailHost(
                                visible = detailVisible,
                                dismissImmediately = state.selectedTab != 2,
                                base = {
                                    Box(Modifier.fillMaxSize()) {
                                        MiuixTabHost(selectedTab = state.selectedTab) { tab, isActive ->
                                            FullScreenContentPage {
                                                when (tab) {
                                                    0 -> HomeScreen(
                                                        state = state,
                                                        onSelectMode = viewModel::setProcessMode,
                                                        onStart = { viewModel.startProcessing() },
                                                        onImport = { openLauncher.launch(arrayOf("*/*")) },
                                                        onOpenFile = viewModel::openLibraryFile,
                                                    )

                                                    1 -> LibScreen(
                                                        state = state,
                                                        isActive = isActive,
                                                        onEnterSelection = viewModel::enterSelection,
                                                        onToggleSelection = viewModel::toggleSelection,
                                                        onSelectAll = viewModel::selectAllFiles,
                                                        onFocusHandled = viewModel::clearLibraryFocus,
                                                        onSortDescending = viewModel::setSortDescending,
                                                        onOpenSortMenu = { anchor ->
                                                            dropdown = DropdownSpec(
                                                                anchor = anchor,
                                                                items = FileSortMode.entries.map { it.label },
                                                                selectedIndex = state.sortMode.ordinal,
                                                                key = "SORT",
                                                            )
                                                        },
                                                    )

                                                    2 -> ReportScreen(
                                                        result = state.result,
                                                        onOpenFile = { group -> detailFileId = group.fileId },
                                                    )

                                                    else -> SettingsScreen(
                                                        state = state,
                                                        openedMenu = currentMenu,
                                                        onOpenMenu = { menu, anchor ->
                                                            val (items, selected) = when (menu) {
                                                                SettingsMenu.THEME -> ThemeMode.entries.map { it.label } to state.themeMode.ordinal
                                                                SettingsMenu.AUX_TYPE -> AuxType.entries.map { it.label } to state.auxType.ordinal
                                                                SettingsMenu.TONE -> ToneMode.entries.map { it.label } to state.tone.ordinal
                                                                SettingsMenu.OUTPUT_DELIMITER -> OutputDelimiter.entries.map { it.label } to state.outputDelimiter.ordinal
                                                                SettingsMenu.INPUT_ENCODING -> InputEncoding.entries.map { it.label } to state.inputEncoding.ordinal
                                                                SettingsMenu.EXPORT_CONFLICT -> ExportConflictMode.entries.map { it.label } to state.exportConflictMode.ordinal
                                                            }
                                                            dropdown = DropdownSpec(
                                                                anchor = anchor,
                                                                items = items,
                                                                selectedIndex = selected,
                                                                key = menu.name,
                                                                expandAbove = menu != SettingsMenu.AUX_TYPE && menu != SettingsMenu.TONE,
                                                                alignToAnchorRight = true,
                                                                endInset = 20.dp,
                                                                verticalInset = 6.dp,
                                                            )
                                                        },
                                                        onSelectMode = viewModel::setProcessMode,
                                                        onSelectAux = viewModel::setAuxType,
                                                        onReplaceAux = viewModel::setReplaceAux,
                                                        onReplacePinyin = viewModel::setReplacePinyin,
                                                        onSetColumns = viewModel::setColumns,
                                                        onSetWeight = viewModel::setWeight,
                                                        onPickExportDirectory = {
                                                            val initial = state.exportDirectoryUri?.let(Uri::parse)
                                                                ?: Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
                                                            runCatching { folderLauncher.launch(initial) }
                                                                .onFailure { folderLauncher.launch(null) }
                                                        },
                                                        onAbout = { aboutOpen = true },
                                                    )
                                                }
                                            }
                                        }

                                        BottomArea(
                                            state = state,
                                            onSelectTab = viewModel::selectTab,
                                            onStart = viewModel::startProcessing,
                                            onCancel = viewModel::cancelProcessing,
                                            onBatchExport = doBatchExport,
                                            onDelete = { if (state.selectedFileIds.isNotEmpty()) showDeleteConfirm = true },
                                            onClearSelection = viewModel::clearSelection,
                                        )
                                    }
                                },
                                detail = {
                                    detailGroup?.let { group ->
                                        FullScreenContentPage {
                                            AmbiguityDetailScreen(
                                                group = group,
                                                isActive = detailVisible,
                                                onBack = { detailFileId = null },
                                                onLoadPage = viewModel::readAmbiguousPage,
                                            )
                                        }
                                    }
                                },
                            )
                        }

                        if (showDeleteConfirm) {
                            DeleteConfirmDialog(
                                count = state.selectedFileIds.size,
                                onDismiss = { showDeleteConfirm = false },
                                onConfirm = {
                                    showDeleteConfirm = false
                                    viewModel.deleteSelected()
                                },
                            )
                        }

                        dropdown?.let { spec ->
                            DropdownOverlay(
                                spec = spec,
                                onDismiss = {
                                    dropdown = null
                                },
                                onSelect = { index ->
                                    dropdown = null
                                    when (spec.key) {
                                        "THEME" -> viewModel.setThemeMode(ThemeMode.entries[index])
                                        "AUX_TYPE" -> viewModel.setAuxType(AuxType.entries[index])
                                        "SORT" -> viewModel.setSortMode(FileSortMode.entries[index])
                                        "TONE" -> viewModel.setTone(ToneMode.entries[index])
                                        "OUTPUT_DELIMITER" -> viewModel.setOutputDelimiter(OutputDelimiter.entries[index])
                                        "INPUT_ENCODING" -> viewModel.setInputEncoding(InputEncoding.entries[index])
                                        "EXPORT_CONFLICT" -> viewModel.setExportConflictMode(ExportConflictMode.entries[index])
                                    }
                                    dropdown = null
                                },
                            )
                        }
                    }
                },
                detail = {
                    Box(
                        Modifier
                            .fillMaxSize(),
                    ) {
                        MiuixDetailHost(
                            visible = sourceLicensesOpen,
                            dismissImmediately = !aboutOpen,
                            base = {
                                val pkgInfo = packageInfo.value
                                val versionName = pkgInfo?.versionName ?: "unknown"
                                val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
                                FullScreenContentPage {
                                    AboutScreen(
                                        onBack = { aboutOpen = false },
                                        onOpenSourceLicenses = { sourceLicensesOpen = true },
                                        versionName = versionName,
                                        versionCode = versionCode,
                                    )
                                }
                            },
                            detail = {
                                FullScreenContentPage {
                                    LibrariesScreen(onBack = { sourceLicensesOpen = false })
                                }
                            },
                        )
                    }
                },
            )
        }
    }
}

/** The page background moves edge-to-edge; only leaf content consumes the status bar inset. */
@Composable
private fun FullScreenContentPage(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(DictTheme.palette.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
        content = content,
    )
}

/** 详情共享进度 p：0 表示隐藏，1 表示完全显示；mounted 覆盖退出动画期间的内容挂载。 */
@Composable
private fun MiuixDetailHost(
    visible: Boolean,
    dismissImmediately: Boolean,
    base: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    val palette = DictTheme.palette
    var mounted by remember { mutableStateOf(visible) }
    val visibleState = rememberUpdatedState(visible)
    val immediateDismissState = rememberUpdatedState(dismissImmediately)
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    val screenCornerRadius = rememberScreenCornerRadius()
    val layoutDirection = LocalLayoutDirection.current
    val directionSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val ancestorTransitionActive = LocalAncestorDetailTransition.current
    val transitionActiveForChildren = remember(ancestorTransitionActive, progress, immediateDismissState) {
        {
            val fraction = progress.value
            ancestorTransitionActive() ||
                (!immediateDismissState.value && fraction > 0f && fraction < 1f)
        }
    }

    LaunchedEffect(visible, dismissImmediately) {
        if (dismissImmediately) {
            progress.stop()
            progress.snapTo(0f)
            mounted = false
            return@LaunchedEffect
        }
        if (visible) {
            if (!mounted) {
                progress.snapTo(0f)
                mounted = true
                withFrameNanos { }
            }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(MIUIX_DETAIL_DURATION, easing = MiuixPageMotionEasing),
            )
        } else if (mounted) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(MIUIX_DETAIL_DURATION, easing = MiuixPageMotionEasing),
            )
            if (!visibleState.value && progress.value == 0f) mounted = false
        }
    }

    // The screen-edge mask uses the same rounded-arc fallback as Miuix, without a shader mask.
    // Its shape stays attached; translation and clip are updated together in the layer block.
    val edgeShape = remember(screenCornerRadius, layoutDirection) {
        val left = if (layoutDirection == LayoutDirection.Ltr) screenCornerRadius else 0.dp
        val right = if (layoutDirection == LayoutDirection.Rtl) screenCornerRadius else 0.dp
        AbsoluteRoundedCornerShape(topLeft = left, topRight = right, bottomRight = right, bottomLeft = left)
    }
    val baseModifier = Modifier.fillMaxSize().graphicsLayer {
        val fraction = if (immediateDismissState.value) 0f else progress.value
        translationX = -0.25f * fraction * size.width * directionSign
        alpha = 1f - 0.1f * fraction
    }

    CompositionLocalProvider(LocalAncestorDetailTransition provides transitionActiveForChildren) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            Box(baseModifier) {
                base()
            }

            if (mounted && !dismissImmediately) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (immediateDismissState.value) 0f else 0.5f * progress.value }
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent()
                            }
                        },
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val fraction = if (immediateDismissState.value) 0f else progress.value
                            translationX = (1f - fraction) * size.width * directionSign
                            shape = edgeShape
                            clip = fraction > 0f && fraction < 1f && screenCornerRadius > 0.dp &&
                                !ancestorTransitionActive()
                        }
                        .background(palette.bg),
                ) {
                    detail()
                }
            }
        }
    }
}

@Composable
private fun MiuixTabHost(
    selectedTab: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int, Boolean) -> Unit,
) {
    val pageCount = 4
    val initialPage = selectedTab.coerceIn(0, pageCount - 1)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    val saveableStateHolder = rememberSaveableStateHolder()
    // Pager 会提前组合相邻页；只有选中且滚动结束后才把页面视为前台。
    val activeTab = if (!pagerState.isScrollInProgress && pagerState.currentPage == selectedTab) selectedTab else -1

    LaunchedEffect(selectedTab) {
        if (selectedTab !in 0 until pageCount) return@LaunchedEffect
        // 仅在目标页已完全对齐且未滚动时跳过，动画中断在半页时仍从实际位置收敛。
        if (!pagerState.isScrollInProgress && pagerState.currentPage == selectedTab &&
            pagerState.currentPageOffsetFraction == 0f
        ) return@LaunchedEffect
        pagerState.springAnimateToPage(selectedTab)
    }

    Box(modifier.fillMaxSize().clipToBounds()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
            key = { it },
        ) { tab ->
            saveableStateHolder.SaveableStateProvider(tab) {
                Box(Modifier.fillMaxSize()) {
                    content(tab, tab == activeTab)
                }
            }
        }
    }
}

@Composable
private fun rememberScreenCornerRadius(): Dp {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val radiusPx = remember(context, view, density, configuration) {
        // API 31+ 读取真实屏幕圆角；旧系统回退框架尺寸资源，缺失时为零。
        val insets = view.rootWindowInsets
        val fromInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.takeIf { it > 0 }
        } else {
            null
        }
        fromInsets ?: bottomCornerRadiusFromResources(context)
    }
    return with(density) { radiusPx.toDp() }
}

@Suppress("DiscouragedApi")
private fun bottomCornerRadiusFromResources(context: android.content.Context): Int {
    val id = context.resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
    return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
}

@Composable
private fun BottomArea(
    state: DictUiState,
    onSelectTab: (Int) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onBatchExport: () -> Unit,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val p = DictTheme.palette
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gapBelowNav = if (navBottom > 12.dp) navBottom + 10.dp else 26.dp
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (state.selectedTab == 0) {
            val currentName = state.files.firstOrNull { it.id == state.currentFileId }?.name ?: "未选择词库"
            PrimaryButton(
                if (state.processing) "取消处理" else "开始处理",
                if (state.processing) {
                    "正在处理：${state.processingFile ?: currentName} · ${(state.progress * 100).toInt()}%"
                } else {
                    "当前：$currentName · ${state.processMode.title}"
                },
                onClick = if (state.processing) onCancel else onStart,
            )
            Spacer(Modifier.height(16.dp))
        } else if (state.selectedTab == 1) {
            if (state.selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CompactActionButton(
                        label = "导出",
                        enabled = state.files.any { it.id in state.selectedFileIds && it.hasOutput },
                        modifier = Modifier.weight(1f),
                        onClick = onBatchExport,
                    )
                    CompactActionButton(
                        label = "删除",
                        enabled = state.selectedFileIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = onDelete,
                        backgroundColor = if (p.isDark) Color(0xFF48282D) else Color(0xFFFFE8EB),
                        textColor = if (p.isDark) Color(0xFFFF6B74) else Color(0xFFD9343E),
                    )
                    CompactActionButton(
                        label = "取消",
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onClearSelection,
                        backgroundColor = Color.White,
                        textColor = Color.Black,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        DictBottomBar(selectedIndex = state.selectedTab, onSelect = onSelectTab)
        Spacer(Modifier.height(gapBelowNav))
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    textColor: Color? = null,
    onClick: () -> Unit,
) {
    val p = DictTheme.palette
    val indication = rememberMiuixIndication()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(backgroundColor ?: if (enabled) p.accent else p.card2)
            .clickable(
                interactionSource = interaction,
                indication = indication,
                enabled = enabled,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor ?: if (enabled) p.onAccent else p.text3,
        )
    }
}

@Composable
private fun DeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val p = DictTheme.palette
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(p.popup)
                .padding(20.dp),
        ) {
            Text("删除词库", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.text)
            Spacer(Modifier.height(10.dp))
            Text("从词库列表移除所选 $count 个词库及其处理结果？原始文件不会被修改。", fontSize = 12.5.sp, color = p.text2)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", p.text2, onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                DialogAction("删除", p.accent, bold = true, onClick = onConfirm)
            }
        }
    }
}