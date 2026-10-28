# Android 0.12.3：DC3DD 转场性能修复

安装 `build/distribution/circus-runtime-0.12.3-android-dev.apk`。
包名、签名和存档格式沿用 0.12.2，可直接覆盖安装；不要先卸载或清除数据。
最近游戏列表、EXE 数据选择及 [0.12.2 的 DC2DM 立绘附属图修复](ANDROID_0122.md) 保留。

## 定位与修改

夏露露线 `DD_2_1212_A1` 开头，在报告台词前连续执行三次 mode 21 遮罩：

| 指令位置 | 遮罩 | 脚本时长 |
|---|---|---|
| `0xe33` | `MSK\MASK05A` | 600 ms |
| `0xe5f` | `MSK\MASK05B` | 1000 ms |
| `0xe95` | `MSK\MASK05A` | 1000 ms |

旧共享绘制器对 1024×576 的每个像素分别读取遮罩、读取旧画面、写回透明度。
Android 每帧因此调用约 177 万次 Bitmap 单像素接口。现在批量读写像素，
缓存按原规则采样的遮罩蓝色通道，并用 256 项表保留原透明度截断和钳制规则。
遮罩对象或目标尺寸变化时重新采样；结束转场或读档时释放临时缓冲。

普通淡入淡出也复用一张整屏临时图，避免每帧重新分配。每帧仍重绘旧场景，
先用不透明黑底覆盖前一帧，防止透明度积累或旧画面残留。
没有更换原遮罩、降低分辨率、跳过效果或缩短脚本时长。

修复适用于 DD 共用绘制路径，不包含路线、台词或脚本地址特判。
本地 214 个 MES 的静态扫描发现 mode 17 有 207 处、mode 21 有 63 处，
合计 270 处遮罩转场指令；这些指令均进入该绘制路径。
这是静态指令数量，不代表已经逐场观看或测量全部路线。

## 性能与画面验证

同一 API 35 x86_64 模拟器、相同原数据和帧进度，测量 `render()`：

| 阶段 | 修复前连续帧中位数 | 修复后连续帧中位数 | 修复后首次绘制 |
|---|---:|---:|---:|
| 第一次张开 | 536.87 ms | 3.54 ms | 131.43 ms |
| 再次合上 | 530.96 ms | 3.65 ms | 62.85 ms |
| 第二次张开 | 526.46 ms | 3.83 ms | 69.66 ms |

每阶段取 9 个进度点，连续帧统计使用首次绘制之后的 8 帧。
Android 修复前后 27 帧 ARGB 哈希全部相同；PC Java2D 另做同平台比较，也全部相同。
两平台分别验证三段演出中途存读档、到达报告台词，以及 8 组转场的新建/复用缓冲等价性，
各通过 19 项场景检查。8 组涵盖普通 mode 0/1/2、白闪 3、两种固定遮罩 17、反向 20 和普通 21。
Android 活动层另通过 4 项初始化、标题、演出后台词及错误状态检查。

另外有 484 组像素比较，覆盖遮罩放大/缩小、蓝色通道、透明输入、不同进度、
缓存命中、换遮罩、相同面积但尺寸改变，以及清空缓存。JVM 和 ART 均通过。
374 项核心断言、7 项 Android Canvas 像素合同和 47 项 Python 测试通过。
四部游戏的 Android 启动、对白推进、存读档、回看和原版菜单回归共 98 项通过。

以上为渲染耗时，不是实体手机 FPS。定位过程模拟媒体完成；没有启动 Windows 游戏、
操作实体手机或逐段验证全部音视频。首次资源解码和读取仍可能造成短暂停顿，
本次不据此宣称所有场景都不会卡顿。

## 复现

```powershell
python tools/build_desktop.py --java-home "E:/DevEco Studio/jbr"
java -cp build/desktop/classes org.circus.core.TransitionMaskTests
java -Xmx768m -cp build/desktop/classes org.circus.core.DesktopTransitionAudit D:/gal/DC3DD build/transition-audit/desktop
python tools/build_android.py --java-home "E:/DevEco Studio/jbr"
adb -s emulator-5584 install -r build/android/circus-core-debug.apk
adb -s emulator-5584 shell am instrument -w -e tree content://com.android.externalstorage.documents/tree/primary%3ADownload%2FDC3DD -e transitionRegression true org.circus.runtime/.SharedRuntimeInstrumentation
python tools/android_shared_smoke.py --out build/android/verification-015-final
```

模拟器需预先导入自己的原游戏目录并授予 SAF 读取权限。
Android 专项结果位于应用私有目录
`files/android-011-audit/dc3dd-a5a8/transition-regression/`。
使用 `tools/compare_transition_audits.py before/transition-result.json after/transition-result.json`
比较同一图形后端的两次结果，工具会检查帧哈希、脚本位置、遮罩与时长。
不要直接比较 PC 与 Android 的哈希：两平台字体和图形后端不同。

本机证据位于 `build/transition-015/` 和 `build/android/verification-015-final/`，
发布摘要为 `build/distribution/android-verification-0.12.3.json`。
原游戏画面、脚本、存档及测试数据不进入 APK 或源码仓库。
