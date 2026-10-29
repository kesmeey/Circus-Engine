# Android 0.13.2：DC2PC 菜单及存档停顿修复

安装 `build/distribution/circus-runtime-0.13.2-android-dev.apk`，直接覆盖旧版。
沿用已有签名、包名和存档格式，不要卸载或清除应用数据。
双指点按操作见 [0.13.1](ANDROID_0131.md)，游戏兼容范围见 [0.13.0](ANDROID_0130.md)。

## 修复

原版菜单和保存确认会把当前画面截图作为背景。旧实现将 800×600 截图的每个像素单独传入 zlib，
一次截图产生 480,000 次压缩调用；读取截图也存在逐字节调用。
现在使用 32 KiB 缓冲批量压缩和解压，截图像素、原版菜单流程和存档格式保持不变。

同一 API 35 模拟器、同一 DC2PC 开场、各三次的中位耗时：

| 操作 | 旧实现 | 0.13.2 |
|---|---:|---:|
| 打开原版右键菜单 | 1,893 ms | 121 ms |
| 选存档槽、选图标、确认保存 | 3,631 ms | 203 ms |

这些数值测量 worker 内的原版命令、VM 调度和绘制，不包含手指停留、显示提交和媒体准备时间，
不是实体手机的端到端响应保证。进入 SAVE 列表本身没有明显改善，本次消除的是截图处理的大幅停顿。

## 原版 Windows 存档

原版 DC2PC 使用 `AdvData/SaveData/system.dat` 保存系统进度，`dc2pcNN.dat` 保存剧情槽位。
解释器使用应用私有目录 `files/shared-saves/<版本>-<START哈希>/` 中的 `progress.dat`、`slot-N.sav`。
两者的路径和二进制格式均不同；当前没有原版 Windows 存档导入功能。
因此复制整部原游戏及其存档到手机，不会自动继承原版通关、CG／场景解锁和剧情槽位。
重命名或更改存档路径不能完成转换。

原版通关／鉴赏标记可单独实现版本匹配的导入；剧情槽位还需要转换脚本位置、变量和运行状态。
本次更新只修复卡顿，没有新增上述导入功能。
本项目自己的 PC 解释器与 Android 使用共享存档格式，但目前也没有面向用户的跨端导入／导出入口。

## 验证

新增旧编码→新解码、新编码→旧解码、跨缓冲边界逐像素一致，以及截断／多余数据拒绝检查。
Android 性能测试同时验证三次原版菜单／存档往返保持原句，随后执行双指手势回归。
最终 APK 通过 559 项 JVM／Android ART 断言、7 项 Canvas 像素契约、484 项遮罩像素比较，
以及 DC2PC 24 项菜单／手势检查和 25 项正文、存读档、缩略图及画面恢复检查。
日志、对照 APK 和截图位于本地 `build/android/verification-0132/`，不随源码发布。

复现命令（模拟器需已有游戏测试目录和 SAF 授权）：

```powershell
python tools/build_android.py --java-home '<JDK directory>'
adb -s emulator-5584 install -r build/android/circus-core-debug.apk
adb -s emulator-5584 shell am instrument -w -e tree 'content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDC2PC' -e menuPerformance true org.circus.runtime/.SharedRuntimeInstrumentation
python tools/android_shared_smoke.py --serial emulator-5584 --games PC2 --out build/android/verification-0132/regression
```
