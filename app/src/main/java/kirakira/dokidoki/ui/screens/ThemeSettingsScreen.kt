package kirakira.dokidoki.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kirakira.dokidoki.data.DictUiState
import kirakira.dokidoki.data.ThemeMode
import kirakira.dokidoki.ui.components.DictCard
import kirakira.dokidoki.ui.components.DictTopBar
import kirakira.dokidoki.ui.components.DropdownAnchor
import kirakira.dokidoki.ui.components.captureDropdownAnchor
import kirakira.dokidoki.ui.components.FixedTopScrollPage
import kirakira.dokidoki.ui.components.LocalBarBackdrop
import kirakira.dokidoki.ui.components.rememberBarBackdrop
import kirakira.dokidoki.ui.theme.DictTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.unit.sp

/**
 * 主题设置二级页：主题模式用下拉弹窗选择（与设置页其他下拉项同一套），
 * 并加入「模糊」开关作为前提选项。
 *
 * 模糊开关只影响顶栏、分类轨道与底栏的材质：开 = miuix 玻璃，关 = 纯色。
 * 与 c001apk 一致，Android 13 以下没有 runtime shader，开关会被禁用并提示。
 */
@Composable
fun ThemeSettingsScreen(
    state: DictUiState,
    openedMenu: SettingsMenu?,
    onOpenMenu: (SettingsMenu, Rect) -> Unit,
    onBack: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleBlur: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = DictTheme.palette
    val blurSupported = Build.VERSION.SDK_INT >= 33
    val themeAnchor = remember { DropdownAnchor() }
    // 主题设置是独立详情页，使用自己的采样层，避免底层设置页的内容混进顶栏玻璃。
    val pageBackdrop = rememberBarBackdrop(state.blurBars)

    CompositionLocalProvider(LocalBarBackdrop provides pageBackdrop) {
    FixedTopScrollPage(
        modifier = modifier,
        topBar = { DictTopBar("主题设置", onBack = onBack) },
    ) {
        Spacer(Modifier.height(16.dp))

        // 主题模式：下拉弹窗，与设置页其他下拉项同一套交互。
        DictCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp)
                .captureDropdownAnchor(themeAnchor),
        ) {
            SettingsRow(
                title = "主题模式",
                value = state.themeMode.label,
                holdDownState = openedMenu == SettingsMenu.THEME,
                last = true,
                onClick = { onOpenMenu(SettingsMenu.THEME, themeAnchor.bounds()) },
            )
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
                title = "开启模糊效果",
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
}
