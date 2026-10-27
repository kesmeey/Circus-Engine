# Android 0.11 开发版

安装包：`build/distribution/circus-runtime-0.11.0-android-dev.apk`。
Android 8.0（API 26）及以上；包含 arm64-v8a、armeabi-v7a、x86_64。沿用本项目开发签名、应用 ID 和用户提供的 D.C. 樱花图标。APK 不含原游戏资源。

## 使用方法

1. 安装 APK；已安装本项目旧版时直接覆盖安装，保留应用数据。
2. 将一部游戏的完整文件夹复制到手机，例如 `Documents/DC3DD/`。保留原目录结构及全部 `AdvData`、影片、配置和根目录文件。
3. 打开 CIRCUS Runtime，点击“选择数据目录”，选中该游戏根目录并允许读取。选择的目录下应有 `AdvData`；只有一部游戏的单层外包装目录也能识别。
4. 点击“启动游戏”，从原版 `START` 脚本进入标题和菜单。通过原版 New Game / Start 开始。

触摸对应鼠标左键；Android 返回键对应右键。原版滑条可以拖动，列表也支持双指上下滑动。更换作品时重新选择该作品目录。

本次接入以下四个本地版本，不能据此推断任意 D.C. 版本都兼容：

| 作品 | 配置 |
| --- | --- |
| D.C.III DreamDays | `dc3dd-a5a8` |
| D.C.II Dearest Marriage | `dc2dm-9d72` |
| D.C.P.C DVD | `dcpc-2063` |
| D.C.II P.C | `dc2pc-5769` |

D.C.P.C 的姓名输入字符表来自根目录 `DCPCDVD.EXE`，所以复制完整目录时应保留它。兼容层仅静态读取其中的数据，不执行 Windows 程序。

## 本次接入

Android 与 PC 0.11 共用 VM、原版系统按钮、菜单、回看、鉴赏、姓名编辑、截图、存档元数据及缩略图的实现。构建时仅将五个已验证的桌面呈现类的绘图导入映射到 Android Canvas 接口；不另外维护一套剧情或路线选择页面。映射记录在 `build/android/shared-backend.json`。

Android 层提供只读 SAF 文件读取、触摸、Canvas、MediaPlayer、MPEG/MP2 播放及前后台暂停。修复快速切换场景时过期的音频准备回调尝试启动已释放播放器的问题。绘图适配单独检查变换顺序、剪裁恢复、透明度、区域绘制、翻转及 PNG 像素往返。

存档写入应用私有目录，游戏原目录保持只读。旧 Android CAS 存档保留原件并转换到新的存档封装；旧版通关标志和 DM/PC 设置也迁移。原 Windows 存档不导入。卸载应用会删除其私有存档，覆盖安装不会。

## 验证及边界

在 Android 15 / API 35 的 x86_64 模拟器上安装签名 APK，运行 325 项核心断言、Android CAS 兼容检查和 7 项 Canvas 像素契约检查。

四个版本通过同一个 `RuntimeActivity` 从原版数据启动，用实际触摸进行标题、新游戏和 12 次对白推进，并检查存档后文本、指令位置、缩略图及画面的恢复，回看打开和返回、原版存档列表及音频实际启动。D.C.P.C 另检查原版姓名输入和确认。

证据及截屏保存在 `build/android/verification-011/`。PC1/PC2 使用本机资源制作的私有开场测试子集；测试子集不随 APK 或源码包分发。手机使用时应复制完整游戏目录。

最终 APK 共通过 86 项四游戏前端检查：DD 21、DM 21、PC1 23、PC2 21。DD/DM 的最终检查同时使用正常应用存档路径，验证旧版本数据迁移后仍能从标题运行。核验清单为 `build/distribution/android-verification-0.11.0.json`；对应源码为 `build/distribution/circus-runtime-0.11.0-android-dev-source.zip`。

这些是 Android 开场与系统功能检查，不等于四部游戏已在 Android 上全路线通关。PC 0.11 的路线验证不能冒充手机验证。字体度量和部分演出仍有平台差异，ARM 真机和不同厂商系统需要进一步实测。PC 引擎尚未完整还原的功能范围仍以 `docs/PC_COMPATIBILITY.md` 为准。

## 复现

```powershell
python tools/build_android.py --java-home "E:/DevEco Studio/jbr"
adb -s emulator-5584 install -r build/android/circus-core-debug.apk
python tools/android_shared_smoke.py
```

测试脚本要求独立测试模拟器中已有对应目录和 SAF 读取授权；不会自动运行原 EXE 或导入私人存档。普通测试使用隔离的测试存档目录。`--upgrade` 用于专用测试设备的旧版应用数据升级验证，使用该设备的正常应用存档。
