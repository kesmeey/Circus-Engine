# 版本记录

本文件收录各版本的变更说明。开发构建产物在本机选定的构建目录，不随仓库分发；
带 `-dev` 的是开发签名包，可以覆盖安装，不要卸载或清除应用数据。

## 0.14.14-dc4ph-dev

新增 D.C.4 Plus Harmony ABB6 独立宿主，核对536份MES与静态原引擎。实现1280×720场景、背景缩放、长相机运动、粒子、文本宏、COMU、PCK动画、模糊、片尾、设置、原菜单及便携快照；各项状态由版本owner保存。实际验证与近似范围见[本作说明](DC4PH.md)，不外推其他D.C.4版本。

最终同源首选策略11,529等待/115脚本、末选策略在原密码题选择正确项后11,522等待/128脚本，均经ED及后续剧情回标题。原菜单存读档、实际立绘与正文窗口及冷恢复分别验证；修正PH `49 :n/:r` 被误用作旧版小说窗口模式的问题。片尾99:250播放结束查询是依据八份ED脚本的兼容推断，使用真实音频完成回调；静态提取引擎的getter差异与其他剩余边界在验证说明中保留。

构建工具支持 `--build-root`／`CIRCUS_BUILD_ROOT`，本次大型输出移至F盘，原游戏目录不写入。沿用既有开发签名，旧固定包保留。改名表单、原Windows槽及本作全CG进度导入暂未实现。

## 0.14.13-ty-music-display-dev（Windows／Android）

补齐To You的原生Music Mode服务9：读取所选EXE中的48项曲名表，并将曲名一次写入背景捕获；原版两份EXE曲名为CP932，已验证汉化EXE曲名为GBK。原菜单选曲、换曲、停止、返回及快照恢复后继续选曲分别检查，保留已有TYH3快照格式。

曲名采用所选EXE的原表；汉化EXE中少量标点未完整转换，保留该原始数据限制，不推断替换或修改游戏EXE。平台字体绘制不宣称与原GDI逐像素相同。

Scene Replay的58列表标题与汉化正文分开解码；原四份列表脚本共258个字段保持CP932，正文和其他文字规则保留。可通过原分页／选项进入用户报错的hyd_b_028_pst：GBK正文正常，手选日文仍按显式设置严格检查。Music Mode和回放标题均加入TY标准工作流，避免只验证正文入口。

[本轮验证](validation/ty-music-display-source-2026-11-09.json)记录原生证据、源码、安装包与实际手机检查；此前固定包和SC修复保留。

## 0.14.12-sc-display-dev（Windows／Android）

修复SC Scene Replay标题和日期乱码：原菜单51按钮文字保留CP932，汉化正文／选择项仍使用GBK，自由文字指令不统一改码。全53份原菜单脚本的2733项非ASCII按钮标题及自然翻页、快照恢复、回放进入正文分别验证。

SC显式背景资源缺失时报告具体路径；空背景、真实黑色图片、捕获画面与其他版本的可选图像行为保留。实机截图同句需要bg060_d_no.CRX，手机实际缺257个BG文件，共142785968字节，已仅补齐缺失文件并逐一校验；原有背景、应用记录与存档文件保持不变。这是素材修复，安装包不包含游戏素材。

源码、安装包和实机检查范围见[本轮验证](validation/sc-display-source-2026-11-09.json)。此前固定安装包保留。

## 0.14.11-ty-sc-fl-dev（Windows／Android）

新增DC2TY独立运行和Android CG/场景已读导入；四个主要故事按首选项策略经结尾回标题，原selector/scroll及便携Save/Load通过，未穷举全部分支。FL兼容2028字节旧版系统存档，本次实际源含597项CG、360项场景已读及16项通关标记；按native STOP结束索引以支持非截断汉化覆盖留下的旧尾，复现并修复手机原标题Load越界。该FL汉化使用CP932＋uif字符映射，不切换GBK。

SC真机报错的实际原因是缺少必需资源编号索引 `AdvData/DAT/names.dat`，已补齐匹配文件。另修SAF按列名读取元数据的鲁棒性问题，并更新缺资源提示、实际版本显示和START缓存测试；SAF修正不是这次真机缺文件的原因。SC仍只验证三类代表路线，截断Coco分支保留失败；DCDX两条长流程仍在服务11／滚动62处停止，本版不宣称二者全部路线通过。

[本轮验证](validation/ty-sc-fl-source-2026-11-08.json)记录来源、实际手机原文件、冻结源码与安装包；旧包保持不变。

## Android 0.14.10-dcdx-system-import-dev

新增DCDX `system.dat` 的CG与场景已读合并，使用已有原版进度导入入口；预览后备份已有手机进度，再原子写入，重复导入不重复写。保留设置、姓名和已有剧情槽，不导入globals、原版历史检查点或剧情槽。SC/FL进度导入继续保留，DCDX运行范围不变。

[本轮验证](validation/dcdx-system-import-source-2026-11-08.json)单独记录源码、实际原文件与Android服务检查；此前0.14.9 Windows/APK固定包保留。

## 0.14.9-dcdx-import-dev（Windows／Android）

新增独立DCDX宿主、两个自然剧情入口、原轮转及滑动菜单；完整路线、独立MINI和特殊消费者仍有边界。Android新增SC/FL系统进度合并，SC为CG/已读，FL另含已确认通关标记；SC/FL/DCDX原Windows剧情槽转换仍未实现，DCDX系统进度未导入。

准确源码/平台/打包范围见[本批验证](validation/dcdx-import-workflows-source-2026-11-08.json)。固定Windows0.14.8与旧APK均保留。

## Android

按时间倒序。每个版本的范围、验证方式和已知缺口见对应 `ANDROID_*.md`。

| 版本 | 安装包 | 内容 |
|---|---|---|
| 0.13.15 | `build/distribution/circus-runtime-0.13.15-android-dev.apk` | 修复 W.Y. `WY_2_1226_A1_SRR @0xE24C` 的 `Invalid resource path segment`，在原生资源读取层兼容重复分隔符。原章节、立绘像素、存档恢复与 P.P. 同类引用通过验证，详见 [说明](ANDROID_01315.md)。 |
| 0.13.14 | `build/distribution/circus-runtime-0.13.14-android-dev.apk` | 增加 W.Y.／P.P. 原版系统进度选文件导入，按各自字节／位图布局合并通关、CG、场景和音乐标记；验证备份、幂等、手机设置／剧情槽保留及原版鉴赏解锁。详见 [说明](ANDROID_01314.md)。 |
| 0.13.13 | `build/distribution/circus-runtime-0.13.13-android-dev.apk` | 补齐 W.Y.／P.P. 的 Android 入口，支持 `cn_Data` 中文 MES／CRX、GBK 正文／UI 文本和完整文件长度型 CRX，区分回看贴图；两部 Android 启动、正文、立绘、原版菜单存档／恢复、回看通过。详见 [说明](ANDROID_01313.md)。 |
| 0.13.12 | `build/android/circus-core-debug.apk` | 接入 D.C.III With You（`dc3wy-a09f`）与 D.C.III P.P.（`dc3pp-9872`）：按操作码重编号绑定语义表，场景图面布局（立绘／雾／樱花／擦除遮罩／对话框 UI 面）按版本区分。两部均验证启动、正文、选择、立绘、存读档像素往返。 |
| 0.13.11 | `build/android/circus-core-debug.apk` | 接入 D.C.II 2006 原版：按钮文字（`0x50`）、资源句柄（`0x24`）、闹钟音床的裸 WAV、缺素材降级。见 [0.13.11 说明](ANDROID_01311.md) |
| 0.13.10 | `build/distribution/circus-runtime-0.13.10-android-dev.apk` | DC3R Unity 菜单与存档优化：保存只更新当前槽位，菜单贴图共用文件句柄，缩小暂存预览图。长历史／102 槽保存由约 1.16 秒降至 49 毫秒；原画面与旧存档兼容。见 [说明](ANDROID_01310.md) |
| 0.13.9 | `build/distribution/circus-runtime-0.13.9-android-dev.apk` | 原版进度导入简化为「更多 → 导入原版进度（选文件）」，直接选 `system.dat` 或 DC3R 的 `global.dat`，不要求固定目录或原文件名。见 [说明](ANDROID_0139.md) |
| 0.13.8 | `build/distribution/circus-runtime-0.13.8-android-dev.apk` | DC3R 原版通关、鉴赏进度和剧情存档导入（默认保留手机同号存档）；修复 DC2PC Dacapo 近景立绘定位与顺序；减少 DC3R 背景动画逐帧位图分配。见 [说明](ANDROID_0138.md) |
| 0.13.7 | `build/distribution/circus-runtime-0.13.7-android-dev.apk` | 修复 DC3R 选序章后长时间白屏且无声；资源索引共用只读文件句柄。见 [说明](ANDROID_0137.md) |
| 0.13.6 | `build/distribution/circus-runtime-0.13.6-android-dev.apk` | 修复 DC2PC 选择支普通状态文字不显示，补回 DCPC／DC2PC／DC2DM 的原版文字阴影；四个 MES 版本减少启动脚本读取。见 [说明](ANDROID_0136.md) |
| 0.13.5 | `build/distribution/circus-runtime-0.13.5-android-dev.apk` | 原版系统进度导入扩展到 DCPC、DC2PC、DC2DM、DC3DD，按版本合并通关与鉴赏解锁。见 [说明](ANDROID_0135.md) |
| 0.13.4 | `build/distribution/circus-runtime-0.13.4-android-dev.apk` | 修复 DC2PC Dacapo 线切换长 BGM 时的内存溢出；原始 PCM／Ogg 分块写入播放缓存。见 [说明](ANDROID_0134.md) |
| 0.13.3 | `build/distribution/circus-runtime-0.13.3-android-dev.apk` | DC2PC 原版通关／CG／场景进度导入。见 [说明](ANDROID_0133.md) |
| 0.13.2 | `build/distribution/circus-runtime-0.13.2-android-dev.apk` | 修复 DC2PC 打开菜单、存档确认时的截图压缩停顿，保留原存档格式。见 [说明](ANDROID_0132.md) |
| 0.13.1 | `build/distribution/circus-runtime-0.13.1-android-dev.apk` | 双指点按＝鼠标右键，打开或返回原版菜单；单指推进、系统返回键、双指滚动保留。见 [说明](ANDROID_0131.md) |
| 0.13.0 | `build/distribution/circus-runtime-0.13.0-android-dev.apk` | DC3R Unity 初步兼容：直接读 `DC3_Data/StreamingAssets`，用独立的 CircusScr VM 执行原版标题、正文和系统脚本。见 [说明](ANDROID_0130.md) |
| 0.12.6 | `build/distribution/circus-runtime-0.12.6-android-dev.apk` | 影片音频与视频解码／绘制分离，修复画面卡顿导致的音频断续和 MP2 归一化失真。见 [说明](ANDROID_0126.md) |
| 0.12.5 | — | CG／Scene Jump 进度修复与旧存档恢复。见 [说明](ANDROID_0125.md) |
| 0.12.4 | — | 屏幕刷新调度。见 [说明](ANDROID_0124.md) |
| 0.12.3 | — | 遮罩转场优化。见 [说明](ANDROID_0123.md) |
| 0.12.2 | — | DC2DM 近景附属图修复。见 [说明](ANDROID_0122.md) |
| 0.12.1 | — | 快进菜单修复。见 [说明](ANDROID_0121.md) |
| 0.12 | — | 最近游戏列表、独立 EXE 数据选择、DD 再次启动、DM 片头路径。见 [说明](ANDROID_012.md) |

## PC

### 0.14.8-sc-fl-dev（2026-11-08）

新增SC和FL的独立版本绑定、变量布局及角色stage/live，修正PC2加载时已读缓存、默认设置与真实bank尺寸。SC补原版五格存读菜单和菜单返回的saved-stage前三字段；FL补15路独立片尾、GED长图裁切、20ms单poll及24位有序合成。两作原版Save→Load均验证同一句子和PC恢复，FL十五个故事入口的自然首选路线结果见[本批验证](validation/sc-fl-workflows-source-2026-11-08.json)。

SC三类代表路线回标题；本地Coco一份MES截断，无备份，对应分支仍明确失败。FL目录遗留的cg_etc旧6C69不被本作loader接受，运行时也不别名放行。PC2旧执行合同的剧情快照拒绝，独立PCP1/2进度迁移到PCP3并保留旧额外尾字节。旧0.14.7固定包保留；[新包收据](validation/windows-sc-fl-0.14.8.json)单独记录。

### 0.14.7-common-workflows-dev（2026-11-08）

修正DC2首次加载已读缓存，补齐常见角色淡化、平移和暂存坐标、正文后镜头/消息窗、闹钟存读上下文、六角色与通用片尾；真实杏/七夏路线分别经过片尾回标题。桌面新增普通ADV原Windows槽导入，遵守原LOAD十页的48个可见位置并拒绝覆盖已有槽。PC1补原:q候选策略、便携快速槽与失败重试。

本包使用rev8及DC2单独的首次游玩宿主修订。旧错误DC2便携剧情快照明确拒绝并保留，独立系统进度不因此失效。JVM/ART、实际流程、原存档与实包验证见[源码检查](validation/first-play-workflows-source-2026-11-08.json)、[包收据](validation/windows-common-workflows-0.14.7.json)；当前范围见[常见流程](COMMON_WORKFLOWS.md)。0.14.6固定包保留。


| 版本 | 内容 |
|---|---|
| 0.13.12-pc-dev | 与 Android 0.13.12 同核心，含 D.C.III With You 与 D.C.III P.P. 接入。产物 `build/distribution/dc-runtime-0.13.12-pc-dev-windows-x64/` |
| 0.13.11-pc-dev | 与 Android 0.13.11 同核心，含 D.C.II 2006 原版接入。产物 `build/distribution/dc-runtime-0.13.11-pc-dev-windows-x64/` |
| 0.11.0-pc-dev | 自包含 Windows x64 解释器测试包，双击选游戏目录，自带 JRE 与 FFmpeg。DCPC、DC2PC、DC2DM、DC3DD 四部均验证原版启动、正文、选择、存读档和到达结尾；0.10 的 48 次路线策略测试通过，0.11 另跑四部各一条完整路线，含实际音视频输出检查。修复连续系统截图丢失底图，并保存冻结的控件、颜色和动画状态（四部均通过嵌套截图、40 次重复捕获及存档后的逐像素比较）。见 [PC 兼容性说明](PC_COMPATIBILITY.md) |
| 0.10 | 补入 PC1 原版姓名编辑、存档缩略图／角色图标、音乐位置续播、逐字显示及显示进度存档、角色语音设置、原版滑条、回看预览与滚动条拖动；保留 0.9 的原脚本鉴赏列表。325 项 JVM 断言、47 项 Python 测试通过。见 [PC 兼容性说明](PC_COMPATIBILITY.md) |
| 0.6.0-core-dev | DC2DM 原版启动、正文、存读档，加入回看／语音重播／回退、CG／场景／音乐鉴赏和原版操作说明。见 [DC2DM 使用及验证说明](DC2DM_PLAYBACK.md) |
| 0.4.1 | DC3DD 原版 `START → WIN_SIZE → MAIN`、角色介绍和部分原版菜单接入。见 [DC3DD 原版系统进度](DC3DD_ORIGINAL_UI.md) |
| 0.3 | DC3DD 原版数据 Android 剧情播放器：七条路线、选择分支、正文与头像、场景演出、BGM／语音／音效、MPEG 影片、片尾、存档和后台恢复。见 [DC3DD_PLAYBACK.md](DC3DD_PLAYBACK.md) |
| 0.2 | 80 项 Java 断言在桌面 JVM 和 Android 15 ART 通过；验证外置字符映射和不支持的 tunnel 配置拒绝。Android UI 测试涵盖正文显式等待、隐式显示完成、存档字节一致、进程结束后读档。 |

### 0.2 的正文覆盖率审计（历史基线）

DC3DD 全量只读文本审计：214 份 MES，20,903 条姓名、31,397 条正文，51,852 条通过当时的纯文本子集；
448 条因尚未支持的 `@b`、`@t`、`@muf`、`@se` 控制停止。1,154 对外置汉化映射影响 29,620 条记录，CP932 解码失败为 0。
这是文本记录覆盖率，不是可玩率或原作宿主语义完成率。

```powershell
python tools/audit_dialogue.py D:\gal\DC3DD --java-home "E:\DevEco Studio\jbr"
```

审计报告在 `build/dialogue-audit/summary.json`，215 个输入文件的前后哈希一致。
该目录包含从本地配置生成的字符表，不进入源码包或 APK。
