package kirakira.dokidoki.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kirakira.dokidoki.data.DictUiState
import kirakira.dokidoki.data.ThemeMode
import kirakira.dokidoki.ui.components.DictCard
import kirakira.dokidoki.ui.components.DictTopBar
import kirakira.dokidoki.ui.components.FixedTopScrollPage
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.unit.sp

/**
 * 主题设置二级页：原来设置页里的「主题模式」选择搬到这里的卡片里，
 * 并加入「模糊」开关作为前提选项。
 *
 * 模糊开关只影响顶栏与分类轨道的材质：开 = miuix 玻璃，关 = 纯色。
 * 与 c001apk 一致，Android 13 以下没有 runtime shader，开关会被禁用并提示。
 */
@Composable
fun ThemeSettingsScreen(
    state: DictUiState,
    onBack: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleBlur: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = DictTheme.palette
    val blurSupported = Build.VERSION.SDK_INT >= 33

    FixedTopScrollPage(
        modifier = modifier,
        topBar = { DictTopBar("主题设置", onBack = onBack) },
    ) {
        Spacer(Modifier.height(16.dp))

        // 主题模式：与原先设置页一致的单选卡片。
        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(168.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    RadioRow(
                        title = mode.label,
                        selected = state.themeMode == mode,
                        last = index == ThemeMode.entries.lastIndex,
                        onClick = { onSelectTheme(mode) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 模糊开关：玻璃材质的前提。
        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
        ) {
            SettingsRow(
                title = "模糊",
                switchOn = state.blurBars && blurSupported,
                last = true,
                onSwitch = { if (blurSupported) onToggleBlur(it) },
            )
        }

        if (!blurSupported) {
            Spacer(Modifier.height(8.dp))
            Text(
                "需要 Android 13 及以上",
                fontSize = 12.5.sp,
                color = p.text3,
                modifier = Modifier.padding(horizontal = 34.dp),
            )
        }
    }
}
