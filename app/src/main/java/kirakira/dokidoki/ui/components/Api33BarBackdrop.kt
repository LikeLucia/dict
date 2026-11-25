package kirakira.dokidoki.ui.components

import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 移植自 c001apk 的 Api33BarBackdrop（`ui/component/Api33BarBackdrop.kt`，AGPL-3.0，
 * 参考提交 8cfc1c099c5b473614e02f68de3fd1091cd571dc）。
 * 只在 [rememberBarBackdrop] 做完运行时版本判断后才会被加载。
 */
@RequiresApi(33)
@Composable
internal fun rememberApi33BarBackdrop(): BarBackdrop {
    // 采样层：与 c001apk 的 Api33BarBackdrop 一致——
    // 先铺一层 surface，再把内容区画进来，玻璃后面不会出现透明空洞。
    val surface = MiuixTheme.colorScheme.surface
    val layer = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
    return remember(layer) { Api33BarBackdrop(layer) }
}

@RequiresApi(33)
private class Api33BarBackdrop(private val layer: LayerBackdrop) : BarBackdrop {
    override val rawBackdrop: Backdrop get() = layer

    override fun recordModifier(): Modifier = Modifier.layerBackdrop(layer)

    @Composable
    override fun blurModifier(shape: Shape): Modifier =
        Modifier.textureBlur(
            backdrop = layer,
            shape = shape,
            blurRadius = 40f,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))),
            ),
        )

    // 渐进模糊与标签材质参考 c001apk 的 Api33BarBackdrop；其来源注明 BoxProxy bf0b1d5（GPL-3.0）。
    @Composable
    override fun progressiveBlurModifier(shape: Shape): Modifier =
        Modifier.progressiveTextureBlur(
            backdrop = layer,
            shape = shape,
            blurRadius = 100f,
            // 0.25 与 miuix GlassTopAppBar 的顶栏渐进渐变一致：顶部保持满强度，
            // 到栏底迅速收敛，避免把下方卡片文字拖成顶栏里的斑点。
            gradient = ProgressiveBlur.Top.copy(curve = 0.25f),
            colors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.3f))),
            ),
        )

    @Composable
    override fun glassTabModifier(shape: GlassShape): Modifier =
        Modifier.glass(
            backdrop = layer,
            shape = shape,
            material = GlassSegmentedTabRowDefaults.material().copy(blurRadius = 40.dp),
            stroke = GlassSegmentedTabRowDefaults.stroke(),
            shading = false,
        )
}
