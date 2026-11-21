# kirakira · Android（Kotlin + Compose + Miuix）

离线词库处理工具的原生 Android 实现，使用 Kotlin + Compose + Miuix，业务处理通过内嵌 QuickJS 接入离线词库处理脚本。支持导入、处理并导出 Rime YAML / TXT / TSV / ZIP 词库。

## 功能范围

- 四个平级 Tab：首页 / 词库 / 报告 / 设置
- 首页：动态统计、导入词库（系统 SAF 多选）、处理状态与按文件字节更新的进度卡（行数 / 预计总时长 / 百分比，空闲时显示待导入词库）、处理队列卡（当前文件“处理中”、其余“排队中”，默认显示 3 个，可展开查看全部，完成或取消后隐藏）、处理方式滑块、辅助码快捷行、最近使用（最多 3 个非“待处理”记录，点击跳转并定位词库）、底部开始处理 / 取消处理
- 词库：顶栏放大镜控制搜索框显隐（显示后自动聚焦并唤起输入法，再次点击隐藏并退出输入），类型筛选、按修改时间/文件大小/文件名排序及倒序开关、词库文件列表、状态徽章；长按进入多选，选择状态再次长按全选，可批量导出或删除
- 处理设置：主题模式下拉（跟随系统 / 浅色 / 深色）、处理方式单选（刷拼音和辅助码 / 仅刷拼音 / 仅刷辅助码 / 仅删辅助码）、辅助码类型（渊默码 / 自然码 / 小鹤 / 墨奇）、固定辅助码分隔符“;”、替换已有辅助码、替换已有拼音、补充权重、拼音声调、输出分隔符（Tab / 空白）、输入编码（UTF-8 / GB18030）、词语列 / 拼音列、导出路径（默认 Download）、同名文件策略（覆盖原文件 / 不覆盖并自动加序号）；辅助码类型和拼音声调向下展开，其余设置项向上展开，展开面板右边缘恢复为距卡片右侧 20dp，面板边缘向对应设置行内收 6dp
- 处理报告：显示处理行数、补拼音、替换辅助码和多音字行数。报告总览按导入记录及 ZIP 内词库条目展示文件列表与明细行数，同名导入记录分别保留；总览卡片的放大镜用于按文件名筛选，点击文件进入独立的多音字详情页，不再使用文件区块收起 / 展开方式。详情页顶栏放大镜控制搜索框显隐，打开后自动聚焦并唤起输入法；支持搜索词语、涉及的多音字、当前读音、其它可选读音和行号，纯数字按行号包含匹配，`L123` / `l123` 按行号精确匹配
- 导入与队列：单文件或批量导入后自动排队处理；同一轮队列统一使用启动时的处理设置。待处理或处理失败的词库可通过“开始处理”再次进入队列
- 自动导出：只有来自单文件导入的记录，在本轮队列仅成功处理一个文件、没有处理失败、仅产生一个输出文件，且未检出多音字行时，才会自动发起导出；含多音字、批量处理或手动启动处理的结果留在词库页手动导出
- 已取消或已处理完成的词库不会再次进入处理队列；需要重新处理时再次导入词库
- 主题、处理方式、辅助码类型、拼音声调、输出分隔符、输入编码、补充权重、替换已有辅助码、替换已有拼音、词语列/拼音列、词库排序方式/倒序、导出路径与同名文件策略通过 DataStore 持久化；深浅主题共用同一套布局，仅颜色 token 不同
- 会话恢复：导入记录、待处理输入文件、处理结果文件和报告保存到应用私有的 `noBackupFilesDir/kirakira-session` 目录；重新启动后恢复已保存的记录与可用结果。恢复记录不会自动续跑原处理队列，待处理或失败项可手动开始处理
- 文件与导出：使用系统 SAF 多选导入，支持 ZIP 条目处理及 UTF-8 / GB18030 词库正文解码；自动导出和词库页手动导出均优先使用配置的路径。Android 10+ 未设置自定义目录时直接写入 Download；自定义目录使用 SAF 持久授权；旧系统未配置自定义目录时回退系统保存窗口。词库正文、临时输出及批量 ZIP 使用文件流读写；报告明细另外全量保存在会话模型中
- 多音字统计与明细：生成拼音时按内置 `zi.dict.yaml` 权重表选择多音字的最高权重读音；识别到的至少两个汉字、含多音字的词条均计入统计，包括已有拼音及保留原拼音的行。详情显示最终行的当前读音和其它可选读音，点击行号或读音可复制。每次导入使用独立记录 ID，同名或同 URI 的重复导入分别保留；ZIP 内词库条目使用各自的详情分组。报告汇总当前保存的处理结果，删除记录时同步移除对应报告
- 多音字明细完整保留，不设每文件 60 条上限；JavaScript / Kotlin 桥接按每批最多 256 条读取，批次大小不限制总条数。普通处理诊断样本使用独立的 `samples` 字段，最多保留 60 条，与多音字详情分别管理
- 替换已有拼音默认关闭：已有拼音段原样保留，只有缺拼音的行才补拼音；开启后仅刷拼音和刷拼音和辅助码模式会按词语重新生成拼音并替换已有拼音段，已有辅助码后缀与权重保持不变。仅刷辅助码、仅删辅助码模式始终保留已有拼音
- 处理引擎通过 `ProcessingEngine` 接口解耦：当前使用内嵌 QuickJS 运行离线处理脚本与 pinyin-pro 3.27.0，Kotlin 侧负责文件流、ZIP、进度回调、报告接收和结果文件管理
- 16 KB 页面大小兼容：QuickJS 使用本地重新编译的 16 KB 对齐 AAR，graphics-path 原生库带 16 KB RELRO 补丁，APK 内所有 arm64 原生库均通过 LOAD/RELRO/ZIP 对齐检查
- Miuix 动效与交互：设置开关使用 Miuix Switch（弹簧滑块与触感反馈），处理进度使用 Miuix LinearProgressIndicator，常用点击区域使用 MiuixIndication；处理方式使用无按压灰遮罩的滑块式分段切换；四个主栏目使用 HorizontalPager 配合 miuix `springAnimateToPage` 弹簧切换（322.2 stiffness、32.31 阻尼分子、0.5 可见阈值），底栏点击驱动且禁用滑动；详情页使用 500ms Miuix 曲线双层转场（详情层全宽滑入/滑出，底层 25% 视差与 0.9 透明度，50% 黑色遮罩，移动前缘按设备圆角裁切），进度在 graphicsLayer/绘制阶段读取；可展开菜单使用 Miuix ListPopup 的 spring 缩放、clip-reveal 和遮罩动画
- 处理实现：引擎按需加载，导入后后台预热；正文按 8192 行分块处理，使用自缓冲行读取器 / BufferedOutputStream，ZIP 非词库条目的内容通过文件流保留；多音字详情按批次跨桥接接收后完整保存，随会话持久化。启动配置合并为单个 ThemeSnapshot 数据流，设置页与列表减少重复计算和重组
- 导出策略：同名文件默认“不覆盖，自动加序号”，也可选择覆盖模式；导出采用临时文件写入流程，写完后执行目标名称设置及旧文件清理。Download 同名检测针对根目录；自定义 SAF 目录会区分文件和文件夹，避免将文件夹作为覆盖对象

## 工程结构

```
app/src/main/java/kirakira/dokidoki/
├── MainActivity.kt          # edge-to-edge 入口、ViewModel 创建
├── App.kt                   # Tab 导航、文件选择、导出、下拉弹层宿主
├── data/
│   ├── Model.kt             # ProcessOptions / ProcessResult / ProcessingEngine / 数据模型
│   ├── MainViewModel.kt     # StateFlow、自动处理队列、会话保存/恢复、批量导出与删除
│   ├── JsProcessingEngine.kt# QuickJS 处理引擎（使用 assets 内的原处理脚本）
│   ├── AuxTables.kt         # 四套辅助码表加载
│   ├── ThemeStore.kt        # DataStore 主题与处理设置持久化
│   ├── ExportStore.kt       # Download / SAF 目录导出与 ZIP 输出
│   └── ExportNaming.kt      # 导出文件名、冲突序号与 MIME 类型
└── ui/
    ├── theme/DictTheme.kt   # 双主题 token + MiuixTheme 映射
    ├── components/Common.kt # 卡片、顶栏、底栏、按钮、下拉弹层等
    └── screens/             # Home / Lib / Settings / Report / AmbiguityDetail 页面
```

处理资源位于 `app/src/main/assets/`：

- `pinyin-pro.js`：pinyin-pro 3.27.0（MIT）
- `dict_engine.js`：原页面词库处理逻辑的离线版本，含 CRC/ZIP、Rime columns、TXT/TSV 与权重处理
- `aux_tables.tsv.deflate`：墨奇 / 自然码 / 小鹤 / 渊默码四套主码表
- `zi_weights.json.deflate`：从用户提供 `zi.dict.yaml` 提取的多音字字符读音权重表
- `THIRD_PARTY_NOTICES.md`：第三方来源与许可证索引

## 构建

环境要求：JDK 17（编译）、Android SDK Platform 37.2、Build-Tools 37.0.0。

`local.properties` 指向本机 SDK：

```properties
sdk.dir=/root/android-sdk
```

构建命令：

```bash
cd /root/workspace/dict-android
/tmp/gradle-9.9.1/gradle-9.9.1/bin/gradle :app:assembleDebug
# 或使用项目自带 wrapper（首次运行会下载 Gradle 9.9.1）
# ./gradlew :app:assembleDebug
```

产物：

```
app/build/outputs/apk/debug/app-debug.apk
```

当前 APK 使用现有本地测试签名，namespace 与 applicationId 均为 `kirakira.dokidoki`，版本 `2.0`（versionCode 33），应用图标使用 https://github.com/LikeLucia/dict 项目的 `cimu_foreground` 矢量图与 `ic_launcher_105` 自适应/单色资源；APK 仅打包 `arm64-v8a` 原生库。QuickJS 字符串通道改为复用单实例，支持连续处理多个词库文件。若手机已安装旧版 1.1.0（WebView 版本），因签名不同需先导出旧数据并卸载旧版再安装；升级正式版时请保持同一签名。

## 预览与验收

- 定稿图：`/sdcard/Download/DSH-Go/dict-miuix-ui/`
- 本工程用同一套 Compose UI 代码生成桌面验收图：`/root/workspace/dict-render/previews/`
- 验收重点：四屏比例、底栏胶囊、顶栏布局、双主题颜色、首页滑块式处理方式、设置下拉方向与右边缘位置、报告文件列表、独立多音字详情页及两级搜索、报告统计间距
- 说明：桌面预览用同一 Compose 布局渲染，仅用来检查排版；真正 Android 运行仍以 APK 为准

## 依赖

- Kotlin 2.4.21 / Compose Multiplatform 1.13.0-rc01 / AGP 9.5.1
- `top.yukonga.miuix.kmp:miuix-ui:0.9.5-beta03`
- `miuix-glass`、`miuix-preference`、`miuix-icons`、`miuix-glass-icons`
- 关于页设计参考：COUI 2.1.0（本地 Compose 自绘，不引入 COUI 运行时依赖）
- AndroidX Activity Compose 1.13.0、Lifecycle ViewModel Compose 2.11.0、DataStore Preferences 1.3.0、Graphics Path 1.1.0
- `app/libs/quickjs-android-0.9.2-16kb.aar`（基于 app.cash.quickjs 0.9.2 源码重新编译 arm64 库，Apache-2.0，内嵌 QuickJS 2021-03-27，MIT；源码归档内含该 AAR）

## 实现说明

- QuickJS 运行离线词库处理脚本；补充权重、列位置、声调、输出分隔符、辅助码及拼音替换选项通过 `__start` 传入，Kotlin 侧将词库正文分块提交，并接收输出行和多音字报告
- 文件选择使用 Android 系统 SAF；自动导出遵循上文的单文件、单输出、无多音字等条件，并优先写入配置路径；其他处理结果可在词库页长按进入多选后手动导出
- 导出一个处理结果时保留其原文件名；选中多个处理结果导出时打包为 `dict-batch.zip`，ZIP 中重名输出条目自动追加序号。ZIP 输入对应的处理结果仍为 ZIP，内部词库条目处理后写回，非词库条目保留内容
- Miuix 玻璃模糊在 API < 33 设备按库的 fallback 降级为半透明/纯色面板
- 本地预览 Release APK 使用现有 debug 证书签名；真机安装和系统文件窗口仍需单独验收
