# DC3DD Android 播放与验证

> 本文记录 0.3 剧情验证基线。当前 0.4 原版系统入口的实现与限制见 [DC3DD_ORIGINAL_UI.md](DC3DD_ORIGINAL_UI.md)。

2026-10-26，0.3.0-preview。当前实现可以直接读取用户提供的 DC3DD 数据，
执行七条剧情路线；仍是适配预览，不能把自动通关检查称为整部原作的人工逐场景验收。

## 安装和数据

1. 安装 `build/android/circus-core-debug.apk`。最低 Android 8.0，提供 ARM64、ARMv7 和 x86_64 原生影片解码库。
2. 将游戏的完整 `AdvData` 文件夹复制到手机的一个目录，例如 `Documents/DC3DD/AdvData`。
   汉化版还需将其 `uif_config.json` 放在 `Documents/DC3DD` 下。不要只复制拆出的 MES 或图片。
3. 在应用中点“选择数据目录”，选择 **DC3DD 根目录**，即含 `AdvData` 的目录。
4. 选择角色路线开始；“继续游戏”读取最近自动存档，“读取存档”可选择手动存档。

不需要复制或运行 Windows EXE、DLL、原字体。APK 与源码包不包含原作数据。
游戏目录只读；应用使用系统字体，读取原文字节和可选字符映射。
目录需要由支持文件大小及随机读取的本地文件提供器提供。

## 操作和保存

- 轻触画面先显示本页全文，再次轻触翻页或继续；选项出现时点选分支。
- “自动”在文字显示及语音播放完成后继续，可随时关闭。
- “回看”显示已显示的正文；打开回看、存读档窗口时暂停自动推进。
- 6 个手动存档，覆盖已有存档需要在应用中确认；另有 1 个自动存档。
- 自动存档在脚本要求保存时、运行中约每 15 秒、进入后台时写入。
- 存档按选择的数据目录区分，含脚本指纹、变量、选择历史、演出、文字页码、音频位置和影片位置。
- 通关标志 `100:100..110` 根据 ED / MED 的写入保留，读取旧进度不会倒退这些标志。
- “路线”从头开始另一条路线；切换前保存自动进度，需要长期保留当前路线时请使用手动存档。

应用自己的存档格式为 CAS2，封装核心 CVS1；支持读 CAS1。
0.2 原型宿主的存档格式不兼容；原 Windows 原生存档不支持导入。

## 已验证的范围

| 验证 | 范围及证据 |
| --- | --- |
| 静态工具 | 34 项 Python 测试；`build/python-tests.txt` |
| 核心 | JVM 和 Android ART 各 122 项核心断言，加 Android 存档封装、原子替换回滚与版本兼容测试；`build/android/build-report.json` 与 `art-core-tests.txt` |
| 全资源静态解码 | 21,084 张图片，含 18,563 个差分；14,257 个支持的音频；`build/dc3dd-java-static-audit.txt` |
| 剧情流程 | 七条路线分别总选第一项／最后一项，走到 ED、MED 后返回平台标题；`build/playback-audit/*route-*.txt` |
| Android 剧情流程 | 同样 14 条执行路径，在 ART 中执行并检查存档恢复；`build/playback-audit/android-art.txt` |
| 选择组合 | 从默认路线状态遍历每个选项：立夏 3 个二选一节点的 8 种组合，其余 6 条无选项路线；全部到标题；4,949 个图像引用、14,028 个音频引用解码通过；`build/playback-audit/branches.txt`，Android 同组检查在 `android-art.txt` |
| 手动存读档 | 保存、推进、读档、结束进程后读档；场景区域逐像素一致；`build/android-original-smoke/save-load-comparison.json` |
| Android 画面 | 31 个由原脚本实际到达的检查点：路线开场、头像、选项、镜头、小说模式、粒子、转场、七套片尾等；`build/render-cases/android-result.txt` |
| 原版影片 | GOP 3,477 帧、OP2 2,968 帧完整解码；SurfaceView + AudioTrack 完整播放约 116 秒／99 秒，包含暂停恢复；`build/android-movie-audit/` |
| 影片定位 | OP2 从 90 秒恢复，播放至结尾；`build/android-movie-audit/seek-op2.txt` |

剧情审计中的媒体完成事件被模拟；影片和画面在独立测试中验证，不能将它们描述为连续数十小时的人工游玩。
后续分支检查覆盖当前默认初始状态下全部可到达选项组合；随机种子、任意改写变量、历史通关状态和原生回放菜单状态仍未穷举。
Android 验证设备为隔离的 Android 15 x86_64 模拟器；ARM 库已编译，尚无实体手机运行证据。
音频已验证解码和播放轨道，尚未进行全程人工听音。

## 实现选择及未完成部分

- MES、PCK/CRM、变量、跳转、文字、保存等在独立 Java 核心中实现。
  Android 只负责存储、绘图、输入、媒体输出与生命周期。新增 DC1/2/3 版本需要新增独立语义绑定，不能用统一 opcode 偏移代替。
- MAIN 的剧情返回被交给 Android 标题页。Windows 标题／设置／CG 鉴赏／音乐鉴赏界面没有整体移植，当前 Android 菜单提供剧情所需入口。
- 头像、文字框、分页和菜单采用移动端布局；原作的逐像素 UI、字体与文字排版不作一致性承诺。
- 镜头、片尾、部分转场的时间和混合效果仍需更多原版动态画面对照；粒子的随机序列是可保存的本地序列，不与原进程全局 RNG 同步。
- BGM 当前按音频文件整体循环。本份 NAMES 的 400 条音乐附加数据均为 `(0,0,0,3)`；这些字段的完整原生意义尚未全部确认，不能直接推广到其他作品。
- XPCM mode 1 / 3 尚未实现；本份数据仅发现未被这些剧情路径引用的 `VOICE.DAT:000SILENT24.PCM` 为 mode 3。
- 未实现的指令或子模式会报告脚本位置并停止，不用空操作掩盖。

此前授权的 3 分钟原生采集已完成并用于核对正文、分发和初始宿主状态。
追加的 10 分钟验证尝试在 Direct3D 初始化阶段失败，且桌面控制被用户 Esc 停止；
进程已结束，未得到新的剧情画面对照证据。校验确认原游戏文件未变。

## 开发复现

```powershell
D:\ANACONDA\python.exe tools/build_android.py --java-home "E:\DevEco Studio\jbr"
D:\ANACONDA\python.exe tools/playback_audit.py D:\gal\DC3DD --java-home "E:\DevEco Studio\jbr" --entry DD_1_0611_A1 --media
```

显式选择自己用于测试的模拟器后，可调用 `.CoreInstrumentation`、
`.OriginalInstrumentation`、`.MovieInstrumentation`、`.PlayerInstrumentation` 和 `.RenderInstrumentation`。
后四者需要本地私有测试数据；它们不会启动 Windows 游戏。
影片测试文件放在应用私有 `files` 下，画面检查点由 `RenderCases` 生成后放在私有 `files/render-cases`。

不要分发 `analysis/`、私有测试快照、画面截图、原作资源、动态采集副本或日志中的原文。
源码打包使用白名单脚本 `tools/package_source.py`；许可与来源见项目 `LICENSE` 和 `THIRD_PARTY.md`。
