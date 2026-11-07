# 非 Unity MES 执行核心：原生契约与本轮重写

日期：2026-11-04。修改前代码：`93e0b0e3a4960e1147fabdbce705bb17f5fd24ed`。

本轮使用 `D:\gal\rea` 源码构建的 REA 6.8.0、Ghidra 12.1.4，结合 PE 机器码和整套 MES 数据核对行为。
不运行原 Windows 游戏，不写原游戏目录，暂不修改 Unity / CircusScr 前端。
本文件描述已核对的取指和变量子系统，不能代替整个引擎的兼容性验收。
尚未理解或尚未实现的宿主行为另见 `NATIVE_FIDELITY_AUDIT.md`。

## 同族引擎的版本差异

| MES profile | opcode 取值 | 扩展参数的实际消费者 | 普通变量 |
| --- | --- | --- | --- |
| `dcpc-2063` | 有符号 | handler `50 / 53 / 54 / 56` | 0..9 银行，饱和字节 |
| `dc2pc-5769` | 有符号 | handler `50 / 53 / 55 / 57` | 同上，另有 200..209 大端 32 位视图 |
| `dc2-3363` | 有符号 | handler `4F / 52 / 54 / 56` | 0..9 银行，饱和字节，无上述宽视图 |
| `dc2dm-9d72` | 有符号 | 取指器检查一次后继 `57` | 饱和字节、16/32 位视图及持久区别名 |
| `dc3pp-9872` | 有符号 | 取指器检查一次后继 `57` | 同类宽视图，持久区布局独立于 DM/DD |
| `dc3wy-a09f` | 无符号 | 取指器检查一次后继 `6D` | 普通字节写入保留低 8 位 |
| `dc3dd-a5a8` | 无符号 | 取指器检查一次后继 `71` | 普通字节写入保留低 8 位 |

表中的编号为十六进制。签名、opcode、参数区间、变量模型分别绑定，不按系列名称或固定 opcode 差值推断。
DC2 当前 EXE 与其 OLD 备份的相关函数只有在逐区间机器码完全相同后才复用解释；两份完整文件哈希仍分别保留。
PP 本轮结论来自独立定位的汉化 EXE，SHA-256 为
`19d9091a95613d757b7807af1ce839b65cbfd222e9a53422fd1010ba8ca210ab`。
其他汉化目标只对明确比对过的取指区间声明相同，不推广为整份 EXE 行为等价。

## 物理记录、取指与复合数据

`MesRecordDecoder` 是 Java 的结构读取边界，执行取指、标签扫描、块扫描共用结构规则，分别指定原生的有符号/无符号比较方式。
`MesScript.instruction()` 负责一次实际取指的参数和后继 PC，`MesVm` 负责执行及宿主调度。
宿主要求先等待时，不提前消费下一条逻辑指令。
结构索引只用于语料分析，不能代替运行时 PC。原生标签扫描若跳入扩展或求和项内部，执行器按该 PC 重新解码，
后继 PC 也不受此前静态记录划分限制；保存/恢复在等待中的 PC 不提前解码尚未获准执行的参数。

- 有符号取指器将 `80..FF` 当负数参与布局比较，先读取三字节；后面的无符号分发表边界使它们进入已确认的原生默认分支。
  这不是把未知表内 opcode 都当 NOP。
- 老 PC/DC2 的上述 handler 无条件跳过下一字节，再读取八字节参数，不检查该字节是否为惯用的扩展 opcode。
  其载荷按九个原始字节保留，Java `payloadOwner` 与 Python `consumed_by` 记录归属。
- PC1的 `1B/25/26` 还各自无条件跳过一字节，读取原始NUL串，不按marker选择framing或codec。
  1B保存候选时的原始PC位于尾串之前，26系统返回PC位于尾串之后；25的无效选择值仍消费尾串。
  owned数据不进入静态标签索引；原生required-label扫描或实际跳转到该PC仍按各自规则处理。
- 新版取指器仅吸收一次匹配的后继扩展。不能递归吸收整串扩展，也不能把老版任意相邻扩展都吞入前一指令。
  真实 PC2 `sce_other.mes` 中存在 `BF` 后接 `52` 的反例。
- 求和列表的项同样是九个原始字节，不按项首 opcode 递归解释业务命令。
  项首恰为图像 handler、标签或 STOP 时仍是数据；直到原生 companion 才结束列表。
- STOP 保持原 PC。已确认的旧版短终止片段保留实际长度和实际存在的操作数字节，不把缺失字节伪造为零。
  STOP 不使用这些操作数，非零的剩余字节也必须保留。

静态标签候选、宿主字符串、条件 companion、扩展载荷与即时控制流不是同一概念。
解析出边界或找到原生分发表，都不代表对应宿主效果已经实现。

## 格式识别与原生加载条件

`MesScript` / Python 工具可以结构化读取已收录的格式别名，并保留文件实际 marker。
`MesVm` 加载脚本时另行执行 `Profile.canExecuteVersion()` 的原生加载规则；此规则也覆盖跨脚本转移、系统脚本和快照恢复。

DC2 原生 loader `004137D0` 只接受 `63 33`。
本地 `kaiso_menu.mes` 的 `1A63` 与 `title.mes` 的 `3163` 虽可按同布局解析，不能据此让运行时冒充原生加载成功。
其中 `title` 还有四处真实跨脚本引用；这些路径是否实际可达、资源是否与当前 EXE 匹配仍待核验。
版本识别也必须检查整个 MES 集合，而不是只看 START 或多数文件。

## 变量读写与快照

`Variables` 重写为明确的字节银行、宽视图和宿主持久区委托。
PC2 的宽视图不能移植给 DC2；DD/WY 的低八位写入不能移植给旧版的饱和写入。
DM/PP 的 bank 10 前 21 字节与 bank 100 联动，宽视图写入必须经过字节 setter，不能绕过持久化副作用。
普通银行的正向越界按已确认原生规则读零/忽略写。PC1 的原生 signed 比较只检查上界，负 bank/index 可以别名到同一份已知 frame；97/98/100 的原生字节区也允许该 owner 内的相邻别名。其他尚未证实的负引用、超出已知 owner 的访问仍拒绝，不虚构原进程内存。

位标志的逻辑数量限制属于 setter/解锁清单。原生 getter 可读取相邻已确认连续存储和最后的填充位，不能误套写入数量作为读取边界。
PP 的原始持久字节、逻辑解锁投影、旧手机存档迁移分别保留；具体版本格式见忠实度审计及 `PpStateTests`。

新剧情快照使用 `CVS2`，当前开发源码执行合同为rev8（固定0.14.3包仍为rev7），同时保存脚本哈希与执行合同指纹，后者包含已审查的核心/宿主修订号、变量模型、
opcode映射、取指规则和raw string ownership。PC1系统调用与候选状态、DC2粒子/绘制准备、DD modal各有独立wire版本。
旧原生 `CVS1` 没有这些信息，且可能已经按错误变量模型产生剧情状态，不能仅凭数组长度相同就忠实迁移。
因此新原生运行时明确拒绝这些旧剧情槽并保留原文件；独立的系统进度迁移不受此限制。已知原创 demo 的旧格式仍可读。
未来更改这些规则或宿主语义时，须复核并更新合同修订号，不能静默重用旧剧情快照。

## 可复现证据与验证边界

证据目录为 `build/native-rewrite/`，不进入游戏数据或源码发行包。

- `native-analysis-summary.json`：REA 版本、源码提交、目标身份、已确认规则及未验证范围。
- `dc2-current-verified/`：对当前 DC2 文件校验完整 SHA 后重新提取的 99 项分发表、反汇编和区间哈希。
- `dc2-original-current-function-comparison.json`：当前 EXE 与 OLD 的逐函数机器码等价证据。
- `rea-pp-cn-core-batch.json`：新汉化目标自身的 REA 变量读写与取指证据。
- `translated-fetch-historical-comparison.json`：另外三份汉化 EXE 的独立定位及有限区间逐字节比对。
- `handler-extension-corpus.json`：老版扩展前置 handler 的全语料统计。
- `pc1-owned-corpus/corpus.json`、`jvm.json`、`comparison.json`：本批九个非Unity样本逐文件SHA、实际marker、物理布局、载荷归属和短终止片段对照。
  较早的 `corpus.json` / `corpus-jvm.json` 保留作rev4历史输入。

语料共5681个脚本，其中5680个结构合法，Python/JVM对6,293,176个记录及其载荷归属一致，包含1277个短STOP。
全部原MES的SHA与rev4相同。PC1有346份脚本的layout哈希因新增692个owned标记改变；
清除新增ownership的内存反事实检查可以重现全部960份旧layout哈希，记录数和字节边界没有改变。
两份DC2替代marker继续结构可读但执行拒绝；不能由解析成功推导原生loader接受。
唯一失败是 S.C. `drm_0621_b1_kko.mes` 末尾单独的 `4C`：字符串载荷和终止符缺失；没有通过宽松解析掩盖它。
这些检查不是原版与重写引擎的画面、音频、全部路线、全部系统服务的等价证明。

```powershell
python -m unittest discover -s tests
python tools/rea_static.py --rea-root D:\gal\rea --version
python tools/capture_mes_evidence.py D:\gal\DC2\dc2.exe --profile dc2-3363 --out build/native-rewrite/dc2-current-verified-new
```

最后一条命令要求新的输出目录，并在固定地址读取前核对目标完整 SHA；不能拿任意汉化 EXE 直接替换目标。

## rev6：PC1 byte text / current-code ownership

0.14.2 的合同指纹包含 PC1 raw 日志、四块展开缓冲、geometry/layout journal 与 dispatcher 状态。
1B/25/26/5C 都按 raw NUL 消费尾串；5C 保存当前 loaded-code 的相对 offset，生产时不按尾串 marker
做 +20 或 Unicode 解码。绘制及 capture 时从当前已知 code 范围重新读取、按当前变量展开；
原生大 code buffer 的未知 stale 尾部不会被虚构。quad-list -3 格式保存条件与 offset。

PC1 的本地前一 opcode、全局最后 yield、成功 loader 重置和预算边界分别保存。07 仅在
上一 yield 为 4C 时建立消息等待；09 会重绘当前 raw 日志。43D410 回看游标不是 VM 回滚点。
系统 1C 在校验 caller SHA/PC 后先还原 frame，再 mode0 load，07/09 才按当前变量重读日志。
1C 不回滚四块文本 buffer、ruby/dictionary globals 或现场 Variables。PCH9/P1R3/P1C3/P1F3
与原生 Windows 槽位格式不同；槽位完整41A620 bridge 仍未实现。

当前 CHS 的字节分类、ruby delimiter 和 CRT 前置表是指定 PE 的规则，不能声称日文或别的
EXE 自动等价。原生日志/布局不等于原进程字体栅格、Win32 消息调度或全路线验证。

## rev7：PC1 画面返回的协作阶段

0.14.3 的普通mode0系统返回在校验caller后仍保留现场Variables；07/09先按当前变量重画，
wait-1直接使用call_newback捕获的完整像素。41A0B0以40ms反馈步进返回，最终progress16的
画面还须持有40ms，之后才完整复制target、退休临时capture并重启已保存的loop SE。
进度是frame外9CCA8C，不能污染frame内argument1/9DA4C4。活动阶段阻塞取指和普通输入，
lastYield到完成后才更新；P1R4保留来源/目标/当前front、phase、剩余片段及原caller owner。
wait-1提交后的像素仍保留到显式便携full-frame发布替换，纯变量指令不退休这个front。
21h仅后台绘制，不据Java publish推断前台已更新；retained期间未映射的partial producer明确拒绝。
共享前端仍使用便携时钟/输入与AWT/Canvas；未宣称完整Win32消息泵及partial-GDI绘制等价。

5C生产阶段仍只保存当前code相对offset；411D30消费者从raw NUL源复制到独立2048-byte
空间，每个原始非NUL字节加20h后才4123A0展开。尾串marker不决定转换，不能按marker为K
才转换，也不能误用K/L路径的1024-byte scratch。263个当前原指令loop参考覆盖回绕及NUL。

Pc1NativeSlot只还原41A970的8004-byte读取/签名准入与lossless backing，不运行41A620。
文件trailing不参与native frame，原始归档保留它；fromNativeSlot纯投影不能回滚其它global。
本机未找到dcpc槽位样本，不能声称真实槽位互通或完整native load/write完成。

## rev8：PC1 同一份原生 frame 与取指顺序（进行中）

PC1 live frame 为8004字节。普通变量、1000个预留日志word、bank90及已命名DWORD都直接读写它；bank90并非独立100字节scratch。bank90写低8位，普通字节setter饱和到0..255，允许已知frame内的signed别名。日志count/current/cache保留完整DWORD；越过日志预留区时，runtime-backed消费者会访问实际相邻frame字节，独立日志投影只有1000word的权限。

原始code PC同在frame。取指先按真实物理记录推进该PC，再调用handler；指令结束只读取其结果，不能把Java预先算出的next写回并覆盖变量别名修改。实际分支、owned尾串、label及成功loader各在自己的原生边界写PC。0/1 marker属于已知loaded code，但未知100000-byte旧缓冲尾部不补零。PC1真实default与已定义但未实现的handler分别处理。

完整系统frame/candidate codec为P1C4/P1F4，runtime为P1R5。1C先将现场ordinary1000写进suspended frame，再复制全部8004；资源重建后的pendingCount清零另行发生。Variables的VNM1只保存history/shadow及未用portable尾部，live ordinary随canonical frame序列化。DMH7省去PC1重复的music/paragraph字段。

41A970/41AAD0文件服务另有完整branch与I/O顺序对照；其回调必须提供完整41A620重建，不能用纯投影或subset apply代替。当前仍不据这些独立验证开放原版槽位。绘制池、invalidation、设置及完整加载消费者的集成继续进行，尚未发布rev8开发包。
