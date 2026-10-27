# Circus Engine：D.C.1／2／3 通用运行时研究

源码仓库：[kesmeey/Circus-Engine](https://github.com/kesmeey/Circus-Engine)。项目目录及命令仍使用 `circus-runtime` / `dc_runtime` 命名。
仓库包含资源解包工具、VM 规范、Java 核心、PC/Android 前端、测试及构建脚本；不包含游戏数据、采集副本、签名密钥或构建工具链。下文 `build/` 和 `analysis/` 路径指本机构建/验证生成物，不是 GitHub 中已上传的文件。APK 和 PC 包需按文档构建。

**2026-10-27 当前验收顺序：资源解包器 → VM 规范 → PC 解释器 → 移动端。**
Android 0.12.2 安装包：`build/distribution/circus-runtime-0.12.2-android-dev.apk`，修复 DC2DM 近景人物与帽子等附属图的尺寸、位置和色调不匹配，覆盖安装方法与验证范围见 [Android 0.12.2 说明](docs/ANDROID_0122.md)。保留 [0.12.1 的快进菜单修复](docs/ANDROID_0121.md)，以及最近游戏列表、独立 EXE 数据选择、DD 再次启动和 DM 片头路径修复，配置方式见 [Android 0.12 说明](docs/ANDROID_012.md)。
新增 `dc_unpack.py/.cmd`：读取整部游戏或单个 DAT/PCK/CRM，导出原始字节、PNG、WAV/Ogg、MES 与反汇编，并生成逐条哈希清单。
新增 [D.C. Engine VM Specification](docs/VM_SPECIFICATION.md) 和 `profiles/vm-specification.json`，包含四个版本的 485 项原生分发表；PC1/2 已用 REA + Ghidra 交叉核对。未知语义不当成 NOP。
PC 测试包：`build/distribution/dc-runtime-0.11.0-pc-dev-windows-x64/DC Runtime.cmd`，双击选择原游戏目录，自带 Java 和 FFmpeg。四部本地版本均已验证原版启动、正文、选择、存读档和到达结尾；0.10 的 48 次路线策略测试通过，0.11 另重跑四部各一条完整路线；已有实际音视频输出检查。
0.11 修复连续系统截图丢失底图，并保存冻结的控件、颜色和动画状态；四部均通过嵌套截图、40次重复捕获及存档后的逐像素比较。兼容0.9/0.10解释器存档。
0.10 补入 PC1 原版姓名编辑、存档缩略图/角色图标、音乐位置续播、逐字显示及显示进度存档、角色语音设置、原版滑条、回看预览与滚动条拖动；继续保留 0.9 的原脚本鉴赏列表。325 项 JVM 断言和 47 项 Python 测试通过。部分演出、字体和系统设置副作用仍未完整还原，不能称为所有功能完成。确切范围、运行方法和证据见 [PC 兼容性说明](docs/PC_COMPATIBILITY.md)。`old_source_circus_dcpc` 不作为输入或参考。下方 Android 0.3–0.6 记录为历史基线；当前 APK 以 Android 0.12.2 文档为准。

**项目目标已重新明确：实现类似用户所说的 krkr 模拟器使用体验的 DC1／2／3 引擎兼容层。**
选择游戏目录后，应执行该版本的原版启动、标题、菜单、正文与返回流程，使用原版界面资源和脚本定义的按钮行为。
历史开发版 **0.6.0-core-dev** 在 DC2DM 原版启动、正文和存读档基础上，加入回看／语音重播／回退、CG／场景／音乐鉴赏，以及原版操作说明。两种选项策略在桌面核心运行到片尾并返回标题；该版本 Android 检查与限制见 [DC2DM 使用及验证说明](docs/DC2DM_PLAYBACK.md)。这仍不是完整兼容全系列的正式版，部分设置和演出仍有缺口。

DC3DD 保留 0.4.1 的原版 `START → WIN_SIZE → MAIN`、角色介绍和部分原版菜单接入，见 [DC3DD 原版系统进度](docs/DC3DD_ORIGINAL_UI.md)。APK 图标继续使用用户提供的 D.C. 樱花图片。

DM 与 DD 使用独立宿主绑定，共用资源接口、VM 和 Android 基础设施；[DM 语义记录](docs/DC2DM_RUNTIME_SEMANTICS.md) 保留早期静态证据，最新接入情况以 0.6 文档为准。

0.3 剧情验证基线已接入 **DC3DD 原版数据的 Android 剧情播放器**：七条路线、选择分支、正文与头像、场景演出、BGM／语音／音效、MPEG 影片、片尾、存档和后台恢复。
七条路线的首项／末项选择策略都已通过桌面核心和 Android ART 流程检查；影片与场景绘制另有 Android 验证。
该基线不等于原 Windows 版本所有功能与演出已完整兼容。
安装步骤、证据和具体边界见 [DC3DD_PLAYBACK.md](docs/DC3DD_PLAYBACK.md)；
原剧情基线的 56 个运行时绑定与可复用层见 [DC3DD_RUNTIME_SEMANTICS.md](docs/DC3DD_RUNTIME_SEMANTICS.md)。
范围只包含 D.C.1、D.C.2、D.C.3 及相关 FD，不包含 D.C.4／5；其他作品的格式支持不等于已可运行。

游戏输入保持只读，生成物位于本项目的 `analysis/` 和 `build/`。
用户已分别授权 DC3DD、DC2DM 隔离副本的限时动态采集，现均已结束；
该许可不由工具自动扩大到其他作品。采集证据与原 EXE 的静态证据分别保存，
不发布游戏数据、启动程序、原文脚本或采集副本。

## Android 核心

构建结果：`build/android/circus-core-debug.apk`，Android 8.0 / API 26 起，开发签名。
支持 arm64-v8a、armeabi-v7a、x86_64。原游戏资源不在 APK 中。
打开后选择包含 `AdvData` 的四个已接入版本之一的完整根目录，再点“启动游戏”，由原版脚本进入标题。
当前验证范围见 Android 0.12.2 说明；不把脚本解析成功或模拟流程结束视为所有系统功能已完成。
SAF 仅申请读取权限；存档和解码缓存写入应用私有目录。
导入时读取外置 `uif_config.json` 的字符替换表，不加载 DLL 或程序；
开启 tunnel decoder 的配置会明确拒绝。原 Windows 存档目前不导入。

核心代码在 `runtime/core/src/org/circus/core/`，没有 Android 依赖；
Android 宿主在 `runtime/android/`。新增作品应增加版本绑定及宿主行为，复用资源、
变量、跳转和保存基础设施，不需要按每部作品重做一套 APK。

已实现的模块：

- `GameVersion` / `MesFormats`：26 份格式表的集合交集识别，混合版本拒绝导入。
- `MesScript` / `Profiles`：DD、WY、DM 的独立取指绑定、字节精确标签与边界检查。
- `MesVm` / `Variables` / `Dc2State`：版本独立的语义、DM 连续内存与位标志、有限步调度、条件和状态快照；DM 通过 Dc2Host／Dc2Session 执行原版跨脚本与系统调用。
- `DialogueHost` / `NativeText` / `TextCodec`：DD 普通正文子集、两种显示等待、带代次的显示回调、外置字符映射；未实现的演出控制停止执行。
- `GameResources` / `CircusResources`：统一的只读资源接口，显式脚本版本，散文件、PCK/CRM、字节精确条目和有限索引缓存。
- `Crx`：CRXG、CRXD/CRXJ、图像预测和差分合成；`Xpcm`：mode 0 PCM / mode 5 Ogg，以及 mode 1/3 整数频谱解码。
- `NamesCatalog`：DD/DM 已知的五表结构，附加数值原样保留。
- Android Canvas、触摸、MediaPlayer、PL_MPEG / AudioTrack、SAF 目录读取和应用私有存档。

`circus-demo-v1` 使用独立版本号和明确标记的测试指令。测试场景的点击/选择/延时
指令不冒充 DD/WY 的同号指令。未实现指令或特殊变量访问会停止并报告脚本和 PC。

本机可复现构建与测试（需要已有 Android SDK、NDK 29.0.14206865、JDK 17、API 35 模拟器镜像）：

```powershell
D:\ANACONDA\python.exe tools/build_android.py --java-home "E:\DevEco Studio\jbr"
D:\ANACONDA\python.exe tools/android_smoke.py
```

构建脚本不下载 SDK、不依赖 Gradle；SDK/JDK 路径可通过参数指定。
构建报告和签名校验在 `build/android/`，模拟器证据在 `build/android-smoke/`。
完整 Python 静态工具用法见下文。

## 已有能力

- 外置、可扩展的 26 份 MES 格式配置；参考配置不等于游戏兼容承诺。
- offset1／offset2 两种 MES 头结构和五种参数布局的严格解析。
- 整套脚本版本交集识别，避免标签区字节碰巧匹配其他版本号。
- PCK（包括本地两作 VOICE.DAT）和 CRM 的索引读取、原样提取与 SHA-256 验证。
- CRM 空索引槽、共享偏移、CRXD／CRXJ 依赖的保留与检查。
- Python 的 CRX／CRXD／XPCM 元数据检查；Java 核心另有实际解码实现。
- DC2DM／DC3DD 的 NAMES.DAT 五张资源名表读取，保留未知数值字段。
- 文件清单、逐脚本指令统计、候选脚本引用、汉化字符映射摘要。
- 可选 PE 静态工具（依赖 `pefile`），读取头部、节区和导入表。
- DC3DD 19 / DC3WY 16 项带原生证据的 MES 语义映射，区分完整子规则与 partial 宿主行为。
- 复合参数、条件 companion、求和列表、UI 附属字符串识别与潜在控制流边检查。
- SHA-256 锁定的 x86 静态证据导出工具（`pefile` + `capstone`），从不加载或运行 EXE。

## 使用

核心工具只依赖 Python 3.10+ 标准库，在项目目录执行：

```powershell
python -m unittest discover -s tests -v
python -m circus_tools scan D:\gal\DC3DD --out analysis\dc3dd --extract
python -m circus_tools scan D:\gal\DC2DM --out analysis\dc2dm --extract
python -m circus_tools scan D:\gal\KTLEP_zh\source --out analysis\ktlep
python -m circus_tools verify analysis\dc3dd
python -m circus_tools verify analysis\dc2dm
python -m circus_tools verify analysis\ktlep
python -m circus_tools names D:\gal\DC3DD\AdvData\GRP\NAMES.DAT --out analysis\dc3dd\names
python -m circus_tools names D:\gal\DC2DM\AdvData\GRP\NAMES.DAT --out analysis\dc2dm\names
python -m circus_tools mes D:\gal\DC3DD\AdvData\MES\main.mes --profile dc3dd-a5a8 --out analysis\dc3dd\disassembly\main
python tools/build_report.py
python -m circus_tools semantics D:\gal\DC3DD\AdvData\MES --profile dc3dd-a5a8 --out analysis\semantics\dc3dd
python tools/capture_mes_evidence.py D:\gal\DC3DD\DC3DD.EXE --profile dc3dd-a5a8 --out analysis\evidence\dc3dd
```

`scan` 不加 `--extract` 时只读取、解析和记录哈希。不要用不同选项覆盖同一个
已完成的扫描目录：重扫会重建清单，已有解包文件会保留。需要不同扫描模式时，
选择新的输出目录。

解包文件名采用 `条目序号__编码后的原名称`，避免重复名称、大小写冲突和路径
越界。原始名称字节、封包路径、原始偏移、长度和 SHA-256 均保存在
`archive-entries.jsonl`。CRM 差分关系必须依靠这些元数据还原；不能把散文件
当作已经合成好的最终图片。

## 输出

每个扫描目录包含：

| 文件／目录 | 用途 |
|---|---|
| `summary.json` | 统计、错误和分析边界 |
| `inventory.json` | 原文件大小与 SHA-256；排除存档与备份目录 |
| `archives.json` | 封包类型、索引槽与条目统计 |
| `archive-entries.jsonl` | 所有条目的名称、偏移、长度、哈希、资源头信息 |
| `unpacked/` | 解出的原始载荷，不自动转换或播放 |
| `scripts.json` | 每个 MES 的版本和指令分布 |
| `candidate-script-references.json` | 字符串与脚本名的匹配，尚不是已证明的控制流 |
| `crm-links.json` | 差分图像的封包内偏移依赖 |
| `verification.json` | 对原文件和输出文件重新计算哈希后的验证结果 |
| `names/names.json` | 单独解析的资源名目录，含未知字段原始值 |
| `disassembly/` | 选定脚本的结构反汇编，保留原始字节而不解释执行含义 |

第一轮拆包报告见 [docs/STATIC_ANALYSIS.md](docs/STATIC_ANALYSIS.md)，最新语义表与复现方式见
[docs/MES_SEMANTICS.md](docs/MES_SEMANTICS.md)，架构边界见
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。`analysis/` 不纳入版本控制。
工具来源和许可证见 [THIRD_PARTY.md](THIRD_PARTY.md)。

续轮新增 [DC3DD 状态配置](profiles/dc3dd-state.json)：bank 99 的 83 个显式条目、
普通变量历史规则、UI 绑定和脚本切换证据。`semantics` 输出变量访问说明及延迟 UI 目标，
初次静态表中有 223 个动作路由待确认；此数字不包含后续 `Dc3Host` 的运行时绑定。34 项测试通过，DD/WY 的 458 份脚本重新通过当前静态检查；
这些结果仍不代表完整引擎兼容。

## 当前限制

当前支持 DC3DD 七条剧情路线，详细验证与未完成项目见 [播放说明](docs/DC3DD_PLAYBACK.md)。
DC2DM 的标题、正文、回看和鉴赏接入范围见上文；DC3DD 的原版系统适配仍有独立缺口，不能把 DM 的新增功能当成 DD 已验证的功能。
部分演出时序仍需原版画面对照，ARM 实体设备尚未验证；不宣称所有功能完整复刻。
XPCM mode 1/3 已接入整数频谱解码；原 Windows 存档导入尚未实现。MPEG-1／MP2 影片已在 Android 上验证。
自己的 CAS2 / CVS1 存档不是原 Windows 格式。模拟器已验证播放轨道，未验证扬声器听感。
独立 `DialogueHost` 的严格纯文本接口仍可用于早期测试；实际播放使用 `Dc3Host`、
`Dc3Performance` 与 Canvas，实现逐字显示、分页和正文内演出控制。
KTLEP 当前只有脚本样本，没有对其完整资源包作出结论。
NAMES.DAT 布局仅在这两份 DC2DM／DC3DD 样本中验证过。
F 盘新增样本中，DC2PC 的两份脚本未通过严格 framing，DCPC 尚未识别对应配置；不能声称全系列已兼容。

## 0.2 正文验证（历史基线）

80 项 Java 断言在桌面 JVM 和 Android 15 ART 通过；另验证 Android 外置字符映射和不支持的 tunnel 配置拒绝。
Android UI 测试涵盖正文显式等待、隐式显示完成、存档字节一致、进程结束后读档。

DC3DD 全量只读文本审计：214 份 MES，20,903 条姓名、31,397 条正文，
51,852 条通过当前纯文本子集；448 条因尚未支持的 `@b`、`@t`、`@muf`、`@se` 控制停止。
1,154 对外置汉化映射影响 29,620 条记录，CP932 解码失败为 0。
这是文本记录覆盖率，不是可玩率或原作宿主语义完成率。

```powershell
python tools/audit_dialogue.py D:\gal\DC3DD --java-home "E:\DevEco Studio\jbr"
```

审计报告在 `build/dialogue-audit/summary.json`，215 个输入文件的前后哈希一致。
此目录包含从本地配置生成的字符表，不进入源码包或 APK。
