# Android 0.12.1：DC2DM 快进时打开菜单报错修复

安装 `build/distribution/circus-runtime-0.12.1-android-dev.apk`。
包名和开发签名延续 0.12，直接覆盖安装，保留游戏列表、目录授权和解释器存档。
不要先卸载或清除应用数据。游戏配置与 EXE 数据选择方式见 [0.12 说明](ANDROID_012.md)。

## 原因与修复

用户报告 `MAR_0114_A1 @0x4938`：`No stable story context for system menu`。
共享前端在帧末向宿主发送快进事件，清除文字显示等待，但下一帧才执行后续 MES。
此时旧工具栏仍能接收输入。系统菜单要求有可恢复的对白或选项等待，导致正常输入抛出异常。
本地原始数据与旧控制器能在同一脚本、同一偏移复现。

`0x4938` 是原版的 `dm.wait.message`，该指令执行后停在 `0x493b` 等待点击。
现在菜单请求立即停止自动播放/快进，执行已释放的指令，等到可恢复的剧情状态再调用原版系统脚本。
若正在场景延时或等待音频，请求保留到下一处稳定等待；不会伪造媒体完成事件。
重复的菜单请求不会覆盖已挂起的剧情；读档取消未处理的菜单请求和快进状态。
原 `Dc2Session` 的快照保护及 VM 未实现指令诊断继续生效。

修改位于 `DesktopLegacy`，同时用于 PC 和 Android 的 DM、PC1、PC2 前端。
DD 使用独立前端；本次没有改动其脚本语义。存档格式没有变化。

扩展回归还发现 PC1/PC2 的菜单 Back 沿用了 DM 动作槽。原生 PC1 `419310..419379`、
PC2 `423A4C..423AB5` 都把右键/Esc 绑定到 action0，左键/Enter 绑定到 action1；
接受跳转后清空对应绑定，确认框则另用 action2/action3。现已按这两份原生实现修复，
同时覆盖菜单和可中断延时，语义写入 VM specification。新增 20 项核心断言检查标签跳转、
过期输入、绑定清除及确认框独立性；JVM 核心共 362 项通过。

## 可复现验证

`LegacyInputAudit` 在 JVM 和 Android 的实际共享前端上运行同一组检查：

- 从原版启动流程快进到 `MAR_0114_A1@4938`，复现两种文字等待标志都已清除的瞬间。
- 菜单返回后仍是报告的句子，PC 为正确的 `0x493b`；存读档后文字、PC 与渲染像素一致。
- 回看、操作说明和设置均返回同一句；20 次快进/菜单往返保留整个挂起的 VM/宿主快照。
- 在真实的 `MAR_0114_A1@4d6c` 场景延时请求菜单，确认延迟执行，而不是忽略请求或直接放行不稳定快照。
- 继续快进通过整段 `MAR_0114_A1`，进入 `MAR_0114_B1`。

该确定性回归每个平台 33 项，**模拟媒体完成**以定位输入时序，不等同于完整音视频播放验证。
Android 另通过实际触摸展开工具栏、打开原版菜单、按 Back 返回该句，截图保存在本机构建目录。
四部游戏的常规 Android 检查仍覆盖标题、12 次对白推进、回看、存档画面和实际音频启动；
DM/PC1/PC2 额外检查快进时打开菜单、回看及 Back 返回。

```powershell
python tools/build_desktop.py --java-home "E:/DevEco Studio/jbr"
javac -encoding UTF-8 --release 8 -cp build/desktop/classes -d build/input-013 tools/LegacyInputAuditMain.java
java -cp "build/desktop/classes;build/input-013" LegacyInputAuditMain D:/gal/DC2DM build/input-013/desktop
python tools/build_android.py --java-home "E:/DevEco Studio/jbr"
adb -s emulator-5584 install -r build/android/circus-core-debug.apk
adb -s emulator-5584 shell am instrument -w -e tree content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDC2DM -e inputRegression true org.circus.runtime/.SharedRuntimeInstrumentation
python tools/android_shared_smoke.py --out build/android/verification-013
```

测试设备为 Android 15 / API 35 x86_64 模拟器。未启动 Windows 游戏、未操作连接的手机，未宣称 Android 全路线已验证。
本次日志、画面及构建摘要位于 `build/input-013/`、`build/android/verification-013/` 和
`build/distribution/android-verification-0.12.1.json`；原游戏资源和画面不进入源码仓库。
