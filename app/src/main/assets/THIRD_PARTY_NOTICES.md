# 开源许可与来源说明

## 项目许可

本项目采用 **GNU Affero General Public License v3.0（AGPL-3.0）**，完整许可证文本见仓库根目录的 [LICENSE](../../../../LICENSE)。

除另有说明外，本仓库中的源代码、界面实现与文档均按 AGPL-3.0 提供。第三方组件、代码、码表与素材仍遵循各自许可证；第三方许可证不改变相应作品的权利归属。

## 应用依赖与处理引擎

- **Compose Multiplatform 与 AndroidX**：Compose Runtime、Compose Foundation、Compose UI、Compose Components Resources，以及 AndroidX Activity Compose、Core KTX、Lifecycle ViewModel Compose、DataStore Preferences、Graphics Path，均采用 Apache License 2.0。Graphics Path 使用的本地原生库包含 16 KB RELRO 对齐补丁。
- **Miuix 0.9.5-rc01**：`miuix-ui`、`miuix-glass`、`miuix-preference`、`miuix-icons`、`miuix-glass-icons`，采用 Apache License 2.0。
- **QuickJS**：QuickJS 2021-03-27 引擎采用 MIT License；`app.cash/quickjs-java` 包装采用 Apache License 2.0。本项目使用基于 app.cash 0.9.2 包装重新编译、用于 16 KB 页面兼容的本地 AAR（`app/libs/quickjs-android-0.9.2-16kb.aar`）。
- **pinyin-pro 3.27.0**：MIT License，Copyright (c) 2022-present zh-lx。
- **Kotlinx Coroutines / Kotlinx Serialization**：Apache License 2.0。
- **JetBrains Annotations**：Apache License 2.0。
- **Guava ListenableFuture**：Apache License 2.0。

## 移植的界面代码

- **c001apk**（[KiritoXDone/c001apk](https://github.com/KiritoXDone/c001apk)，参考提交 `8cfc1c099c5b473614e02f68de3fd1091cd571dc`）：GNU Affero General Public License v3.0（AGPL-3.0）。`Api33BarBackdrop.kt`、`BarBackdrop.kt` 的模糊栏管线，以及顶栏、底栏、词库分类轨道的玻璃布局与交互，参考/移植自该项目的 `app/src/main/java/com/example/c001apk/compose/ui/component/Api33BarBackdrop.kt`、`BlurredBar.kt`、`MiuixScaffold.kt`、`MiuixNavigationScaffold.kt`、`MiuixTabs.kt`；关于页的居中图标、版本信息与项目列表布局参考该项目的 `ui/settings/AboutScreen.kt`；`FlowLightBackground.kt` 的流光渐变背景移植自该项目的 `ui/component/FlowLightBackground.kt`。
- **BoxProxy**（[boxproxy/boxproxy](https://github.com/boxproxy/boxproxy)，提交 `bf0b1d5`）：GNU General Public License v3.0（GPL-3.0）。c001apk 的渐进模糊与标签材质实现注明参考该项目，本次移植一并保留该来源说明。
- **Mishka**（[YuKongA/Mishka](https://github.com/YuKongA/Mishka)）：GNU General Public License v3.0（GPL-3.0）。固定顶栏滚动页与玻璃底栏参数参考该项目。

## 辅助码数据与多音字数据

- **墨奇、自然码及万象野鹤扩展码表**：来源于 [rime-wanxiang](https://github.com/amzxyz/rime-wanxiang/tree/47f0c48787ce08d6c37526a2612163a946de5668) 固定提交 `47f0c48787ce08d6c37526a2612163a946de5668`。原页面保留 CC BY 4.0 与相关墨奇 MIT 声明；万象野鹤扩展并非未经修改的官方小鹤码表。逐字选择来源顺序中的首个完整两字母码。
- **渊默辅助码表**：来源于 [https://github.com/yuanz-12/momo](https://github.com/yuanz-12/momo) 的 `aux_code.csv`。数据含 30 个字的兼容码（如 丰 `sl,af,al`、甩 `kv,ov,yv`），按上游顺序以逗号连接后内置。来源当前标记为 NOASSERTION；本工程未另行指定其许可证。
- **kirakira 辅助码表**：来源于 [LikeLucia/kirakira](https://github.com/LikeLucia/kirakira/tree/8746a2bb53355bf1b775fe0874a3ef5993278ec7) 的 `aux_code.csv`（提交 `8746a2bb53355bf1b775fe0874a3ef5993278ec7`）。该码表基于墨奇码魔改（字根与单字拆分改动见其 README），共 43216 字，其中 7 个字保留兼容码。来源未声明许可证；本工程未另行指定其许可证。
- **多音字权重**：由用户提供的 `zi.dict.yaml` 提取，仅内置于应用中用于本地多音字排序；提供的数据未附独立许可声明。

## 来源链接

- pinyin-pro：https://github.com/zh-lx/pinyin-pro
- rime-wanxiang：https://github.com/amzxyz/rime-wanxiang/tree/47f0c48787ce08d6c37526a2612163a946de5668
- 渊默码：https://github.com/yuanz-12/momo
- kirakira：https://github.com/LikeLucia/kirakira/tree/8746a2bb53355bf1b775fe0874a3ef5993278ec7
