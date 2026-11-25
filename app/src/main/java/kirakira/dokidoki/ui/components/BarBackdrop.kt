package kirakira.dokidoki.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 玻璃材质的采样源。移植自 c001apk 的 BarBackdrop（`ui/component/BlurredBar.kt`）。
 *
 * 有值 = 开模糊，走 miuix 玻璃管线；为 null = 关模糊（或系统低于 Android 13），
 * 走普通纯色 `Modifier.background(...)`。两套材质共用同一个判空，不写两套组件。
 */
val LocalBarBackdrop = staticCompositionLocalOf<BarBackdrop?> { null }

/**
 * 全局模糊开关。由 App 根层下发，App 层据此建页面级与底栏级两个采样层：
 * 页面级供顶栏、轨道使用，底栏级固定挂在 pager 根节点上供底栏使用。
 */
val LocalBlurBars = staticCompositionLocalOf { false }

/**
 * 当前页面是否是前台页。Pager 会预组合相邻页，
 * 只有前台页才把自己的内容录进页面级采样层，避免多页同时写同一层。
 * 底栏的视口级采样层是整块 pager 一起录的，不受这个开关影响。
 */
val LocalPageActive = staticCompositionLocalOf { true }

/**
 * 底栏专用采样源。底栏在 pager 外，不能复用页面级层：页面切换时页面级层会随
 * pager 平移，纹理边界扫过底栏就会形成竖线。这一层挂在 pager 的静止根节点上，
 * 纹理始终覆盖整个视口，底栏任何时刻都只采到完整页面。
 */
val LocalBottomBarBackdrop = staticCompositionLocalOf<BarBackdrop?> { null }

/** 不向低版本 Android 暴露任何 AGSL / RenderNode 类型。 */
interface BarBackdrop {
    val rawBackdrop: Backdrop

    /** 把内容区录进采样层，供上方玻璃读取。 */
    fun recordModifier(): Modifier

    @Composable fun blurModifier(shape: Shape): Modifier

    @Composable fun progressiveBlurModifier(shape: Shape): Modifier

    @Composable fun glassTabModifier(shape: GlassShape): Modifier
}

/**
 * 只有系统支持且用户打开「模糊」时才返回非空。
 * Android 13（API 33）以下没有 runtime shader / RenderEffect 的对应能力，直接降级成纯色。
 */
@Composable
fun rememberBarBackdrop(enabled: Boolean): BarBackdrop? =
    if (enabled && Build.VERSION.SDK_INT >= 33) rememberApi33BarBackdrop() else null

@Composable
fun Modifier.barBlur(backdrop: BarBackdrop, shape: Shape = RectangleShape): Modifier =
    if (Build.VERSION.SDK_INT >= 33) then(backdrop.blurModifier(shape))
    else background(MiuixTheme.colorScheme.surface, shape)

@Composable
fun Modifier.progressiveBarBlur(backdrop: BarBackdrop): Modifier =
    if (Build.VERSION.SDK_INT >= 33) then(backdrop.progressiveBlurModifier(RectangleShape))
    else background(MiuixTheme.colorScheme.surface)
