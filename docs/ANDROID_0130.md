# Android 0.13.0：DC3R Unity 初步兼容

安装 `build/distribution/circus-runtime-0.13.0-android-dev.apk`。
沿用已有包名、签名和四个 MES 游戏的存档格式，可以覆盖安装。
不要卸载或清除应用数据。DC3R 使用独立的游戏标识和存档目录。

## 游戏目录

本次针对本地 `Da Capo 3R X-Rated/Da Capo 3R X-Rated` 中的 Unity 5.5.0f3 版本。
它使用 `CircusScr1.0` 脚本，并不是 DC3DD 的 MES 字节码。
解释器新增独立的 UnityFS 资源读取器和 CircusScr VM，共享 Android 的显示、触摸、音频与存档平台。
原有 DC3DD、DC2DM、DCPC、DC2PC 继续使用原来的 MES 后端。

将完整游戏目录复制到手机，在“添加游戏目录”中选择包含 `DC3_Data` 的目录，
也可以直接选择 `DC3_Data`。保留 `StreamingAssets` 内的资源包与 `movies` 子目录。
该版本的文字在资源包中使用 UTF-8，不需要选择 EXE 数据表，也不会执行 Windows 程序。
标题、章节选择、正文和系统菜单使用原版脚本及素材。

APK 不含原游戏资源、原脚本、程序集或反编译代码；无需提前把整个游戏解包成图片和音频。
FSB5/Vorbis 音频按需重封装为 Ogg，不重新编码压缩音频。
MP4 使用 Android 的 VideoView/MediaPlayer 播放，原四部游戏的 MPEG 播放路径保留。
脚本格式与控制流说明见 [CircusScr VM](CIRCUSSCR_VM.md)。

## 验证范围

- 全部 634 个 CircusScr 脚本可解析，约 204 万条 VM 指令的结构、跳转表和静态脚本目标通过校验。
- PC 解释器从原版启动脚本进入标题与第一章，推进 1,145 条对白到第一处选择支；两个选项分别进入不同的后续对白。
- 原版存档、读档、设置、回看通过 62 项 PC 系统流程检查，包含存档界面恢复后继续保存。CG、Scene Jump、音乐鉴赏可进入和返回，音乐页面的播放／停止命令可改变对应音轨状态。
- Android 模拟器实际触摸推进 50 条对白，验证立绘、存档恢复前后的 Canvas 像素一致，以及原始语音的 MediaPlayer 播放启动。MP4 标志片自然播放结束，片头视频通过播放、Activity 暂停／恢复及触摸跳过检查。
- 27,568 条可区分的 BGM、音效、语音引用通过 FSB5 转 Ogg 的压缩包一致性、页 CRC、结束标记和采样数检查。这是封装检查，不是逐条试听。
- 最大的 32 张原始图片在 256 MiB Java 堆限制下依次解码成功。图片缓存按字节限额淘汰；首次系统语音的读取次数由 27,223 降至 7。

上述静态审计和 PC 流程中，影片完成及非循环音频完成由测试器模拟；不能用来证明实际音画同步。
Android 触摸、音频、影片和既有四个游戏的回归结果记录在构建目录的验证报告中。
最终 APK 通过 552 项 JVM／Android ART 断言、7 项 Canvas 像素契约、484 项遮罩像素比较，以及四个既有 MES 游戏共 98 项 Android 检查；47 项 Python 测试也通过。
测试产物只保存在本地，不会随源码包或 APK 发布。

模拟器第一次检查 DC3DD 时内部空间过低，影片缓存打开失败；移除本次 DC3R 测试样本后，同一 APK 的 DC3DD 21 项检查通过，首次失败日志仍保留。实际播放需要手机内部空间用于临时音频／影片文件。

## 当前边界

这是 DC3R 的初步兼容版本，没有逐条路线通关验证，不能称为完整兼容整个 DC3 系列或所有 Unity 游戏。
特殊粒子和部分转场效果仍有缺口，字体排版与 Unity/TextMeshPro 也未做到像素一致。单角色语音开关等设置副作用尚未完整验证。
已有指令继续按原脚本执行；没有实现的实质性命令会显示脚本位置诊断，避免静默跳过剧情。
Windows 注入式汉化、不同 Unity 版本及不同打包格式需要另行验证。

## 本地复现

```powershell
python tools/build_desktop.py --java-home '<JDK directory>'
java -cp build/desktop/classes org.circus.core.CircusScrCorpusAudit '<game directory>'
java -cp build/desktop/classes org.circus.core.UnityStartupAudit '<game directory>' build/dc3r-startup-choice 1200
java -cp build/desktop/classes org.circus.core.UnitySystemAudit '<game directory>' build/dc3r-system-audit
python tools/build_android.py --java-home '<JDK directory>'
```

Android 测试读取从本机原资源记录的稀疏范围包，避免在模拟器中复制整个游戏；
正式入口仍通过用户授予的 Android 文档目录权限读取完整原始资源。
`UnityInstrumentation` 只用于开发验证，稀疏包和影片样本不会打入 APK。
