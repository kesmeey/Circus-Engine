# 构建与缓存目录

核心编译、常见流程检查、Android 构建、Windows 打包和 REA 静态分析支持
`--build-root`，也可统一设置 `CIRCUS_BUILD_ROOT`。显式参数优先；两者都未设置时
继续使用仓库的 `build`。已有工具链、SDK、原游戏和旧验证记录无需迁移。

例如，在 PowerShell 中将本轮产物放到 F 盘：

```powershell
$env:CIRCUS_BUILD_ROOT = 'F:\circus-runtime-work\dc4ph-20261111'
$env:PYTHONDONTWRITEBYTECODE = '1'
$jdk = 'D:\gal\circus-runtime\build\toolchain\jdk-21.0.9+10'
D:\ANACONDA\python.exe -B -X utf8 tools\check_native_core.py --java-home $jdk
D:\ANACONDA\python.exe -B -X utf8 tools\build_desktop.py --java-home $jdk
```

`desktop` 和 `desktop-tests` 分别包含播放器与完整测试类；编译器响应文件、冻结输入、
资源副本、测试夹具也留在所选目录。子进程的 `TEMP`、`TMP` 和 `TMPDIR` 指向该目录
下的 `tmp`。`check_ty_workflows.py`、`check_holdout_workflows.py` 和
`check_dcdx_workflows.py` 的默认报告目录跟随此设置，原有 `--out` 仍可指定独立目录。

Android 发布时优先使用冻结镜像，避免生成器修改正在编辑的源码：

```powershell
D:\ANACONDA\python.exe -B -X utf8 tools\freeze_android_validation.py `
  --output 'F:\circus-runtime-work\dc4ph-20261111\android-final-v1' `
  --sdk 'F:\ethornell-android\sdk' --java-home $jdk
```

冻结目录必须是所选构建根的新子目录，已有镜像拒绝覆盖。镜像中的构建根显式设为
`workspace/build`，不会受外层环境变量影响。现有 `build/android/debug.keystore`
只读复制到镜像；也可用 `--signing-key` 明确选择原签名文件。移动构建目录不会自动
换签名，覆盖安装仍需使用与已安装 APK 相同的证书。直接使用 `build_android.py`
也支持这两个参数，但会像旧命令一样运行源码生成器。

`package_desktop.py` 将编译输出与 `distribution` 均放在构建根，仍通过 `--jdk`
和 `--ffmpeg` 只读复用已有工具。`build_windows_native.py` 的 MSVC 产物也跟随构建根。
`rea_static.py` 保留既有 Node、JDK、Ghidra 和 REA 路径，只把新临时数据库放到构建根
的 `rea-temp`；REA 自身的显式报告/缓存参数仍按该工具的参数设置。本机 REA native guard
会拒绝部分 F 盘目标路径，已保留的诊断并不表示普通 Java/Android 构建失败；这类分析
可用 `--temp-root` 明确选择最小的可用临时目录，最终报告仍指定到 F 盘。

这些选项不会移动或删除旧的 `build`。需要保留的旧安装包与验证记录继续保持原路径。
