package kirakira.dokidoki.ui.components

import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 移植自 c001apk 的 Api33BarBackdrop（`ui/component/Api33BarBackdrop.kt`）。
 * 只在 [rememberBarBackdrop] 做完运行时版本判断后才会被加载。
 */
@RequiresApi(33)
@Composable
internal fun rememberApi33BarBackdrop(): BarBackdrop {
    val surface = MiuixTheme.colorScheme.surface
    // 采样层：先铺一层 surface 底色，再把内容区画进来。
    // 有这层底色，玻璃后面不会出现透明空洞；内容滚到下方时也会被一起采样。
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
            blurRadius = 25f,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))),
            ),
        )

    @Composable
    override fun progressiveBlurModifier(shape: Shape): Modifier =
        Modifier.progressiveTextureBlur(
            backdrop = layer,
            shape = shape,
            blurRadius = GlassTopAppBarDefaults.ProgressiveBlurRadius.value,
            gradient = ProgressiveBlur.Top.copy(curve = 0.5f),
            colors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.3f))),
            ),
        )

    /**
     * 轨道玻璃。
     *
     * 材质不用 [GlassSegmentedTabRowDefaults.material] 的默认值：那套 puredThinGlass
     * 自带明显的灰色层（暗色下 second/third 是 0x66565656 / 0x993F3F3F），
     * 叠在 TabRowWithContour 自己的选中胶囊上会看成「两层灰胶囊」。
     *
     * 这里换成只留模糊与极淡高光的材质：玻璃仍然透出下方内容，
     * 但不再自带一层底色，选中态只剩 TabRowWithContour 那层
     * 由 indicatorPosition + tween(200) 驱动的动画胶囊。
     */
    @Composable
    override fun glassTabModifier(shape: GlassShape): Modifier =
        Modifier.glass(
            backdrop = layer,
            shape = shape,
            material = TrackGlassMaterial,
            stroke = GlassSegmentedTabRowDefaults.stroke(),
            shading = false,
        )
}

/**
 * 轨道玻璃材质：只保留模糊与极淡高光，不带可见底色。
 *
 * 不用 [GlassSegmentedTabRowDefaults.material] 的 puredThinGlass——那套自带明显灰层
 * （暗色下 0x66565656 / 0x993F3F3F），叠在选中胶囊上会看成两层灰胶囊。
 */
internal val TrackGlassMaterial: GlassMaterial = GlassMaterial(
    blurRadius = 20.dp,
    first = GlassColorLayer(Color(0x08000000), GlassColorBlendMode.PlusDarker),
    second = GlassColorLayer(Color(0x26FFFFFF), GlassColorBlendMode.SoftLight),
)
