# 开源许可与来源说明

## 项目许可
当前工程未附独立的项目级许可证文件，未为整个 kirakira 应用指定统一的项目级许可证。以下声明分别适用于对应组件、代码或数据；第三方许可证不代表整个项目的许可证。

## 应用依赖与处理引擎
- Compose 与 AndroidX 依赖（Compose Activity、Core KTX、Lifecycle、DataStore、Graphics Path）：Apache License 2.0。Graphics Path 使用的本地原生库包含 16 KB RELRO 对齐补丁。
- Miuix 0.9.5-beta03：Apache License 2.0。
- COUI 2.1.0（coui-ui / coui-preference / coui-icons）：Apache License 2.0。
- QuickJS（2021-03-27）：QuickJS 引擎采用 MIT 许可证；app.cash/quickjs-java 包装采用 Apache License 2.0。本应用使用基于 0.9.2 包装重新编译、用于 16 KB 页面兼容的本地 AAR。
- pinyin-pro 3.27.0：MIT 许可证，Copyright (c) 2022-present zh-lx。

## 辅助码数据与多音字数据
- 墨奇、自然码及万象野鹤扩展码表来源于 rime-wanxiang 固定提交 47f0c48787ce08d6c37526a2612163a946de5668。原页面保留 CC BY 4.0 与相关墨奇 MIT 声明；万象野鹤扩展并非未经修改的官方小鹤码表。逐字选择来源顺序中的首个完整两字母码。
- 渊默辅助码表取自 LikeLucia/momo 的 aux_code.csv（提交 ee7d45e58ce1f02eca76bd3956c41eb72aeff55a，上游 yuanz-12/momo）。数据含 30 个字的兼容码（如 丰 sl,af,al、甩 kv,ov,yv），按上游顺序以逗号连接后内置。来源当前标记为 NOASSERTION；本工程未另行指定其许可证。
- kirakira 辅助码表取自 LikeLucia/kirakira 的 aux_code.csv（提交 8746a2bb53355bf1b775fe0874a3ef5993278ec7）。该码表基于墨奇码魔改（字根与单字拆分改动见其 README），共 43216 字，其中 7 个字保留兼容码。来源未声明许可证；本工程未另行指定其许可证。
- 多音字权重由用户提供的 zi.dict.yaml 提取，仅内置于应用中用于本地多音字排序；提供的数据未附独立许可声明。

## 来源链接
- pinyin-pro：https://github.com/zh-lx/pinyin-pro
- rime-wanxiang：https://github.com/amzxyz/rime-wanxiang/tree/47f0c48787ce08d6c37526a2612163a946de5668
- momo：https://github.com/LikeLucia/momo/tree/ee7d45e58ce1f02eca76bd3956c41eb72aeff55a
- kirakira：https://github.com/LikeLucia/kirakira/tree/8746a2bb53355bf1b775fe0874a3ef5993278ec7