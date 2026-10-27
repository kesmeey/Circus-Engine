# Android 0.12.2：DC2DM 近景立绘附属图修复

安装 `build/distribution/circus-runtime-0.12.2-android-dev.apk`。
包名、签名和存档格式沿用 0.12.1，可直接覆盖安装。不要先卸载或清除应用数据。
最近游戏列表、EXE 数据选择及此前修复保留；配置方法见 [0.12 说明](ANDROID_012.md)。

## 原因

报告画面对应 `SIN_0810_F12`，正文位于 `0x2c34`，消息等待结束位置为 `0x2c59`。
脚本加载人物 `BST\NNK_0C_202_B@,BST\NNK_C`，并保留附属图
`BST\E_NNK_HAT2,BST\E_NNK`。`_B` 是近景图，`@` 是夜间色调。

原 DC2DM EXE 的 `428990` 将附属图原有的 `_B`、`@`、`*` 去掉，然后按人物资源
重新添加近景后缀与色调；归档名保持独立。`412504` 在角色提交时调用它，
`432488` 和 `432699` 在预载人物或附属图时调用它，因此只换表情也会重新匹配附属图。
这里只静态读取 PE 和原版数据，没有启动 Windows 游戏。

旧解释器直接叠加普通帽子，遗漏了这条资源选择规则：

| 图像 | CRX 原点 | 尺寸 |
|---|---|---|
| 错误的普通帽子 `E_NNK_HAT2` | 396,159 | 247×198 |
| 正确的近景帽子 `E_NNK_HAT2_B@` | 356,84 | 328×263 |

现在共享资源层按人物匹配附属图，PC/Android 的共享绘制路径使用解析后的资源名。
坐标仍来自原 CRX 和脚本的角色变换，没有按截图添加硬编码位移。
解析使用每一帧自身的角色信息，读档、回看与菜单场景捕获也适用；无需修改旧存档。
规则仅启用于已确认的 DC2DM profile，不扩散到其他三部作品或头像槽。

## 验证

核心回归增加 12 项，包括普通/近景切换、夜间/暖色色调继承、保留附属图后切换人物、
重复解析、扩展名与归档名保留，以及其他三个 profile 不受影响。
JVM 与 Android ART 均通过 374 项核心断言，Canvas 7 项像素合同、47 项 Python 测试通过。
四部游戏的 Android 标题、对白推进、存读档、回看及原版菜单回归共 98 项通过。

`DmOverlayAudit` 在 JVM 与 Android ART 上使用同一套测试，从原版标题、选项流程
推进至报告中的台词。`SIN_0810_F1` 选择 `sel1b`，此前按原始第一选项推进。
检查原脚本资源、CRX 几何、角色变换、18,546 个不透明帽子像素，以及存读档和
原版菜单往返后的画面一致性。快速流程模拟媒体完成，不代表逐段音视频播放验证。

```powershell
python tools/build_desktop.py --java-home "E:/DevEco Studio/jbr"
java -cp build/desktop/classes org.circus.core.DesktopOverlayAudit D:/gal/DC2DM build/render-alignment-014/desktop
python tools/build_android.py --java-home "E:/DevEco Studio/jbr"
adb -s emulator-5584 install -r build/android/circus-core-debug.apk
adb -s emulator-5584 shell am instrument -w -e tree content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDC2DM -e overlayRegression true org.circus.runtime/.SharedRuntimeInstrumentation
python tools/android_shared_smoke.py --out build/android/verification-014
```

Android 另检查实际工具栏触摸、Back 返回及渲染/媒体错误状态。
报告场景的共享回归在两平台各通过 12 项，Android 活动层另通过 5 项。
完整验证记录位于本机 `build/render-alignment-014/`、`build/android/verification-014/`，
发布摘要为 `build/distribution/android-verification-0.12.2.json`。
测试目标是 API 35 x86_64 模拟器；没有操作实体手机，也不声称 Android 全路线已验证。
原游戏图像、存档和脚本不进入 APK 或源码仓库。
