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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kirakira.dokidoki.ui.components.DictCard
import kirakira.dokidoki.ui.components.DictTopBar
import kirakira.dokidoki.ui.components.FixedTopScrollPage
import kirakira.dokidoki.ui.components.SectionHeader
import kirakira.dokidoki.ui.components.rememberMiuixIndication
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight

private const val APACHE_LICENSE = "https://www.apache.org/licenses/LICENSE-2.0"
private const val MIT_LICENSE = "https://opensource.org/license/mit"
private const val JETBRAINS_COMPOSE = "https://github.com/JetBrains/compose-multiplatform"
private const val ANDROIDX = "https://github.com/androidx/androidx"
private const val MIUIX = "https://github.com/compose-miuix-ui/miuix"
private const val MIUIX_LICENSE = "https://github.com/compose-miuix-ui/miuix/blob/main/LICENSE"
private const val COUI = "https://github.com/suqi8/coui"
private const val COUI_LICENSE = "https://github.com/suqi8/coui/blob/main/LICENSE"

internal data class SourceLicenseLink(val label: String, val url: String)

internal data class SourceLicenseEntry(
    val category: String,
    val name: String,
    val version: String,
    val purpose: String,
    val license: String,
    val links: List<SourceLicenseLink>,
)

internal val SourceLicenseEntries = listOf(
    SourceLicenseEntry("构建工具", "Kotlin Standard Library", "2.4.21", "Kotlin/JVM 标准运行时", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", "https://github.com/JetBrains/kotlin"),
        SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("构建工具", "Gradle", "9.9.1", "Gradle Wrapper 构建系统", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", "https://github.com/gradle/gradle"),
        SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("构建工具", "Android Gradle Plugin", "9.5.1", "Android 应用构建插件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目文档", "https://developer.android.com/build/releases/gradle-plugin"),
        SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("构建工具", "Kotlin Compose Gradle Plugin", "2.4.21", "Kotlin 与 Compose 编译插件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", "https://github.com/JetBrains/kotlin"),
        SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("构建工具", "Compose Multiplatform Gradle Plugin", "1.13.0-rc01", "Compose Multiplatform 构建插件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", JETBRAINS_COMPOSE),
        SourceLicenseLink("许可证原文", "https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "Compose Runtime", "1.13.0-rc01", "Compose 状态与组合运行时", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", JETBRAINS_COMPOSE), SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "Compose Foundation", "1.13.0-rc01", "Compose 布局、手势与基础组件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", JETBRAINS_COMPOSE), SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "Compose UI", "1.13.0-rc01", "Compose UI 绘制与输入基础", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", JETBRAINS_COMPOSE), SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "Compose Components Resources", "1.13.0-rc01", "Compose 资源组件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", JETBRAINS_COMPOSE), SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "AndroidX Compose Runtime", "1.13.0-rc01", "Android 平台 Compose 运行时", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", ANDROIDX), SourceLicenseLink("许可证原文", "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "AndroidX Compose Foundation", "1.13.0-rc01", "Android 平台布局、手势与基础组件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", ANDROIDX), SourceLicenseLink("许可证原文", "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "AndroidX Compose UI", "1.13.0-rc01", "Android 平台 UI 绘制与输入", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", ANDROIDX), SourceLicenseLink("许可证原文", "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "AndroidX Activity Compose", "1.13.0", "Activity 与 Compose 集成", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", ANDROIDX), SourceLicenseLink("许可证原文", "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "AndroidX Core KTX", "1.17.0", "AndroidX Core Kotlin 扩展", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", ANDROIDX), SourceLicenseLink("许可证原文", "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "AndroidX Lifecycle ViewModel Compose", "2.11.0", "Lifecycle 与 ViewModel Compose 集成", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", ANDROIDX), SourceLicenseLink("许可证原文", "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "AndroidX DataStore Preferences", "1.3.0", "设置持久化存储", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", ANDROIDX), SourceLicenseLink("许可证原文", "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "AndroidX Graphics Path", "1.1.0", "屏幕圆角路径；本地原生库含 16 KB RELRO 补丁", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", ANDROIDX), SourceLicenseLink("许可证原文", "https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "Kotlinx Coroutines Android", "1.9.0", "Android 协程调度器", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", "https://github.com/Kotlin/kotlinx.coroutines"), SourceLicenseLink("许可证原文", "https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "Kotlinx Coroutines Core", "1.9.0", "异步任务与协程基础", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", "https://github.com/Kotlin/kotlinx.coroutines"), SourceLicenseLink("许可证原文", "https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "Kotlinx Serialization Core", "1.7.3", "AndroidX SavedState 传递依赖", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", "https://github.com/Kotlin/kotlinx.serialization"), SourceLicenseLink("许可证原文", "https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt"),
    )),
    SourceLicenseEntry("运行依赖", "Miuix UI", "0.9.5-beta03", "Compose UI 基础组件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", MIUIX), SourceLicenseLink("许可证原文", MIUIX_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "Miuix Glass", "0.9.5-beta03", "玻璃效果与导航组件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", MIUIX), SourceLicenseLink("许可证原文", MIUIX_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "Miuix Preference", "0.9.5-beta03", "偏好设置组件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", MIUIX), SourceLicenseLink("许可证原文", MIUIX_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "Miuix Icons", "0.9.5-beta03", "基础图标组件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", MIUIX), SourceLicenseLink("许可证原文", MIUIX_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "Miuix Glass Icons", "0.9.5-beta03", "Glass 图标组件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", MIUIX), SourceLicenseLink("许可证原文", MIUIX_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "app.cash QuickJS Android", "0.9.2", "QuickJS Android/JVM Java 包装层；项目内使用本地 16 KB 页面兼容构建", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", "https://github.com/cashapp/quickjs-java"), SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "QuickJS C Engine", "2021-03-27", "内嵌 JavaScript 引擎", "MIT License", listOf(
        SourceLicenseLink("项目主页", "https://github.com/bellard/quickjs"), SourceLicenseLink("许可证原文", "https://github.com/bellard/quickjs/blob/master/LICENSE"),
    )),
    SourceLicenseEntry("运行依赖", "pinyin-pro", "3.27.0", "内置拼音生成库；Copyright (c) 2022-present zh-lx", "MIT License", listOf(
        SourceLicenseLink("项目主页", "https://github.com/zh-lx/pinyin-pro"), SourceLicenseLink("许可证原文", "https://github.com/zh-lx/pinyin-pro/blob/master/LICENSE"),
    )),
    SourceLicenseEntry("运行依赖", "JetBrains Annotations", "23.0.0", "Kotlin/JVM 与 Java 注解", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", "https://github.com/JetBrains/java-annotations"), SourceLicenseLink("许可证原文", APACHE_LICENSE),
    )),
    SourceLicenseEntry("运行依赖", "Guava ListenableFuture", "1.0", "AndroidX 并发接口兼容组件", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", "https://github.com/google/guava"), SourceLicenseLink("许可证原文", "https://github.com/google/guava/blob/master/COPYING"),
    )),
    SourceLicenseEntry("运行依赖", "COUI", "2.1.0", "关于页 UI 组件、偏好设置组件与图标", "Apache License 2.0", listOf(
        SourceLicenseLink("项目主页", COUI), SourceLicenseLink("许可证原文", COUI_LICENSE),
    )),
    SourceLicenseEntry("内置数据来源", "Rime Wanxiang 码表", "提交 47f0c487", "墨奇、自然码、万象野鹤扩展码表；万象野鹤扩展并非未经修改的官方小鹤码表", "CC BY 4.0 与相关墨奇 MIT 声明", listOf(
        SourceLicenseLink("来源提交", "https://github.com/amzxyz/rime-wanxiang/tree/47f0c48787ce08d6c37526a2612163a946de5668"),
        SourceLicenseLink("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
        SourceLicenseLink("MIT License", MIT_LICENSE),
    )),
    SourceLicenseEntry("内置数据来源", "渊默辅助码表", "提交 18dbd2f8", "LikeLucia/momo 的 aux_code.csv；当前来源标记为 NOASSERTION", "未单独指定许可证", listOf(
        SourceLicenseLink("项目主页", "https://github.com/yuanz-12/momo"),
    )),
)

@Composable
fun LibrariesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val grouped = remember { SourceLicenseEntries.groupBy { it.category } }
    FixedTopScrollPage(
        modifier = modifier,
        topBar = { DictTopBar(title = "源代码许可", onBack = onBack) },
    ) {
        Spacer(Modifier.height(8.dp))
        grouped.forEach { (category, entries) ->
            SectionHeader(category)
            entries.forEach { entry -> SourceLicenseCard(entry, context) }
        }
        Text(
            "各条声明对应所列组件或数据来源；本工程未指定统一的项目级许可证。",
            fontSize = 11.sp,
            color = DictTheme.palette.text3,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun SourceLicenseCard(entry: SourceLicenseEntry, context: Context) {
    val p = DictTheme.palette
    DictCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 5.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(entry.version, fontSize = 10.5.sp, color = p.text3, maxLines = 1)
            }
            Text(entry.purpose, fontSize = 11.5.sp, color = p.text2)
            Text("许可证：${entry.license}", fontSize = 11.5.sp, color = p.accent)
            entry.links.forEach { link -> SourceLinkRow(link, context) }
        }
    }
}

@Composable
private fun SourceLinkRow(link: SourceLicenseLink, context: Context) {
    val p = DictTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val indication = rememberMiuixIndication()
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = indication) { openSourceLink(context, link.url) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(link.label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = p.accent)
        Spacer(Modifier.weight(1f))
        Text(
            link.url.removePrefix("https://"), fontSize = 9.sp, color = p.text3, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 190.dp),
        )
        Spacer(Modifier.width(5.dp))
        Icon(MiuixIcons.Basic.ArrowRight, contentDescription = null, modifier = Modifier.size(width = 8.dp, height = 12.dp), tint = p.text3)
    }
}

private fun openSourceLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        .onFailure { Toast.makeText(context, "没有可打开链接的应用", Toast.LENGTH_SHORT).show() }
}