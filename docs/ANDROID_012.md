# Android 0.12：游戏列表、EXE 选择、DD 重启与 DM 片头修复

安装 `build/distribution/circus-runtime-0.12.0-android-dev.apk`。
包名和签名与 0.11 相同，直接覆盖安装；不要卸载或清除应用数据，才能保留私有存档和目录授权。

## 操作

- 首页显示最近打开的游戏。添加目录后，选择 EXE 数据文件并保存；以后直接点对应游戏的“启动”。
- 每部游戏独立保存目录、EXE、脚本文字编码与字符替换表。进程退出、重新打开后仍保留。
- 旧版的最后一个目录自动迁移。旧版没有记录的其他目录需要添加一次。
- 目录移动或授权失效时，用“更多 → 重新定位目录”恢复访问。移除列表项不会删除游戏或存档。
- 手动选择 EXE 后只读取该文件。不会因为名字含有 `chs`、`crack` 而替用户决定，也不会在选择错误时自动改用另一个文件。

EXE 是只读数据来源，不作为 Windows 进程启动。引擎版本仍通过原版 MES 的格式识别。
DD 使用角色头像映射，DM 使用头像和音乐标题表，PC1 使用姓名编辑表；PC2 当前只检查所选文件的 PE 格式，没有从它加载额外补丁代码。
可以选择“仅使用资源数据”，但 PC1 姓名编辑等依赖 EXE 数据表的功能仍需要兼容文件。
文字编码可独立指定 CP932 / GBK，字符替换配置支持选择其他文件名的 JSON。
Windows EXE/DLL 注入式汉化、未实现的 tunnel decoder 不会因为选择了文件而自动兼容。
更改文字编码/字符映射后，与原映射不符的解释器存档仍会被校验拒绝；恢复原配置可继续读取。

本地 DCPC 的 `DCPCDVD_CHS.exe` 中 `0x430e20` 字符行含有无效 GBK 字节，当前明确拒绝这张表。
可选原版 `DCPCDVD.EXE` 读取姓名表，中文脚本文字继续独立按 GBK 读取。
有效的 CP932 / GBK 姓名文字表按内容识别；数字字符范围仍采用其原来的 CP932 编码。

## `START @0xf86 / system.persist mode 26`

DD 的原版 START 根据系统标志 `100:10` 执行 `17 [26,0]`。0.11 的指令分发缺少该子模式，
所以保留已初始化的系统设置后再次启动可能报错；不需要 DC2DM 数据串入 DC3DD 才能发生。

静态调用链：`42E0FC → 40EE90`。原生函数打开系统设置文件，读取 `0x4b74` 和 `0x7f8` 字节，
恢复持久状态；文件不存在时调用 `40ED10(1)` 装入默认状态。
证据锁定原版 DD EXE SHA-256：
`ff119f4396cc1382e349fb1ed2c2eb77191db8ec699cb1352ca021cc73407e8d`。
`tools/capture_system_ui_evidence.py` 已增加默认设置和重读函数的静态范围。

共享核心现在将此指令绑定到当前游戏的持久设置存储，并在读档重建宿主后重新绑定。
它会重新读取解释器 `progress.dat` 中已建模的设置、解锁、角色语音参数，保留普通变量与当前 VM；
不存在时使用默认设置，损坏/无法读取时明确报错。未实现原 Windows 存档导入。
DD / DM 的存储目录由各自 profile 与 START 指纹区分，无需清空任何旧存档。

## `MAR_0101_F1 @0xa62 / DC2DM_OP01.MPG`

DM 的 `3B` 视频指令在该段脚本只给出文件名。宿主错误地拼成 `AdvData/DC2DM_OP01.MPG`，
漏掉原版使用的 `MOVIE/` 子目录，导致开场几分钟后的片头读取失败。这不是存档写入故障。
静态证据为 `43046F` 接收文件名、`432D17..432D31` 把 `MOVIE` 和 `.mpg` 交给路径函数 `422C20`。
现在正确请求 `AdvData/MOVIE/DC2DM_OP01.MPG`，旧解释器快照中停在该视频的路径也会迁移。

以前的 Android 开场检查只有 12 次对白；PC 路线检查使用模拟媒体完成事件，而单独视频测试直接选择文件，
因此没有覆盖“原脚本指令 → 拼路径 → 打开视频”的完整过程。
现在 PC 路线检查在模拟完成前也必须实际打开目标文件；新增 Android 测试从原版标题和正文推进，
实际播放该片头到自然结束，再检查下一段正文与存读档。
`tools/MovieReferenceAudit.java` 逐个把四部游戏的所有视频记录交给真实宿主绑定，确认生成的路径能读取。

## 验证

验证输出保存在本机 `build/android/verification-012/`；源码仓库不携带原游戏画面、脚本或 EXE。
测试使用 Android 15 x86_64 隔离模拟器和只读游戏资源；没有启动原 Windows 游戏，也没有操作连接的手机。
本次是针对启动、持久化与配置的回归验证，不是四部游戏的 Android 全路线认证。

```powershell
python tools/build_android.py --java-home "E:/DevEco Studio/jbr"
python tools/android_shared_smoke.py --out build/android/verification-012
adb -s emulator-5584 shell am instrument -w -e sequence true org.circus.runtime/.SharedRuntimeInstrumentation
adb -s emulator-5584 shell am instrument -w -e tree content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDC2DM -e opening true org.circus.runtime/.SharedRuntimeInstrumentation
```

序列测试复用应用已有存档，连续运行 DM → DD → DD，检查标题、首句正文、设置值、存储隔离及媒体/线程关闭。
合成测试同时覆盖原子列表写入、重新读取、两部游戏配置隔离、重复目录、移除、重新定位和损坏文件保护。
`NativeSelectionAudit` 静态读取本地 EXE，验证重命名后仍可使用，以及存在其他原版 EXE 时也拒绝错误的手动选择。

本次最终 APK 验证记录：

- JVM / Android ART 各 342 项核心断言，Android 列表持久化、CAS 存档与 7 项 Canvas 像素契约通过。
- 四部游戏通用前端回归共 86 项通过（DD 21、DM 21、PC1 23、PC2 21），包括原版标题、正文推进、回看、原版存档列表及音频启动。
- 同一应用进程的 DM → DD → DD 序列 13 项检查通过，复用已有系统记录和存档目录。
- DM 原版开场到 OP、OP 自然结束、下一场景及存读档共 9 项检查通过。实际播放 3,776 帧，最后一帧约 125,959 ms，未用模拟完成事件跳过。
- 通过实际目录选择器添加 DD、选择改名后的 EXE、启动后强制结束应用，再打开仍保留记录；PC2 的独立 GBK / 仅资源配置也保留。
- 四部本机游戏共 40 条视频记录的路径全部可读；这是路径检查，不表示每个编码格式都经过播放测试。

详细日志、截图和 APK 摘要见本机 `build/android/verification-012/` 与 `build/distribution/android-verification-0.12.0.json`。
