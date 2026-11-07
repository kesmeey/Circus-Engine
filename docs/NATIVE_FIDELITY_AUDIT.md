# 非 Unity CIRCUS 引擎忠实度审计

日期：2026-11-03；更新：2026-11-08。初始代码基线：`93e0b0e3a4960e1147fabdbce705bb17f5fd24ed`。
本文件区分**已确定的行为差异**、**尚未完成的行为**和**尚待核验的设计**；
既有文档中的 0.2、0.3、0.4 等历史计数不能作为本轮当前状态。
本轮排除 D.C.III R/XR 的 Unity / CircusScr 前端，不删除其现有代码或用户数据。

本轮读取源码与原始样本，并运行独立指令参考、合成测试和自建 Win32 服务审计；不执行原游戏，不写游戏目录。
它不是完整引擎验收，也没有完成原版与兼容引擎的逐屏、逐帧对照。

## 2026-11-08主要流程补缺

本批修正DC2首次加载已读查询，补齐原闹钟/存档上下文、普通ADV原槽转换、角色换图/移动与暂存坐标、消息窗整数合成和两类片尾；PC1新增原:q候选策略及便携快速槽。修正后的实际杏/七夏路线已各经角色片尾、GEND返回标题。后文保留历史发现与当时边界，当前逐项范围以[本轮冻结验证](validation/first-play-workflows-source-2026-11-08.json)和[重写状态](NATIVE_REWRITE_STATUS.md)为准。

原指令解释器的受控算术/分派验证、实际素材的独立像素参考、受控时间媒体的整条路线、JVM/ART与实包分别记录。没有执行原EXE，没有把这些计数换算成完成百分比，也没有宣称所有Win32时钟、字体、NVL历史和窗口生命周期都已等价。

## 审计结论

当前工程已有真正的 MES 解析器、控制流解释器、版本绑定、资源解码和原版脚本驱动的系统界面。
但“某版本脚本能通到片尾”不能推出“该版本原生引擎行为全部实现”，更不能推广到同系列的其他版本。
当前缺口中既有可由既有反汇编直接修正的确定错误，也有为了继续运行而明确省略的副作用。
重写应消除这些行为债务，并把每条受支持规则关联到相应版本的原生证据及反例测试。

## 已确认问题与本轮修复

### F01：PC1 / PC2 随机指令的算法、结果寄存器和零参数分支错误

初始实现：`PcHost.command("pc.random")`（基线第 232 行）推进 XORSHIFT 状态，写入 `99:255`；
参数为零时也推进状态并取低八位。

原生证据：

| 规则 | PC1 | PC2 |
| --- | --- | --- |
| 指令处理器 | `00414DC0..00414DE5` | `0041EDA4..0041EDC9` |
| CRT `rand` | `00420862..0042087F` | `0042B54B..0042B568` |
| 结果地址 | `00BB8428` | `00CEBA1C` |
| `99:2` getter | `0041193D..00411947` | `0041A385..0041A38F` |

现有只读反汇编位于 `build/pc-port/dcpc-native.asm`、`dc2pc-native.asm`、
`pc1-state-read.asm`、`pc2-state-read.asm`。这些是本机分析产物，不应把原游戏数据打入发行包。

两套处理器在参数为零时直接写结果零，不调用 `rand`。非零时调用 CRT，再用 `IDIV` 的余数写 `99:2`。
CRT 递推为 32 位环绕的 `seed = seed * 214013 + 2531011`，返回 `(seed >>> 16) & 32767`。
随机语音组也消费这一条 CRT 流。

**本轮已修复：** `PcHost` 使用同一个 `nextRandom()`；随机指令写 `99:2`；零参数不推进状态。
原有随机种子字段及存档布局保持不变。种子仍是兼容层的确定初值；本修复不声称它与原进程的启动时间种子一致。

验证：`PcRandomTests.run()` 的 48 项断言通过，覆盖 PC1/PC2 的固定数值向量、零参数、结果字段隔离、
零/负/高位种子、保存恢复后续流，以及随机语音组与随机指令共用流。
这些 opcode 编码的是无符号字节；负除数测试只用于核对直接宿主调用的 `IDIV` 余数语义，不宣称原脚本能编码负数。
`dc2-3363` 没有 `pc.random` 绑定，本轮没有仅凭宿主共享就替它新增此指令。

### F02：PP 不能沿用 DD/WY 的普通字节变量模型

基线 `Profiles.java` 第 611 行把 `dc3pp-9872` 绑定为 `BYTE_WRAP`。
但 `MES_SEMANTICS.md` 已记录 PP 原生读取函数 `004256A0` 的如下分支：

- `200..210`：普通银行内连续四字节的大端 32 位视图，起始索引最大 96。
- `300..310`：普通银行内连续两字节的大端有符号 16 位视图，起始索引最大 98。
- `400`：系统字节区的相邻两字节大端有符号 16 位视图。

DD/WY 的 getter 没有这些分支。基线 PP 的上述访问落到 `Dc3State` 的默认零/忽略路径，
属于确定的语义缺口。2026-11-04 已用新可读 PP 汉化 EXE 独立核对其读写、银行 10 别名、夹限与取指规则，
由共用变量核心绑定对应规则；持久存储的版本化修复见下一项。不能由这部分完成推出整个 PP 宿主已完整复刻。

### F03：PP 的特殊存储布局与 DD 不同

`NativeProgress` 已记录 PP 的 `system.dat` 为 15,500 字节，音乐/CG/场景标志是位图，音乐数量 210；
原生全局区 `741160`，标志区 `7411C4 / 7411DF / 741275`。
DD 则是字节标志和 400 首音乐区。基线运行时与 `NativeProgress.Target` 都直接构建同一个 `Dc3State(true)`。

独立的逻辑布尔数组能够表达单个解锁标志，但不能据此证明原生银行跨界读取、连续内存别名及持久写入规则等价。
版本化状态模型必须同时用于正常运行、原版进度导入和保存恢复，避免导入投影与执行器有两套规则。

**本轮已修复：** `Dc3State` 按 profile 为 PP 使用 359 字节的连续内存：100 全局字节、27 音乐位图字节、
150 CG 位图字节和 82 场景位图字节。证据来自 PP 汉化 EXE
SHA-256 `19d9091a95613d757b7807af1ce839b65cbfd222e9a53422fd1010ba8ca210ab` 的独立 REA 结果，
见 `build/native-rewrite/rea-pp-cn-core-batch.json`，getter `4256A0`、setter `4216B0`。

- bank100 写入仅 index 0..99，低八位；读取可以继续进入后续已核对位图区。
- bank96/97/98 写入分别限 210/1200/650 位，非零置位、零清位。
- 原生 bit getter 未在调用方检查上述写边界；兼容层按已核对连续区处理跨界读取。
  例如 `96:216` 与 `97:0` 是同一位，`97:1200` 与 `98:0` 是同一位。
- `400:99` 的读取低字节可以观察第一音乐字节，但写入只影响全局第 99 字节；写 index100 被原 setter 拒绝。
- PP 全局第49字节不触发 DD 的额外偏好 setter，不再继承 DD 的预置音乐解锁字节。
- 普通运行、系统默认恢复、读档时的进度合并、旧版设置迁移和 `NativeProgress.Target` 统一使用带 profile 的 owner。

为了完整保存两个末字节中原生读取可见的填充位，PP 故事状态加入 `PPS1` 前缀，设置状态加入 `PPT1` 前缀，
先保存全部 359 原始字节，再保存原有固定长度的逻辑字节数组投影。读取时校验原始字节与有效标志投影一致。
新版可读取旧 PP 系统进度投影，有效的布尔标志和全局字节无损迁移，未用尾部字节原样保留在嵌入的旧投影中；
旧格式没有保存的原始填充位无法恢复，迁移时明确初始化为零。旧程序不能读取新 PP 格式，DD/WY 格式不变。
此格式仍是兼容层格式，未把它误称为原 Windows 存档。
若旧文件的活动标志不是 0/1，或在原生不可写的 bank100:100..149 保存了数据，就无法忠实恢复其含义，
会明确报告无法迁移并保留原文件，不静默归一化或丢掉数据。迁移失败不会发布部分进度。

`PpStateTests.run()` 独立通过 47 项断言，覆盖实际内存别名、写边界与只读跨界、普通/持久镜像、
非零原始填充位在故事/设置状态中的完整保存、原有存档投影迁移和拒绝、DD/WY 回归、进度合并以及原版系统进度导入后的正常持久化。
PP 的 93/94/95 原生不存在，本轮已改为读0/写忽略，并移除语音消费者对 DD 角色表的依赖，见 F12。
不存在的 bank99 索引也不再透出 DD 默认值。其余有定义的 bank99 宿主查询、副作用仍需逐项核验，
不能把现有 DD 特殊状态表整体当作 PP 已验证实现。

## 仍未实现或近似的行为

### F04：部分 D.C.II 指令被记录后放行

基线 `Dc2Host.command` 存在三个明确入口：

- `native.unmodelled`（第 176 行）：仅追加诊断字符串，返回成功。
- `dm.scene.effect` 的 5/6 子模式（第 182 行）：记录后返回，未建模效果。
- `dc2-3363` 的未识别系统子命令（第 291–294 行）：追加 `unmodelled` 后返回成功。

这些不是已证明的原生无副作用分支。它们会让流程审计通过，但原副作用仍缺失。
`native.no_effect` 则是独立概念：只有确实到达原生默认返回的已核对分支才可标记为此语义。

处理建议：

1. 先用原生分发表和系统服务函数补足每个实际子命令，不直接把现有保存/读取页面改成一律中断。
2. 把兼容运行中的未建模行为汇总为结构化债务：profile、脚本哈希、PC、opcode、子模式、参数、原生处理器、证据状态。
3. 同一事件应有界去重；原列表会随循环无界增长，也未成为现有流程审计的失败条件。
4. 兼容运行可保留明确降级，但忠实度验收必须要求债务为零。研究诊断模式可在第一处精确失败。
5. 不把“没有异常”或“已经追加日志”计为实现了该副作用。

2026-11-04 进展：使用用户指定 `D:\gal\rea` 重新反编译 DC2 的 `00404F50`，
已在 `Dc2SystemCommands` 中取代其中第三条放行路径。
随后前两条真正未建模路径也已改为精确 `unsupported`：错误包含 profile、语义、脚本、PC 和参数，
不会再追加诊断后返回成功。已核对的 `native.no_effect` 与系统服务 default 保持无副作用，
包括不清除等待输入状态；不知道行为和已证明无副作用是两种不同状态。

新证据与目标身份：

- 当前补丁 EXE：SHA-256 `d4748bc7a4c385b893ff16f4ef76c95e8759bfe8ffad22a6b728706965c5e345`。
- 原版 `dc2.OLD`：SHA-256 `78b5912574d5799b148b265232f4d07990b8ef9b25ff47ca56f66530f6e1f2c2`。
- `build/native-rewrite/rea-dc2-system.json` 的主体为当前 EXE，过程 `00404F50`。
- helper 的原版 REA 结果在 `rea-dc2-original-batch.json` 和 `dc2-original-0x*.c`。
- `dc2-original-current-function-comparison.json` 分别读取两目标的 VA 区间，证明分发器、
  `404CD0 / 405340 / 4057B0 / 40B700 / 40E410` 及变量读写区间的机器码 SHA 相等；没有把两个整文件哈希混用。

`00404F50` 只有服务 `1,2,6,8,10,11,12,20,30,31,40,50` 有代码，其余服务号都直接返回。
因此 `7/18/19/21/207/213` 等编号在 DC2 不能执行同号 DM 服务。现在 dispatcher 在进入 DM 分支前按版本分流。

已实现服务：

| 服务 | 经核对的行为 | 验证 |
| --- | --- | --- |
| 1 | mode 0 从当前脚本 basename 提取日期/时段；mode 1 单独计算非闰年 day-of-year+1 的星期；其他 mode 返回 | 合成文件名、大小写/路径、两种 mode 独立副作用 |
| 2 | 六个角色的 `var(0,i)*100 + {6,5,4,3,2,1}[i]` 严格取最大，写 `9:10`；仅 mode 0 工作 | 第七变量不得参与、同好感原生优先级、mode 1 无动作 |
| 6 | `404CD0` 的保存/读取页条目绑定：每页步长 8，保存初始化 5 条，读取初始化 10 条后台记录；原脚本定义可见矩形；0/1 保存槽禁用，读取只启用存在槽位 | 预留槽、已存在/缺失槽、`selsave/selsave2`、配置后不重新按新页码解释点击 |
| 其他真实 default | 不执行宿主副作用 | 244 个服务号逐一检查宿主快照不变，且不追加 `unmodelled` |

`Dc2SystemTests.run()` 独立编译执行通过 303 项断言，同时检查 DM 的服务 6/18 保留原行为，
原生 default 不清除等待输入状态，以及真正未知指令/效果精确停止而不冒充 no-op。
PC1/PC2 的格子逻辑未套用 DC2 规则；只有原版 DC2 使用配置时保存的槽位与动作。

另修复平台控件转换覆写原生按钮的问题：`DesktopLegacy` 和旧 Android `Dc2Activity` 曾直接把
`grid` 中按钮的动作改成 `@slot:N`，会破坏原生 `selsave/selsave2` 绑定。
现在通过 `Dc2Host.gridControl` 产生独立输入视图，保留原对象的槽位、动作、禁用状态和快照。
独立桌面 `Dc2GridControlAudit` 运行实际 AWT 渲染、控件转换和指针按下/松开，通过 13 项断言；
该测试使用合成脚本，不依赖原游戏，也不进入 Android 的核心测试集合。

**仍未完成：** `8/10/11/12/20/30/31/40/50` 的完整宿主效果。现在这些服务精确报告服务号、参数、
脚本/PC 和原生 helper，不再记录后假装成功。8 是姓名输入初始化；10/11 涉及角色片尾准备与播放；
12 涉及大结局片尾；20 是六角色移动地图，31 进入另一原生等待子状态；30 调度专用转场；40 清除 Ctrl 跳过等效的锁存状态；
50 复制原生脚本上下文。知道这些入口或名称不代表已经实现其业务。

本机直接服务调用共有 94 条（`dc2-system-corpus.json`）。其中 24 条服务 21 属于真实 default；
18 条服务 6 已有版本独立实现。其余涉及尚未完成的服务，尤其服务 20 的 24 次调用。
严格报告会揭露原来被跳过的剧情、片尾或闹钟模块；不能把这次纠错描述为“DC2 所有路线已兼容”。

DC2 特殊变量也仍待独立建模。原生 `413A70` 未识别 bank 95（返回 0），
bank 99 只识别 `0,2,3,4,5..18,20,21,22,247..254`；
当前公共 PC 宿主中的 `99:243/244/245/255` 等同号业务不能直接算作 DC2 的原生语义。
`404CD0` 写入的 `988CAC=2` 是内部字段，原生 getter 没有 `99:246` 分支；本轮已移除这条错误寄存器投影，
DC2 的 `99:246` 现在按真实 default 返回 0，内部等待与选择通过专有格子状态表达。
service40 也不能简单设平台 `rapid=false`：仍按住 Ctrl 时原查询应保持真，必须先区分物理按键与锁存跳过状态。

### F05：特殊查询的默认值存在未经验证的宿主假设

`Dc3State.read` 对 `Dc3StateRules.READ==2` 的原生查询返回保存字段；注释明确说这是为 WY/PP 的
`WIN_SIZE` 查询提供默认值，而原查询所在宿主对象未建模。
`PcHost.read` 的 `99:3` 固定返回 1，`99:242/247` 固定返回 0，其他未登记寄存器默认 0。
`Dc2Host.read` 也把窗口查询映射为移植宿主状态。

这些返回值是否正确必须按版本与平台能力逐条判断；本审计不能把全部默认值都认定错误，
也不能把字段能读写当作已经理解原查询。需要登记实际原生 getter、转换、只读性、副作用和平台来源。
对未知业务用途字段，应保留原始证据和未核验状态。

### F06：触屏理由被写进跨平台宿主逻辑

基线 `PcHost.command("pc.button_hover_voice")`（第 168 行）直接返回，理由是触屏没有 hover；
`pc.rapid_binding`（第 169 行）也直接返回。相同宿主同时用于桌面，而 `DesktopLegacy.hover` 确实处理鼠标悬停。

这是明确省略的交互路径。应先还原按钮悬停语音/快速模式装饰的注册和触发规则，再由平台决定是否有 hover 输入；
不能因一个平台没有事件就从公共宿主丢掉原脚本状态。尚未用原生代码确认全部子模式，不应凭注释直接补猜测逻辑。

### F07：效果和转场仍存在近似、替代或未完成子模式

`PcHost` 的部分转场 10/12/14 统一发布普通场景和延迟；`Dc2Host` 部分时长使用固定估算或通用公式。
`Dc2Scene` 的程序效果只实现少数 kind；DD 的粒子、转场、相机虽然已有独立模块，也不表示全部变体已核对。
`PC_COMPATIBILITY.md` 明确承认部分转场、粒子、字体、自动推进时序仍近似。

需要按版本给每个效果建立状态、时钟、随机流、开始/结束条件、中断、捕获及保存恢复规则。
截图测试应有原版参考输入和画面，兼容层捕获前后的像素相同只能证明内部一致。

### F08：文本控制的显式拒绝是正确边界，但仍是覆盖缺口

`NativeText.CONTROLS` 明确拒绝尚不支持的 DD 文本命令及注音控制。
`Dc2Text` 也拒绝部分 `@w/@r/&f/&n` 路径。
`Dc3Performance` 已实现部分字节级事件，但不覆盖所有预处理与排版命令。

未知控制不应被删掉后显示剩余文字。需要分别核对字节预处理、姓名替换、内联事件、字形/注音排版、
文本完成和用户推进；不能用文本解码成功替代上述阶段。

### F09：兼容层存档与原生存档不是同一契约

基线 `MesVm.snapshot` 保存兼容层 `CVS1` 的 PC、变量和宿主状态，带脚本哈希与校验；这是已有且有用的恢复机制。
`Dc2Session` 另保存系统菜单调用者，并在返回/读档时合并持久状态。
但原 Windows 剧情槽位除被排除的 Unity 前端外尚未完整转换。

需要独立核对原生槽位包含哪些数据、哪些全局状态在读档后保留、系统菜单期间音频/时钟如何处理、
原生 autosave/quicksave 槽位、格式迁移以及 CRC/压缩。不能为追求原生互通破坏现有可读的手机存档。
本审计没有把自定义存档格式本身认定为错误，也没有验证原生读档生命周期的全部副作用。

本轮已把非 Unity VM 的新剧情快照升级为 `CVS2`，保存 `Profile.executionIdentity()`，
包含已审查执行语义修订、变量模型、opcode/解码契约。旧非示例 `CVS1` 剧情槽明确拒绝：
旧 PP 的 `BYTE_WRAP` 和旧 DC2 的 `PC_WIDE` 已执行出的状态，无法仅凭相同线格式或长度证明忠实迁移。
这与系统持久进度是两个契约；合法旧系统进度仍通过版本化 owner 迁移。

## 需要继续核验的核心契约

### F10：物理解码和原生搜索的版本行为应分离

基线 `MesScript` 包含 `dcpc-2063` 高位 opcode 三字节行为、首个 STOP 截止、
PC2 指定 `61 64` 尾巴和 DC2 两字节零尾巴等 profile ID 分支。
其中高位有符号取指已有机器码证据，不能在重写时“规范化”为统一无符号行为。
但语料中的固定尾巴不是整个原生扫描规则；应把取得证据的终止/扩展/扫描规则做成显式版本策略。

`requiredLabel` 与 `scanBlock` 还分别使用有符号与可配置取指。
原生扫描可能跨过当前执行器会如何解释的记录，尤其 WY 高位扫描与 DM 条件块 ID。
静态标签索引、执行取指、命名标签扫描和块扫描不应强行共用一种解码结果。

### F11：STOP、跨 MES 转移、系统返回与退出不能混为通用结束

基线 `MesVm` 将 `stream.stop` 直接设为 HALTED；`system.exit` 会通知宿主。
不同原生分支的停止/让出理由、PC 是否前移、重入和状态清理需要分别验证。
`script.transfer`、`dm.script.transfer`、`pc.script.transfer` 已存在不同 mode，不能简化成统一 CALL。
本审计尚未证明基线 HALTED 在每个版本的外层生命周期都等价，因此将此项列为未核验契约，未声称必然错误。

### F12：PP 语音不再复用 DD 的角色银行和百分比增益

证据来自 PP 汉化 EXE 的独立 REA 结果 `rea-pp-cn-voice-*.json`，完整规则记录在
`build/native-rewrite/pp-cn-voice-native-contract.json`。角色表 `4883F0`、分类 `432010`、
强制判定 `4320A0`、准备 `4325D0`、提交 `432450`、普通提交屏障 `4278E0`、增益换算 `430DA0`。

本轮新增 `PpVoice` 并替换对应消费者：

- 八个固定角色后缀和 other；角色静音来自 `100:(60+role)`，非零为静音，默认全部0。
- 全局开关 `99:5`；强制 `#` 或 SYS 路径仅绕过角色静音，仍受全局开关。
- 原生角色/强制扫描只识别反斜杠，资源查找层再单独处理路径规范化。
- 去掉一个 `=/< />` 前缀后处理分号角色覆盖；VOICE 索引路径不消费分号。`#` 保留在资源名中。
- 普通37的准备状态在下一次取指前提交；系统36立即提交，两者共享同一虚拟语音通道，分别保存原生的声像单位。
- 被全局或角色门限拒绝的新请求不停止旧声道；`99:249` 在全局关闭时仍报告语音已完成。
- 增益采用原生 DirectSound dB 曲线，默认27对应 `-498` hundredths dB；0对应 `-9999`，并非数学0。
  在提交时捕获声道增益，之后只写 `99:5/9` 不会追溯改变正在播放的声道。
- PP 专属宿主状态版本保存捕获的增益和普通37准备标记。DD/WY 宿主格式不因此改变。
- 桌面/Android 共用 DirectSound 声像换算，不再把原生1000误当百分比满声像。
- PP 语音不再乘 DD 的 `99:203`，也不把 `99:225` 的 Windows 混音器服务重复当作软件声道系数。
  其 BGM/SE 基础增益也使用原生30级曲线；逐素材 SE 衰减及完整停止/轮换规则仍单独核对。

独立 `PpVoiceTests` 当前40项断言通过；桌面 `PpVoiceOutputAudit` 的6项断言通过真实 presentation owner 的资源、增益、
声像、渲染和恢复检查。完整原生异步预解码、绘制/文本工作队列阻塞，以及相关原生 UI/replay 模式门限仍需继续核验；
逻辑提交顺序正确不等于已完成逐帧或扬声器听感对照。

### F13：DC2 原生0F必须清普通字节，不能误用完整状态重置

严格报告后，真实启动流程在 `title_02@0x71` 的 `0F 00 00` 暴露缺口。
本轮没有回退到日志放行，也没有按其他作品的同号指令猜测。
当前 EXE 的 `dc2-current-verified/dispatch.json` 将0F指向 `004149D5`；
`fetch_and_dispatch.asm` 明确是 `ECX=0xFA, EAX=0, EDI=A0996C, REP STOSD`，随后跳回分发尾部。
既有独立 REA `dc2-original-0x414700.c` 的 `case 0xF` 同样只循环写250个DWORD。
目标身份与两版本对应区间的证据仍分别保留，不以原版整文件哈希冒充当前汉化文件。

因此该指令无条件清空1000个普通变量字节（bank0..9），不读取两个操作数字节，
不调用宿主，不清日志或历史影子，也不重置全局状态、声音或 UI。
现绑定为 `variable.clear_ordinary_bytes`，由 VM 调用只操作 live 区的 `Variables.clearOrdinaryBytes()`；
没有复用同时清 shadow 的 `clearOrdinary()`，也没有调用 PC/DM 的完整 reset。

独立 `Dc2ResetTests` 通过1521项断言：逐字节检查全部1000个变量、
遍历两个操作数中的全部字节值、比较真实 `PcHost` 的持久/音频/UI快照，
以及用 append/replay 观察保留下来的影子和历史。特别是reset后append必须新增清零delta，
不能通过清空历史伪造“reset后变量是0”的测试结果。

### F14：DC2 原生5E的逐帧移动与反馈混合已替换，pending生产者仍是明确边界

真实标题下一处缺口是 `title_02@0x1B7` 的 `5E [0,-600,0,2]`。
本轮针对当前 `dc2.exe`（SHA-256 `d4748bc7a4c385b893ff16f4ef76c95e8759bfe8ffad22a6b728706965c5e345`）
重新通过本机 `D:\gal\rea` 分析，完整结果是
`build/native-rewrite/rea-dc2-current-motion.json`，Evidence
`ev_3a707d394556f332f1daac665b1685b56484ca352f2f822e91896c2458127242`。
其中 `416499` 是指令入口，`418890` 是等待消费者，`41AE00` 是等待分派，
`408160` 是24位整数反馈绘制，`41C440` 是已有待合成画面时的前置淡入。

现绑定 `dc2.scene.motion`，使用独立 `Dc2Motion`，不继承DM滚动公式：

- 第三个操作数任何非零值均只赋 live 背景坐标，忽略第四个操作数，不立即重画，也不赋人物/立绘坐标。
- 第三个操作数为0时进入等待；`407106→40717E` 的原生调度使第一步立即发生，随后每10ms一步。
  `99:12` 为0或其他值时每轴2px，1时4px，2时8px，3时16px；Ctrl/跳过也取16px，
  但普通字节 `8:18==1` 禁用该跳过输入。两轴独立趋近并钳在目的坐标。
- 坐标是带符号的目标绘制偏移。`-600` 表示向上绘制，不能沿用DM渲染中的取反坐标。
- 第四个操作数是固定反馈权重，范围0..16的逐通道公式为
  `((old*(16-weight)+fresh*weight)>>4)`，每次绘制均发生整数截断。
  抵达后才逐帧增加settled；`settled+weight>16` 时清权重并完整重画，下一等待边界才释放。
  不能用一次透明绘制或固定延时替代这串有顺序的像素运算。
- `408160` 只接受native format0（24bit）；32bit源在该反馈helper中确实不绘制，
  最后完整重画仍走另一绘制入口。当前PE核验见 `dc2-current-image-create.asm`、
  `dc2-current-image-init.asm` 和 `dc2-current-pending-feedback-contract.json`。
- 非零pending的前置消费者按40ms、native速率/权重循环，结束后会把移动权重覆盖为16。
  该消费者已实现并独立验证。

`Dc2Scene` 保留有序绘制journal，桌面/生成的Android后端使用同一core整数混合函数；
大步elapse仍按原生帧拆分，renderer缓存只减少重复重画，不反过来决定VM状态。
DC2宿主 `PCH6` 保存pending三态、独立voice-enable、播放增益、输入等待种类/模式和完整motion journal，
途中恢复可重建同一反馈画面；PC1/PC2仍输出原 `PCH5`，DM序列化布局未改。
这属于当前CVS2语义版本，不是旧剧情槽的忠实迁移证明。

**未完成的生产者没有伪装成false。** `BE8BC8` 还由32角色批次、`41A020`正文首帧等逻辑改变，
条件涉及native batch `9DA2D0`、`A0AC24`、`A09194` 与历史标志，现宿主未完整表示它们。
因此角色/正文生产者之后标记pending unknown；若随后5E需要判定，明确报告上述原生字段缺口。
已证实的背景2D、quad52、0B、0A的特定分支及0D mode0/13可证明清0，才解除unknown。
原生1/2的所有生产者/消费者没有被宣称统一；41C440消费者可用不等于其全部生产者已还原。
第四参数超出0..16时涉及原生packed算术跨字节溢出，本轮精确拒绝，未擅自clamp。

`Dc2MotionTests` 当前4419项断言通过，包含真实raw5E、时钟余数、两轴/速度/skip边界、
large/small elapse等价、CVS2途中恢复、pending unknown与恢复、独立标量像素反例。
实际 `Dc2MotionOutputAudit` 通过14项全帧/平台检查：重复render无副作用、反馈顺序、
新renderer恢复、负坐标、最终完整重画以及32bit反馈no-draw。这些合成像素检查没有运行原EXE，
不能替代对原版完整UI/演员/特效帧序列的比较。

### F15：DC2 原生3D/3E属于当前按钮region，语音不能套PC1角色或99:5音量门

当前EXE独立REA结果为 `build/native-rewrite/rea-dc2-current-buttons.json`，Evidence
`ev_71ef11ffc7aa6c5728f487abdc9db0f6829188e78a430b0b57c9661d25b4f6c7`。
结构与原生表的机器码/数据读取合同保存在 `dc2-current-region-voice-contract.json`，
另有 `dc2-current-button-consumers.asm`、`dc2-current-button-voice.asm`。
本轮没有按其他版本的opcode差值推断归属。

`4E→40DA30` 在 `PTR451D10`/`988C9C` 创建0x230字节region；默认pending列表位于97B1D8，
已激活模板列表位于A00028。它们对应普通 `ui.buttons`，不是55构造的缩略图grid。
3D在列表非空时写最后一项 `+228` 的原始点击语音串，并将 `+200` clickSound置FF；
3E写 `+224` hoverVoice，并将 `+1FC` hoverSound置FF；空列表原生直接返回。
`40C6F0` 的悬停变化读取+224，`40C810` 的点击读取+228，均交给 `41F130`。
现分别绑定 `dc2.ui.button_voice`、`dc2.ui.button_hover_voice`，模板复制、controlView与快照均保留这两项。
没有hoverVoice的按钮列表仍输出原字节布局；有值的列表以-1前缀标识扩展，旧app不能读取它。

`41F130` 先要求SE开关 `99:15` 非零，再进入 `41EED0` 的普通native声道2，
宿主逻辑上仍映射到voice通道5，不混入SE的逻辑通道1/2。
该内部入口还检查独立字段 `473FB0`，它来自 `system.dat+0x38`，原生默认1，
**不是** `99:5`（473FB8）。拒绝/空串/角色静音在替换声道之前返回，不主动停止上一段声音。
`Dc2Voice` 独立使用45EB98的26项角色表；只检查原始反斜杠后3字节，未匹配17，
SYSTEM/OMAKE/GAYA段前缀可绕过角色静音。没有先把正斜杠改成反斜杠，也没有剥离原串中的pan前缀。

音量 `41E5D0` 的规则是level0取-10000，否则
`clamp(masterDb+100*level-3000,-3000,0)`；新voice声道master为0，level29为-100（线性约0.891251）。
99:9赋值不改变当前voice增益；新提交读取它，99:7经41F0C0也会刷新当前voice增益。
宿主与平台保存/使用实际播放增益，避免每帧把它重新乘99:5或线性29/30。

默认值另从当前PE的42D078复制表和413A70 getter逐项读取，未继续套用DM默认：
`99:5..18 = [1,1,25,25,29,1,8,1,0,1,1,8,1,1]`，`99:20=1`。
这里仅说明这些已核验字段；不代表全部99寄存器、所有偏好副作用和默认状态均已完成审查。

`Dc2ButtonVoiceTests` 当前63项断言通过：raw3D/3E、空列表、grid隔离、模板/快照、
原生角色/门限/增益与旧按钮线格式。实际 `Dc2ButtonVoiceOutputAudit` 通过11项，覆盖
4E+45创建按钮、鼠标进入/停留/离开/重入、SE gate、99:5独立、恢复及点击label转换。
这仍不是整个音频子系统完成：native system.dat偏好没有由只合并unlock的NativeProgress导入，
473FB0当前从已证默认1开始，不能宣称已迁移原生语音偏好；原生focus恢复触发41F0C0的事件还未接入，
全部声道/设备、按键焦点和按钮命中优先级仍需独立验证。

### F16：PP 音乐拥有独立请求、缓冲、队列与 dB 渐出状态

PP 汉化目标 `19d9091a...210ab` 的 `428B00` case05、`432E80`、`432870`、
`4328B0`、`431B10/431B90`、`433140` 构成当前实现的独立合同，见
`build/native-rewrite/pp-cn-music-native-contract.json`。05不是DD的临时音量恢复，
可消费紧邻的一条06；单独06刷新duck及声道音量。FF立即释放并清队列，FE取调用者场景保存的曲号，
模式0/1/2分别控制重播/复用/单次，10/11调用的`411450`在本目标只有RET。

`PpMusic` 分开保存脚本请求、实际缓冲、PCM是否播放、待播曲、duck、捕获master dB、
当前声道dB与渐出累加器。曲表extra[3]贡献master衰减；新加载与99:7/06刷新使用不同原生公式。
PCM自然结束保留缓冲；下一次重播仍须先渐出、释放旧缓冲，再提交待播曲。
关99:14先渐出，不能即时把输出乘零；开99:14重播请求、清duck且不额外解锁96。
12忘记脚本请求后渐出，后续06不能把99:244复活成旧曲号。

普通渐出每个433140事件减150 hundredths dB，候选低于-5000时减300；
有替换队列则用800/1600。99:7/06能刷新当前声道音量，但不重置渐出累加器。
`PPM2` 保存队列、音量相位及剩余逻辑时间，拒绝旧PPM1与不合法状态；
新宿主只在队列实际提交时改变平台音频generation。核心72项及真实宿主25项断言已通过，
覆盖渐出途中恢复、大步/分片时钟、自然结束、静音与请求/解锁的分离。

**时钟边界仍未完成。** `tick()`只代表一次433140 helper事件，
`elapse()`模拟新建BGM/SE timer请求的10ms路径，不能等同整个WndProc调度。
WM_ACTIVATEAPP可能留下150ms首tick，且后五次事件在渐出后还会432870刷新原音量；
原生主frame另有fallback。当前尚未统一还原这些平台事件，不声称焦点切换、墙钟时序或听感已逐帧一致。
本实现仅覆盖原生fresh默认PCM后端；741110的另一路是MCI文件播放，它没有99 getter/setter映射，
并非99:6（74112C）。原生偏好整块加载和MCI后端没有借用PCM合同冒充支持。
SE素材extra[0]衰减已经接入，但其播放时捕获、双缓冲轮换、223例外和渐出仍需继续核验。

PP/WY的只读99:3也已从普通存储移到平台能力owner：PP在`41A3D0`检查1024x576与
BITSPIXEL>=9，WY在`4311A0`将窗口对象member18置1；99:4是另一个当前模式字段。
桌面/Android入口提供实际显示能力，写99:3不起作用；证据为
`pp-wy-cn-query99-3-contract.json`，12项阈值/只读/模式独立检查通过。

### F17：DC2 3B/3C是真实输入绑定，36保留上下文与35清除上下文须区分

真实标题又在 `title_02@0x13E3` 的空3B暴露旧映射缺口。当前EXE的
`41717B..41719F` 把包括NUL的原串复制至A09D54，`4171A4..4171C8` 复制至A09D74，
空串确实清除绑定。现分别绑定 `pc.action0` 和 `pc.action1`，没有把空串特判成无副作用。
已有独立REA函数 `dc2-modal-0x41b611.c` 实际入口为41B5F0；其输入字段、调用者与setter
已用当前PE机器码重新交叉核验。`dc2-current-global-action-contract.json` 分别记录当前/OLD的整文件身份，
并实际比较setter、41B5F0输入消费者和4137D0 loader三个代码区间的哈希；三段均相同。

输入消费是按原生wait状态区分的，不能只按PC1/PC2的共用数组编号推用：

- wait12允许Esc/右键使用action0，左键/Enter使用action1；可选label成功才清除被使用的那一项。
  label不存在时413630返回-1，绑定保留，等待继续，不能变成VM错误。
- 普通UI wait0A/0B只使用action0；Enter走选中region的点击路径，不用全局action1。
  choice wait9也不能借用这两个全局动作。
- wait12的模式bit0独立控制未命中label时是否允许输入完成；0A mode1的定时等待忽略输入。
  桌面背景左右键经过相同host gate，右键没有可用label时不会误请求剧情菜单。

36的原生handler直接调用 `4137D0(str,2)`，现绑定 `dm.script.transfer_alt` 进入mode2，
保留两项动作。35则根据后继 `02,01` 选择mode3，否则mode1；loader的mode1/3都会清空两段32字节动作区，
mode2保留。`beforeTransfer` 这一版本规则经真实VM跨脚本测试验证，没有只直接调用宿主回调代替转移。

恢复后的首次输入还发现当前script引用未重绑：root已增加专用 `ContextHost.restored` 回调，
`Dc2Host` 仅重绑script引用。没有把VM的下一PC覆盖到宿主保存的上一条执行PC，
因此历史/诊断位置与快照字节往返保持各自含义。PCH6另外保存waitKind/inputMode，
恢复后可立即进行相同可选label查询及输入门限判断。

37也已独立核对：`4174FA`把原始字符串复制到BE8BF8；`4137D0`加载新脚本后调用
`413700`进行必需标签查找，随后清空BE8BF8和BE8C18各32字节。绑定为`script.entry_label`，
空串清除注册、后一次覆盖前一次、成功加载只消费一次；缺失或大小写不同的标签明确报错。
新`Dc2EntryLabelTests`通过19项真实VM转移/恢复断言。反例还修正了Java三元表达式在
缺标签时隐式拆箱null的问题，现在报告原生契约对应的缺标签错误。
证据为`dc2-current-entry-label.asm`及已逐字节比对的loader/dispatch区间。

`Dc2ActionTests` 通过36项：raw3B/3C含空串、两键/各wait种类、缺label保留、
定时输入gate、恢复、35/36及35+companion模式。实际 `Dc2ActionOutputAudit` 通过13项鼠标转换/恢复检查。
这里没有声称整个0A/0B/12的计数器、音频结束、历史/modal调度均已完全还原；
超过原生32字节动作区的字符串也未模拟越界内存破坏。

### F18：DC2 2A 的 5/6 是真实粒子内核；4B/34 的 dirty 提交不是立即显示

本阶段重新导入的是当前 `D:\gal\DC2\dc2.exe`，整文件 SHA256 为
`d4748bc7a4c385b893ff16f4ef76c95e8759bfe8ffad22a6b728706965c5e345`。
`build/native-rewrite/effects-stage2/dc2-native-particle-contract.json` 记录各批 REA、当前 PE
机器码区间、常量表与工件哈希。这里不以 OLD 的地址或某条真实剧本能运行代替当前目标验证。

`411670` 的 1/3/4/5/6 实际共用 `411CE0/411D60/412050/412250`，分别绑定
SACRA01、YUKI01、SACRA02、SAKU_YUKI01、SAKU_YUKI02；5/6 不再按 F04 的旧状态报未知或使用 DM 近似动画。
`Dc2Particles` 保存原生 50×25 DWORD、动画/交替标记、wind、共享 CRT RNG 和 40ms timer 余数。
强度 0/1/2/3/4 对应 0/5/10/15/30 个，>=5 为50个；无自动结束。
同类重发会重新初始化槽位，但不重启 timer phase 或交替标记。关闭99:20或FF busy只抑制更新，
不把已经存在的贴图和粒子清掉；0停止也保留timer phase。原版门限是99:20，而非通用近似渲染器的99:10。

坐标、重生、角度、帧计数、随机消费顺序均按原生整数实现。30组独立参考结果通过只读的 Python
x86 小解释器获得：仅把原 PE 的指定算术函数及数据映射到私有 bytearray，解释其指令，
不启动 PE、不调用 Win32、不在宿主 CPU 执行目标机器码。五个 seed/配置、六个时间点比较全部5000字节槽位、
RNG state和toggle；不是第二份 Java 同算法互相印证。完整4096项 COS 数据 SHA256 为
`5fc89e4e961c8449584602bffd5e92369af5dc804d8535cc2a43b9ab9194c5e7`，生成的 Java 常量逐字相同。
默认便携单调毫秒仅作为启动 seed，不能宣称其 epoch 等于另一 Windows 原进程的 GetTickCount；测试支持显式注入 seed。

`41D3F0` 的当前机器码 `[41D3F0,41D975)` SHA256 为
`86a7e20fbe8d1e8d1ab0ff2a4e02c562148df1fedead700d2373dc6da6505b6e`。
它依次画背景、layer1粒子、演员、layer0粒子，再画UI；已经烘入背景的quad也在演员与粒子之前。
贴图以32×24、28行距裁切；24位跳过FF00FF，32位的非零alpha只是是否绘制的mask，
没有再乘一次alpha。普通sprite固定8/16，kind3为12/16，各通道整数加权后右移4。
平台的24位色键必须保留原RGB给这个内核，不能先改成透明黑再当作普通RGB混合。

另一个独立原生字段9DA2D0表示尚未烘入的draw batch，不能与BE8BC8的历史/演员pending混为一谈。
2D背景、52quad及21 selector0设置dirty；不能因为一次 `MesVm.run` 预算耗尽或调用通用 `scene.publish`
就宣称原生batch已经清除。非停止34语音和4B正文遇到dirty，先释放两个演员句柄，
`413E70`烘入背景，然后 `41C440(45E800,1)` 对旧完整前台图像做40ms分帧准备。
speed0/1/2/3的进度增量为1/2/4/8，反馈权重3/6/8/10；到16后仍拥有最后40ms，随后完整替换。
独立准备没有再加5E运动的10ms尾帧。Ctrl/skip的原有8:18门限仍独立处理。

现使用同一个 `Dc2Motion` journal 的 `prepareOnly` 分支，冻结mask0F图像和旧前台UI raster。
4A暂存说话者，4B只有紧邻前一条4A才使用它。准备期间不会提前发布body/speaker、历史、scenario解锁或voice；
完成后由 `beforeInstruction` 提交已经取到的命令，避免在 `elapse` 内执行可能失败的IO或重放副作用。
准备中、绘制完成但尚未提交、提交后均有恢复反例。实时桌面捕获与逻辑headless捕获的范围不同，
前者保留旧消息面板等完整像素，后者只能冻结已有逻辑scene，不据此宣称完整UI像素已经存在。

DC2宿主 wire 现在是 **PCH7**，motion 为DCM2，particle为DCP1，legacy capture追加DCPC。
F14/F17所述PCH6是前一阶段格式，缺少粒子与draw-batch/延期命令状态，现明确拒绝恢复为当前DC2故事状态，
不能静默补零。PC1/PC2/DM旧capture payload及独立PCP2持久进度格式不因此改写；
CVS2的执行身份也由root推进到新的语义版本，旧阶段同长度的状态不视作等价。

本阶段新增 `Dc2ParticleTests` 82项、`Dc2ParticleHostTests` 37项，平台 `Dc2ParticleOutputAudit` 17项，
另有6390项既有独立core回归和51项既有DC2平台回归通过。实际渲染比较覆盖24/32位mask、双层顺序、
反复render不推进RNG、整数反馈、旧UI raster、恢复后重建和当前偏好的速度门限。
当前速度偏好属于session保留的设置；比较恢复路径时需要使用相同当前偏好，不能把它当作故事槽强制恢复值。

独立 immutable PCH7 class 的真实数据 smoke 又通过 `START → 03 → title_02 → start → fst_1216_a1_cmn` 前20句，
2521步、4次CVS2逐字节恢复、12张CRX（含SAKU_YUKI02）和5个PCM容器读取。
日志是 `effects-stage2/dc2-pch7-startup-20.log`，class与观察到的source哈希分别登记，
不会把root交接后继续修改的共享source冒充该编译产物。媒体完成仍由审计模拟，不证明听感或整部演出一致。

后续已把这项验证接入正式 `NativeStartupAudit.java`：同时解析live/capture的真实粒子atlas，
验证DCP1完整状态可无损冻结，并独立比较新VM恢复前后的粒子状态。冻结当前源码的
`dc2-final-current-20261103T200933076336Z` 回归通过6528项focused core及68项桌面断言，
并再通过真实DC2同一路径20句。记录2026次live状态验证、3次capture验证、4次粒子快照比较，
其中2次具有10个active kind6槽位，实际解码SAKU_YUKI02。清单记录源码、class与静态EXE哈希；
捕获期间及本次测试结束前共享源码均未改变。该目录 `REPORT.md` 分列真实资源/VM、合成fixture，
以及媒体结束、时间步、菜单输入的模拟；没有把headless逻辑capture视作实际旧UI raster对照。

仍未完成：kind2雨使用另一个内核，现精确报错；活跃粒子与5E移动反馈交织的统一journal未核，现精确报错；
32在dirty后没有02延期标记的演员协程尚未完整还原，现精确报错，不能借4B准备来伪装已实现。
BE8BC8的历史/演员未知producer仍按F14拒绝，原生system.dat设置导入和完整演员/排字/历史绘制也不在本阶段完成声明中。
便携40ms运算分区一致性不等于Win32消息投递与墙钟调度一致。

### F19：DD 绑定控件的 x87 运算、可变裁切及 modal clone 必须分别还原

窄核目标是 `D:\gal\DC3DD\DC3DD.EXE`，SHA256
`ff119f4396cc1382e349fb1ed2c2eb77191db8ec699cb1352ca021cc73407e8d`。
`dd56-stage2/bound-native-contract.json` 保存当前代码区间哈希、指令转储、解释器与参考结果哈希。
这里直接核了 x87 指令，不把 Ghidra 的 `extraout_ECX/EDX` 当成丢失的坐标参数。

`42B56E` 的 state5 把参数0/1/2作为left/right/count，参数3×1000+参数4作为编码变量，
调用 `412F70`，不产生drawable。原生先用 FILD/FIDIV 对整数差与count-1运算，
只在 FSTP32 时存float步长。`DdBoundControl.Binding` 独立保存left/right/extraRight/count、
编码变量、step原始位、leftOffset和musicSeek标记；变量编号按signed IDIV的 `/1000,%1000` 分解。
默认memset的正零位与 state5 实际算出的负零必须区分。保存格式DDB1验证步长来自原生配置，
不能让损坏的状态任意注入一个有限float。

当前 `416840..416BB5` 的100/101控件将中心设为
`trunc(step * minSigned(value,count-1) + left)`，然后减去原source crop宽度的整数半宽；
y保持不变。>104且buttonState!=99时，把source crop的right改成
`trunc(step * minSigned(value,count-1) + sourceLeft)`；没有通用的下界0钳位。
native `441130` 读的是 `440FC0` 保存的crop，不是缩放后的目标包围框。
例如left100/right410/count31的step为41255555，末端中心实际截断到409；不能用round或强制right来美化。

这些更新属于可变sprite owner。active把fill裁到零宽后，idle99跳过更新并保留零宽；
渲染器若每帧从最初的quad临时重算，就会错误恢复原宽。helper只返回纯几何结果，
公共frame必须提交并保存其变化；UI origin的最终加法也属于绘制owner。
方向5/6仅在变量正好等于0或count-1时不写，越界值不自动钳位。
拖动先按left+leftOffset和right决定端点，中段有严格浮点阈值；实际除式只减left，
没有减leftOffset。音效、动作、按键消费、repeat与释放时序由调用者独立接入，不能用“改了数值”代替。

另一个确定差异来自 `417E20` 的模板克隆。当前机器码 `[417E20,418110)` SHA256 为
`97a86ba271cbddf943a5cb789953ea42723eba86dce8816b508f3ac3a6038675`。
尽管函数把源button整块复制到栈临时区，实际重建只复制rect/action、sound、两份voice和指定flags；
它没有调用412F70或复制31C..330，因此新button的Binding是 **memset全零**，不是源模板Binding。
克隆也只枚举source的+30 button与+50 sprite列表，不枚举直接quad的+90列表。
输出backdrop/caption来自source sprite99/选中的sprite2转换，不能直接 `copyPanel.quads` 冒充克隆。

typed state6/8/10/11并非原始state直接绘制：REA已另确认418D60将前三种转100/101/102，
418D20将11转3，再进入同一个417A10/+50 sprite列表。它们会被克隆保留为转换后的sprite，
也不能因为不是普通quad而把它们统统丢掉。
`415CC6..415CD7` 把440430返回的映射后全局mouseX直接写入帧局部，未减UI+394；
克隆也没有把modal origin写进button264。不得自行加入拖动坐标平移来“修正”原生quirk。

纯helper新增271项断言：9组当前机器码配置、152行独立整数/x87指令解释参考值，
以及保留crop、字段保存、损坏拒绝和零配置反例。解释器只在私有内存中解释选定算术块，
变量访问与有限范围CRT截断是显式纯fixture hook，不运行原EXE或OS API。
4550D0对NaN/Inf/超int32转换有CPU分支差异，helper保留原始配置位，但在实际几何使用时精确拒绝，
不借Java饱和转换伪造原生结果。此处交付的是可接入的算术合同；完整DD公共frame、modal生命周期、
输入/音频消费者与typed wire整合由对应阶段另行验证，不能把helper测试计为整个DD56完成。

### F20：DD56 是独立提示框，必须保存构造块、公共帧及跳转后保留画面

本阶段以 `DC3DD.EXE` 的 SHA256
`ff119f4396cc1382e349fb1ed2c2eb77191db8ec699cb1352ca021cc73407e8d` 为唯一原生身份，
使用用户指定 `D:\gal\rea` 静态核实，不执行原EXE。
完整入口、机器码范围哈希与消费者合同在 `build/native-rewrite/dd56-stage2/dd56-stage2-contract.json`；
独立编译、断言与实际资源绘制结果在同目录 `f20-verification.json` 和 `desktop-audit/result.json`。

`430606` 的56只消费自身短记录，使用模板20、caption选择34D0、93:19复选值、原生位置和初始focus0；
`4331BE`等待该独立模块关闭，不能别名普通55确认框。
DD34现绑定 `ui.button_checkbox`：`412DE0`写的是正在构造的按钮块，而非列表末项。
首次构造前设置的flag可留在未开启的构造块中；已有按钮后则设置当前块；下次构造先提交前块再清零，
`4175F0`完成构造也清块。实现显式保存buttonOpen和pendingCheckbox，不能凭`:c`动作猜复选框。
93 setter会更新实际复选值，正常55和新56共享经过核实的builder及精确flag。

模板克隆严格遵守F19：不复制直接quad、header/text或原Binding；
state6/8/10/11只在源builder边界转换为100/101/102/3，state5配置不产生drawable。
新modal panel显式标记nativeModalEntries，旧direct UI仍保留自己的raw marker投影，渲染器不再二次猜状态。
克隆Binding为memset零；100/101位置及>104裁切由公共frame提交到owned sprite，idle99保留上次裁切。
modal origin与可变sprite坐标分开保存；全局mouseX直接进入拖动运算，不擅自减origin。
截图、preview及SceneCaptures都消费相同的resolved复选sprite与保留画面，绘制查询不修改几何。

`433F8C..434270`的公共尾部仍在56本地输入后执行。
全局left/right使用action2/3，方向使用4/5/10/11；设置action0/1会清对应2/3，7/8/9是原生default。
同一帧可由本地关闭再产生全局命名跳转，也可跳转后保留popup。
实现分别保存modal visibility与host wait，并以经过当前脚本label解析的pendingFrameAction交给VM；
缺label按已确认分支继续，不能先清画面再让通用named-jump报错。
跳转后不再运行本地415C60，保留画面的focus、checkbox与sprite几何冻结；
例如checkbox本地切换之前遇到公共left跳转，保留的仍是该帧原复选图像。

物理按键也按实际表转换：共享Java Enter=10映射原生Enter并接受，Space32消费但不关闭，
PageDown34仅触发已确认的公共left路径；鼠标right/cancel会被该模块消费。
idle16只在严格超过60000ms、device可用及cursor条件成立时查询，鼠标位置或显隐变化重置，普通键盘不重置。
原生input对象的active owner是D3D device，不是应用focus；未用AWT窗口焦点伪造它。
非空音乐callback会抑制已确认的window6/idle分支，其音乐查询间隔为严格超过500ms。

DD专属host格式升级为DD41，DDM2附录保存模块类型、精确checkbox、构造块、origin、retained panel、
pending frame action及idle owner；PP/WY不写入该模块。旧DD3C迁移测试使用独立冻结fixture，
不再截断当前格式来假造历史输入。global故事执行合同已升级，旧便携存档不冒充新原生执行合同。

增量验证通过90项DD modal断言、F19的271项绑定控件断言，以及75项实际DD START/MES/CRX资源绘制检查，
含五处真实56、复选状态、克隆零Binding、公共跳转后的保留画面与快照往返。
这些证明当前实现和选定原生规则的符合性，不是原EXE逐帧像素比较。
普通direct CONFIG仍是部分投影；button51/52语音、嵌套活动55/56和未收齐的Windows cursor/device时序明确拒绝或列为未完成。
完整WndProc、OS指针warp及所有UI页面尚未等价，因此本阶段不宣称整个DD UI或整个引擎完成。

### F21：PC1 系统调用、输入事件和存档候选分别持有状态

当前可读汉化样本 `DCPCDVD_CHS.exe` 的SHA256为
`7988971ff38f6b0fb2f21827b1ba38c3c7fe5cc824420b179ea9d9a838872057`。
REA、当前机器码和全部真实调用点的证据在 `pc1-context-stage2/`；没有执行原EXE。

`1B/25/26` 均无条件跳过下一字节，读取原始NUL串；marker不是编码或业务opcode。
`1B` 在消费尾串前保存独立候选帧，`26` 在消费尾串后保存系统返回PC。
`25` 对无效资源选择值仍消费串，仅0/2/3有资源写入；texture selector5/6属于独立native cache，
不是后两份named资源，现明确拒绝原有假映射。结构索引不收录owned串内标签；
运行时跳入该处仍重新按实际PC取指，原生required-label扫描也可独立识别这些字节。

`Pc1SystemContext/Pc1Frame/Pc1SaveContext/Pc1Runtime` 分开表示系统调用帧、候选帧、候选选择策略和现场服务。
`MesVm` 的typed26/1C与输入事件入口持续使用同一个Variables对象；返回不恢复整个VM、普通变量、shadow/history、
持久设置、进度或物理音频数组。恢复调用方代码时先核profile/SHA/PC，再应用owned状态；无caller的1C直接拒绝，
不借DM返回服务改变其他owner。普通变量在系统菜单里的修改会保留。

frame已核的owner包含背景/两actor、6/7 overlay路径、三base图像、坐标、两action、100字节已核scratch前缀、
逻辑BGM请求与packed SE请求。40C的list/rect/边界/offset单独恢复；只有历史active/selected及选中项身份属于
该历史游标保存，不回滚entries、滚动、预览或变量历史。43D410是历史重画游标，不能错存为资源21；资源21继续现场持有。
1C显式清理pending绘制，不把调用方active按钮整表重新放回。完整native文本栅格和0x1F44每个字段仍未全部映射。

普通right/Esc在flag0时先刷新候选、停止voice再调用412160；toolbar菜单是stop→条件刷新→stop→call，
直接OPTION等是stop→条件刷新→call。事件捕获使用VM已消费的PC，不是host保存的上一条07地址。
显式26不额外刷新候选或停止voice。mode0/2保留候选flag，loader1/3只清flag而保留候选。
内部wire为PCH8和P1C2/P1F2/P1S2；pending native slot必须与host.saveRequest一致。

脚本读99:244/245/246均没有native case，返回0；写246无效果。平台列表offset用单独接口，
不能继续通过假脚本寄存器控制。bank90超出已核100字节时明确拒绝，未把尚未定位的邻接alias伪造为0。
backlog走原生checkpoint/log重画，不是412160调用；该重画生命周期未完成前入口明确拒绝。
原版保存/载入/快速槽位桥接也明确拒绝，不能用当前VM或26调用帧代替独立候选；用户F5/CVS2仍是独立便携快照。

helper合成测试1077+1062项、VM/host集成58项、raw字符串2311项通过。
全960份PC1脚本中的29个26和661个1B点均核对raw tail、SHA、两类不同PC及codec往返；
这使用真实脚本和合成host/2×2像素帧，不是29条路线执行。
`Pc1NativeSystemAudit` 另以真实START、ADVMENU、OPTION和CRX通过23项检查：三条平台入口、实际1C返回、
菜单普通写保留、新VM菜单快照字节和像素一致。启动输入/时间/媒体完成由工具模拟，
没有原进程像素或时序对照。完整槽位重建的新增独立证据见F22。

### F22：PC1 原生槽位必须按加载重建合同投影，候选PC不是最终恢复PC

本阶段只读分析可读的 `DCPCDVD_CHS.exe`，SHA256
`7988971ff38f6b0fb2f21827b1ba38c3c7fe5cc824420b179ea9d9a838872057`，
未访问被安全软件拦截的原版，未执行原EXE。用户指定REA的四批新结果、当前Capstone区间哈希、
完整子调用表和缺失owner清单在 `pc1-slot-stage3/slot-reconstruction-contract.json` 与该目录README。
该阶段没有修改生产runtime源，也没有把分析完成等同于槽位桥接完成。

`41AAD0` 的写入顺序现已明确：`404E70`先调用`404EE0(1,1)`，根据现场9:0/9:1重算现场9:2星期，
再更新候选9:2、9:20及本地时间9:21..25。之后格式化`dcpc%02d.dat`路径，
调用`4027E0`保存独立现场 `system.dat`，把候选首DWORD写为1，再打开并写0x1F44字节槽位。
`4027E0`保存的是454CD8起0x6D4字节，包含现场偏好、100个global、512个CG字节和1024个scene字节，
不是剧情帧。原版未检查global保存返回、槽位write长度及close结果；metadata也在槽位open失败前已经改变。
便携实现仍须明确报告文件失败并保留原文件，不能把这种原版未检查I/O当作完整保存成功。

`41A970` 检查的是一次read返回0x1F44和首DWORD1，而非整个文件恰好等长；尾随字节不被读取。
通过后先把working9D3F00复制到backup9D5E48，才进入`41A620`释放旧图像、恢复backup并重建。
来源、候选9CDD28、working与live9D8588应保持各自owner，不可互相别名到可变Java对象。

恢复PC的决定性证据是当前机器码：`41A745`整块working→live之后，
`41A748/41A74E/41A754`又从保留寄存器回写逻辑music、PC与wait。PC取自
**保存的checkpoint index→新加载MES checkpoint表**，不能直接使用保存帧的PC字段：

| checkpoint头 | 恢复native code PC | wait | 额外效果 |
| --- | --- | --- | --- |
| 1B | tablePC+3 | -1 | 从头a0恢复候选flag，并把原working复制成候选；候选内部保存PC仍不改 |
| 1D | tablePC+3 | 1D | 预读a0/a1后又被块复制覆盖，最终参数来自保存working字段 |
| 07 | tablePC+3 | 07 | 后续还要重建消息历史与工具按钮 |
| 其他 | tablePC | -1 | 不跳过3字节 |

`checkpoint_projection.py`只在私有数组内解释当前PE这些指令块，五组刻意不一致的saved-PC/table-PC、
wait、music和参数反例均通过；不调用OS，不运行完整loader。便携filePC另需加versionOffset。
`Pc1Frame.returnPoint().pc`目前是捕获候选时的cursor，不能因此声称已实现上述native load projection。

`419E40/419D30`现确定为独立fadeout/fadein：分别捕获旧/新前台图像，每40ms按当前速度或Ctrl/latch
把progress增加1/2/4/8；`408880`按捕获RGB的weight/16绝对缩放到黑或从黑显示，
不是对变化中的目标画面反复alpha混合。最后progress16仍拥有40ms。
退出由`405710`的Win32消息泵决定；fadeout完成留下BB8434，fadein完成才清除它。
`41A620`在成功和过渡被中断时都可返回0，不能只看返回值就提交“加载完成”。

完整子调用还确定了几项不能被通用snapshot替代的owner：

- `41A620`在六个base handle重载前，显式复制working+204/+284的演员overlay路径；
  `4111A0`由它们重载overlay并生成mask。stage3发现的`Pc1Frame.SLOTS`缺6/7已在后续typed候选保存中补齐；
  这只解决overlay名字owner，不代表原生mask、整个加载重建已完成。
- `415D50/416220(...,2)`只根据当前body/name模板计算消息窗口位置、pitch、style和bounds，不绘制。
  模板9D7D90/9D8020、count和窗口图像handle在故事帧之外，须有已初始化的现场owner。
- 普通分支经`412260`选toolbar/choice缓冲，`411D30`烘入pending quads及caller内文本offset，
  然后绘制背景、actor/mask、overlay，fadein之后才恢复BGM和带100h标记的循环SE。
  BGM逻辑请求、现场物理播放/渐出/MCI backend、native voice2..9及SE1不能用六个音频数组整块回滚冒充。
- 07还调用`418220(-1)`，消费frame+494的history count与+49C的ushort checkpoint历史及高位分页标记，
  并依据当前text-speed重建名字、正文、排字和inline regions；随后`412320`附加extra region记录。
  单条body或Java prose history不是已证明等价的原生owner。
- 1D跳过上述普通背景/文字重建分支，需要独立service scratch/consumer合同；
  结束还恢复native timer1、音频timer2和PC1效果timer5。PC1效果1..4不能借用DC2的5/6内核。

安全的typed槽位桥接应先验证不可变候选、caller身份与checkpoint投影，再按有版本的phase逐步提交资源、
绘制、文字和音频副作用，并保存已提交阶段以避免恢复后重放。overlay、native history、条件pending绘制、
完整scratch/未映射frame字段、service和音频/调度owner尚未收齐前，应对相应路径明确拒绝。
本阶段把若干“未知helper”细化成上述规则和缺口，没有把原生0x1F44直接当成完整引擎状态或Windows槽位互通实现。

F22后续纯helper已新增 `Pc1SlotProjection`：使用`headerOffsets+versionOffset`，明确保留候选rawCodePC、
最终resume code/filePC、signed header、wait、1B刷新及flag、保存帧原参数、重建期间现场music与后续保存music。
不会把PC1零填充的`headerMetadata`当成原生checkpoint地址表，也不要求静态record边界。
该helper原参数值仅指41A754后的投影阶段；后续419D30还把9DA4C4作为fade进度重置0再推进16，
不能把阶段结果误称整个load结束后仍不变的参数owner。
`Pc1SlotProjectionTests`通过79项反例；冻结Java8源码的全实际表审计通过960个MES、77787个checkpoint，
其中574个1B头（325个flag0仍须刷新），94个地址不在静态record索引但仍位于拥有的caller字节内。
实际语料未观察到07头，07分支由当前PE的私有CPU指令参考反例覆盖，不能宣称观察了真实07加载路径。
每个MES SHA、投影digest、源码/class哈希及范围在`pc1-slot-stage4`，仅验证投影；
native arg0/1、完整历史/geometry/audio/service等owner仍由下一阶段接入，原生槽位I/O与完整重建继续保持gate。

后续 `Pc1MessageGeometry` 已独立还原415D50/416220的param5=2准备路径：只保存43D438..43D468的
13个global，frame的9DA4B0 headY明确作为body准备入参/返回值，不能被globalcopy或P1G1 codec带走。
直接消费未改动的4组当前PE私有CPU参考（完整两个函数mode2路径，共781 steps），97项Java8断言通过。
body当前style的机器遮罩是0000FFBF，连高16位也清除；history/name字段不套该遮罩。
空列表保留旧global，最后有效type10写入获胜，条件按condition0或非零cachedRead判断。
原生CHS13字段初值与literal遮罩机器码、source/class/reference哈希在
`pc1-message-stage3/geometry-helper-contract.json`，API/owner范围在`GEOMETRY_HELPER.md`。
这仅完成几何准备与独立13字段保存，不把glyph、inline link、实际绘制或整个native slot load记为已完成。

后续独立 `Pc1TextBuffers` 明确了4个互不别名的展开结果owner：live正文9D3AF8/1024、
live名字9CCAA0/256，以及418220的local_400/1024、local_500/256。
4123A0只覆盖新前缀及两个NUL，之后的旧尾部保留；case4C无前置4B时以及redraw入口的名字清空，
都只写首字节，不能推广成清除全部数组或补第二个NUL。
当前CHS PE中两个live区位于.data的rawfile外虚拟区，启动映射为全零，故初始全容量已知；
redraw栈尾没有该证据。每次beginRedraw将正文known重置0、名字known重置1，保留原始backing但不把
上次调用的栈尾冒充已知；同次调用中较短展开则保留已知stale suffix。
Layout源须取knownBytes并按稳定ownerID刷新，pending指针以ownerID+offset观察后续覆写；
fullBytes仅供快照/诊断，不能把未知尾部交给layout作为已知零。
P1B1独立保存全部2560原始bytes及四个known长度，共2580bytes，保留未知和stale bytes而不提升known；
它不属于1F44帧，不能被system26/1C复制回滚，只能进入完整portable snapshot。
4组当前4123A0私有数组指令参考共10602 steps，整块结果SHA与Java一致；199项Java8断言覆盖容量、
双终止输入、未知边界、单字节清空、别名隔离与codec。冻结源码/class SHA及PE零填充证据在
`pc1-text-buffers-stage3`，这些测试仍不代表glyph、host/context集成或native槽位重建完成。

随后对root集成的只读复核，抓到并由root修复了三个具体边界：411670每次成功load须重置43D414，
否则新脚本首07错误继承旧4C；mode1/3须清BB8430而0/2保留；成功切MES后须立即重绑当前code owner，
不能等下一次fetch才让global type10 offset改读新code。dispatcher局部前一opcode与全局最后yield现在分别保存，
非yield的run预算结束及portable恢复不能被当作native dispatcher重入。
2A条件在绘制和捕获时按缓存99:251消费，捕获先深拷贝再冻结，不能移除/改写live frame的条件、binding或动画。
5C只记录loaded-code-relative原始串offset，producer不因为marker为K就做+20或提前Unicode验证；
全局template指针随当前code替换并按当前变量展开，不冻结到原caller的字符串或SHA。
quad-list新-3 codec保存condition与offset，同时保留-2条件列表及普通旧列表的读取；
actual draw/capture仅消费当前已知loaded MES范围，不虚构原生100000字节code buffer末端的stale内容。
新增 `Pc1ConditionalTests` 的131项真实Java VM/host反例覆盖四mode、缓存/seen分离、原始指针、
预算/快照边界和capture冻结；MES/NAMES为自编有效fixture，framebuffer adapter为合成1像素，
未验证原生glyph像素、完整07/1C历史重画或原生槽位I/O。源码冻结及并发变化检测在
`pc1-integration-review-stage4`；变化中的一次测试PASS不能冒充稳定源码验收。

独立集成复核还新增 `Pc1HistoryTests`，347项Java8断言通过：自编有效MES的真实03/K/L/07/09、
4F/17/08/45按钮生产者、菜单1C返回和实际label激活，分别验证PC不被回看推进、当前变量/姓名重展开、
旧entry0边界保留显示、P1B1不随frame回滚，以及全局story table较短覆写后保留stale选项尾部。
机器码40AAB0证实hit为0based；只有7/9的index>0成功label接受才减count，保留current checkpoint和旧word，
missinglabel/index0不清wait不改PC，真实0A/0B的label分支不pop。证据、冻结test SHA及逐条字节反汇编在
`pc1-history-integration-stage4`。这些fixture使用合成两像素framebuffer，不证明原生GDI字形、物理音频、
动态choice0/2、wait-1完整bitmap返回或Windows槽位I/O；最终整体源码/class构建由root单独冻结。

后续独立 `Pc1LoadFade` 仅还原419D30/419E40，而非41A0B0或完整41A620。当前PE的完整两函数私有
CPU解释42组、7980steps，调用/字段日志SHA与Java全部吻合，962项Java8断言通过。
C4是外部frame argument1别名；IN/OUT两flag写序不同，帧计数为signed ADD后只钳上限16；
draw/present在消息泵之前，最终16帧仍拥有40ms，计时差为u32。中断先于该次clock检查，保留捕获句柄与flags，
normal OUT只清94C698、IN清两flag。P1E1只存局部phase/startTick/opaque handle，不复制外部C4、flags、
图像资源或时钟epoch；回调执行中拒绝snapshot/reentry，安全边界恢复不重复capture/draw。
源码/class冻结、现PE逐字节asm、完整call journals与typed Host合同在`pc1-load-fade-stage5`。
其draw/present/pump/clock/skip/capture/release仍是外部明确hook，不宣称已实现Win32消息投递、408880像素、
slotload平台绑定或原生槽位互通；完整41A620继续保持gate。

### F23：PC1 原始日志、文本内存、绘字与现场历史

以当前CHS PE `7988971f…872057` 为目标，独立原指令私有数组参考还原4123A0展开、
416D80/416E50与417410字节布局及4170C0绘图命令。`Pc1TextExpander`、
`Pc1TextBuffers`、`Pc1MessageGeometry`、`Pc1TextLayout` 与 `Pc1Runtime` 分开保存各自owner。
字节分类包括该PE初始CRT表中的高字节反例，不能用Unicode字符分类或统一SJIS长度代替。
原生按固定两字节推进且不自动换行；字形限1000但光标继续前进，ruby及dictionary保存原始
指针别名、known backing与stale suffix。只凭传入字符串的Unicode长度无法重现这些状态。

03/04/07建立的1000-word raw日志独立于expanded body。418220按版本相对header表读取
当前loaded MES、当前变量/名字重新展开，-2在paragraph路径省略当前body。历史输入不换VM、
不恢复Variables或undo；最早entry0再次PgUp仅刷新controls，不重新展开文本。
1C先还原frame再mode0 loader，以已验证SHA/PC的caller做07/09重画；global text buffers不回滚。

9CFC70故事按钮表、BB8450工具栏prefix、9B8E1C完整选择数量均在frame外：17(1)写prefix及
保留旧tail，17(4)不会改此prefix；08模式1追加template3[0]，45随后写实际选项；09保存full count。
回看时只开放prefix，关闭后重开当前完整表。菜单中重写这个global时，1C不会恢复旧按钮表。
mode0/2需要40B900动态caption producer，目前严格拒绝，不误用模式1模板。
成功的7/9普通label点击只在index>0且lookup成功后减logcount、设wait=-1；missinglabel保留现场。
空白left不能继承wait12的action1；选择Enter需要独立40A6D0 focus owner，目前明确拒绝。
这些owner以P1R3进入整体portable snapshot，不能塞进P1C/P1F伪装原生帧回滚。

桌面/共享Android绘图消费raw glyph journal，每批先non12后12。40底色、80阴影、
16分制逐channel混色和@K underlay恢复有原指令trace/合成像素反例。MOVSX使raw819C不走
反编译C表面显示的替代分支；8145才使用43D9FC当前CHS替代。证据在`pc1-glyph-desktop-stage3`。
字体最终栅格仍是AWT/Canvas，full clip、BB844C/空正文、selector0完整conditional composition、
实时reveal/Win32时钟、wait-1完整bitmap恢复仍是边界，不能称像素或帧等价。

当前本机没有jisho.mes，960份PC1中未观察49、1D7或literal81m/81n；不排除宏注入。
当前CHS的1D7是404EE0已证default return；字典真正query consumer49比较frame外BB84F0。
inline区域布局有合成原指令证据，:w/jisho/49完整调用bridge仍拒绝，未推造一个字典弹窗。
对应证据与反例在`pc1-jisho-stage1`。完整Windows槽位41A620重建仍未完成。

### F24：PC1 native frame格式与返回过渡

`Pc1NativeSlot`只执行41A970读取前1F44和signature1的准入，不把C串/计数/资源路径验证
倒塞进reader。完整原始输入与native8004 frame分开，file trailing没有被native读入frame；
known C串可跨128byte字段，但不得读到未知frame外。metadata只补已证七byte与signature，
不写system.dat或slot文件。NativeSlot无MES SHA，必须在独立确认的game/caller中投影checkpoint。
fromNativeSlot保持全部1000个ushort的pagebits与inactive内容，head/wait/args保留32bit；
当前typed owner不能表示的checkpoint/count/cache明确拒绝，raw parse仍完整保留原样。
本机没有dcpc*.dat样本，没有声称真实slot互通，41A620全loader继续gate。

`Pc1LoadFade`由42组完整419D30/419E40私有原指令日志驱动：C4就是frame argument1，
normal/interrupt不同清理、speed3短路skip、u32时钟、末帧40ms与callback重入均独立保存。
P1E1仅局部phase/tick/handle，必须与Host的global/capture/逻辑epoch一并恢复。
这不是41A0B0；后者progress实为frame外9CCA8C，不能污染恢复的argument1。

`Pc1ReturnSurface`依据4079A0 packed/tail kernel及完整41A0B0 calltrace保留反馈后的front。
ratio为3/6/8/10，当前front参与下一轮反馈，最后407880才精确复制目标。
它区分临时capture退休与front像素继续存在；中断不被视为commit。
Runtime普通mode0的wait-1使用原capture，07/09先rawlog/currentvars重画；active期间取指和普通
输入阻塞，lastYield与loopSE在提交后处理，portable中途快照保留当前front和caller。
wait-1 front到显式便携full-frame发布才替换，变量opcode/缓存revision不退休。
原生21仅后台target绘制而非前台提交；retained状态下21、角色33、条件12、滚动5E及其它
未映射partial producer在改变资源前明确拒绝，未把Java publish冒充原生fullpaint。共享前端仍是便携时钟；
无完整Win32消息泵，特殊reason4跳label、其它wait及所有partial-GDI仍未验证。

在复核411D30时修正了上一阶段遗漏：5C的source始终逐byte加20h后才展开，
不能因arbitrary companion marker而跳过转换。它的两个2048-byte局部区也不能
套K/L的1024-byte scratch。263个PE机器码循环参考共14691steps，256-marker的真实VM
fixture及current-code/capture反例覆盖此修复。原源码仍只在producer记录offset。

证据分布`pc1-native-slot-stage5`、`pc1-load-fade-stage5`、`pc1-return-surface-stage5`、
`pc1-code-text-stage5`、`pc1-return-platform-stage5`。最终平台与source稳定性以
`stage5-delivery.json`为准，变化中的局部PASS不代替最终交付。

### F25：PC1 图像堆使用单一物理存储与显式 Guest32 地址域

`Pc1ImagePool` 是100个128-byte图像记录、resident name、shape、实际allocation与
失败占槽/orphan状态的唯一owner。真实HeapAlloc(flags0)的初始像素、行padding和额外
1byte guard不被Java零填充替代；Window image0借用原DIB lease，长度仅1440000，
不虚构guard，也不借HeapFree释放DIB。原生free返回false时槽位两pointer仍清除，
原allocation和guest映射保留；返回true才退休lease并释放地址范围。旧raw DWORD可
因已声明的地址复用规则指向后续allocation，不在指令末尾修复alias。

`Pc1GuestAddressSpace` 在同一个canonical native bus上将现有lease映射为不重叠32bit VA。
策略明确为`[20000000,70000000)`、8byte对齐、最低地址first-fit与成功释放后的复用；
这是重写运行时的地址配置，不冒称原Windows进程的堆基址或随机地址分配。
physical64地址仍保留独立authority，没有截断或Java object ID。像素只有一份存储，
raw pointer运算、跨相邻映射和固定PE/BSS alias都按当前raw bytes访问；对齐gap、未知
邻居与未映射范围精确拒绝。固定native owner与guest arena/map碰撞在发布前拒绝，
不能用静态view优先级掩盖冲突。整个原PE4..12MiB预留，后续已知静态holes另行建模。

Pool P1IP v4保存raw记录和每个live lease字节一次，并承载唯一完整guest布局；P1O2/P1G2
不重复保存图像。物理地址可以在恢复时改变，guest VA与raw DWORD保持。旧v3 observed32/64
fixture合同仍可读，禁止静默改解释为guest。外置Audio ABI等owner可用明确type/id、长度、
physical kind和saved VA引用，必须由其codec验证字节及生命周期后stage；完整layout不再存
第二份，opaque服务leaf不被补成零指令。它不等于CRT、栈、COM对象图和OS handle已完成。

`Pc1Compositor.deferred`允许实际401000前建立同一图形owner，但不制造front或提前调用
fullclip/resetDirty。CHS PE初始dirty四DWORD为(800,0,600,0)，base/current clip全0，
active0；实际401000只发布DIB metadata，后续4076B0/407680须按真实启动时序调用。
两world临时共借同DIB时以显式space选择VA，旧world不会任意被替换为staging地址。

独立冻结证据在`build/native-rewrite/pc1-guest32-stage28/attempts/20261106T042628Z/`：
19个focused suite共6000断言，涵盖DC2 CRX/rain、glyph storage、图像pool/codec和新guest反例；
当前CHS401000/401050/402000/4020B0/401130共6个私有机器程序363steps，对比全部12800
metadata/name bytes及完整allocation；原版1698个CRX经实际WindowsA/CRT/NAMES/HeapAlloc
共28842检查，visible哈希与此前独立解码相同，3336byte原生padding保持；另实际Heap/DIB/
WM_PAINT/GDI24项验证零upload、全1.44MB DDB像素与physical64重绑定。原EXE从未执行。
资源循环的1x1 front明确为合成已知fixture，DIB另用真实OS检查；地址放置及私有CPU API
leaf明确是配置/fixture。freeze期间只有Root的ScriptLoader/ScriptMemory发生外部变化，
31个持有family文件与冻结SHA全部一致，详`owned-source-handoff.json`，不为抹外部delta重跑。
这不是整作原生窗口/脚本/音频/存档的完整验收；CRX调色板allocation失败后的相邻metadata
consumer、64MiB provider/256MiB snapshot界限与完整进程地址空间仍有明确边界。

### F26：PC1 可选静态PE间隙不能冒称完整已初始化进程

`Pc1StaticImage`仅接受核实SHA的用户原CHS PE，私有clone保留不可变source；它不分发或
执行原EXE。明确phase为preferred-readable-section seed，原file prefix与已证BSS suffix
可在所有固定owner之后补holes。代码/常量区只读，`.data`每4096byte页只保存与seed不同
的owned bytes；同页Frame、模板、Font、ImagePool或Audio字段不被复制，也不被其codec回滚。
fixed-layout/exclusions/sourceSHA/phase全部校验后才恢复；不同layout或损坏offset先拒绝。
NativeMemory必须显式finalize，之后固定owner绑定锁定；Guest映射与固定区碰撞不靠优先级隐藏。

IAT426000..426258不把raw hint RVA冒充resolved API地址；本目标无TLS/relocation/delay等
目录，其他非零形式明确不支持。保守CRT exclusion为43E100..4409B0、BC5DA8..BC5F58、
BDAFC4..BDB33C；已先claim的真实owner如43E100共享RNG仍优先借用。PE headers、section外
padding、未知stack/CRT heap均不被补零。

生产graph接入必须在finalize前预claim真实异步/direct spans与所有会被物理OS改变的word。
例如HWND94C63C、HFONT/DC94C640..650、bitmap/DC94C654/94BDBC、effectHDC94BDC0、
processHeap94C668和mutex94C664须来自canonical typed/opaque view或显式exclusion；它们在
调用OS后的值不能继续用PE初始0表示。完整Guest OS registry/CRT/WinMain初始化与整图snapshot
仍由后续集成负责，本helper不自行启用全profile fallback。

证据`pc1-static-image-stage31`：39个411910/411B10私有原指令程序2014steps，覆盖极负/
wrapped alias、原始data/RO、跨code/row-pointer固定owner与BSS邻居；11组focused共11448
断言、实际CHS只读字节与Runtime-beforeWinMain+Font/deferred GuestPool135检查通过，冻结
source delta为空。Core49项使用自制PE；实际审计的NAMES是fixture，未运行游戏VM或完整平台。
Readonly原文件SHA保持；可变gap48D58F通过真实Variables银行90访问，而live frame前的模板
尾byte已经属于DrawMemory，不能重复分配到StaticImage。详情见阶段README和immutable result。

### F27 — PC1 原始 OS DWORD 与实际 GDI 对象已统一；完整平台恢复仍须分阶段组装

当前 CHS 的 `Pc1OsWords` 只占有已证 BSS 字段：94BDBC/94BDC0、94C30C、
94C63C..94C668、48D580/584/588、949868。它们在物理创建前确为已知零，
不代表后续 Win32 API 已成功。FILE42AB1C 保留原 EffectPe/Audio owner。
`Pc1OsRegistry` 只关联标量句柄与真实 provider：可如实记录32位创建值；物理64位值用
同一 Guest32 allocator 的 opaque scalar token，不能截断、取 Java object ID 或伪造
COM/vtable 字节。标量 namespace 与内存 VA 可以同数值；同域 live 对象不得被覆盖，
不同存活对象恢复时也不得合并成一个物理 lifetime。FILE 低2位 tag 仅对 FILE 生效，
有 Audio 实际自建文件测试，不推广到其它 HANDLE。活性从原物理 owner 每次查询；
关闭保留 stale rawword，未知关联失败则保留明确 unknown，而非伪造成功0。

GDI bound ABI2 在原始 MOV/API 点发布或晚读：404230 只创建 DC/DIB，先发布 DC、
ReleaseDC、实际 CreateDIBSection/out BITS，再捕获 FONT28/DC、发布 bitmap、
SelectObject、保存 previous，最后 requested=0。它不提前核对其它4个字体。
4042D0 先按 uint32 requested²×8 清当前 BITS，再按原 fallback 读取 FONT12/FONT22，
真实 SelectObject/GetTextMetrics 后提交 cached requested，才读字符、调用 GGO。
GetTextMetrics 失败保留原输出字节；未知字体/参数拒绝时保留已经发生的清零前缀。
4042A0 按 previous/DC、SelectObject、重读 bitmap/DeleteObject、重读 DC/DeleteDC
执行，未清任何 engine rawword。物理 bundle cleanup 是另一项操作。

`NativeGlyphProjection` 不再提前冻结 mono。每行在 destination clear 之后按原顺序
读取当前 ascent/BITS；真实 negative cache cursor−1 会令48CF50开始的1600字节清零
覆盖 DC48D580/previous584/BITS588，首个 mono 读取应在0处失败，不能偷偷用旧数组
完成字形。清零和 replacement cursor 的前缀已保留，后续 key/size store 未执行。
新增 `consumedPrefix/liveRows` 保留原 array constructor，并只接收/读取已知实际行字节，
不替未用 row padding 补零。

FontSet 物理 capsule 独立保存创建参数/live；P1G4 只保存该 context 的实际 DIB bytes、
创建 header、选中对象引用、有限 DC 属性及 OsWords owner 摘要。FontMemory、rawword、
Guest layout 均不重复保存；registry 的 bitmap proof 只含实际 checksum。恢复先 stage
全部物理字体/DC/DIB，再恢复 selected refs、registry/其它 external，Pool 恢复唯一
Guest layout；OsWords/FontMemory 从各自 owner 安装后才 activate。新物理DIB仍保持原
guestVA、stale suffix，以及“FONT28 rawslot 已指另一字体，但当前 DC 仍选旧字体”的差异。
字体 program/ACP/face/DPI 在实际 provider 上验证，不以 requested size 猜环境。
旧 P1G3 仅为明确 unbound fixture；bound P1K2 不能隐式重建完整 OS 世界。

证据 `pc1-os-words-stage32/attempts/20261106T071734478898Z`：10组 focused5831，
3组实际自建 Win32 审计12884，17个私有原指令程序239674steps；包括实际GGO payload
送原4042D0的10组全 mask/stale scratch 对照，以及初始化失败前缀、原生销毁、raw alias、
新物理地址恢复和 changed-provider proof 拒绝。原 EXE/原游戏目录未写入或执行，本阶段
没有运行游戏 VM、使用真实游戏资源或对照原游戏截图。11项 owned source freeze 无变化；
记录中的外部 `Pc1Fetch.java` delta 归 Root，不冒称整个 workspace 同时冻结。

尚未完成：whole bundle 的 MainGraph 接线/完整 OS 重建仍归后续；GGO raw 输出指针当前
仅接受此 FontSet 已验证实际 scratch leases，其它 pool/PE 物理跨度仍需真实 leaf。
本模块只实现已核 BI_RGB24/32/no-palette header（16MiB provider 上限）；其它格式、
GDI_ERROR 后未知 local metrics、retained-deleted selected 对象、外来 DC/SaveDC 状态
保留精确边界。Window 独立 DDB/DC 的 proof 不能冒称其完整重建；COM ABI 不属于 scalar
registry。详见阶段 README、`os-words-native-contract.json` 与 immutable result。

### F28 — PC1 等待演出 consumer 已按原始迭代移植；平台叶与调用方调度须接同一图

新增 `Pc1WaitEffects` 实现4158E0屏幕效果、416620背景平移、4167D0角色淡入、
416A30角色移动与4047A0特殊模式 wrapper。每次调用只运行一次原生 consumer 并返回
原 int0/1，没有便携 duration 插值或自有 reveal clock。参数、当前坐标、9CCA8C进度、
capture/BUSY/delay 全部通过 canonical bus 读写；9DA4C8是8004字节 frame 最后DWORD，
未创建另一个进度/参数 DTO 或存档副本。只增加瞬时 depth fence，回调期间不能保存。

实际机器顺序包含反编译 C 容易遗漏的行为：角色淡入416943先将 argument1加一并写回，
再检查 capture是否为−1；screen cleanup先 capture=−1，后 busy=0；背景平移保留本轮
目标值，角色移动却在坐标 store 后重读目标。角色坐标 pair在入口按BB8400捕获，
之后 timer/skip 回调改side不切换 owner。fullClip/setClip后的参数和图像handle按原位置
晚读。signed wrap、负数余数、shake边缘19/20不对称均未“修正”。screen2在进度16
直接返回0，没有screen1/3/4/6的free/reset；kind5及超出switch的原生默认返回1且无其它
动作。缺素材路径清glyph count、使用原始名字指针绘字，并按真实DWORD/tail复制追加
43D8D8 suffix；没有从Unicode正文构造替代内容。

所有低层服务均 required：4020B0 createLike、401EE0 raw-address capture、401130 free、
409480 target、41AF50 redraw、411080 release、418220 text redraw、409140 rawGlyph28、
415D50 body restore、407040 EnsureTimer1、4118B0 skip及原生像素/clip/present。
40/10是写430284再调用真实Timer1 helper，绝不是resetDirty、等待毫秒插值或强制重启
已有token。404770/4051E0仍是必须真实实现的 minigame 叶，不提供空成功。叶失败保留
已发生的参数/handle/字节副作用，不倒回场景。生产Root graphics/input/timer/text services
与调用方wait/timer调度尚须接到同一canonical图，不能把纯模块通过算作整平台已完成。

`pc1-wait-effects-stage42/attempts/20261106T081953809834Z` fresh Java8全core编译、
WaitEffects34917与Root Timer1 99共35016断言通过，source delta为空。独立私有原指令
297程序/1299迭代/50698steps逐步对比完整8004B frame、相关原始globals、调用参数与
返回/失败前缀；含callback mutation、极值、原生default和结束序列。测试 payload由原指令
产生并压缩嵌入，Android不依赖build目录的外部golden文件。本阶段无OS/游戏VM/真实资源
或截图执行；journal中的叶是明确fixture，不据此推断完整像素与平台副作用。详见阶段
README、`wait-effects-native-contract.json` 与 immutable source/class manifests。

### F29 — 图形快照先完整解析，再接回同一图像池与物理字体上下文

`Pc1RenderContext.loadImage` 先解析完整 compositor/pool/glyph capsule，不提前分配、
绑定或调用字体服务。恢复方先构造同一 Guest32、Pool、Compositor 和 FontMemory，
`restorePixels` 只填入这些既有 owner；`finishStagedRestore` 验证字体物理状态和原始
缓存字节，不再重放初始化或覆盖一次缓存。截断、重复安装和验证回调改写原始字节均有
明确反例。Pool 保存序号可供外部物理服务引用同一份 lease，避免重复保存 DIB payload。

11 项核心检查及 7 项真实 GDI/render 检查通过。后者重新创建 Font/DC/DIB，按阶段接回
registry、唯一 layout 和原始 OS words；新 DIB 的物理地址改变，但 Guest VA 和陈旧尾部
保持。FONT28 的 raw slot 指向 FONT40 时，实际 DC 仍选中原 FONT28；继续一次真实
cache miss 后，像素与完整 render wire 均相同。此审计的 RGB front 是明确的 managed
fixture；完整 Window、音频、HWND 队列与整世界重建不由此推断完成。

### F30 — 设置 I/O 与变量消费共享实时 RAM，局部缓冲保留真实调用顺序

`Pc1SettingsIo` 按当前 CHS 的完整 402890/4027E0 执行：路径由当前 SAVE_ROOT 和
可变 PE suffix 复制到已知局部帧；读 open 仅两个参数，写 open 有 mode180。
1748 字节 read 进入局部 owner，close 先发生，然后仅 exact1748 按 437 DWORD 复制到
live settings；失败分支在实际位置读取当前默认模板。write 在 open 后消费实时 settings，
不提交提前冻结的数组。未知局部、路径溢出到 caller ABI、意外异常均在实际访问点停止；
普通 −1 结果保留原始返回/清理顺序，不用 finally 伪造 close。

52 组原指令程序、3149 steps、74620 次 global byte 访问逐序对照，77395 项独立检查
通过。Runtime 对同一 bus 只绑定一次设置/变量 consumer，并在回调中禁止全局快照；
Windows 启动服务要求真实 402890 consumer。before-WinMain 的设置为零 BSS，直接
RAM 读写不会自动加载文件或触发 bank100 persistence；显式接入后旧 convenience
importer 不再替换该 owner。旧便携前端仍可走其独立导入路径，不能据此宣称已整体替换。

### F31 — 原始语音源已接入实际 DirectSound，跨并发存储边界仍须补齐

41CD70/41CB10 admission 保留固定原始源指针，通过后才 stop/release 旧 channel2，
再进入生产 41BE20 raw 路径。资源名、路径、表、长度与最终 forward strcpy 在各自原生
消费点重新读取，允许第二次 strlen 在 metadata 副作用后增长并跨资源 cell 写入。
流快照保留实际 CreateFileA 路径与 offset，恢复时不重新解析已变化的资源名和目录。

stage31 冻结通过 12373 项检查，包括 15 组原机程序/2589 steps、391 项对应检查，
实际 voice/SE/大音乐文件、原始 ANSI 路径及流恢复。非法 CP936 路径的实际错误 123
保留，未伪造 replacement-open 成功。Java PDB6 与新增 native word 导出必须使用匹配
DLL。静态 voice 仍用既有 Xpcm decoder；生产 COM public graph/retained 生命周期、
无 native VA 的 fetched local-name route 及跨独立 MM backing 的单次 DWORD 尚未完成。
最后一种在最终 MOVSD 位置明确拒绝并保留已发生前缀，不以锁住 MM 线程替代原调度。

### F32 — 窗口图形可创建新的物理对象，并显式移交同一个 HWND

`Pc1WindowsWindow.State` 的 P1W1 保存真实 DC 属性、clip/transform、选择对象引用，
以及独立 DDB 的原生格式/stride/完整像素。DIB 在 Pool 注册时只引用对应 allocation；
未注册的 orphan 才由 capsule 保存一份 payload。恢复分为 stage、重建选择关系、唯一
Guest layout/原始 words 恢复和最后 activate，不重放 WM_CREATE、401000 或 paint。
同 HWND 的接纳显式移交 dispatcher 与销毁责任；abort 保留原窗口，旧世界关闭不销毁
新物理图形或已移交的 HWND。Pool 的真实 physical64 诊断地址随新对象改变，未伪造
整个 Pool wire 与旧地址相等；metadata、Guest VA、payload及角色引用分别核对。

最终 stage45 冻结通过39项新检查及321项旧实际检查，Java8/C/header源码均冻结无变化。
包含32bpp/stride3200/1920000B的 DDB、非默认 DC/复杂 clip，以及 raw FONT28 指向
FONT40、实际选中原FONT28且当前 glyph DC 又指向 Window DC 的交叉关系。重新创建
字体/DC/DIB并关闭旧 provider 后，继续真实 GGO和局部GDI paint仍通过。

本阶段要求显式同进程 HWND adoption；没有保存消息队列、菜单、timer、完整窗口状态
或原Windows进程。外来/retained-deleted对象、未支持的DC内部状态与完整整世界恢复
仍保留required边界。新 capsule 不证明生产旧桌面前端已经整体切到新原生服务图。

## 现有测试能证明什么

| 检查 | 有效证据 | 不能推出 |
| --- | --- | --- |
| 合成核心测试 | 指定规则的边界、拒绝和快照往返 | 所有原生规则均已发现 |
| 全语料解码 | 文件结构、指令边界、版本集合 | handler 语义正确或可玩 |
| `BranchAudit` | 固定初态/随机种子下的可达选择序列 | 启动/标题/系统菜单完整；所有种子；原生演出一致 |
| `DesktopRouteAudit` | 从原标题经过指定选择策略到结束的兼容层流程 | 所有结局、全部选择组合、真实媒体时序 |
| `Dc2SystemAudit` | 指定菜单、槽位、返回调用者、存读档闭环 | 原生存档格式互通；所有系统子模式 |
| 截图与捕获往返 | 兼容层内部渲染/恢复的一致性 | 与原 EXE 的最终像素或帧序列一致 |
| 解码器独立实现对比 | 指定输入的像素/PCM 算法一致性 | 原游戏的全部资源都已被覆盖 |

`BranchAudit` 使用剧情入口和旧剧情审计宿主，返回 MAIN 可被当作到标题的边界；
其媒体完成事件由测试合成。`DesktopRouteAudit` 以很大的时间步推进，并主动完成所有声音/影片。
这两者是有效的流程测试，但都不是实时引擎忠实度的完整证明。

## 重写验收清单

1. 固定各原版 EXE 的哈希与 MES 指纹，登记原生加载、取指、分发表、getter/setter、系统服务和绘制入口。
2. 共用实现必须列明跨版本相同的证据；一部作品的 opcode 偏移、默认设置、变量布局、图层容量不能自动推广。
3. 将“可识别、可解码、指令已绑定、子模式已实现、原版对照通过”分开统计。
4. 以原始输入和独立原生规则建立反例；避免测试仅复述实现或只检查不崩溃。
5. 对每个系统页面记录输入、变量副作用、目标脚本/PC、音频及渲染状态，验证返回时调用者。
6. 为随机、边界内存、复合记录、计时/中断和持久化建立确定可重放的基准。
7. 真实媒体与实时交互单独验证；原生参考轨迹只证明实际覆盖的路径。
8. 未建模债务和未知副作用未归零前，不标记“整个引擎忠实复刻完成”。

本文件不把尚未实施的建议计为已完成。其他版本的新增绑定、PP 变量重写及 REA 新分析结果，应随实际实现与验证更新。
