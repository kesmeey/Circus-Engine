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
    parser.add_argument('--version', default='0.14.8-sc-fl-dev')
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
    readme = '''DC Runtime — 非 Unity 原生规则重写开发版

双击 DC Runtime.cmd，选择包含 AdvData 的游戏目录。
也可以把游戏目录拖到 DC Runtime.cmd 上。无需安装 Python、Java 或 Android SDK。
非 Unity 入口包括 DCPC、DC2PC、DC2、DC2SC、DC2FL、DC2DM、DD、WY、PP。
读取原版 START/MES，使用原版标题、系统脚本、图片和声音；包内不含游戏数据。

左键 / Enter：推进；右键 / Esc：原版菜单或返回。PC1 空格：隐藏/显示正文；选择画面请用鼠标点选。
PC1 的 PgUp/PgDn、对话框上下箭头按原生日志回看，右键退出回看；PC2 对话框上下箭头可回看。DC3DD/DC2DM 使用各自 Log 面板。
鉴赏列表：鼠标滚轮、原翻页按钮或拖动滚动条；DD CG 查看时右键返回。
A：切换自动；按住 Ctrl：快进；F5 / F9：独立快速保存 / 读取；F11：全屏。
存档写入本包 saves，影片缓存写入 cache。
PC1 原版存读菜单和剧情自动槽使用独立便携检查点；F5/F9 保留当前会话与先前候选。
PC1快速保存已接独立便携槽1；原Windows PC1剧情槽互通仍未实现。
DC2闹钟存读上下文和自动/快速槽已接入；原生自动/快速槽均为0。
DC2桌面菜单「存档 → 导入原版剧情存档…」可转换普通ADV文本等待槽到原读取菜单可见的空闲普通槽。
已有目标槽拒绝覆盖，当前剧情保留；旧回看、NVL、选择/画面批次等待不在转换范围。
不同游戏分开保存，不写原游戏目录。Windows 原游戏剧情槽尚未完整导入，系统进度按各版本另行迁移。

本版本优先覆盖主要功能和大多数常见流程，允许延期少量难处理的版本或边界。
当前不把约80%的工程目标当作精确完成率，实际范围见 COMMON_WORKFLOWS.md。
SC/FL采用各自原生证据和独立宿主；SC本地Coco剧本有一份截断文件，对应分支仍会明确报错。
FL目录遗留cg_etc.mes的6C69标记不被本作原生loader接受，运行时也不会将它别名为9C69。
PC2首次已读缓存和角色坐标修正后，拒绝旧执行身份的PC2剧情快照；独立系统进度保留另行迁移。
已按各 EXE 独立证据重写脚本取指、变量布局及部分版本专属音频、UI、系统指令。
PC1新剧情存档以P1CS封装CVS2/rev8当前会话与独立候选；DC2用D2CS封装当前会话和独立闹钟/存档上下文；其他家族使用CVS2/rev8。旧CVS1或不同执行合同的旧快照明确拒绝读取并保留旧文件。
独立系统进度使用自己的迁移规则，不能用旧剧情槽的限制推断系统进度也不可用。
遇到未实现指令会停下并显示脚本位置，不会静默跳过。
本次验证范围和缺口见随包 NATIVE_REWRITE_STATUS.md 与 NATIVE_FIDELITY_AUDIT.md。
PC_COMPATIBILITY.md 包含旧版兼容层记录，不是本次全引擎忠实度证明。
'''
    (package/'先读我.txt').write_text(readme, encoding='utf-8-sig')
    shutil.copy2(ROOT/'docs/PC_COMPATIBILITY.md', package/'PC_COMPATIBILITY.md')
    for name in ['NATIVE_REWRITE_STATUS.md', 'NATIVE_FIDELITY_AUDIT.md', 'NATIVE_DECODER_CONTRACT.md', 'ENGINE_SCOPE.md', 'COMMON_WORKFLOWS.md']:
        shutil.copy2(ROOT/'docs'/name, package/name)
    (package/'validation').mkdir(exist_ok=True)
    for path in sorted((ROOT/'docs/validation').glob('*.json')):
        shutil.copy2(path, package/'validation'/path.name)
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
