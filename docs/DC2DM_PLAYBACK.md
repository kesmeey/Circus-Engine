# DC2DM Android 接入（0.6.0-core-dev）

目标是执行原版 MES 和使用原版资源的 CIRCUS 兼容层。DC2DM 使用独立的 `dc2dm-9d72` 语义绑定，与 DC3DD 共用目录识别、档案读取、图像／音频解码、VM、变量模型接口和存档容器。APK 不包含游戏数据。

## 安装和数据

Android 8.0 以上；arm64-v8a、armeabi-v7a、x86_64。将完整 DC2DM 目录复制到手机，例如 `Documents/DC2DM`，保留 `AdvData` 的全部子目录和 `uif_config.json`。在 APK 中选择包含 `AdvData` 的根目录，启动后执行原版 `START → WIN_SIZE → MAIN`。

保留根目录 `DC2DM.EXE` 可读取原版音乐曲名和回看头像对照表；程序仅按 PE 文件格式读取其中的数据，不执行 EXE／DLL。只有 AdvData 的安装仍可播放音乐，但曲名会退化为资源文件名。中文文本通过该目录的外置字符映射处理。游戏目录只读；存档、全局进度和解码缓存位于应用私有目录。移动端存档与 Windows 原版存档格式不同，卸载应用会删除私有存档。

正文点击推进；原版正文框右侧按钮展开工具条，OPTION 进入原版系统菜单。存读档页面保留原版 Quick、Auto、New 和普通槽位：第一页第四格才是普通存档 01。Android 返回键在菜单里触发脚本的返回动作，在正文里展开工具条。

## 已接入的主要路径

- 原版标题、新游戏、原版剧情分支、正文框和姓名、人物及头像、背景和脚本滚动。
- 原版 ADVMENU、SAVE、LOAD、确认框和 OPTION 的基础设置；按钮位置、状态图片及跳转标签来自 MES。
- 手动／快速／自动存档，菜单返回恢复调用前的正文位置，全局标志和设置持久化。
- XPCM PCM、Ogg、mode 1 LZSS／mode 3 zlib 频谱解码，BGM／语音／音效，MPEG-1 播放接口。
- 日历和片尾的原版图像资源；夜景 `@`、暖色 `*` 图像滤镜。

## 回看和鉴赏

- 原版正文工具条的回看按钮进入 `LOG.mes`。上下滑动或点击箭头翻阅；点击有语音的记录重播；左侧「ここから読む」恢复所选记录的正文位置。退出回看和操作说明保留调用前的正文，并继续背景音乐。
- 回看保留最近 200 条文本；最近最多 64 条可回退，回退快照合计上限 2 MiB（因此可回退数量也受快照大小限制）。更早的记录仍可阅读、播放语音，但选中后不显示回退按钮。全局解锁状态不会因回退或读档倒退。
- 标题 EXTRA → CG MODE 执行原版 `CGMODE / CG_*`，包含角色切换、分页、差分与全图浏览；全图点击推进，Android 返回键回到缩略图。
- EXTRA → SCENARIO MODE 执行 `SCEMODE / SCE_*`，确认后进入原版剧情，结束经 `SC_END` 返回对应页面。回放不覆盖普通自动存档。
- EXTRA → MUSIC MODE 的 73 首曲目按原生表顺序排列，支持播放／停止、前后曲、单曲循环、全曲循环、随机顺序和滑动列表。原程序的这个音乐列表没有 CG 那样的解锁判断，因此没有人为锁住音乐。
- 帮助按钮执行原版 `MANUAL.mes`，保留其说明图片及返回按钮。

覆盖安装升级保留 0.5 的存档。旧存档没有存入的历史文本与 CG 已看标记不能凭空恢复；从新版继续游玩后记录。鉴赏测试中的解锁标记仅在测试内存中注入，正式入口没有“全部解锁”开关。

## 验证与边界

0.5 基线的桌面 Java 核心两种选择策略从 START 走到 MAR、SIN、SUC、MED、ED，并返回 MAIN。首项策略执行 273,534 条指令，实际解码 3,959 个图像引用；末项策略执行 273,179 条指令，实际解码 3,938 个图像引用。两者各经过 261 个脚本。这些流程测试模拟时间和媒体完成事件，**不等于已在 Android 上逐段看完整部作品**，也不覆盖所有选项组合。

Android API 35 x86_64 模拟器的实际输入／媒体检查已通过：直接从 START/WIN_SIZE 启动到标题，新游戏、正文、系统菜单、手动保存、加载、设置音量变更、返回标题和 CONTINUE 均完成。结果在本机 `build/dc2dm-android-ui-test.txt`，13 张截图在 `build/android/dc2-ui-verified/`。桌面系统往返检查 `Dc2SystemAudit`、JVM 与 Android ART 的 205 个核心断言、38 项 Python 测试也通过。DD 原版启动回归仍到达 MAIN。

`DC2DM_OP01/02/03.mpg` 均在 0.5 基线 APK 的 Android 原生解码器中完整解码，输出 1280×720／29.97 fps 图像及 48 kHz 音频。结果在 `build/dc2dm-movie-audit.txt`；这项检查验证完整解码，不代替真机实时播放性能与音画同步验收。

本版仍为可玩性验证开发版，不能声称完整还原所有 Windows 功能：

- 未完成小游戏、逐角色语音设置和部分设置子页；不能把本轮的回看／鉴赏接入视为所有系统功能已完成。
- 淡入淡出以交叉渐变实现，部分人物动作、粒子、日历翻页和片尾运动／时序采用近似实现，尚未逐帧对齐。
- 文字排版使用 Android 字体；没有导入 Windows 字体渲染、原存档、存档缩略图或逐角色语音设置。部分设置项的显示与效果尚未逐项对齐原版，已验证音量减按钮及菜单返回。
- 实际音画验证使用本机 Android 模拟器，尚无用户手机上的测试结果；其他 D.C.1／2／3 作品不能据此认定兼容。

0.6 新增检查：`Dc2ExtrasAudit` 的 106 项断言覆盖 12 组 CG／场景页面共 31 页、锁定／解锁、原版场景回放结束返回、音乐顺序与自动切曲、回看语音选择、回退及存档恢复。结果在 `build/dc2dm-extras-audit.txt`。Android 实际触摸检查（含已保存进度读取、回看语音重播及原版头像）在 `build/dc2dm-extras-android.txt`，18 张截图在 `build/android/dc2-extras-verified/`；使用真正的 0.5 私有存档测试升级读取。所有游戏输入均保持只读，本轮没有启动 PC 游戏。

## 可复现检查

```powershell
python tools/build_android.py --java-home "E:\DevEco Studio\jbr"
& "E:\DevEco Studio\jbr\bin\java.exe" -cp build/android/jvm org.circus.core.Dc2SystemAudit D:\gal\DC2DM
& "E:\DevEco Studio\jbr\bin\java.exe" '-Ddm.assets=true' -cp build/android/jvm org.circus.core.Dc2UiAudit D:\gal\DC2DM sce_start '@last'
& "E:\DevEco Studio\jbr\bin\java.exe" -cp build/android/jvm org.circus.core.Dc2ExtrasAudit D:\gal\DC2DM
adb -s emulator-5584 shell am instrument -w org.circus.runtime/.Dc2ExtrasInstrumentation
```

仪器测试写入测试应用的私有存档槽，要求预先在该应用内授权 DC2DM 数据目录。不要对用户正在游玩的应用数据运行测试。

## 静态证据

原始 `DC2DM.EXE` 哈希沿用 `DC2DM_RUNTIME_SEMANTICS.md`。

| 行为 | 原生位置 | 实现要点 |
|---|---|---|
| LOG 1B[7,0/1] | 4159F8 → 410540／410630；410D..4115 | 内置回看页面；LOG_BACK／LOG_CHIP 原图、四行记录和返回脚本 |
| MUSIC 1B[213] | 415FF0 → 413490；414630；496FF8 | 73 项曲目表、十行列表、六个播放控件；不做 CG 式解锁筛选 |
| 49 空字符串 | 4305CA／4320DF | 退出音乐页时取消原生音乐事件跳转 |
| CG 解锁 | 4075F0 → 427CB0 | 加载图像名对照 NAMES.images，置 bank 97 位 |
| 场景解锁 | 432887 → 428270 | 进入 NAMES.scenario_scripts 后置 bank 98 位 |
| 鉴赏与帮助流程 | CGMODE/CG_*、SCEMODE/SCE_*、SC_END、MANUAL | 由原版 MES 定义布局、判断、跳转；不是另写路线菜单 |
| 11 音乐淡出 | 42E91C → 435E60；4374B0 | 淡出 BGM；不能错误停止语音；完成后解除 13[1] |
| 13 音频等待 | 432A9E；435F30／436AC0／436170 | 位 1 音乐、2 语音、4 音效 |
| 图像后缀 | 407645、4065BF、405560／405690 | 去除 @／*，与 DD 同形的整数色彩变换 |
| 存档格 | 40F540、40F220 | 页×10＋格＋1；1/2/3 保留给 Quick/Auto/New；SL_CHIP 原图绘制 |
| 确认框 | START 模板 2、59／42 | 标题图与 yes/no 来自原资源；动作槽 0/1 |
| 1B207/208/209 | 414FF0／415EEC／43F010 | 日历、片尾时钟、片尾资源；目前运动和计时为近似 |

经用户授权的 DC2DM 隔离动态采集已结束，原目录未修改；本轮其后检查为静态分析及自研运行时测试。
