package kirakira.dokidoki.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kirakira.dokidoki.ui.components.DictTopBar
import kirakira.dokidoki.ui.components.FixedTopScrollPage
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference

private const val COMPOSE_URL = "https://github.com/JetBrains/compose-multiplatform"
private const val ANDROIDX_URL = "https://github.com/androidx/androidx"
private const val MIUIX_URL = "https://github.com/compose-miuix-ui/miuix"
private const val KOTLINX_COROUTINES_URL = "https://github.com/Kotlin/kotlinx.coroutines"
private const val KOTLINX_SERIALIZATION_URL = "https://github.com/Kotlin/kotlinx.serialization"
private const val C001APK_URL = "https://github.com/KiritoXDone/c001apk"
private const val MISHKA_URL = "https://github.com/YuKongA/Mishka"
private const val QUICKJS_JAVA_URL = "https://github.com/cashapp/quickjs-java"
private const val QUICKJS_URL = "https://github.com/bellard/quickjs"
private const val PINYIN_PRO_URL = "https://github.com/zh-lx/pinyin-pro"
private const val JETBRAINS_ANNOTATIONS_URL = "https://github.com/JetBrains/java-annotations"
private const val GUAVA_URL = "https://github.com/google/guava"
private const val RIME_WANXIANG_URL = "https://github.com/amzxyz/rime-wanxiang/tree/47f0c48787ce08d6c37526a2612163a946de5668"
private const val MOMO_URL = "https://github.com/yuanz-12/momo"

internal data class SourceLicenseItem(
    val name: String,
    val author: String,
    val url: String,
    val license: String,
)

internal val SourceLicenseItems = listOf(
    SourceLicenseItem("Compose Runtime", "JetBrains", COMPOSE_URL, "Apache License 2.0"),
    SourceLicenseItem("Compose Foundation", "JetBrains", COMPOSE_URL, "Apache License 2.0"),
    SourceLicenseItem("Compose UI", "JetBrains", COMPOSE_URL, "Apache License 2.0"),
    SourceLicenseItem("Compose Components Resources", "JetBrains", COMPOSE_URL, "Apache License 2.0"),
    SourceLicenseItem("AndroidX Compose Runtime", "Google", ANDROIDX_URL, "Apache License 2.0"),
    SourceLicenseItem("AndroidX Compose Foundation", "Google", ANDROIDX_URL, "Apache License 2.0"),
    SourceLicenseItem("AndroidX Compose UI", "Google", ANDROIDX_URL, "Apache License 2.0"),
    SourceLicenseItem("AndroidX Activity Compose", "Google", ANDROIDX_URL, "Apache License 2.0"),
    SourceLicenseItem("AndroidX Core KTX", "Google", ANDROIDX_URL, "Apache License 2.0"),
    SourceLicenseItem("AndroidX Lifecycle ViewModel Compose", "Google", ANDROIDX_URL, "Apache License 2.0"),
    SourceLicenseItem("AndroidX DataStore Preferences", "Google", ANDROIDX_URL, "Apache License 2.0"),
    SourceLicenseItem("AndroidX Graphics Path", "Google", ANDROIDX_URL, "Apache License 2.0"),
    SourceLicenseItem("Kotlinx Coroutines Android", "JetBrains", KOTLINX_COROUTINES_URL, "Apache License 2.0"),
    SourceLicenseItem("Kotlinx Coroutines Core", "JetBrains", KOTLINX_COROUTINES_URL, "Apache License 2.0"),
    SourceLicenseItem("Kotlinx Serialization Core", "JetBrains", KOTLINX_SERIALIZATION_URL, "Apache License 2.0"),
    SourceLicenseItem("Miuix", "YuKongA / compose-miuix-ui contributors", MIUIX_URL, "Apache License 2.0"),
    SourceLicenseItem("c001apk", "KiritoXDone", C001APK_URL, "GNU Affero General Public License v3.0"),
    SourceLicenseItem("Mishka UI", "YuKongA", MISHKA_URL, "GNU General Public License v3.0"),
    SourceLicenseItem("app.cash QuickJS Android", "app.cash", QUICKJS_JAVA_URL, "Apache License 2.0"),
    SourceLicenseItem("QuickJS C Engine", "Bellard", QUICKJS_URL, "MIT License"),
    SourceLicenseItem("pinyin-pro", "zh-lx", PINYIN_PRO_URL, "MIT License"),
    SourceLicenseItem("JetBrains Annotations", "JetBrains", JETBRAINS_ANNOTATIONS_URL, "Apache License 2.0"),
    SourceLicenseItem("Guava ListenableFuture", "Google", GUAVA_URL, "Apache License 2.0"),
    SourceLicenseItem("Rime Wanxiang 码表", "amzxyz", RIME_WANXIANG_URL, "CC BY 4.0 / MIT License"),
    SourceLicenseItem("渊默辅助码表", "yuanz-12", MOMO_URL, "未单独指定许可证"),
)

/** 列表式开放源代码许可页，卡片格式与 c001apk 的 LicenseScreen 一致。 */
@Composable
fun SourceLicenseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    FixedTopScrollPage(
        modifier = modifier,
        topBar = { DictTopBar(title = "开放源代码许可", onBack = onBack) },
    ) {
        Spacer(Modifier.height(10.dp))
        SourceLicenseItems.forEach { item ->
            LicenseCard(item = item, context = context)
        }
    }
}

@Composable
private fun LicenseCard(
    item: SourceLicenseItem,
    context: Context,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
        colors = CardDefaults.defaultColors(DictTheme.palette.card),
    ) {
        ArrowPreference(
            title = "${item.name} - ${item.author}",
            summary = "${item.url}\n${item.license}",
            onClick = { openSourceLink(context, item.url) },
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

private fun openSourceLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        .onFailure { Toast.makeText(context, "没有可打开链接的应用", Toast.LENGTH_SHORT).show() }
}
