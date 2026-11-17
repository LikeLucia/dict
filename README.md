# dict

离线词库处理工具：为 Rime YAML、TXT、TSV 和 ZIP 词库补拼音、生成或替换辅助码。处理在本机完成，网页和 Android 应用都不上传词库。

## 下载

在 [Releases](https://github.com/LikeLucia/dict/releases) 下载：

- `dict-1.0.6.apk`：Android 6.0+，Kotlin 宿主 + Android 系统 WebView。
- `SHA256SUMS.txt`：APK 的 SHA-256 校验值。

源码、测试和离线页面保存在本仓库中。

APK 使用包名 `local.ksm.aux`，显示名为 `dict`，版本 `1.0.6`。已安装 1.0.4/1.0.5 的用户可以直接覆盖更新；1.0.3 及更早版本使用旧签名，需要先保存数据后卸载再安装。系统 WebView 需要支持 `DecompressionStream('deflate-raw')`、Streams 和 GB18030 `TextDecoder`。

## 词库格式

无 `columns` 的 Rime 词库按默认 `text/code/weight` 列位逐行识别，允许同一文件混合以下形式：

- 只有词语；
- 词语 + 数字权重；
- 词语 + 已有编码；
- 词语 + 已有编码 + 权重。

已有拼音和权重会保留，缺编码的行才补拼音；辅助码是否替换由页面选项决定。声明了 `columns` 的文件继续按声明严格检查。未知字、混合非汉字和无法识别的编码会原样保留并出现在报告中。

## TXT/TSV 与空白分隔输入

纯文本自动模式会在所选拼音列及词语后的生成位置识别已有拼音：完整音节数与汉字数匹配时复用该列，空拼音列原位补音；未识别到已有拼音才在词语后新增列。数字权重和额外字段继续保留。多个候选列都像拼音时保留原行并报告，请确认列号或改用 Tab 明确分列。

“空白”模式支持以空格连接多音节，例如：

```text
你好 nǐ hǎo 120
```

解析时按词语字数将音节合并为一个逻辑拼音字段，也保留旧撇号形式兼容。只要输入包含 Tab，就优先按 Tab 分列，拼音内部的空格和空字段会保留，因此程序生成的 Tab 输出可以沿用“空白”设置再次导入。自动生成的新拼音仍以空格连接。

同一设置下已识别的输出再次处理不会新增拼音列；更换码表、替换选项或列设置可能改变结果。词语本身含空格、备注恰好与拼音形态相同等有歧义的数据应使用明确列边界并核对报告。页面已将无条件“重复处理保持幂等”改为沿用已识别拼音列的说明。

这些修复当前位于仓库 `main` 的网页版源码；已有 v1.0.6 Release APK 尚未重建。

## 从源码构建 APK

构建脚本使用 Kotlin 编译器、Android SDK 的 `android.jar`、`aapt`、`zipalign`、`apksigner` 和 R8，不依赖 Gradle。环境变量：

- `ANDROID_JAR`：Android 平台 `android.jar`，建议 API 23 或更高；
- `R8_JAR`：R8 jar，默认 `android/tools/r8.jar`；
- `JAVA_HOME`：JDK 路径；
- `KOTLIN_STDLIB`：Kotlin 标准库 jar，默认 `/usr/share/java/kotlin-stdlib.jar`；
- `KOTLIN_STDLIB_JDK7`：同版本 Kotlin JDK7 扩展 jar，默认与标准库同目录的 `kotlin-stdlib-jdk7.jar`。

首版验证环境为 JDK 17、Kotlin 1.3.31、Android API 23、R8 8.3.37、Node 24。测试需要支持 Web Streams 和 `deflate-raw` 的现代 Node。R8 可从 https://storage.googleapis.com/r8-releases/raw/8.3.37/r8.jar 下载到 `android/tools/r8.jar`。

准备依赖后运行：

```bash
node test-compat.cjs
node test-bridge.cjs
python3 android/build.py
```

上面的命令构建未签名的 `android/build/dict-1.0.6-unsigned.apk`。签名材料不要放进仓库；需要签名时，在环境变量中提供 `DICT_KEYSTORE`、`DICT_STORE_PASS`、`DICT_KEY_ALIAS`，可选 `DICT_KEY_PASS`，然后运行：

```bash
python3 android/build.py --keystore /path/to/release.keystore --alias dict
```

需要生成图标资源时，先安装 CairoSVG，然后在受控环境运行：

```bash
python3 -m pip install --target render-tools cairosvg
python3 android/build.py --regenerate-icons
```

源码仓库不包含签名私钥、R8 jar、构建目录、Python 依赖目录或 APK。`tests/mixed-layout.dict.yaml` 是公开的合成回归样例，不包含用户词库。

## 测试

`test-compat.cjs` 使用公开样例覆盖混合列、三种处理模式、五类码表、声调、替换开关、显式 `columns`、TXT/TSV、BOM、ZIP Store/Deflate 和 CRC。`test-bridge.cjs` 覆盖 Android 导出桥的分块传输、连续导出和失败中止。测试运行在 Node 中，不等同于真机 WebView 或系统文件选择器测试。

## 许可证与来源

项目中的页面保留了内嵌第三方来源和许可证文本：`pinyin-pro 3.27.0`（MIT）、`amzxyz/rime-wanxiang` 相关表（CC BY 4.0/相关 MIT 说明）和渊默码表来源说明。渊默码表页面标记为 `NOASSERTION`；发布者应在继续分发前自行核对相应数据的分发条件。项目源码本身未在本次工作区另行声明许可证。
