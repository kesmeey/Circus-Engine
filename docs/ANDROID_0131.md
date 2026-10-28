# Android 0.13.1：双指点按打开原版菜单

安装 `build/distribution/circus-runtime-0.13.1-android-dev.apk`。
沿用已有包名、签名及存档，可覆盖安装；不要卸载或清除应用数据。
游戏兼容范围沿用 [0.13.0](ANDROID_0130.md)。

## 操作

- 两根手指轻点游戏画面并快速松开，等同于鼠标右键。在 DC2PC 正文中打开原版菜单，可选择 SAVE、LOAD、CONFIG 等按钮。
- 菜单中的双指点按仍交给游戏原有右键处理；通常用于关闭或返回。
- 单指点击继续推进正文，系统返回键也仍对应鼠标右键。
- 双指上下滑动仍用于已有滚动支持的列表；滑动结束不会再触发右键。

双指点按需在 350 毫秒内完成，两指移动均不超过 Android 的触摸容差。
两种抬指顺序都可用。长按、第三根手指、触摸取消或切换到后台会取消点按识别。
输入接入共享 Android 游戏画面，沿用各游戏已有的右键行为；影片弹窗继续使用原来的播放和跳过操作。

## 复现验证

本次 APK 在 API 35 模拟器通过 DC2PC 22 项、DC2DM 23 项手势与原版菜单检查；
552 项 JVM／Android ART 断言、7 项 Canvas 像素契约和 484 项遮罩像素比较也通过。
未在实体手机上验证本次手势。

构建后，在已有原版游戏测试目录和 SAF 授权的 Android 模拟器运行：

```powershell
python tools/build_android.py --java-home '<JDK directory>'
adb -s emulator-5584 install -r build/android/circus-core-debug.apk
adb -s emulator-5584 shell am instrument -w -e tree 'content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDC2PC' -e gestureRegression true org.circus.runtime/.SharedRuntimeInstrumentation
adb -s emulator-5584 shell am instrument -w -e tree 'content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDC2DM' -e gestureRegression true org.circus.runtime/.SharedRuntimeInstrumentation
```

测试通过真实 Android `MotionEvent` 向游戏画面派发双指事件，检查原版菜单以及菜单前后的脚本位置和台词。
DC2PC 现有回看没有接入滚动处理，本次只核对双指滑动不误关闭回看；实际回看位置变化使用 DC2DM 验证。
截图和日志保留在本机构建目录，不随源码或 APK 发布。
