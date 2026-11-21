# kirakira · Android

离线词库处理工具的原生 Android 实现，基于 Kotlin + Compose + Miuix。支持导入 Rime YAML / TXT / TSV / ZIP 词库，在本机完成拼音与辅助码处理并导出结果，词库数据不会上传。

## 功能

- **四个页面**：首页、词库、报告、设置。
- **导入与队列**：通过系统 SAF 单选或多选导入，自动排队处理；首页展示处理进度、预计时长与队列状态，支持开始和取消。
- **处理方式**：刷拼音和辅助码、仅刷拼音、仅刷辅助码、仅删辅助码。
- **辅助码**：内置渊默码、自然码、小鹤、墨奇四套码表，支持固定分隔符和替换已有辅助码。
- **处理设置**：可配置拼音声调、补充权重、替换已有拼音、输出分隔符、输入编码（UTF-8 / GB18030）、词语列、拼音列、导出目录及同名文件策略。
- **词库管理**：支持搜索、类型筛选、按修改时间 / 文件大小 / 文件名排序及倒序；长按进入多选，可批量导出或删除。
- **处理报告**：统计处理行数、补拼音、替换辅助码及多音字行数；提供文件列表与多音字详情页，支持按文件名、词语、读音或行号搜索。
- **导出**：支持导出到 Download 或自定义 SAF 目录，同名文件可覆盖或自动加序号。单文件、单输出且无多音字的结果会自动导出，其余结果可在词库页手动导出。
- **会话恢复**：导入记录、待处理文件和报告保存在应用私有目录，重启后恢复，不会自动续跑原队列。
- **主题与交互**：浅色 / 深色主题，使用 Miuix 组件、开关、进度条、列表弹层和页面转场动效。

## 工程结构

```
app/src/main/java/kirakira/dokidoki/
├── MainActivity.kt          # 应用入口、ViewModel 创建
├── App.kt                   # Tab 导航、文件选择、导出、弹层宿主
├── data/
│   ├── Model.kt             # 数据模型与处理接口
│   ├── MainViewModel.kt     # 状态管理、处理队列、会话恢复
│   ├── JsProcessingEngine.kt# QuickJS 处理引擎
│   ├── AuxTables.kt         # 辅助码表加载
│   ├── ThemeStore.kt        # 主题与处理设置持久化
│   ├── ExportStore.kt       # 文件导出与 ZIP 输出
│   └── ExportNaming.kt      # 导出命名与类型处理
└── ui/
    ├── theme/DictTheme.kt   # 双主题与 MiuixTheme 映射
    ├── components/Common.kt # 通用组件
    └── screens/             # Home / Lib / Settings / Report / AmbiguityDetail
```

处理资源位于 `app/src/main/assets/`：

- `pinyin-pro.js`：pinyin-pro 3.27.0（MIT）
- `dict_engine.js`：离线词库处理脚本，包含 CRC / ZIP、Rime columns、TXT / TSV 与权重处理
- `aux_tables.tsv.deflate`：墨奇 / 自然码 / 小鹤 / 渊默码四套主码表
- `zi_weights.json.deflate`：多音字读音权重表
- `THIRD_PARTY_NOTICES.md`：第三方来源与许可证索引

## 构建

环境要求：JDK 17、Android SDK Platform 37.2、Build-Tools 37.0.0。在项目根目录创建 `local.properties` 并配置 `sdk.dir`。

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 签名与发布

Release 构建使用仓库中已保存的 Secrets（`KEYSTORE_BASE64`、`DICT_STORE_PASS`，`DICT_KEY_PASS` 可选）。工作流模板见 `ci/signed-apk.yml`：把它放到 `.github/workflows/signed-apk.yml` 后，可在 Actions 页面运行 `Release signed APK`。每次运行会把 minor 版本 +1（例如 2.0 → 2.1）并递增 versionCode，构建完成后写回 `version.properties`，并创建 GitHub Release（默认草稿），上传 `dict-<version>.apk` 与 `SHA256SUMS.txt`。

- `KEYSTORE_BASE64`：签名密钥库的 base64 内容
- `DICT_STORE_PASS`：密钥库密码
- `DICT_KEY_PASS`：密钥密码；未设置时使用 `DICT_STORE_PASS`
- `publish_release`：工作流输入，默认创建 GitHub Release；关闭后只上传 Actions artifact
- `draft`：工作流输入，默认创建草稿 Release
- `key_alias`：工作流输入，默认 `ksm`

Gradle 通过 `RELEASE_KEYSTORE_PATH`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` 环境变量读取签名配置。未提供这些环境变量时，Release 构建回退到 debug 签名，仅用于调试。

## 版本说明

- namespace 与 applicationId：`kirakira.dokidoki`
- versionName `2.0`，versionCode `34`（记录在 `version.properties`）；本地构建自动递增 versionCode，GitHub Actions 每次发布将 minor 版本 +1（2.0 → 2.1）并同步递增 versionCode
- Release 签名由 GitHub Actions 使用仓库 Secrets 完成。
- 从旧版 1.1.0（WebView 版本）升级时，因签名不同需先备份数据并卸载旧版。

## 依赖

- Kotlin 2.4.21 / Compose Multiplatform 1.13.0-rc01 / AGP 9.5.1
- Miuix 0.9.5-beta03（ui、glass、preference、icons、glass-icons）
- AndroidX Activity Compose 1.13.0、Lifecycle ViewModel Compose 2.11.0、DataStore Preferences 1.3.0、Graphics Path 1.1.0
- `app/libs/quickjs-android-0.9.2-16kb.aar`：基于 app.cash.quickjs 0.9.2 重新编译的 arm64 库（Apache-2.0；内嵌 QuickJS 2021-03-27，MIT）

## 实现说明

- 处理逻辑通过 `ProcessingEngine` 接口解耦：QuickJS 执行离线脚本，Kotlin 侧负责文件流、ZIP、进度回调、报告接收和结果文件管理。
- 词库正文按块处理，ZIP 中的非词库条目按原内容保留；批量导出时重名条目自动追加序号。
- QuickJS 与 graphics-path 原生库已按 16 KB 页面大小对齐，APK 仅打包 arm64-v8a 原生库。
- 第三方组件与许可证见 `app/src/main/assets/THIRD_PARTY_NOTICES.md`。

