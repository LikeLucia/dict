package kirakira.dokidoki.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.VerticalScrollBar
import io.github.suqi8.coui.kmp.basic.rememberScrollBarAdapter
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.interfaces.ExperimentalScrollBarApi
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior

private const val URL_SOURCE = "https://github.com/LikeLucia/dict"
private const val URL_MIUIX = "https://github.com/compose-miuix-ui/miuix"
private const val URL_QUICKJS = "https://github.com/bellard/quickjs"
private const val URL_PINYIN_PRO = "https://github.com/zh-lx/pinyin-pro"
private const val URL_COUI = "https://github.com/suqi8/coui"
private const val URL_RIME_WANXIANG = "https://github.com/amzxyz/rime-wanxiang"
private const val URL_MOQIMA_TABLES = "https://github.com/gaboolic/moqima-tables"
private const val URL_MOMO = "https://github.com/yuanz-12/momo"

private val HeroHeight = 240.dp
private val HeroCornerRadius = 24.dp
private val HeroLogoTop = 66.dp
private val HeroButtonGap = 32.dp
private val HeroTextColor = Color.White
private val HeroBuildNumberColor = Color(0xD9FFFFFF)
private val RatioBarHeight = 10.dp
private val RatioBarGap = 2.dp

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
    versionName: String,
    versionCode: Long,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = remember(isDark) {
        if (isDark) darkColorScheme() else lightColorScheme()
    }
    COUITheme(colors = colors) {
        val scrollBehavior = COUIScrollBehavior()
        val listState = rememberLazyListState()
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = "关于",
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            material = true,
                        ) {
                            Icon(COUIIcons.Back, contentDescription = "返回")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    showDivider = false,
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 12.dp)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = padding,
                    flingBehavior = rememberCOUIFlingBehavior(),
                ) {
                    item(key = "hero") {
                        AboutHero(
                            versionName = versionName,
                            onViewSource = { openExternalLink(context, URL_SOURCE) },
                        )
                    }
                    item(key = "summary") {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                        ) {
                            CardListGroup {
                                item {
                                    BasicComponent(
                                        title = "名称",
                                        endActions = { EndValue("kirakira") },
                                    )
                                }
                                item(showDivider = false) {
                                    ComponentCountRow()
                                }
                            }
                        }
                    }
                    item(key = "details") {
                        PreferenceCategory("详细信息")
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                        ) {
                            CardListGroup {
                                item {
                                    BasicComponent(
                                        title = "版本",
                                        endActions = { EndValue("$versionName ($versionCode)") },
                                    )
                                }
                                item {
                                    BasicComponent(
                                        title = "设计语言",
                                        endActions = { EndValue("ColorOS 17 · COUI") },
                                    )
                                }
                                item {
                                    BasicComponent(
                                        title = "平台",
                                        endActions = { EndValue("Android") },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "project") {
                        PreferenceCategory("项目")
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                        ) {
                            CardListGroup {
                                item {
                                    LinkRow(
                                        title = "项目主页",
                                        value = "LikeLucia/dict",
                                        url = URL_SOURCE,
                                        context = context,
                                    )
                                }
                                item {
                                    ArrowPreference(
                                        title = "源代码许可",
                                        endActions = { EndValue("${SourceLicenseEntries.size} 项") },
                                        onClick = onOpenSourceLicenses,
                                    )
                                }
                            }
                        }
                    }
                    item(key = "credits") {
                        PreferenceCategory("鸣谢")
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                        ) {
                            CardListGroup {
                                item {
                                    BasicComponent(
                                        title = "Compose",
                                        endActions = { EndValue("UI 框架") },
                                    )
                                }
                                item { LinkRow("Miuix", "组件和动效 · 设计语言", URL_MIUIX, context) }
                                item { LinkRow("COUI", "组件和动效 · 设计语言", URL_COUI, context) }
                                item { LinkRow("QuickJS", "词库处理引擎", URL_QUICKJS, context) }
                                item { LinkRow("pinyin-pro", "拼音生成", URL_PINYIN_PRO, context) }
                                item { LinkRow("rime-wanxiang", "自然码 / 万象野鹤扩展表", URL_RIME_WANXIANG, context) }
                                item { LinkRow("moqima-tables", "墨奇辅助码", URL_MOQIMA_TABLES, context) }
                                item { LinkRow("momo", "渊默码", URL_MOMO, context) }
                            }
                        }
                    }
                    item(key = "tail") {
                        Box(Modifier.height(32.dp))
                    }
                }
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    trackPadding = padding,
                )
            }
        }
    }
}

@Composable
private fun AboutHero(
    versionName: String,
    onViewSource: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    val background = Brush.linearGradient(
        listOf(colors.violet, colors.primary, colors.cyan),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .height(HeroHeight)
            .clip(RoundedCornerShape(HeroCornerRadius))
            .background(background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 900f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = HeroLogoTop - 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "kirakira",
                color = HeroTextColor,
                fontSize = 52.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "版本 $versionName",
                color = HeroBuildNumberColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            TextButton(
                text = "查看源代码",
                onClick = onViewSource,
                modifier = Modifier.padding(top = HeroButtonGap - 12.dp),
                minHeight = 40.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.textButtonColorsTransparent(textColor = HeroTextColor),
                textStyle = COUITheme.textStyles.buttonM,
            )
        }
    }
}

@Composable
private fun ComponentCountRow() {
    val colors = COUITheme.colorScheme
    val palette = listOf(
        colors.blue, colors.green, colors.orange, colors.purple, colors.cyan, colors.red,
        colors.yellow, colors.violet, colors.mint, colors.azure, colors.orangeVariant,
    )
    val segmentCount = 12
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "组件",
                style = COUITheme.textStyles.headlineXS,
                color = colors.labelPrimary,
            )
            Text(
                text = "4 屏 · 16 个源码文件",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                style = COUITheme.textStyles.bodyM,
                color = colors.labelSecondary,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Canvas(
            modifier = Modifier
                .padding(top = 7.dp)
                .fillMaxWidth()
                .height(RatioBarHeight)
                .clip(RoundedCornerShape(RatioBarHeight)),
        ) {
            drawRect(colors.fillThin)
            val gap = RatioBarGap.toPx()
            val usable = size.width - gap * (segmentCount - 1)
            val segmentWidth = usable / segmentCount
            var x = 0f
            repeat(segmentCount) { index ->
                drawRoundRect(
                    color = palette[index % palette.size],
                    topLeft = Offset(x, 0f),
                    size = Size(segmentWidth, size.height),
                    cornerRadius = CornerRadius.Zero,
                )
                x += segmentWidth + gap
            }
        }
    }
}

@Composable
private fun EndValue(text: String) {
    Text(
        text = text,
        style = COUITheme.textStyles.bodyM,
        color = COUITheme.colorScheme.labelTertiary,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LinkRow(
    title: String,
    value: String,
    url: String,
    context: Context,
) {
    ArrowPreference(
        title = title,
        endActions = { EndValue(value) },
        onClick = { openExternalLink(context, url) },
    )
}

private fun openExternalLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
}