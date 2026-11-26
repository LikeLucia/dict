package kirakira.dokidoki.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kirakira.dokidoki.R
import kirakira.dokidoki.ui.components.FlowLightBackground
import kirakira.dokidoki.ui.components.rememberMiuixIndication
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.icon.glass.HtmlViewer
import top.yukonga.miuix.kmp.icon.glass.Link
import top.yukonga.miuix.kmp.icon.glass.OpenWithOtherApp

private const val URL_SOURCE = "https://github.com/LikeLucia/dict"

/** 关于页：渐变背景与居中图标布局参考 c001apk，项目列表使用 Miuix 组件。 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
    versionName: String,
    versionCode: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val p = DictTheme.palette
    val backdrop = if (isRuntimeShaderSupported()) rememberLayerBackdrop() else null
    val cardModifier = if (backdrop != null) {
        Modifier
            .squircleClip(16.dp)
            .textureBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(16.dp),
                blurRadius = 25f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(BlendColorEntry(color = p.card.copy(alpha = 0.8f))),
                ),
            )
    } else {
        Modifier
    }
    FlowLightBackground(
        modifier = modifier.fillMaxSize(),
        backgroundModifier = backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(144.dp))
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(colorResource(R.color.cimu_icon_background)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_105_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(1.5f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "kirakira",
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        color = p.text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "v$versionName ($versionCode)",
                        fontSize = 14.sp,
                        color = p.text2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroAction(MiuixIcons.Glass.OpenWithOtherApp, "项目主页") {
                            openExternalLink(context, URL_SOURCE)
                        }
                        HeroAction(MiuixIcons.Glass.HtmlViewer, "开放源代码许可", onOpenSourceLicenses)
                    }
                    Spacer(Modifier.height(170.dp))
                }
            }
            item(key = "links") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(cardModifier),
                    colors = CardDefaults.defaultColors(
                        if (backdrop != null) Color.Transparent else p.card,
                    ),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        LinkRow(
                            icon = MiuixIcons.Glass.Link,
                            title = "项目主页",
                            summary = "LikeLucia/dict",
                        ) { openExternalLink(context, URL_SOURCE) }
                        LinkRow(
                            icon = MiuixIcons.Glass.HtmlViewer,
                            title = "开放源代码许可",
                            summary = "第三方组件与数据来源",
                            onClick = onOpenSourceLicenses,
                        )
                    }
                }
            }
            item(key = "scroll-space") {
                // 内容不足一屏时保留可拖动余量，避免 About 页完全无法滚动。
                Spacer(Modifier.fillParentMaxHeight(0.6f))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Start,
                    ),
                )
                .padding(16.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    MiuixIcons.Glass.ChevronBackward,
                    contentDescription = "返回",
                    modifier = Modifier.size(24.dp),
                    tint = p.text,
                )
            }
        }
    }
}

@Composable
private fun HeroAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val p = DictTheme.palette
    val indication = rememberMiuixIndication()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = indication,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = p.text,
        )
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val p = DictTheme.palette
    val indication = rememberMiuixIndication()
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = indication,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = p.text2,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    fontSize = 11.5.sp,
                    color = p.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(width = 8.dp, height = 12.dp),
                tint = p.text3,
            )
        }
    }
}

private fun openExternalLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
}
