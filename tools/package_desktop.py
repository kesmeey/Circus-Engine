"""Build a self-contained Windows PC interpreter, with no game data."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

from build_desktop import build, ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--ffmpeg', type=Path, required=True)
    parser.add_argument('--version', default='0.11.0-pc-dev')
    args = parser.parse_args()
    if any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_' for c in args.version):
        raise SystemExit('Invalid version')
    _, classes = build(args.jdk)
    dist = ROOT / 'build/distribution'
    dist.mkdir(parents=True, exist_ok=True)
    package = dist / ('dc-runtime-' + args.version + '-windows-x64')
    package.mkdir(exist_ok=True)
    (package / 'bin').mkdir(exist_ok=True)
    if not (package / 'jre/bin/javaw.exe').exists():
        subprocess.run([str(args.jdk/'bin/jlink.exe'), '--add-modules', 'java.desktop,jdk.charsets',
                        '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=zip-6',
                        '--output', str(package/'jre')], check=True)
    icon = ROOT/'runtime/android/res/drawable-nodpi/launcher_logo.png'
    subprocess.run([str(args.jdk/'bin/jar.exe'), '--create', '--file', str(package/'dc-runtime.jar'),
                    '--main-class', 'org.circus.core.DesktopRuntime', '-C', str(classes), '.',
                    '-C', str(icon.parent), icon.name], check=True)
    shutil.copy2(args.ffmpeg, package/'bin/ffmpeg.exe')
    for name in ['LICENSE', 'THIRD_PARTY.md']:
        shutil.copy2(ROOT/name, package/name)
    shutil.copy2(args.jdk/'NOTICE', package/'JRE-NOTICE')
    # The command processor receives paths only as individually quoted arguments.
    launch = r'''@echo off
setlocal
cd /d "%~dp0"
start "DC Runtime" "%~dp0jre\bin\javaw.exe" -Xmx1536m "-Ddc.ffmpeg=%~dp0bin\ffmpeg.exe" "-Ddc.saveRoot=%~dp0saves" "-Ddc.cacheRoot=%~dp0cache" -jar "%~dp0dc-runtime.jar" %*
'''
    (package/'DC Runtime.cmd').write_bytes(launch.replace('\n','\r\n').encode('ascii'))
    readme = '''DC Runtime — 四版本 PC 解释器测试版

双击 DC Runtime.cmd，选择包含 AdvData 的游戏目录。
也可以把游戏目录拖到 DC Runtime.cmd 上。无需安装 Python、Java 或 Android SDK。
支持本次核验的 DC3DD、DC2DM、D.C.II P.C、D.C.P.C 四种数据版本。
读取原版 START/MES，使用原版标题、系统脚本、图片和声音；包内不含游戏数据。

左键 / Enter / 空格：推进；右键 / Esc：原版菜单或返回。
旧 PC 两代对话框的上下箭头可回看；DC3DD/DC2DM 使用原版 Log 面板。
鉴赏列表：鼠标滚轮、原翻页按钮或拖动滚动条；DD CG 查看时右键返回。
A：切换自动；按住 Ctrl：快进；F5 / F9：独立快速保存 / 读取；F11：全屏。
原版存读档菜单也已接入；存档写入本包 saves，影片缓存写入 cache。
不同游戏分开保存，不写原游戏目录。Windows 原游戏存档尚不导入。

这是兼容性测试版。已验证标题、正文、立绘、选择、原版存读档和选定完整路线。
已接入PC1原姓名编辑、存档预览/角色图标、BGM位置续播、文字显示速度和角色语音设置。
0.11修复连续系统截图丢失底图，冻结截图中的控件/颜色/动画，兼容0.9/0.10解释器存档。
尚不表示所有路线、鉴赏、设置和演出均已逐项与原作一致。
遇到未实现指令会停下并显示脚本位置，不会静默跳过。
更多测试范围和限制见随包 PC_COMPATIBILITY.md。
'''
    (package/'先读我.txt').write_text(readme, encoding='utf-8-sig')
    shutil.copy2(ROOT/'docs/PC_COMPATIBILITY.md', package/'PC_COMPATIBILITY.md')
    ffmpeg_version = subprocess.run([str(args.ffmpeg), '-version'], capture_output=True, text=True, check=True).stdout
    (package/'FFMPEG-BUILD.txt').write_text(ffmpeg_version, encoding='utf-8')
    report = {'version': args.version, 'game_assets_included': False, 'source_sha256': (ROOT/'build/desktop/source.sha256').read_text(),
              'ffmpeg_sha256': hashlib.sha256(args.ffmpeg.read_bytes()).hexdigest(), 'files': {}}
    for p in sorted(package.rglob('*')):
        if p.is_file() and p.name != 'BUILD.json' and not any(x in {'saves','cache'} for x in p.relative_to(package).parts):
            report['files'][p.relative_to(package).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
    (package/'BUILD.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    archive = dist/(package.name+'.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for name in list(report['files'])+['BUILD.json']:
            z.write(package/name, package.name+'/'+name)
    print(json.dumps({'directory': str(package), 'zip': str(archive), 'bytes': archive.stat().st_size,
                      'sha256': hashlib.sha256(archive.read_bytes()).hexdigest()}))


if __name__ == '__main__':
    main()
