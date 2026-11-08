# Circus Engine

用**原版脚本和原版素材**驱动 D.C.（ダ・カーポ）系列 1／2／3 的兼容运行时。
选择游戏目录，按已实现的版本规则执行原版启动、标题、正文和系统脚本；
不运行原 Windows EXE，不附带任何游戏数据。

仓库：[kesmeey/Circus-Engine](https://github.com/kesmeey/Circus-Engine)。
项目目录与命令行工具仍沿用 `circus-runtime`／`dc_runtime` 命名。

Android 更新为 **0.14.10-dcdx-system-import-dev**，新增 DCDX `system.dat` 的 CG／场景已读进度导入，见[本轮验证](docs/validation/dcdx-system-import-source-2026-11-08.json)。Windows 保留 **0.14.9-dcdx-import-dev** 固定包；此前[DCDX 运行与 SC／FL 导入验证](docs/validation/dcdx-import-workflows-source-2026-11-08.json)继续单独保留。DCDX 长流程仍有片尾／背景滚动缺口，手机整部原数据流程尚未验证。

当前目标已调整为约80%的主要功能与大多数常见游戏流程，允许延期少量难处理的版本和边缘行为；这是工程范围，尚不是完成率声明。见[当前交付目标](docs/ENGINE_SCOPE.md)。

本批 Android 安装包为 `build/distribution/circus-runtime-0.14.10-dcdx-system-import-dev-android.apk`，可覆盖安装，保留应用数据。新增 DCDX 系统进度导入，SC／FL 导入继续保留；DCDX 手机整部原数据流程尚未验证。

此前分发的 Android 开发包 **0.13.15** 修复 W.Y. 夏露露剧情中的重复目录分隔符报错，同时覆盖 P.P. 的同类图片引用。旧安装包：`build/distribution/circus-runtime-0.13.15-android-dev.apk`，见 [修复说明](docs/ANDROID_01315.md)。保留 [0.13.14 的 W.Y.／P.P. 原版系统进度导入](docs/ANDROID_01314.md)和 [0.13.13 的手机启动与中文补丁兼容](docs/ANDROID_01313.md)。

## 运行入口与历史验证范围

当前源码正在重写非 Unity 引擎的执行规则，详见 [忠实度审计](docs/NATIVE_FIDELITY_AUDIT.md)。
本轮用原生证据替换了 D.C.II（2006）的系统服务放行逻辑：已证实的默认分支直接返回，
有实际副作用但尚未实现的服务会明确报出脚本位置和原生入口。
这会暴露此前被跳过的模块；当前重写源码不能沿用旧开发包对该作的可玩承诺。
以下其他作品的进度保留为此前验证范围，不能据此宣称本轮已忠实复刻整个引擎。

有独立版本绑定、由原版脚本驱动的一共11个入口（包含暂不推进的Unity版）。Windows与Android共用核心；
下表保留历史验证范围，本轮的实际验证与未实现边界以重写状态为准。

| 作品 | 数据格式 | 目前能跑到哪 |
|---|---|---|
| D.C. 〜ダ・カーポ〜 P.C. | `dcpc-2063` | 独立便携自动槽与原版存读菜单；两项合法选择调整后音梦10,358句经STAFF回标题；Windows槽文件互通仍未实现 |
| D.C.II 〜ダ・カーポII〜 P.C. | `dc2pc-5769` | 启动／正文／选择／存读档／到片尾 |
| D.C.II 〜ダ・カーポII〜（2006 原版） | `dc2-3363` | 首次剧情、角色移动、闹钟存读与片尾已补齐；实际杏/七夏路线经片尾回标题；桌面支持普通ADV原Windows槽转换，少数特殊合成与全部路线尚未穷举 |
| D.C.II Spring Celebration | `dc2sc-3b69` | 三类故事代表路线回标题，原版Save→Load恢复句子及PC；本地Coco分支一份MES截断，仍明确失败 |
| D.C.II Fall in Love | `dc2fl-9c69` | 原菜单15个故事的自然首选路线均回标题，独立画面提交、15路片尾及原版Save→Load通过 |
| D.C. Dream X’mas | `dcdx-7769` | 独立1024×576宿主；原轮转/滑动菜单、D.C.与D.C.II两个入口各20次正文等待；长流程分别停在片尾服务11和背景滚动62，未整线通过 |
| D.C.II Dearest Marriage | `dc2dm-9d72` | 启动／正文／存读档／回看与语音重播／CG·场景·音乐鉴赏／到片尾 |
| D.C.III DreamDays | `dc3dd-a5a8` | 七条剧情路线到片尾 |
| D.C.III With You | `dc3wy-a09f` | 代表策略2,737句后经该版本片尾回标题；其他选择组合未穷举 |
| D.C.III P.P. | `dc3pp-9872` | 首/末选项策略15,006/13,362句后经MED片尾回标题；粒子时钟及部分绘制采用兼容近似 |
| D.C.III R（Unity 5.5 版） | `dc3r-unity-550` | 标题／章节选择／正文／系统菜单；全路线尚未验证 |

「到片尾」指在本机完整跑通并返回标题；不等于原版所有演出时序和设置副作用都已复刻。
各版本的验证细节和缺口见 [版本记录](docs/RELEASES.md)。

### 只能识别，不能运行

解包器和格式表覆盖 26 种 MES 变体（D.C. White Season、Summer Vacation、After Seasons、
D.C.II To You 等），可以扫描、解包、反汇编、
导出图片和音频；但运行时没有对应宿主，选中会直接报 `Unsupported profile`。

范围只到 D.C. 1／2／3 及其 FD，不含 D.C.4／5。其他作品的格式支持不等于能运行。

## 跑起来

### Android

```powershell
python tools/build_android.py --java-home "E:\DevEco Studio\jbr"
```

产物 `build/android/circus-core-debug.apk`：API 26 起，含 arm64-v8a／armeabi-v7a／x86_64，
开发签名，不含游戏资源。需要 Android SDK（build-tools 36、platform 36）与 NDK 29。

1. 把**整部游戏目录**复制到手机，不要只拷 `AdvData`。
2. 「添加游戏目录」选包含 `AdvData` 的那一层；Unity 版选包含 `DC3_Data` 的目录。
3. 首次会识别版本并让你确认文字编码，之后点「启动游戏」，由原版脚本进标题。

SAF 只申请读取权限；存档和解码缓存写应用私有目录。

### Windows

```powershell
python tools/package_desktop.py --jdk <JDK21> --ffmpeg <ffmpeg.exe> --version 0.14.9-dcdx-import-dev
```

产物 `build/distribution/dc-runtime-0.14.9-dcdx-import-dev-windows-x64/`，双击 `DC Runtime.cmd`，
再选游戏目录。包内自带 JRE 和 FFmpeg，不需要另装 Java；存档写在包目录的 `saves/`。

用当前源码构建的播放器可在同一目录有多个补丁EXE时，明确选择用于读取静态数据表的文件：

```powershell
& ".\DC Runtime.cmd" "D:\game" --exe "patched.exe"
```

EXE只作数据读取，不会执行；未指定时继续自动选择。

### 验证当前源码

SC/FL实际测试集复查（原游戏只读，结果写入新的`build/holdout-workflows/`目录）：

```powershell
python tools/check_holdout_workflows.py --java-home <JDK21> --spring "D:\games\SC" --fall "D:\games\FL" --full-routes
```

包括两作原菜单20次正文等待、原版Save→Load、FL合成和片尾像素、SC三类代表及FL十五个故事的自然首选路线。时间与媒体完成由审计控制；SC截断分支的已记录结果见本批测试集验证，不声称穷举每个分支。

DCDX 的只读测试（两入口、便携存读档、角色与原菜单图集像素）：

```powershell
python tools/check_dcdx_workflows.py "D:\gal\DCDX" --java-home <JDK21>
```

不需要原游戏文件的核心边界检查：

```powershell
python tools/check_native_core.py --java-home <JDK21> --heap-mb 192
python -m unittest discover -s tests
```

`tools/build_desktop.py` 默认构建播放器；`--with-tests` 将全部测试与物理审计入口编译到独立的 `build/desktop-tests/classes`，避免把审计夹具放入播放器包。Windows JNI 服务可用本机 MSVC 构建：

```powershell
python tools/build_windows_native.py --java-home <JDK21> --vcvars "<Visual Studio>\VC\Auxiliary\Build\vcvarsall.bat"
```

每次原生构建冻结所用 C/header，记录源码与 DLL 哈希。实际审计必须搭配同次源码对应的 DLL；核心测试、真实 Win32 服务检查和真实游戏启动分别记录，不能相互替代。当前已验范围及剩余工作见 [重写状态](docs/NATIVE_REWRITE_STATUS.md)，最新源码与同源平台检查见 [验证摘要](docs/validation/first-play-workflows-source-2026-11-08.json)，基础执行链记录见 [上一阶段](docs/validation/majority-source-2026-11-07.json)。

## 操作（Android）

| 操作 | 作用 |
|---|---|
| 单指轻点 | 推进正文／点击按钮 |
| 双指轻点 | 鼠标右键，打开或返回原版菜单 |
| 双指上下滑 | 回看列表滚动 |
| 系统返回键 | 回上一级 |

## 存档

手机存档不是原 Windows 格式。原版的系统进度（通关、CG、场景解锁）可以导入：
游戏卡片「更多 → 导入原版进度」，支持 DCPC／DC2（2006）／DC2PC／DC2SC／DC2FL／DCDX／DC2DM／DC3DD／DC3WY／DC3PP／DC3R。
SC本轮仅合并CG与场景已读；FL另含已确认的通关标记。两作均保留本机设置、姓名和已有剧情槽，重复导入不重复写入。DCDX现支持从 `system.dat` 合并CG及场景已读标记，保留本机设置、姓名和剧情槽；不导入通关状态、原版历史检查点或剧情槽。
SC／FL／DCDX 的原Windows剧情槽转换仍未实现；原版Save→Load通过指运行时通过原菜单保存自己的便携槽，不能据此推断原Windows文件互通。
Android 的原版剧情槽导入入口目前仅覆盖 DC3R；本轮桌面新增 DC2（2006）普通 ADV 剧情槽转换，范围见下文。

本轮非 Unity 剧情快照使用 `CVS2`，保存执行规则身份；旧非示例 `CVS1` 剧情槽会明确拒绝，
因为旧变量模型和取指规则已造成的执行差异不能仅凭存档长度证明可迁移。系统持久进度单独处理。

PC1原版存读菜单、剧情自动槽和快速保存已接入独立便携检查点；快速保存按原生规则替换候选并写入槽1，F5/F9保留当前会话与候选。原Windows游戏槽文件的8004字节互通仍未完成。

DC2（2006）的闹钟上下文、原版存读菜单及自动/快速槽使用独立D2CS候选，原生自动/快速槽均为0。桌面可从「存档 → 导入原版剧情存档…」转换普通ADV文本等待的原Windows槽，写入原读取菜单可见的空闲槽（2..4、8..12、16..20，依此至72..76）；当前剧情继续保留，已有目标槽拒绝覆盖。旧回看记录、NVL、选择等待及未完成画面批次不在转换范围内。

```powershell
java -jar dc-runtime.jar D:\gal\DC2 --exe dc2.exe --import-native-slot D:\gal\DC2\AdvData\SaveData\dc200.dat --slot 12
```

不指定`--slot`时选择空闲普通槽。转换读取原文件，输出写入本运行时存档目录。
F5/F9使用的独立CVS2便携快照与原版菜单槽位不同，不能相互替代。当前执行合同rev8会拒绝不同执行身份的旧快照。DC2新增首次游玩宿主修订，旧版可能已误跳未读剧情，旧DC2便携剧情快照会明确拒绝并保留原文件；独立系统进度不受此限制。
P.P. 重写已改用原生位图和持久变量别名，新版可迁移合法旧手机系统进度。
新 P.P. 状态以版本前缀保存完整持久字节（含可读填充位），旧版程序不能读取；DD/WY 的系统进度格式不变。
旧兼容层系统进度若含非布尔解锁标志或原生不可写的全局索引数据，会明确报出无法忠实迁移并保留原文件；
不会静默修改这些值。具体边界见 [忠实度审计](docs/NATIVE_FIDELITY_AUDIT.md)。

## 已知限制

- 在桌面 JVM 和 Android 模拟器上验证；实体 ARM 手机和扬声器听感尚未验证。
- 部分演出时序、字体细节和系统设置副作用还没逐项对照原版。
- 汉化版未翻译的日文页会显示乱码（例如 D.C.II 的 `kaiso_01`／`kaiso_02`）。
  这是补丁把字形表改成 GBK 顺序造成的，在原版补丁下同样如此，不是这里的额外损失。
- 缺素材时对应槽位留空并记名，不中断整帧：本机 D.C.II 只有 `scene_sam.CRX`、
  缺 `scene_sam_01..30`，所以场景回想的缩略图是空的。

## 仓库结构

```
runtime/core/       平台无关核心：MES 解析、VM、变量、资源、存档
runtime/desktop/    桌面宿主与 Canvas 渲染
runtime/android/    Android 宿主
runtime/native/     MPEG 解码 JNI
circus_tools/       扫描／解包／反汇编（Python）
profiles/           格式表与语义表，编译期生成 Profiles.java
tools/              构建、打包、验证脚本
docs/               VM 规范、各版本兼容性、版本记录
```

新增作品要加版本绑定和宿主行为，复用资源、变量、跳转和存档基础设施，
不需要按每部作品重做一套 APK。

## 构建与测试

需要 Python 3.10+、JDK 17+；打包 Android 另需上述 SDK／NDK。脚本不下载工具链、不依赖 Gradle。

```powershell
python -m unittest discover -s tests            # 47 项 Python 测试
python tools/build_android.py --java-home ...   # APK，并运行 JVM 核心回归
python tools/package_desktop.py ...             # Windows 包
```

构建报告和签名校验在 `build/android/`，模拟器证据在 `build/android-smoke/`。

## 文档

- [版本记录](docs/RELEASES.md)｜[解包与静态分析工具](docs/TOOLING.md)
- [PC 兼容性说明](docs/PC_COMPATIBILITY.md)｜[VM 规范](docs/VM_SPECIFICATION.md)｜[架构边界](docs/ARCHITECTURE.md)
- [MES 语义表](docs/MES_SEMANTICS.md)｜[静态分析](docs/STATIC_ANALYSIS.md)

## 边界与许可

游戏输入只读；不发布游戏数据、启动程序、原文脚本或采集副本。
原 EXE 只做静态分析，从不执行。`build/`、`analysis/` 是本机生成物，不在仓库里。
第三方组件来源和许可证见 [THIRD_PARTY.md](THIRD_PARTY.md)。
