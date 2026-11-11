"""Build self-authored Windows JNI services with MSVC; no game EXE is loaded."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
from build_paths import build_environment, build_root

ROOT = Path(__file__).resolve().parent.parent
TARGETS = {
    'pc1_window': ['user32.lib', 'gdi32.lib', 'imm32.lib', 'winmm.lib'],
    'pc1_gdi': ['user32.lib', 'gdi32.lib'],
    'dc2_gdi': ['user32.lib', 'gdi32.lib'],
    'pc1_audio': ['user32.lib', 'winmm.lib', 'dsound.lib', 'dxguid.lib', 'ole32.lib'],
    'pc1_paths': [], 'pc1_image_heap': [], 'pc1_image_io': [],
    'pc1_com_probe': ['user32.lib', 'winmm.lib', 'dsound.lib', 'dxguid.lib', 'ole32.lib'],
}


def quoted(path):
    text = str(path)
    if any(c in text for c in '%"\r\n'):
        raise ValueError('Path cannot be represented safely in an MSVC batch file')
    return '"'+text+'"'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--vcvars', type=Path, required=True, help='Visual Studio VC/Auxiliary/Build/vcvarsall.bat')
    parser.add_argument('--target', action='append', choices=sorted(TARGETS))
    parser.add_argument('--build-root',type=Path,help='Generated output root; defaults to CIRCUS_BUILD_ROOT or workspace build')
    args = parser.parse_args()
    if os.name != 'nt':
        parser.error('This service build requires Windows and MSVC')
    jdk, vcvars = args.java_home.resolve(), args.vcvars.resolve()
    for path in [jdk/'include/jni.h', jdk/'include/win32/jni_md.h', vcvars]:
        if not path.is_file():
            parser.error('Missing build input: '+str(path))
    generated_build=build_root(args.build_root)
    env=build_environment(generated_build)
    out = generated_build/'windows-native'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    source = out/'source'
    source.mkdir(parents=True)
    inputs = {}
    for path in sorted((ROOT/'runtime/desktop/native').iterdir()):
        if path.suffix in {'.c', '.h'}:
            raw = path.read_bytes()
            (source/path.name).write_bytes(raw)
            inputs[path.relative_to(ROOT).as_posix()] = hashlib.sha256(raw).hexdigest()
    report = {'sources': inputs, 'targets': {}, 'original_exe_executed': False}
    for target in dict.fromkeys(args.target or TARGETS):
        dll = out/(target+'.dll')
        command = ['cl', '/nologo', '/LD', '/MT', '/O2', '/W4', '/WX',
                   '/I'+quoted(jdk/'include'), '/I'+quoted(jdk/'include/win32'),
                   '/Fo:'+quoted(out/(target+'.obj')), '/Fe:'+quoted(dll),
                   quoted(source/(target+'.c')), '/link',
                   '/IMPLIB:'+quoted(out/(target+'.lib')), *TARGETS[target]]
        batch = out/(target+'.cmd')
        batch.write_text('@echo off\nchcp 65001 >nul\ncall '+quoted(vcvars)+' x64 >nul\n'
                         'if errorlevel 1 exit /b %errorlevel%\n'+' '.join(command)+'\n'
                         'exit /b %errorlevel%\n', encoding='utf-8')
        result = subprocess.run(['cmd.exe', '/d', '/v:off', '/c', str(batch)], cwd=out,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,env=env)
        (out/(target+'.log')).write_bytes(result.stdout)
        item = {'exit_code': result.returncode}
        if result.returncode == 0:
            item['dll_sha256'] = hashlib.sha256(dll.read_bytes()).hexdigest()
        report['targets'][target] = item
        (out/'manifest.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
        print(target, item, flush=True)
        if result.returncode:
            print(result.stdout.decode('utf-8', errors='replace')[-4000:])
            raise SystemExit(result.returncode)
    print(out, flush=True)


if __name__ == '__main__':
    main()
