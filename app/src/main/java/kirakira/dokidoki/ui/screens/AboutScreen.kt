package kirakira.dokidoki.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kirakira.dokidoki.ui.components.DictCard
import kirakira.dokidoki.ui.components.FixedTopScrollPage
import kirakira.dokidoki.ui.components.DictTopBar
import kirakira.dokidoki.ui.components.SectionHeader
import kirakira.dokidoki.ui.components.rememberMiuixIndication
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight

private val AboutGradientLightStart = Color(0xFF2C7AF4)
private val AboutGradientLightEnd = Color(0xFF2DBEBA)
private val AboutGradientDarkStart = Color(0xFF1E52CD)
private val AboutGradientDarkEnd = Color(0xFF1E8C8C)

private val AboutAccentBarColors = listOf(
    Color(0xFF3482FF),
    Color(0xFF4CAF50),
    Color(0xFFFF9800),
    Color(0xFF9C50FF),
    Color(0xFF26C6DA),
    Color(0xFFF44336),
    Color(0xFFFFD54F),
    Color(0xFF3F51B5),
    Color(0xFF009688),
    Color(0xFF03A9F4),
    Color(0xFFFF7043),
    Color(0xFF795548),
)

private const val URL_SOURCE = "https://github.com/LikeLucia/dict"
private const val URL_MIUIX = "https://github.com/compose-miuix-ui/miuix"
private const val URL_QUICKJS = "https://github.com/bellard/quickjs"
private const val URL_PINYIN_PRO = "https://github.com/zh-lx/pinyin-pro"
private const val URL_COUI = "https://github.com/suqi8/coui"
private const val URL_RIME_WANXIANG = "https://github.com/amzxyz/rime-wanxiang"
private const val URL_MOQIMA_TABLES = "https://github.com/gaboolic/moqima-tables"
private const val URL_MOMO = "https://github.com/yuanz-12/momo"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val p = DictTheme.palette
    val context = LocalContext.current
    FixedTopScrollPage(
        modifier = modifier,
        topBar = {
            DictTopBar(
                title = "关于",
                onBack = onBack,
                actionSize = 40.dp,
                actionIconSize = 22.dp,
            )
        },
    ) {
        Spacer(Modifier.height(16.dp))

        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(232.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (p.isDark) {
                                listOf(AboutGradientDarkStart, AboutGradientDarkEnd)
                            } else {
                                listOf(AboutGradientLightStart, AboutGradientLightEnd)
                            },
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "kirakira",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = p.onAccent,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "版本 $versionName",
                        fontSize = 12.5.sp,
                        color = p.onAccent.copy(alpha = 0.84f),
                    )
                    Spacer(Modifier.height(20.dp))
                    val pillShape = RoundedCornerShape(19.dp)
                    val pillInteraction = remember { MutableInteractionSource() }
                    val pillIndication = rememberMiuixIndication()
                    Box(
                        modifier = Modifier
                            .width(118.dp)
                            .height(38.dp)
                            .clip(pillShape)
                            .background(p.onAccent)
                            .clickable(
                                interactionSource = pillInteraction,
                                indication = pillIndication,
                            ) { openExternalLink(context, URL_SOURCE) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "查看源代码",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = p.accent,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(108.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth()) {
                    AboutRow(title = "名称", value = "kirakira")
                    AboutRow(title = "组件", value = "4 屏 · 16 个源码文件", last = true)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, end = 16.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .height(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    AboutAccentBarColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(color),
                        )
                    }
                }
            }
        }

        SectionHeader("详细信息", modifier = Modifier.padding(start = 2.dp))

        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(156.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                AboutRow(title = "版本", value = versionName)
                AboutRow(title = "设计语言", value = "Miuix · COUI 2.1.0")
                AboutRow(title = "平台", value = "Android", last = true)
            }
        }

        SectionHeader("项目", modifier = Modifier.padding(start = 2.dp))

        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(104.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                AboutRow(
                    title = "项目主页",
                    value = "LikeLucia/dict",
                    showArrow = true,
                    onClick = { openExternalLink(context, URL_SOURCE) },
                )
                AboutRow(
                    title = "源代码许可",
                    value = "${SourceLicenseEntries.size} 项",
                    showArrow = true,
                    last = true,
                    onClick = onOpenSourceLicenses,
                )
            }
        }

        SectionHeader("鸣谢", modifier = Modifier.padding(start = 2.dp))

        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(416.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                AboutRow(title = "Compose", value = "UI 框架")
                AboutRow(
                    title = "Miuix",
                    value = "组件和动效 · 设计语言",
                    onClick = { openExternalLink(context, URL_MIUIX) },
                )
                AboutRow(
                    title = "COUI",
                    value = "设计语言",
                    onClick = { openExternalLink(context, URL_COUI) },
                )
                AboutRow(
                    title = "QuickJS",
                    value = "词库处理引擎",
                    onClick = { openExternalLink(context, URL_QUICKJS) },
                )
                AboutRow(
                    title = "pinyin-pro",
                    value = "拼音生成",
                    onClick = { openExternalLink(context, URL_PINYIN_PRO) },
                )
                AboutRow(
                    title = "rime-wanxiang",
                    value = "自然码 / 万象野鹤扩展表",
                    onClick = { openExternalLink(context, URL_RIME_WANXIANG) },
                )
                AboutRow(
                    title = "moqima-tables",
                    value = "墨奇辅助码",
                    onClick = { openExternalLink(context, URL_MOQIMA_TABLES) },
                )
                AboutRow(
                    title = "momo",
                    value = "渊默码",
                    last = true,
                    onClick = { openExternalLink(context, URL_MOMO) },
                )
            }
        }
    }

}

@Composable
private fun AboutRow(
    title: String,
    value: String? = null,
    showArrow: Boolean = false,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val p = DictTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val indication = rememberMiuixIndication()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = indication,
                    ) { onClick() }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 14.5.sp, color = p.text)
            Spacer(Modifier.weight(1f))
            if (value != null) {
                Text(
                    value,
                    fontSize = 13.5.sp,
                    color = p.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                if (showArrow) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(width = 9.dp, height = 13.dp),
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


private fun openExternalLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
}
