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
