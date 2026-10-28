# Android 0.12.6：影片音频失真与卡顿

安装 `build/distribution/circus-runtime-0.12.6-android-dev.apk`。
包名、签名及存档格式沿用 0.12.5，直接覆盖安装，不要卸载或清除数据。
保留 0.12.5 的 CG／Scene Jump 进度修复。

## 本次修复

本机 DC3DD 的 `AdvData/MOVIE/GOP.mpg`、`OP2.mpg` 是 MPEG-PS 容器，
包含 1280×720、29.97 fps 的 MPEG-1 视频和 48 kHz 双声道 MP2 音频。
本次针对这些实际文件；不将结果视为任意 MP4 编码都已支持的证明。

旧播放器在一个线程里交替解码视频、转换 RGB、绘制 Surface 和填充 AudioTrack。
画面处理延迟超过音频队列剩余时间时，音频缓冲会耗尽，造成断续、爆音。
新版音频线程持有独立的解码器、文件流和缓冲，停用该实例的视频流；视频实例停用音频流。
两个实例不共享可变解码状态，也不会把不消费的另一条流一直缓存起来。

音频队列先填充再播放，独立线程持续补充；画面仍按实际音频播放位置计时。
迟到超过 100 ms 的画面仍完成 MPEG 预测解码，但省去过时画面的 RGB 转换和绘制，
避免积压。没有修改影片速度、丢弃音频或关闭原作演出。
暂停冻结时钟，恢复沿用队列；影片结束等待音频尾部，跳过会关闭两个工作线程。
视频比音频稍长时，在音频尾部之后延续时钟，避免卡在最后几帧。

另外发现 vendored PL_MPEG 音频的两处归一化误差：

- 反量化除以 `levels + 1`，使不同子带分别出现 3/4、5/6、7/8 等衰减；改为除以 `levels`。
- 合成输出除数为 `65 × 2^24`，额外衰减为 64/65；改为 Q30 的 `2^30`。

改动及原始上游版本记录在 `THIRD_PARTY.md`。数学结果另用 FFmpeg 输出独立核对，
没有将 FFmpeg 库、程序或游戏影片打入 APK。

## 验证与边界

在本机 Android 15 / API 35 x86_64 模拟器执行，未启动 Windows 原游戏，未连接实机。
下列压力测试故意向视频线程每 30 帧注入 500 ms 延迟，用于复现音频供给中断，
不是对用户手机性能的测量。正式入口不设置测试延迟。

| GOP 前 15 秒压力测试 | 旧播放逻辑加统计 | 新播放逻辑 |
|---|---:|---:|
| AudioTrack 缓冲耗尽次数 | 12 | 0 |
| 暂停／恢复 | 通过 | 通过 |

GOP 完整播放约 116 秒，音频缓冲耗尽 0 次，5,571,072 个立体声采样帧全部送出并播放。
OP2 完整播放约 99 秒，同样为 0 次，4,754,304 个采样帧全部播放。
完成后两个工作线程释放。压力测试也验证中途关闭、资源释放及不误发结束回调。
另验证 OP2 从 90 秒恢复到片尾，以及三份自制影片：无音轨、音频先结束、视频先结束。
均通过暂停恢复及自然结束，存在音频的用例均完整播放已写入采样。

前 20 秒的 PCM 与 FFmpeg 逐采样比较，不调整音量、不移动时间轴：

| 影片 | 修复前 RMS 误差（16-bit 采样单位） | 修复后 | 修复后信噪比 |
|---|---:|---:|---:|
| GOP | 690.07 | 2.76 | 69.56 dB |
| OP2 | 844.11 | 4.56 | 66.06 dB |

完整 GOP、完整 OP2、DC2DM_OP01 的音频也通过独立比较，信噪比分别为
71.34、69.87、69.92 dB。四份自制测试影片覆盖 32／44.1／48 kHz、单／双声道、
64／192／384 kbps，均超过 60 dB 的回归阈值。
该阈值验证解码器误差，不代表原文件的音质或听感评分。
Android JNI 实际解码导出的 GOP 前 20 秒 PCM 也与独立参考比较，
结果为 69.56 dB，RMS 误差 2.76，与桌面探针结果一致。

最终 APK 通过 385 项 Android ART 断言、7 项 Canvas 像素契约、484 项遮罩像素比较，
以及 DC3DD／DC2DM／DCPC／DC2PC 共 98 项原版标题、正文、回看与存读档冒烟检查。
构建时的 385 项 JVM 断言和 47 项 Python 测试也通过。
这些回归覆盖既有入口，没有重新认证四个游戏的全部路线。

影片仍用软件解码，画面流畅度受手机性能影响；本机测试不能保证每款手机、蓝牙输出
或其他补丁提供的视频都没有问题。新版首先消除了已复现的画面线程阻塞音频的问题。

## 复现

用 C11 编译器编译 `runtime/native/tests/mpeg_audio_probe.c`，Unix 编译需链接 `-lm`。
在 Windows/MSVC 开发环境可用：

```powershell
cl /O2 /std:c11 runtime/native/tests/mpeg_audio_probe.c /Fe:build/mpeg_audio_probe.exe
python tools/audit_movie_audio.py --probe build/mpeg_audio_probe.exe --ffmpeg <ffmpeg.exe> --movie <GOP.mpg> --movie <OP2.mpg> --seconds 130 --out build/movie-audio-audit.json
```

脚本需要 NumPy，默认也运行自制多频率、幅度变化和静音片段的独立音频比较。
临时自制影片完成后删除，原游戏文件只读。
Android 检查使用私有 `MovieTestActivity`／`PlayerInstrumentation`，
`movie_file` 指模拟器应用私有目录中已准备的测试影片 basename。

```text
adb -s emulator-5584 shell am instrument -w -e movie_file GOP.mpg -e limit_ms 15000 -e video_stall_ms 500 -e max_underruns 0 org.circus.runtime/.PlayerInstrumentation
adb -s emulator-5584 shell am instrument -w -e movie_file GOP.mpg -e max_underruns 0 org.circus.runtime/.PlayerInstrumentation
```

本机证据在 `build/movie-018/`，发布核验摘要在
`build/distribution/android-verification-0.12.6.json`，这些生成物不进入源码仓库。
接口依据：[Android AudioTrack 文档](https://developer.android.com/reference/android/media/AudioTrack)。
解码比较参考：[FFmpeg MPEG audio 源码](https://github.com/FFmpeg/FFmpeg/blob/master/libavcodec/mpegaudiodec_template.c)。
