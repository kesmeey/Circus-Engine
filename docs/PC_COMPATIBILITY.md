# 四版本 PC 解释器：0.11.0-pc-dev

当前产物是可直接运行的 Windows x64 PC 解释器测试包。先验证 PC，之后再同步移动端。既有核心是没有 Android 依赖的 Java；本轮直接复用 Archive、CRX、XPCM、MES、VM 和 Save，不另写一套无法互相校验的解码器。原 Windows EXE 只作静态分析，未启动。

## 使用解包器

在项目目录执行（Python 3.10+，JDK 17+；JDK 的 bin 需在 PATH，或设置 JAVA_HOME）：

```powershell
.\dc_unpack.cmd "D:\gal\D.C.P.C【无密码】\D.C.P.C" build\unpack\my-dcpc
.\dc_unpack.cmd "D:\gal\D.C.II P.C" build\unpack\my-dc2pc
.\dc_unpack.cmd "D:\gal\DC3DD\AdvData\VOICE.DAT" build\unpack\my-voice
# 或使用本机已验证的 Python：
D:\ANACONDA\python.exe dc_unpack.py "D:\gal\DC2DM" build\unpack\my-dc2dm
D:\ANACONDA\python.exe tools/verify_unpacked.py build\unpack\my-dcpc
```

输出必须为空且不能位于原游戏目录中。工具不覆盖已有解包结果。`--raw-only` 只提取原始资源。单个包只能提取包内条目；完整图片、音频和脚本需要选择整部游戏目录。PC2 的 VOICE2.DAT 必须与 VOICE.DAT 相邻。

本机完整提取结果在 `build/unpack/`，不是仅用于启动的资源子集：

| 输入 | 原始资源条目（均哈希通过） | 成功格式转换 | 鉴赏索引匹配 | 转换诊断 |
|---|---:|---:|---:|---|
| DCPC | 23,674 | 23,670 | 385 / 385 | 1 份异常 MES；另 1 条文本保留字节并标记编码问题 |
| DC2PC | 56,569 | 56,562 | 1,336 / 1,336 | 无 |
| DC3DD | 35,559 | 35,556 | 789 / 789 | 无 |
| DC2DM | 28,857 | 28,853 | 579 / 579 | 无 |

合计 144,659 个原始资源条目。图片、音频、MES 结构转换单独计数；影片、NAMES 等原样复制的数据不算格式转换，所以两列不应相等。每次写入后均重新读取输出做哈希比对；DCPC、DC2PC 还分别运行了独立 verify，核对所有原始输入文件和全部导出文件。全量解包同时保留原始切片和转换结果，占用数十 GB，均位于项目 build 中，不在源码分发包内。

输出目录包括：

```text
output/
  bg/ character/ face/ system/ image/    PNG，保留原图像素
  cg/                                  按 NAMES 原版图片索引建立的鉴赏视图
  voice/ bgm/ se/                       可播放 WAV 或原始 Ogg 码流
  script/                              原 MES + 物理指令 JSONL
  movie/ other/                        未改变的媒体与其他数据
  raw/                                 每个条目的原始字节，包括压缩格式
  catalog/names.json                    原版五张索引表及未知字段
  catalog/gallery.json                  原版图片索引到 PNG 的对应关系
  manifest.jsonl                       条目名、原始名字节、源偏移、大小、哈希、导出位置
  sources.jsonl                        原始输入文件完整哈希
  summary.json                         原样提取与格式转换分别统计
  verification.json                    独立重验结果（运行 verify 后生成）
```

文件名加序号并编码，避免归档中的同名、大小写冲突和 `../` 路径。原始名和名字节保存在 manifest。CRXD/CRXJ 在原 CRM 上下文中合成，归档内别名仍各自保留。PNG 的原生 x/y 原点在 manifest，不强行把所有立绘裁成相同尺寸；24 位图片的洋红色透明键保留，运行时绘制时再应用。CG 不是按画面内容猜测，而是由 NAMES 图片表建立独立索引视图；源目录分类仍保留。

`raw_complete` 表示原始条目无损提取，`complete` 表示当前转换过程无错误。二者不同。MES 的字节记录与人类文本编码也分别处理：无法确定编码时保留 native_string_hex，text=null，不替换或伪造原文。

DCPC 的 `0422a_t01.mes` 已重新解析为 1,281 条记录：原引擎按有符号 opcode 取指，`81 83 00` 走三字节默认记录，并非需要修复的九字节指令。重新解析结果在 `build/pc-port/reparsed-0422a-t01`；上表全量提取报告保留最初那次转换的统计。`MEZA.mes` 另有一处不合法 GBK 文本，保留字节和编码诊断。两张系统图的额外全零区域已核对为 `height * (stride - width * bytesPerPixel)` 的分配对齐；只在旧 PC 图像模式中允许精确长度，不忽略任意尾部。

## 原生解释器与 REA

参考源码：`build/references/rea`，提交 `624ebb2a1e633ce14b6d6828eef50bf7d7db68ac`，MIT。执行工具使用 npm `rea-agents@6.8.0`，未复制 REA 源码进运行时，也未修改全局 MCP 注册。

实际分析工具为 REA 6.8.0、Ghidra 12.1.4、项目内 Node 24.22.1/JDK 21。临时数据库位于项目 `build/rea-temp`；被分析 EXE 不执行。REA 的反编译结果是恢复出的伪代码，不是原工程源码。

| 版本 | MES 加载函数 | 解释器入口 | opcode 取指 | switch 跳转 | 跳转表 | 项数 |
|---|---|---|---|---|---|---|
| DCPC 2063 | 00411670 | 004125B0 | 004125F4 | 00412654 | 00415754 | 99 |
| DC2PC 5769 | 0041A050 | 0041C530 | 0041C57B | 0041C640 | 0041F480 | 105 |

DCPC 加载函数读取 count、count*4 字节标签表，再把代码读入 009A077C 并检查 `63 20`。解释器从同一缓冲区取指，用 009D8A14 保存 PC，最终通过 `JMP [EAX*4+00415754]` 分发。PC2 对应缓冲区是 00AAD3B8，PC 是 00B0AB28，版本标记 `69 57`。这条数据关系将文件加载和 opcode switch 联系起来。

复现命令（已有项目内工具链）：

```powershell
python tools/rea_static.py decompile "D:\gal\D.C.P.C【无密码】\D.C.P.C\DCPCDVD.EXE" 0x41172D --provider ghidra --json
python tools/rea_static.py function "D:\gal\D.C.P.C【无密码】\D.C.P.C\DCPCDVD.EXE" 0x412654 --provider ghidra --json
python tools/build_vm_spec.py
```

`build/pc-port/rea-dcpc-loader.json`、`rea-dcpc-function.json`、`rea-dc2pc-loader.json`、`rea-dc2pc-function.json` 保留 evidence_id、源哈希、provider 版本及分析局限。规范生成器重新从锁定哈希的 PE 读取跳转表，并验证每个 PC1/2 handler 是 REA switch 的实际后继；语义来自单独审阅，不能仅靠函数名猜测。

完整表见 [VM_SPECIFICATION.md](VM_SPECIFICATION.md)，机器格式在 `profiles/vm-specification.json`。DD/DM 使用既有静态证据；它们没有冒称本轮重新通过 REA 验证。

## 运行测试包

路径：`build/distribution/dc-runtime-0.11.0-pc-dev-windows-x64/DC Runtime.cmd`。
双击选择游戏目录，或把游戏目录拖到该文件上；自带裁剪 Java 21 与 FFmpeg 7.1，不需要安装 Python、JDK 或 Android SDK。包内没有原游戏数据。

| 游戏 | 已验证目录 | 引擎版本 |
|---|---|---|
| DC3DD | `D:\gal\DC3DD` | dc3dd-a5a8 |
| DC2DM | `D:\gal\DC2DM` | dc2dm-9d72 |
| D.C.II P.C | `D:\gal\D.C.II P.C` | dc2pc-5769 |
| D.C.P.C | `D:\gal\D.C.P.C【无密码】\D.C.P.C` | dcpc-2063 |

执行原 `START`、标题和剧情 MES。原版按钮、图片、音频与文字直接来自选择的目录；没有生成每部作品专用的剧情包。PC 两代画布为 800×600，DD/DM 为 1024×576，窗口等比缩放。用户提供的 D.C. 樱花图片作为程序图标。

左键、Enter、空格推进，右键/Esc 打开原版菜单或返回；A 切换自动，按住 Ctrl 快进。F5/F9 是额外快速存读档，F11 切换全屏。原版保存/读取按钮也能使用，四部游戏均验证过保存、推进数句、读档后对白与 PC 完全恢复。存档写入程序包 `saves/<profile>-<START哈希>/`，影片临时文件写入 `cache/`；原目录只读。尚不导入 Windows 原版存档。

旧 PC 两代的回看是对话框上的上下箭头逐条浏览（原生 4189D0 / 422DA0），没有 LOG.MES；DD/DM 使用独立原版 Log 面板。退出回看恢复原句和指令位置。

## 验证结果与范围

2026-10-27 当前代码通过 47 项 Python 测试和 325 项 JVM 断言。这两组单元测试使用自造数据；下述四游戏回归只读本机资源，不随源码分发游戏脚本。

| 游戏 | 标题到结尾的路线策略数 | 每次对白数 | 合计覆盖的不同 MES |
|---|---:|---:|---:|
| DCPC | 18 | 2,777–7,820 | 457 |
| DC2PC | 14 | 5,823–16,685 | 506 |
| DC3DD | 14（七条路线各首项/末项） | 3,352–5,557 | 196 |
| DC2DM | 2 | 37,663–37,741 | 273 |

上表48次原脚本控制流策略已在0.10重新运行；最终报告位于 `build/desktop/routes-010-final/results.json`。不代表 48 个不同结局或所有选项组合。每次经过原标题、新游戏、正文、选择、结尾并回标题；过程中抽样渲染，并每千句保存/恢复。音视频完成在路线测试中被模拟。DCPC 地图点 8 的末项策略在 `0302b_h01` 会触发原作循环追问，测试明确记录一次改选首项后继续，不把循环当作成功或引擎故障。

DCPC 日历已按原生首四位数字和第五位时段字符解释，`0228_hr` 正确归零时段。此前在 MEZA 错误分支回标题的早期报告不计入上表；现在验收要求经过 STAFF 或 BADEND。

另行实际验证：四部原始 BGM 均经过 Java Sound 输出，四段原始 MPEG 均产生变化的画面和音频设备帧计数。Ogg 由 FFmpeg 解码，XPCM 使用共享解码器，PC1/2 组合语音支持混音。自造两秒 MPEG 的自然 EOF 与主动跳过均验证 VM 恢复并执行下一指令。机器输出检查不是人工逐句试听或音画同步认证。

0.10证据：`build/desktop/routes-010-final/`（48次路线策略）、`regression-010-{dd,dm,pc,pc2}`（标题至首个选择）、`verification-010/features-*`（姓名、回看、保存界面、输入、文字显示及旧存档迁移）、`verification-010/settings-*`（设置探索）、`verification-010/extras-*`（鉴赏）、`packaged-010/media-*`（最终包的实际媒体输出）。`media-eof` 保留此前自造影片自然EOF/跳过证据。汇总与各报告哈希在 `build/distribution/pc-runtime-verification-0.10.0.json`；失败和中间报告不作为通过证据。

## 0.9 原版鉴赏菜单更新

- DC3DD：读取 `CG_ALL.MES` / `MUSIC.MES` 的列表条目、文字、按钮和图层。CG 解锁标志仍由游戏进度决定；可以滚动、打开已解锁 CG、右键返回。未通过测试包全开 CG。
- DC3DD 音乐：88 首原生曲目索引，支持选曲、前后曲、单曲/连续/随机、收藏及全选/清空、只播收藏、音量与点击进度条。播放结束返回原 `musicchg` 标签；标题、标志和按钮继续由原 MES 决定。
- DC2PC 音乐：原 `music.MES` 的图集标题、双列条目、翻页/滚轮、选曲、停止、循环及播放结束回调。条目 ID 与可见行号分开处理，滚到后页不会误当作存档槽。
- 新增 `ScriptList` 是共用核心数据结构；PC2 与 DD 只保留各自的数值指令绑定。窗口滚轮和原滚动条轨道可操作列表。菜单快照保留条目与等待状态，旧版存档/设置继续可读。

专项测试：`DesktopExtrasAudit` 的 DD 19 项、PC2 10 项交互检查通过；实际音频设备测试另加 DD 2 项、PC2 1 项（3 秒输出及 DD 50% 定位）。对应报告在 `build/desktop/extras-{dd,pc2}-verified` 与 `extras-{dd,pc2}-media`。测试中的媒体结束回调可控触发，实际音频输出有独立帧计数；没有运行原 EXE。CG 查看测试只在独立测试状态中设置一个普通 CG 标志，不写用户进度。

0.9当时另外验证了原标题 → 正文 → 首个选择 → 继续20次等待及存档画面恢复，报告保留在 `build/desktop/regression-09-{pc,pc2,dd,dm}/result.json`。0.10已重跑路线策略和新增功能检查，证据见上文。

静态核对发现一个原版本差异：DD `MUSIC.MES` 随机按钮写 `100:26`，旧原生服务却读 `9:30`。兼容层以原脚本按钮所写模式为准，随机排列保留当前曲；不承诺随机顺序与原 EXE 相同。DD 音乐结束回调保持绑定、每超过 500ms 检查，PC2 回调触发后清空。这些版本差异已写入指令规范。

## 0.10 系统功能更新

- PC1姓名输入：读取原生14个按钮、假名/字母/符号页、汉字索引与确认框；支持四字姓/名、删除、移动、默认值、取消、确认和持久保存，原文本的 `$s/$n` 使用已确认姓名。字符表来自用户EXE的静态数据，不执行EXE。
- 存档：原生网格接入DD场景缩略图、PC1/2角色图标与caption；容器保存图片、日期、文字显示进度及六路音频位置，SHA-256检查损坏。兼容0.9解释器的原始快照；不声称兼容Windows原作存档。DD读档后刷新缩略图缓存。
- 声音：读档和退出系统菜单恢复相应BGM位置，角色音量/开关、系统语音例外、DM显式角色前缀、回看重播及PC2声像接入。PC1/2组合语音按原生声道上限播放；随机语音应用角色开关。
- 文本：手动/自动速度、立即显示、首击显示完整句、逐字显示进度保存；字体和定时器仍有平台差异。
- 控件：原滑条的连续拖动、离散档位、进度和滑块；按钮悬停声音与按下图像、移出取消点击、失焦取消按键；回看及鉴赏滚动条可拖动。原始全屏命令和窗口大小恢复传至PC窗口。
- DD回看：原头像表、预览勾选、语音重播、随悬停记录行出现的场景预览及原边框；预览坐标/比例来自406670/4066DF和精灵变换，按钮图集状态来自401110/405680。DM保留场景预览与回退。
- DD设置：首次运行和恢复默认使用40ED10/4A5100原表；94/95角色语音表与选项数量分离，设置保存/取消保留角色偏好。DM确认框选择器与记忆值也保留。

最终源码专项检查：DD 23项、DM 23项、PC1 31项、PC2 21项；四部均验证半句保存后的文字与像素相同、读档缓存、原保存网格和0.9迁移。DD/PC2鉴赏另有21/12项。设置探索访问39/72/8/11种页面布局，执行579/1677/116/159次动作，包含滑条端点和原回调；这是一组按布局去重的探索，不是全部设置组合穷举。正式包另外用自带Java执行功能与媒体检查。

## 0.11 连续画面捕获修复

- 修复效果9再次捕获时删除上一张截图、导致复合菜单丢失底图的问题。四版本共用不可变的 `UiCapture`；新截图引用捕获前的纹理，而不是再次引用自身。
- PC在捕获时合成并压缩一张ARGB图片。连续打开菜单不累计截图链；保存/恢复保留透明度和全部像素。不附带画面后端的VM保留逻辑依赖链，可稍后渲染；逻辑链上限256层、总载荷8MiB，超过明确报错，不删除旧内容。正常PC每次合成后深度为1。
- 逻辑截图记录捕获时的调色板、DM效果时钟与开关；绑定条件、动画帧、按下/悬停及滑条位置在捕获时固定。DD场景quad改为深拷贝，后续修改不再改变已发布帧。
- 解释器存档继续读取0.9和0.10。四部均由已发布0.10程序生成实际含截图的存档，再用0.11读取、保存和读取，画面逐像素相同。旧版本已经丢掉的更早截图不能凭空恢复。原Windows存档仍不支持。

0.11验证：四部各17项截图检查，包括原版标题与设置页、透明/文字叠层、逻辑/栅格两种嵌套路径、40次重复捕获、冻结状态、存档像素及0.10剧情存档迁移。0.10菜单截图迁移另有每部2项检查。此前的功能专项、设置探索（579+1677+116+159次动作）、DD/PC2鉴赏也全部重跑通过；并重跑四部各一条从标题到结尾返回标题的路线。全量48种策略属于0.10证据，不冒充0.11全部重跑。

源码证据：`build/desktop/verification-011/`、`capture-011-final/`、`migration-011/`。最终包的自带Java验证见 `build/desktop/packaged-011/`，汇总在 `build/distribution/pc-runtime-verification-0.11.0.json`。像素比较是解释器捕获前后的一致性，不表示已与原EXE逐屏校准。

## 尚未完成或未充分核验

本版是可玩的兼容性开发版，尚未达到“所有功能均与原作一致”。

- 转场、部分粒子、字体/注音排版、部分自动推进时序仍有近似。未穷举所有路线和所有选项组合；48次策略不代表48个不同结局。
- DD/DM保存页的全部原生说明文字、DM回看预览的位置与所有图像变体仍需校对；已有存档可以正常读写。
- 部分设置只有脚本变量和控件已接入，原宿主副作用尚未全部核定，例如DD部分窗口/选项标志、PC1少量装饰状态。输入法组合文字、真实窗口全屏切换仍缺可见窗口人工验收。
- 连续截图的底图丢失已修复；所有原生演出及特殊面板的最终画面仍需逐屏校对。未覆盖的文本控制仍明确报错，原生外部程序操作未实现。
- 鉴赏专项覆盖DD CG/音乐、PC2音乐；PC1 CG/音乐、PC2 CG和DM鉴赏沿用既有实现，未声称全部页面与解锁组合已逐项比对。
- 仅核验上表四套本地数据版本。其他汉化补丁、同系列其他作品及原Windows存档导入不在本次兼容承诺中。未知指令保留带脚本位置的错误，不静默跳过。

## 从源码构建

```powershell
python tools/build_desktop.py --java-home "E:/DevEco Studio/jbr"
$env:DC_FFMPEG="D:/ANACONDA/Lib/site-packages/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe"
.\dc_runtime.cmd "D:\gal\DC2DM"
python tools/package_desktop.py --jdk build/toolchain/jdk-21.0.9+10 --ffmpeg "$env:DC_FFMPEG"
python -m unittest discover -s tests -v
```

核心在 `runtime/core/src`，没有 Android 依赖；PC 前端在 `runtime/desktop/src`。复用现有 Java VM 是为了让资源、变量、跳转、存档和 Android 后续版本共用同一份经过验证的实现。本轮没有发布新 APK。
