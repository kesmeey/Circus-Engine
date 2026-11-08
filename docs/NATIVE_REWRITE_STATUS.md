# 非 Unity 引擎重写状态

Windows／Android 本批版本为 **0.14.13-ty-music-display-dev**，执行契约为rev8；旧固定包保留。本轮[源码与交付验证](validation/ty-music-display-source-2026-11-09.json)单独记录。

TY补齐原生服务9的曲名背景处理：以ordinary3:5索引所选EXE内48项260字节曲名表，汉化EXE按GBK、两原版EXE按CP932解码；平台一次绘入背景并沿用UiCapture持久化，旧TYH3格式保留，恢复后继续换曲的callback也保留。Scene Replay的58列表标题另按CP932处理；正文和其他UI指令仍保留各自编码规则。Music Mode与回放标题已进入标准TY工作流，不以四故事正文通过替代这些菜单检查。

SC鉴赏标题原来与正文共用GBK，已将51按钮文字按本作保留的原CP932解码；4B/4C正文、选择项与60自由文字继续各自规则。原菜单53份脚本2733个非ASCII字段及翻页／快照／回放正文有独立检查。手机同句黑背景实查为缺失257份BG素材，已仅补缺并核验新旧文件哈希；运行时只增加显式背景缺失路径诊断，没有改变合法黑场或背景坐标。手机验证范围见本轮记录。

本批新增TY独立5F69绑定，1848字节系统布局、PC_WIDE变量、原两阶段卡片选择及17ms背景feedback均有独立证据；四类主要故事按首选项策略经结尾回标题，各类原Save/Load、新Host与未来执行均过，未穷举全部分支。NativeProgress仅合并TY的CG/场景标记。

FL另新增用户实际2028字节旧档兼容，避免四字节偏移造成静默错位；该实际源含597项CG、360项场景已读及16项通关标记。手机汉化脚本旧尾按native STOP隔离，原标题Load同故障已复现并修复；这份汉化采用CP932＋uif字符映射，不切换GBK。

SC手机实际缺少 `AdvData/DAT/names.dat`，已补齐一份匹配文件；原版和本运行时都要求该资源编号索引。SAF按列名读取元数据的鲁棒性修正独立验证，不是这次真机缺文件的原因。

DCDX system.dat新增导入以本作403D40读取与版本检查、41BC60变量读取和原MES门槛为依据；前2136字节为系统表，后最多4096字节检查点不合并。Android入口复用原有预览、备份及原子写入，准确范围和源码／APK检查见[本轮验证](validation/dcdx-system-import-source-2026-11-08.json)。

本批新增DCDX独立7769绑定：原EXE只读，105项分发表、加载/变量、1024×576画布、stage/live角色与65移动、50/53原轮转菜单和12五行滑动菜单分别核对。两个自然剧情入口各20等待及独立像素审计通过；不把它当作SC/FL别名。首选长流程在进入med（此前观察3558等待）后的service11、以及进入dx2_1222e_c1_cm1（此前观察3940等待）后的62背景滚动明确失败；不是整线通过。第五演员、voice/music独立浏览器及MINI仍有明确边界，详见[本批验证](validation/dcdx-import-workflows-source-2026-11-08.json)。

Android新增SC/FL原版系统进度合并：SC仅CG和已读，FL另含已确认通关索引；设置、姓名与已有便携槽保留。原Windows剧情槽转换SC/FL/DCDX均未实现，DCDX在0.14.10新增原system.dat的CG/场景已读合并，不导入globals、设置、姓名、voice开关和原版检查点尾部。原版菜单Save→Load与原文件格式互通分开记录。

用户追加SC/FL作为测试集后，先记录未改动0.14.7的失败，再按两作各自EXE重写绑定、变量布局、角色移动和原版存读菜单。SC为PC_BYTE，FL为PC_WIDE，99:251均读取加载时的已读字节；PC2同类旧错误同步修正。三族bank尺寸、默认值、stage/live及旧进度尾字节分别保存，不将它们当作同一小版本。[本批统一验证](validation/sc-fl-workflows-source-2026-11-08.json)、[SC](validation/sc-holdout-source-2026-11-08.json)、[FL](validation/fl-holdout-source-2026-11-08.json)和[原生合同](validation/pc2-sc-fl-native-contract-2026-11-08.json)记录确切范围。

SC三类代表路线已回标题，Coco Geki原菜单进入162次正文等待后，在转入本地截断的drm_0621_b1_kko.mes时明确失败；用户无备份，未伪造缺失内容。FL十五个原菜单故事的自然首选路线均经片尾回标题；0D/255分别保存背景准备与角色脏矩形反馈，片尾使用独立15路表、20ms单poll、原24位有序16级累积合成和GED长图裁切，不借用PC2片尾。原清黑假设的片尾像素审计已明确作废，修正后验证保留front和恢复后的未来画面。目录中的cg_etc.mes旧6C69标记仍不被执行；启动识别只看START，另提供严格整目录检查，每份实际加载的MES仍验证自己的标记。

PC2新增首次游玩宿主身份修订，旧错误剧情执行快照拒绝读取并保留文件。PCP3仅用于独立进度，按三族真实尺寸导入旧PCP1/2并保留额外原尾部，不能用进度迁移推断旧剧情执行历史也可修复。SC/FL未审服务、非空第五演员像素消费等边界仍明确保留，不宣称整个引擎已完成。

本轮主要补缺见[完整验证记录](validation/first-play-workflows-source-2026-11-08.json)。DC2的99:251现读取加载时缓存的已读字节，普通脚本切换不再提前标记目标已读。修正前的短路线记录不能证明首次角色路线通过。修正后六种原地图选择策略均回标题：杏线13,617个观察等待、158脚本经ed_anz/GEND；七夏线14,640/161经ed_nnk/GEND，通关字节与100:59均由原MES写入。

DC2现有独立闹钟/存档候选D2CS、原版Save/Load和槽0自动/快速保存；普通ADV原Windows槽可在桌面转换到48个原LOAD菜单可见位置（2..4、8..12，依此至72..76），拒绝覆盖已有槽。原五槽已实际导入、翻十页、点选8/9并恢复，四个角色暂存坐标独立恢复。旧Windows回看、NVL、选择/未完成批次及物理音频游标不在转换范围。

常见32换演员、60暂存XY、61平移现有独立状态与绘制前台，处理原步长、40/10ms门控、到达后延后一poll和快照续行。ADV正文后的5E准备/反馈与FULL空消息窗已接通，32位窗口区分加权覆盖和非加权逐像素alpha，按原整数规则取整。10/11六角色片尾、12通用片尾及50ms更新、鼠标重玩跳过、末帧回标题已验证。40只清快进锁存，保留物理Ctrl；22接共享CRT随机数与99:2。当前938份MES中服务8/30/31和雨kind2均零调用，相关未审分支继续明确拒绝。

PC1新增原:q强制替换候选及便携槽1写入/失败重试，保留原声音与等待；现样本没有可见:q按钮，:qs/:qsave/:ql为便携动作别名，F5/F9仍是独立会话快照。PC1原Windows8004槽互通仍未完成。

DC2快照身份另加首次游玩宿主修订，仅该族从旧执行身份升级。旧错误执行状态可能已经跳过未读剧情，不能靠PCH尾解码修复，因此明确拒绝旧DC2便携剧情快照并保留文件；其他六族身份和独立系统进度规则不变。PCH17保存新增owner，DCM3保存独立有序消息窗；嵌套旧codec解码能力不表示可迁移旧错误剧情执行历史。
用户已将目标调整为约80%的主要功能与绝大多数常见游戏流程。接下来优先剧情、画面、音频、选择、菜单和便携存读档的完整可用链；成本很高的个别版本、罕见平台状态和边缘行为可明确跳过。这里的80%是范围与投入目标，尚无能证明“引擎已复刻80%”的统一分母，不用断言数、已命名opcode数或启动成功率代替它。具体范围见[当前交付目标](ENGINE_SCOPE.md)。
**整个引擎尚未忠实复刻完成。** 当前是在原生证据基础上重写的兼容运行时；能读取素材、绑定opcode、启动或跑到片尾，都不能证明剩余行为正确。
用户暂时排除的DC3XR/Unity不在本轮范围；已有Unity前端没有改写。

## 当前实现与原生证据

PC1已补独立producer的便携候选和P1CS会话封装，1B、flag0自动保存、原生菜单事件分别捕获，原版Save/Load菜单和剧情自动槽使用该候选。保存时只补原生规定的7个普通字节，预览、caption、音频游标来自producer现场；物理写失败保留旧槽与pending，F5/F9保留候选侧车。192MiB下首/末策略7,077/1,091句至坏结局，两处原剧情选择调整后音梦10,358句、211脚本经STAFF回标题。核心候选46项及平台60项分别验证，不代替原Windows8004槽文件互通。

DC2新增版本独立的6角色地图：24份原地图MES选点/取消/内联rMenu、265项流程检查，学校/岛16帧的7,680,000像素与独立BGR/BGRA合成参考通过（Java2D整数alpha舍入差不超过1）。地图变量使用5:10/20/21，PCH12保存中途状态并兼容PCH7。此前首/末策略在meza_nin_20/meza_obk_20的system50处停止；本轮已接独立borrowed-context与存读消费链，相关真实闹钟与原Windows剧情槽已检查。该历史路线还存在99:251已读缓存误读，不能用作完整首次角色路线证明。最新全冻和平台范围见[本批验证记录](validation/common-gap-source-2026-11-07.json)。

本轮使用用户指定的 `D:\gal\rea`，结合当前PE机器码、REA/Ghidra、真实MES与隔离的整数/x87参考解释。
原Windows EXE没有执行，原游戏目录没有写入。解释器只在私有字节数组里解释选定算术块，不提供Windows服务或原进程内存。
跨版本共用规则只引用实际比对过的跨度，不能由同属CIRCUS推断整份引擎相同。

W.Y.后续补丁采用该版本自己的9路线/PAN、`ED.PCK`、112000/97000ms时长、独立period、左裁20与横坐标；实际9组资源/像素91项、核心102项通过，192MiB下代表策略2,737句、29脚本后回标题。D.D.56组状态与保存序列签名不变。片尾末尾输入和逐update淡出仍使用兼容时间模型，不声称完整原生调度等价。W.Y.阶段整冻 `producer-integration-20261107T105329947102Z` 的核心 **1,108,669** 项及七入口20等待均通过，源码变化为空；该阶段源码和ART检查见[W.Y.验证记录](validation/wy-credits-source-2026-11-07.json)。各作品可用范围见[常见流程](COMMON_WORKFLOWS.md)。

此前执行链阶段的核心与七组启动冻结为 `build/native-rewrite/producer-integration-20261107T103006702626Z/manifest.json`：192MiB堆下 **1,108,567** 项核心断言通过，七个非Unity入口各通过20次文本等待，未排除Java家族，源码变化为空。公共记录见[本阶段验证与源码哈希](validation/majority-source-2026-11-07.json)。它包含实际目标地址的RawDispatch及文本、脚本、UI、常用图形、双角色和音频handler接线；原版取指局部缓冲保持实时Source，未用虚构地址代替。整个WinMain/WndProc生产入口仍未全部替换，不能把这些接线测试当作原版全路线证明。

Runtime wire现为P1RY，保存MM邻接字节和显式调用者局部帧；恢复先检查PE阶段及分派图身份，再发布各owner。连续音频存储已有93个跨owner DWORD位置的真实JNI检查，另外3个跨未知CRT范围的位置仍拒绝。可写容器标签、流准备顺序、typed文件路径和语音链已有独立专项冻结；匹配当前C/header的八个Windows JNI目标均通过MSVC `/W4 /WX` 构建。静态mode3语音的原解码器等价性限制见[范围说明](ENGINE_SCOPE.md)。

长流程检查修复了原48项图片LRU在192MiB下的内存耗尽：现在按raw像素、不同ARGB面及构造临时空间计费，预算为堆上限的1/8且不超过32MiB。相同192MiB下，DC2DM首/末策略分别37,741/37,663句、DC2PC分别5,496/5,115句、DC3DD分别3,352/3,354句，均回到标题。这些审计使用控制选择、时间和媒体完成，未声称原版实时帧序列等价。

P.P.新增原生system206的五项好感度选择和99:250的常规BGM完成查询，原机20组程序、2,790步对照通过；已越过原来的5,900句阻点。后续已补齐独立scene.effect4粒子和system210滚动片尾，192MiB下首/末选项策略分别15,006/13,362句、109/106脚本后经MED返回标题。粒子使用原整数状态/四词随机算法，计时仍为便携60Hz，RNG独立且双native对象映射外侧两层；滚动片尾保留19页、600行裁切、32位运算与延后一poll结束。核心冻结1,109,277项及七个入口20等待通过，具体源码与平台范围见[P.P.本批验证](validation/pp-workflows-source-2026-11-07.json)。以上为P.P.阶段记录；后续PC1便携槽与DC2地图见下述本批记录，Windows原生槽文件互通仍是独立缺口。

上一阶段的826,261项冻结及Android ART记录保留在 [native-source-2026-11-07.json](validation/native-source-2026-11-07.json)，对应提交`4f6abc4`。既有0.14.3固定包未被覆盖，本批另建0.14.4 Windows包；新源码的Android检查与确切APK哈希单独记录。

`Pc1StagedRenderTests` 另有 11 项恢复检查，真实 GDI/render 分阶段重建有 7 项检查；它们保留同一 Pool/Compositor/FontMemory，不能重放初始化替代物理恢复。DirectSound stage31 冻结通过 **12,373** 项专项检查，覆盖 raw source、实际 voice/SE/大音乐文件和实际 ANSI 文件句柄恢复；跨独立 MM backing、静态 decoder/COM 图等边界仍未完成。公共复查入口为 `tools/check_native_core.py` 和 `tools/build_windows_native.py`，构建冻结实际输入并记录哈希。当前源码阶段没有替换已有发布包。

Window stage45 的最终独立冻结另通过 **360** 项真实 Win32 检查，其中39项验证新物理 DC/DIB/DDB、非默认 DC 属性与复杂 clip、跨 FontSet 选择关系、同一 Pool DIB/orphan payload、abort和显式同 HWND 移交；321项为旧窗口、消息、输入与启动回归。P1W1 只保存所支持的物理图形状态，HWND、消息队列、菜单、timer及完整窗口状态仍需明确外部接纳，不代表整个 WinMain 或跨进程恢复已经完成。

本轮新增的完整原生调用链：

- Pc1UiServices、Pc1ScriptFlow 把输入/历史/弹窗、原生脚本加载与系统返回连接到同一 Runtime、音频、图像池和转场，40项连接/原始前缓冲/身份专项检查通过。源文件身份接纳只更新P1CB元数据，不回填代码或检查点；原始加载与后续分派不依赖成功解析整个文件。
- Runtime 的 BITMAPINFOHEADER94C2E0..94C308 仅有一个40B owner，原Win32写入和P1RX保存恢复借同一份字节。启动 front 消费当前global HWND，启动timer消费CreateWindow留下的局部HWND；实际GetMessage收到的timer HWND已对照。窗口/GDI物理重建仍有明确边界，尚未完成整个WinMain。
- 41B6C0与三个内部helper已有155组原程序、95,466步和470项检查；它是安装键校验。可写置换表从当前.data读取，CRT大小写/分类仍是必须补齐的原生locale服务。
- 411670 的三次有序读取与短读/失败前缀、4115A0 标签跳转、412160/1C 系统调用/返回、419280 输入、418600 历史语音及418A70等待分派均已有实际指令对照。运行时新增稳定的脚本/输入/等待/系统字节 owner，当前 wire 为 P1RX，原有便携桌面主图仍未完全替换。
- 418F20/402130 与44/1A弹窗生产链已有51组实际程序、1,154,840步、6,013项检查；404E70/404EE0(1,1)保存元数据准备已有74组实际程序、11,637步、8,303项检查。后者不表示404EE0所有selector已完成。
- 418930与4176F0补充两组实时源变更对照：当前代码源与可写PE ruby字面量在回调后重新读取。禁止用冻结字符串替代原指针，也不补零未知局部尾部。
- DirectSound stage28实际流解码、压缩LZ与FILE借用图已交回，9,641项专项检查通过；PDB5/PDS6物理状态必须配套同次冻结DLL，不能混用旧后端。COM接口图和窗口/GDI全局句柄接线仍在开发。
- 三个固定帧缓冲区9CDD28/9D3F00/9D5E48各8004B均已实证为PE零填充BSS。候选/工作/系统帧在原生地址总线、slot重建和旧元数据投影中使用同一份存储，恢复时保留owner身份，P1RR不再重复序列化帧内容。证据位于 `build/native-rewrite/pc1-fixed-frame-owners-stage29/pe-initial-bytes.json` 和上述冻结检查。
- 字体请求、缓存、TEXTMETRICA、游标和8B PE字面量归Pc1FontMemory，原GDI正常空格required=0但blackbox非0的路径已修正。PC1 256个首字节×12/28字体及DC2相同分支已逐字节对照；完整合同见 `build/native-rewrite/pc1-font-startup-stage24/font-native-contract.json`。生产主程序仍需使用拆开的5字体创建/404230路径。
- 图像池元数据、名称、指针关联和像素只存一次，物理HeapAlloc/DIB使用直接视图。1698个真实CRX、27,144项检查及完整失败/孤立分配/释放检查见 `build/native-rewrite/pc1-image-pool-integration-stage25/owned-source-handoff.json`；同DIB绘制到整个DDB的实际共享链另见 `build/native-rewrite/pc1-shared-front-stage27/attempts/20261106T023312646534Z/result.json`。Guest32地址空间正在实现。
- DirectSound初始化和共享流字节已与同一个主HWND的timer2/MCI物理服务验证，最终8,693项专项检查见 `build/native-rewrite/pc1-directsound-staged-stage26/handoff-contract.json`。完整VM+物理音频保存必须在snapshotBoundary内；负mix、compressed streaming1/3和活动MCI/遗留COM恢复继续实现。
- 全线程GetMessageA(NULL,0,0)、TranslateMessage、DispatchMessage及线程消息/WM_QUIT已接真实Win32，64位HWND/参数不截成原生DWORD；Startup局部帧codec升级3。20项MSG所有权+15项实际消息、8项实际capture、20项分阶段startup，以及叠加旧冻结的214,671项回归通过，见 `build/native-rewrite/pc1-windows-message-stage30/latest-validation.json`。GetMessage -1仍按原分支推进，未知初始MSG不伪造。首次错误假设及SDK不含_MAC lPrivate的编译反例保留在attempts；完整WinMain平台服务尚未整体连接。

- `41A0B0` 转场保存原生局部阶段，按实际 GetTickCount/405710 顺序推进；最终进度16仍等待40ms，消息泵中断保留 busy/loop/临时图像，不伪装成功清理。158组实际指令程序39,827步、2,208项检查通过，另有整个持久像素缓冲的真实核心图形图验证。工作图像使用401050分配与live `WORK GBUF` 名称，不复制当前画面。图像池失败部分发布与真实heap生命周期继续重写。
- PE效果参数、COS指针、帧数/数量表及默认设置模板均从同一可变地址读取；只读页保持只读，CRX头和资源字面量借既有owner。3,645组实际指令程序97,517步、新增18,223项检查通过；402890仅在实际fallback点读取当前模板，包括read/close回调后的修改。
- NAMES的41ABF0、表/count/locale/字面量及音频/文本/CG视图共用原始字节，65组实际指令程序6,126步；真实NAMES另通过2,460项检查。原生图形图已改用该raw文本视图，旧桌面兼容VM的解析目录适配仍未全部撤除。41AEA0场景标签完整副作用另以11组原指令和整个8004B frame摘要验证；负文本索引已允许读取已知相邻system行。
- WinMain/405800、registry/screens/display/storage启动控制及可恢复局部帧已有266组原指令程序32,601步、1,141项检查。Runtime新增对应共享字节owner；物理句柄恢复必须由外部owner明确adopt，不能从保存的DWORD制造HWND/指针。
- 自建隐藏Win32窗口已按Class/Create/Show/Update/406ED0拆开。新增20项实际检查证明首次黑屏WM_PAINT发生时尚无DIB/DC，401000绑定回调发生在真实DIB创建后、黑色DDB填充前；旧31窗口+25paint+18效果直绘检查也通过。正常GDI与效果直绘已验证；加速显示、完整字体/音频初始化和真实主循环仍需继续接入。新证据为 `build/native-rewrite/pc1-staged-window-stage24/attempts/20261105T180327061791Z/result.json`。

当前进行中的 PC1 改动：

- 8004 字节的原生 frame 成为普通变量、bank90、日志、原始 PC、段落模式、逻辑音乐及 SE 请求的同一份存储。bank90 不再截断为 100 字节；允许落在该 frame 内的有符号别名，超出已知内存范围仍停止。
- 系统返回先把当前普通变量写进调用者备份，再复制完整 frame。存档候选也保存全部原始字节；未命名字段和陈旧字符串尾部不丢弃。当前 P1RW/P1C6（运行时使用 bound metadata）/P1F5/P1S3/P1M2/P1J1v3 与旧开发包状态不兼容。
- PC1 取指先更新原生 PC，handler 的变量别名写入可改变下一条取指位置，指令尾不再覆盖它。初始加载在 VM 构造期完成；raw36 的调用者场景标记发生在目标加载前。
- 原生 RGB24 / inverse-alpha 绘制、CRX 压缩 1 的逐行解码、持久图像池与 invalidation 服务已有独立原指令对照。高级层、原生字体栅格、平台绘制接入仍在进行。
- 41A970/41AAD0 文件服务按原生失败和副作用顺序实现，12 组原指令分支参考、145 项专项断言通过。它们要求完整 41A620 重建接口，尚未因此开放旧版槽位加载。
- 53/5C 绘制及 4F/50/51、54/55/56/57/5F、17/19 等按钮与列表生产器已接入真实 VM。模板、按钮、列表及其原始字符串指针直接使用已绑定的原生字节，handler 自行推进 PC；便携 UI 对象仍是过渡显示投影，原生消费端正在连接。
- 角色当前坐标与 frame 共用字节；60 只暂存目标坐标。CRT 随机数使用显式启动 tick，并向原生 43E100 地址提供同一份状态；文本计时器消耗随机数的完整 WndProc 调度仍在接入。
- 40E5A0/40E870 的主目录、备用目录和绝对路径规则，以及 41CDE0/41CE20 的扩展名规则，已有 638 组原指令对照（177,636 条解释步骤、3,205 项检查）。Windows JNI 的实际 GetFileAttributesA 与 ACP 转换另通过 12 项检查。三块路径全局区及可写 PE 字面量独立保存；启动配置与光盘发现尚未连接到这些全局区。证据见 `build/native-rewrite/pc1-paths-stage14/native-path-contract.json`。
- 按钮与列表消费端已有 62 组原指令对照、4,212 项检查；418930/4176F0 直接字节文本绘制已有 815 组对照、13,013 项检查；417D20/WndProc 文本调度已有 3,782 项检查。这些模块保留必须连接的 underlay、GDI、音频和消息队列接口；字形/ruby/字典共享内存及平台主循环仍在整合。
- 布局的 glyph/ruby/字典字节已使用同一处原生内存；raw glyphCount 和字典指针保留原始 DWORD，不能用可读 DTO 长度覆盖。99:0/2/3/4/19/22/243/247/252/253/254 已改读写对应原生字段，19 只写不读。43D410 的历史选择与 Runtime 查询共用字节，不再另存一份整数。
- 40C1C0/40C0A0 已完整复制列表页及固定容量的陈旧尾部：36 组实际机器指令程序、3,420 条解释步骤逐字节摘要一致。新增 23,276 字节备份 BSS；9553B8 等与图像槽重叠的字段借用原 owner。26/1C 已调用这对 raw 消费器，旧 ListState 只保留显示投影，不再覆盖原生选择值/页偏移。
- PC1 特效已有 110 组原指令程序、1,481,404 条解释步骤、2,428 项检查，粒子、雨、双矩形银行、风和 atlas 句柄使用 Runtime 同一份原始字节。特效与通用重绘、窗口 paint 的完整服务链仍在连接。
- 实际 Win32 HWND、GetTickCount、SetTimer/KillTimer、逐条同步消息泵和 DIB/DDB 初始化已通过 27 项隐藏自有窗口检查；原版 EXE 未运行。正常 WM_PAINT 和效果直绘已有独立实际验证；加速显示分支以及音频与主 HWND 的完整共同调度尚未验收。当前桌面的 GetCursorPos 返回系统错误 5，记录为不可用，没有伪造坐标。

冻结源码检查见 `build/native-rewrite/producer-integration-20261105T162958197598Z/manifest.json`：核心 162,957 项通过，七个非 Unity 入口各通过 20 次文本等待；每次检查保存实际编译的源码及 SHA，后续源码变化另行记录。Python 91 项检查在此前一轮通过。此类启动检查没有比较原版音视频输出，不代表整条路线或整个引擎验收。

Android ART 对冻结的 P1R8/P1E2 源码通过 129,472 项核心断言及 Canvas/存档附加检查，报告在 `build/native-rewrite/android-source-20261105T135113735752Z/art-validation.json`；该内部验证 APK 不包含随后 P1R9 的 resource-literal owner，也没有替换固定分发包。

PC1 的独立原生服务图已把同一处 Runtime 字节、图像池、字形缓存、文字布局、底图恢复、41AF50 局部重绘、416B80 故事控件及特效相互连接。412260/412320 的原始记录复制另有 38 组机器程序和 115 项检查。417D20 通过新服务适配器直接产生同一份画面的 glyph 像素；完整主程序的启动、WndProc、音频和存档外部 owner 仍在接入，旧桌面前端尚未整体替换。

实际自建隐藏 HWND/GDI 验证已通过整个“文字→真实 InvalidateRect→WM_PAINT”和“特效→局部修复→GetDC/BitBlt/ReleaseDC”链路，完整 1.44 MB DDB 像素回读一致，共 12 项组合检查。证据在 `build/native-rewrite/pc1-windows-services-stage22/actual-validation.json`；该检查使用明确的合成脚本/素材，没有运行原 EXE，也不等于真实游戏路线验收。

专用 PC1 CRX 解码已有 512 项检查，保留当前 PE 的 BGR24/ABGR32 逆透明度、真实分配得到的行尾 padding、跨调用 LZ 环与短读陈旧字节。全部 1,698 个原始 CRX（856 RGB24、842 ABGR32）另通过 11,836 项实际 ANSI 文件及堆分配检查，原文件 SHA 未变；`Pc1GraphicsFiles` 借用原生目录和 CG 表，尚未把通用 `Crx.plain` 的跨版本成功当作当前引擎准入。原生 NAMES 表的完整初始化与借用视图已独立实现；主程序仍在迁移到这些 raw owner。

这些是进行中的源码事实；整套设置、绘制和加载链仍在接入，不能将独立模块验证当作完整引擎验收。

- DC2：粒子kind1/3/4/5/6的50槽状态、MSVCRT RNG、40ms计时与隔次更新、两层绘制、透明键和定点混色已经接入；30组当前PE更新参考一致。dirty绘制准备、延期正文/voice与PCH7/DCM2/DCP1中途保存也已接入。真实kind6启动阻点已解决。
- PC1：typed26/1C与普通right/Esc、工具栏菜单分别实现；系统返回保留现场普通变量，不恢复整个旧VM。独立1B候选、persist4选择、overlay路径、list及历史游标owner已分开。1B/25/26/5C的尾串按rawNUL读取，任意marker不改变其归属。新增字节展开、四块known/stale缓冲、geometry、glyph/ruby/dictionary布局日志和原生历史游标；07/09及1C按当前变量重新读取raw脚本。
- DD：56独立modal、34构造中按钮checkbox、原生typed sprite转换、bound float/x87几何、公共输入尾部、保留popup冻结及DD41/DDM2中途状态已经接入；不是普通55的别名。
- 前阶段取指、原始PC、版本marker准入、变量布局、短STOP、PP音频/持久区规则等继续保留，细节见执行核心契约与忠实度审计。

以上均有明确适用边界，不能视为各作品所有宿主效果、绘制、音频和时序均已完成。

## 静态统计：0.14.1 固定基线

报告为 `build/native-rewrite/engine-coverage-stage2.json`。语义表SHA256为
`0f0714a9ed95aedf9e08b2e5425730b1255cbc775daeeea270d3e33ae5168bc8`；统计期间catalog与相关源码均未变化。
原rev4的 `engine-coverage-current.json` 保留为历史报告，不能代替本批。

七个运行入口共4,567份脚本、5,543,886条物理记录和4,371,660个线性指令组。
PC1新增raw ownership共692项（1B：661、25：2、26：29），比rev4少29个独立线性候选，因为26的尾串现在正确归属于handler。
这是静态、按文件顺序的分组，不执行变量或分支，也不表示实际执行次数；原生跳转还可能进入负载或指令内部。

下表partial/未声明为“观察到的opcode种数 / 线性次数”。“未声明”不能自动视为已验证；partial也不代表全部子模式成功。

| profile | 脚本 | 物理记录 | 线性指令组 | unknown/unmodelled线性次数 | 已绑定partial | 已绑定但状态未声明 |
|---|---:|---:|---:|---:|---:|---:|
| `dcpc-2063` | 960 | 495,342 | 445,820 | 40 | 8 / 117,749 | 65 / 328,028 |
| `dc2pc-5769` | 1,557 | 2,646,238 | 1,904,263 | 0 | 7 / 221,929 | 66 / 1,682,327 |
| `dc2-3363` | 938 | 1,025,163 | 756,126 | 881 | 7 / 113,918 | 53 / 632,813 |
| `dc2dm-9d72` | 311 | 376,130 | 346,637 | 0 | 8 / 65,925 | 65 / 280,708 |
| `dc3dd-a5a8` | 214 | 293,205 | 272,275 | 0 | 62 / 172,518 | 31 / 72,322 |
| `dc3wy-a09f` | 244 | 332,846 | 297,830 | 0 | 62 / 187,779 | 27 / 83,578 |
| `dc3pp-9872` | 343 | 374,962 | 348,709 | 0 | 49 / 231,676 | 24 / 81,734 |

明确的未命名/未建模线性候选仍有：PC1 `48` 40次/1脚本、DC2 `22` 1次/1脚本及 `61` 880次/106脚本。
PC1的29个26和DD的5个56已从unknown移到partial，不能将这个分类变化描述为整段原生行为完全验证。
DM的44共有603个物理候选，但都是被宿主消费的字符串，独立线性次数为0。
没有被这类统计捕获的宿主子模式、变量别名和平台状态仍可能停止或只部分实现。

## 0.14.3 新增验证

普通mode0的系统1C返回已接入41A0B0协作阶段：07/09按当前变量重画后过渡，
wait-1直接返回冻结call_newback像素；首feedback立即发生，progress16仍持40ms才完整复制目标。
现场PC已绑定caller，但过渡期间阻塞取指、菜单/历史输入，loop SE到提交后才恢复。
P1R4保存来源/目标/当前front、片段时钟和caller；wait-1提交后纯变量操作不丢弃front。角色/滚动/条件重绘及后台21的完整前台规则未建模时明确拒绝；
便携full-frame发布可替换front，但不能把Java revision/publish推断成全部原生前台提交。
实际ADVMENU/OPTION已验证可见中间帧、新VM中途像素/字节恢复及输入阻塞。
仍未声称完整Win32 busy-pump、特殊reason4 label返回或所有partial-GDI绘制已经等价。

5C消费者修复了此前遗漏的raw-byte +20h步骤；生产阶段仍只保存当前loaded-code offset。
其2048-byte输入/输出空间不同于K/L的1024-byte scratch，marker不决定是否转换。
263组当前PE原指令转换参考与全部256种raw marker回归见`pc1-code-text-stage5`。

Pc1NativeSlot无损保留8004-byte frame和独立file trailing，按原生read-length/signature准入；
未知字段、inactive ushort及pending records均不清零。fromNativeSlot只投影现有frame owner。
本机未找到dcpc*.dat，因此这些是合成/机器码边界验证，不能称真实slot roundtrip。
Pc1LoadFade精确建模419D30/419E40局部phase与Host全局别名，42组完整原指令日志吻合；
它尚未接入完整41A620 loader，原版槽位I/O仍明确拒绝。

本批最终源码/分发哈希与核心、平台计数见`build/native-rewrite/stage5-delivery.json`。
0.14.2固定产物保留。

## 0.14.2 历史验证与基础

字节与布局参考位于 `pc1-text-stage3`、`pc1-text-buffers-stage3`、`pc1-message-stage3`。
原生日志保留1000个ushort，展开后的Unicode正文不再代替PC1日志；四块文本缓冲不随1F44返回回滚。
历史PgUp/PgDn/right/Enter保留现场PC和Variables；最旧边界不重画。
`Pc1TextLayout`保留固定字节步进、ruby、字体控制、1000 glyph上限、源指针别名及@K补丁；
实际绘制按每批non12先、12后，并核对原生24位混色。字体栅格仍由AWT/Canvas提供。
模式1的故事工具栏/选项表为frame外global，菜单返回不复制一个旧控件快照。
成功普通label选择后才减logcount；缺label保持现场，index0不按普通选项跳转。

九个非Unity语料的新版结构结果见 `build/native-rewrite/stage3-final-corpus/`：
5681脚本、5680有效、6,293,176记录、1277短STOP，Python/JVM无差异。
PC1新增5C raw ownership仅6项，均在START；1B661/25两项/26二十九项保持。
本批catalog SHA为 `ae9c33e526927ef5e90e6dd68701caa69daac6a419e3f856d440c1e57ad5e293`。
七入口仍4,371,660个线性组；状态分类相对下表旧基线未变，见新版coverage-totals.json。
物理字节边界及原始MES SHA未变，三份generator输出逐字节一致；Python90项通过。
唯一SC残缺脚本与两个DC2替代marker的执行拒绝仍保留。

0.14.2七入口各20次对白等待通过，仍是模拟输入、媒体完成与时间推进，不是原EXE帧序列对照。
0.14.2固定核心、桌面/ART计数与分发哈希见 `build/native-rewrite/stage3-delivery.json`。

## 0.14.1 历史验证基线

统一固定源码报告为 `build/native-rewrite/unified-stage2-final-20261103T202913967385Z/`。
90个编译输入、204个class登记了逐文件SHA；捕获与测试结束时源码变化检查均为空。
七部均从实际START和指定原生菜单action推进20次对白等待，共140次，观察109次图像解码、69个音频容器和30次新VM CVS2恢复。

| profile | 对白等待 | 图片 / 音频资源 | 新VM CVS2检查 |
|---|---:|---:|---:|
| `dcpc-2063` | 20 | 12 / 6 | 4 |
| `dc2pc-5769` | 20 | 12 / 5 | 4 |
| `dc2-3363` | 20 | 12 / 5 | 4 |
| `dc2dm-9d72` | 20 | 8 / 8 | 3 |
| `dc3dd-a5a8` | 20 | 21 / 14 | 5 |
| `dc3wy-a09f` | 20 | 18 / 11 | 6 |
| `dc3pp-9872` | 20 | 26 / 20 | 4 |

DC2实际解码SAKU_YUKI02，2026次live与3次capture粒子状态验证，4次粒子快照比较中2次有10个活动kind6槽。
PC1另以真实ADVMENU/OPTION和CRX通过23项平台入口/实际1C返回检查，含菜单中途新VM字节与像素恢复、普通变量修改保留。
DD另以真实START模板与CRX通过75项modal检查；checkbox扩展用实际55模板作为明确的测试fixture，未伪称游戏20模板本身有checkbox。

核心回归12,871项通过；Python工具90项通过。相关桌面审计合计173项通过。
Android构建、v2/v3签名检查通过，模拟器ART再次通过12,871项核心断言、7项Canvas及484项transition像素比较。
这些没有验证原版设备播放或原进程像素/帧时序。smoke的媒体完成、时间推进和菜单输入由工具模拟；内存CVS2往返不是原版槽位I/O。
没有用原生槽位、历史重画或其他未实现服务的跳过规则取得通过结果。

## 明确保留的缺口

- PC1 native槽位的41A620会按checkpoint table重新计算最终PC，并重建ushort历史、分页、message模板、画面和音频。候选捕获PC不是该最终PC；完整原Windows槽文件互通未实现。原版存读菜单和剧情自动槽现使用独立P1CS便携候选，F5/F9保留当前会话与该候选；快速保存现按原:q强制复制当前候选并写独立便携槽1；实际OPTION拒绝与文件锁失败重试已验证。
- PC1 原生历史游标/日志与07/09重画已经接入；AWT/Canvas字体、完整BB844C可见性/空正文、原生clip、实时reveal及Win32消息时钟尚未等价验证。mode0/2的40B900动态choice producer、普通选择Enter的40A6D0 focus owner、完整:w/jisho/49查询bridge仍明确拒绝。普通mode0的wait-1/7/9返回已接协作过渡；其它等待类型、特殊reason4 label与完整partialdraw仍需核对。texture selector5/6为独立cache，现明确拒绝原来的named-resource假映射。bank90已有到全部已声明owner的有符号地址别名；未知地址和未建模CRT跨度仍精确拒绝。
- PC1音乐完成回调48已识别原生功能，但40ms公共frame门控/完成查询和输入所有者尚未完整接入，仍unknown。
- DC2仍保留NVL旧字/历史合成、活跃粒子与5E移动反馈交织、超范围feedback及无当前MES调用的雨kind2等边界。原生system.dat完整平台设置互通、完整文字/历史绘制及Win32消息调度尚未等价；未穷举全部角色路线和附加剧情。
- DD direct CONFIG仍保留部分旧投影；51/52 voice生产者仍unknown；嵌套活动55/56 owner明确拒绝。10帧物理OS cursor warp与完整WndProc时钟没有复刻。
- PP的WM activation首tick、五次postfade刷新、主frame fallback、原生默认PCM0/MCI和部分SE capture/轮换/fade仍是部分实现。
- 其他版本的特殊变量、默认值、UI、语音/电影完成、渲染栅格、设置与存储副作用还需逐项核对。旧兼容层的全路线记录不构成本轮原生等价证明。

## 0.14.3存档与开发包（历史）

0.14.3固定包的非示例快照使用CVS2执行合同rev7，包含opcode/decoder/string ownership及宿主修订；当前rev8源码与开发包见文首。
旧CVS1或不同执行规则身份的快照明确拒绝并保留原文件，不能静默沿用错误变量或上下文模型。
PC1 PCH9/P1R4、DC2 PCH7、DD41等内部状态版本与原Windows槽位格式是不同概念；独立系统进度迁移另行处理。

本次Windows包和Android APK使用0.14.3名称，0.14.0/0.14.1/0.14.2固定产物保留。包内不包含游戏素材、EXE或原始脚本。
文件哈希和构建/签名清单随 `build/distribution/` 的产物保存。旧版发行说明中的可玩性和存档支持不能覆盖以上缺口。
详细原生入口、机器码、参考解释、反例和未完成边界见 `NATIVE_FIDELITY_AUDIT.md` 的F18–F24与 `NATIVE_DECODER_CONTRACT.md`。
