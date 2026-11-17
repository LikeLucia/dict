# Changelog

## v1.0.6

首个 GitHub 发布版本，应用显示名称为 dict。

- Kotlin Android 宿主，使用 Android 系统 WebView。
- 支持 Rime YAML、TXT、TSV 和经典 ZIP 的离线处理。
- 支持补拼音、生成或替换辅助码。
- 兼容无 columns 的 Rime 默认 text/code/weight 布局以及混合短行。
- 保留已有拼音读音和权重，提供替换已有辅助码开关。
- 系统文件多选和系统保存窗口，Blob 分块导出。
- 浅色状态栏、深色状态栏图标。
- 蓝底白字词库图标，包含 PNG、自适应和单色资源。

## 安装说明

包名 local.ksm.aux，versionCode 7。v1.0.4/v1.0.5 可覆盖更新；更早版本使用不同签名，需保存未导出结果后卸载再安装。

## 验证范围

公开合成样例 17 组 Node 回归和导出桥模拟测试通过，公开源码可编译。测试结果不代表所有 Android 设备的 WebView、安装器或系统文件窗口已完成真机验证。
