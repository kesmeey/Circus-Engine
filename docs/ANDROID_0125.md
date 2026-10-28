# Android 0.12.5：DC3DD 鉴赏进度记录

安装 `build/distribution/circus-runtime-0.12.5-android-dev.apk`。
包名、签名和存档格式沿用上一版，直接覆盖安装；不要卸载或清除应用数据。
保留 0.12.3 的遮罩转场优化和 0.12.4 的屏幕刷新调度。

## 原因与修复

CG MODE 与 SCENARIO MODE 的原版脚本和缩略图图集已经可以执行、绘制，
但 DD 宿主漏掉了原引擎自动设置的两类全局标记：

- 实际显示的 CG 对应 `NAMES.images` 索引，写入 bank 97。
- 执行脚本转移时，已经结束的当前剧情对应 `NAMES.scenario_scripts` 索引，写入 bank 98。

因此正常游玩和通关并没有产生鉴赏菜单需要的记录，菜单仍显示锁定占位图。
旧 `DesktopExtrasAudit` 直接设置一个 CG 标记验证菜单，未验证游玩产生标记，漏掉了这条链路。
本次加入真实路线执行、重启后打开鉴赏、早期存档读取和旧版本恢复的回归。

显示背景及发布场景时按精确目录条目记录 CG；只预加载而未显示的图片不据此解锁。
脚本转移记录的是转移前的剧情，刚进入下一段时不会提前将其标为完成。
新 CG、已完成剧情和路线完成标记发生变化时，在当前 tick 结束后原子写入 `progress.dat`。
重复显示同一张图不会反复写盘。读入早期存档合并已有 CG／剧情记录，保留当前设置。
没有修改原版鉴赏脚本，也没有全解锁正式入口。

## 旧进度能恢复多少

升级后首次打开游戏，会检查该游戏自己的普通存档、快速／自动存档和移动端恢复存档。
恢复已存入快照的鉴赏标记、回看及当前画面里能够确认显示过的 CG，
以及回看记录中能够确认已经跨越的剧情段落。原存档文件保持不变。
读取旧存档时也会补回其中的证据，并与当前全局记录合并。

旧版回看最多保存最近 200 条文本，旧存档并不包含完整游玩轨迹。
因此无法保证补回通关路线全部 CG 差分及场景，也不会仅凭一个路线完成标记推测所有分支。
没有保留下来的内容，需要在新版再次游玩经过才会记录。
恢复结果在应用私有游戏存档目录的 `gallery-recovery-v1.txt`；
无法解析的旧文件会记入报告并保留，之后读取其他存档仍可继续恢复。

## 静态依据

只读取原程序，没有启动 Windows 游戏。分析样本 `DC3DD.EXE` SHA-256：
`ff119f4396cc1382e349fb1ed2c2eb77191db8ec699cb1352ca021cc73407e8d`。

| 地址 | 行为 |
|---|---|
| `40D5B0 → 40EDC0` | 按图像名查 NAMES，写 `state + 0x304 + index = 1`，即 bank 97 |
| `42D3C6`、`425AEA` | 背景显示／交换时调用 CG 记录 |
| `40D6C0 → 40EDE0` | 按剧情文件名查 NAMES，写 `state + 0x7b4 + index = 1`，即 bank 98 |
| `430896`、`43096E` | 两类脚本转移先记录当前剧情 |
| `4277C0` | 随后才把下一文件名写入 `state + 0x4b7c` 并加载 |

`CG_ALL.MES` 按 bank 97 的差分标记合计判断锁定状态；`SCE_ALL.MES` 按 bank 98 判断。
这也解释了为何绘制正常却全部显示占位图。

## 验证及范围

`DdGalleryAudit` 从原版标题选择立夏线并按第一选项推进，完整经过 3,352 句对白回到标题。
PC JVM 与 Android API 35 x86_64 模拟器均得到 79 个 CG 差分、21 个已完成剧情标记。
41 项共享检查覆盖：新安装保持锁定、实际路线解锁、未玩内容仍锁定、
重启保留进度、读早期存档不倒退、原版缩略图及 CG 大图、Scene Jump 启动所选剧情、
旧存档恢复及原文件不变。Android 另检查 SAF 初始化与原版标题到达。
时间和媒体完成由测试驱动，不是实体手机逐段视听验收，也不是所有路线分支的认证。

385 项 JVM／ART 核心断言、484 组遮罩像素比较、7 项 Canvas 合同、47 项 Python 测试通过。
四部游戏 Android 启动、对白、存读档、回看及菜单回归共 98 项通过。
本次仅改变 DD 的鉴赏进度及存档合并，不改变其他三部游戏的语义。

```powershell
python tools/build_desktop.py --java-home "E:/DevEco Studio/jbr"
java -Xmx768m -cp build/desktop/classes org.circus.core.DesktopGalleryAudit D:/gal/DC3DD build/gallery-audit/desktop
python tools/build_android.py --java-home "E:/DevEco Studio/jbr"
adb -s emulator-5584 install -r build/android/circus-core-debug.apk
adb -s emulator-5584 shell am instrument -w -e tree content://com.android.externalstorage.documents/tree/primary%3ADownload%2FDC3DD -e galleryRegression true org.circus.runtime/.SharedRuntimeInstrumentation
```

模拟器需已有游戏数据与 SAF 读取授权。专项结果写到应用私有目录
`files/android-011-audit/dc3dd-a5a8/gallery-regression/`。
本机证据在 `build/gallery-017/`、`build/android/verification-017/`；
发布摘要在 `build/distribution/android-verification-0.12.5.json`。
游戏资源、截图、实际存档及签名私钥不进入源码仓库或源码包。
