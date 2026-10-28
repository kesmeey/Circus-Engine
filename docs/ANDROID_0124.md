# Android 0.12.4：持续动画的刷新调度

安装 `build/distribution/circus-runtime-0.12.4-android-dev.apk`。
沿用 0.12.3 的包名、签名及存档格式，直接覆盖安装即可；不要先卸载或清除数据。
[0.12.3 的遮罩转场优化](ANDROID_0123.md) 与此前兼容性修复保留。

## 场景与原因

报告中的夏露露线台词位于 `DD_2_1224_F2@0x2912`，消息等待位置为 `0x2954`。
该场景在 `0xdcb` 加载 `SYSTEM\EFFSAKURA02`，`0xde0` 执行 `scene.effect [1,2]`，
产生五组、每组两个花瓣。报告台词处共有 10 个粒子，没有尚未结束的转场。

在同一模拟器上，0.12.3 绘制该场景的中位耗时为：开启落樱 3.636 ms，
关闭落樱 3.467 ms；包含粒子更新的 VM tick 中位耗时约 0.027 ms。
两种情况接近，未复现“粒子数量把渲染拖慢”的现象。

但 Android 端使用 `Handler.postDelayed(...,33)` 请求帧，理论上已限制在约 30 Hz，
再叠加主线程调度延迟，与屏幕刷新不同步，实测提交约 27.87 帧/秒。
持续移动的花瓣会明显暴露这种低刷新率；静止对白画面不容易看出来。

现在使用 `Choreographer.FrameCallback` 跟随屏幕刷新，按回调时间推进动画。
工作线程最多处理一个画面任务，忙时跳过本次请求，防止渲染任务排队拖延输入。
暂停、报错和销毁时取消回调，返回前台时重置时钟并恢复刷新。
没有减少花瓣、修改粒子轨迹或改变游戏速度；绘制器与粒子算法沿用 0.12.3。
刷新调度由四部游戏共用。

## 验证

相同 API 35 x86_64 模拟器、相同报告场景，连续观测 5 秒：

| 指标 | 0.12.3 调度 | 0.12.4 调度 |
|---|---:|---:|
| 画面提交速率 | 27.87 帧/秒 | 44.95 帧/秒 |
| 提交间隔中位数 | 36.13 ms | 18.63 ms |
| 提交间隔 P95 | 38.11 ms | 35.54 ms |

这些是 `Stage.present()` 的提交统计，不是实体手机或 SurfaceFlinger 的最终显示帧率。
模拟器仍有部分长帧；本次不声称消除全部卡顿或保证每台设备稳定 60 帧。
新调度也不会超出设备实际处理能力。

`DdPetalAudit` 从原版标题和选项流程到达报告台词，保存粒子状态。
修复后回放同一状态，分别开启、关闭落樱，各推进 120 帧并抽查 12 帧。
共 24 个同平台 ARGB 哈希与基线一致。另验证粒子数、原图集、无残余转场和存读档画面，
共享场景检查 4 项通过；Android 初始化、标题、持续刷新、错误状态、后台冻结、
前台恢复检查 6 项通过。

374 项 JVM/ART 核心断言、484 组遮罩像素比较、7 项 Canvas 合同、47 项 Python 测试通过。
四部游戏的 Android 启动、对白推进、存读档、回看和菜单回归共 98 项通过。
定位流程模拟媒体完成，没有启动 Windows 游戏，也没有操作实体手机。
基线第一次启动曾遇到片头 MPEG 打开错误，第二次启动及后续场景检查通过；
该失败日志保留，本次未修改媒体解码器。

## 复现

```powershell
python tools/build_desktop.py --java-home "E:/DevEco Studio/jbr"
java -Xmx768m -cp build/desktop/classes org.circus.core.DesktopPetalAudit D:/gal/DC3DD build/petal-audit/desktop
python tools/build_android.py --java-home "E:/DevEco Studio/jbr"
adb -s emulator-5584 install -r build/android/circus-core-debug.apk
adb -s emulator-5584 shell am instrument -w -e tree content://com.android.externalstorage.documents/tree/primary%3ADownload%2FDC3DD -e petalRegression true org.circus.runtime/.SharedRuntimeInstrumentation
```

模拟器需已导入原游戏目录并授予 SAF 读取权限。专项测试输出到应用私有目录
`files/android-011-audit/dc3dd-a5a8/petal-regression/`，包括 `scene.state`、
`petal-result.json` 和开启/关闭落樱的两张截图。
后续对比添加 `-e petalReplay true`，即可使用该状态，不再重新寻路；
PC 工具的第三个参数 `replay` 有相同作用。

本机证据位于 `build/petals-016/` 和 `build/android/verification-016/`，
发布摘要为 `build/distribution/android-verification-0.12.4.json`。
原游戏数据、画面及存档均不包含在 APK 或源码仓库中。
