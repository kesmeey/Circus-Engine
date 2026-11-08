"""Check original TY stories, saves, music mode, replay text and rasters read-only."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
from build_desktop import ROOT, build


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('game', type=Path)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--classes', type=Path)
    parser.add_argument('--player-jar', type=Path)
    parser.add_argument('--out', type=Path)
    parser.add_argument('--full-routes', action='store_true')
    args = parser.parse_args()
    game = args.game.resolve(strict=True)
    out = (args.out or ROOT/'build/ty-workflows'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')).resolve()
    if out == game or game in out.parents or out.exists():
        parser.error('Use a new output directory outside the original game')
    out.mkdir(parents=True)
    if args.classes:
        java, classes = str(args.java_home/'bin/java.exe'), args.classes.resolve(strict=True)
    else:
        java, classes = build(args.java_home, with_tests=True)
    classpath = str(classes)
    if args.player_jar:
        classpath = str(args.player_jar.resolve(strict=True))+';'+classpath
    plan = [('startup-'+name, 'NativeStartupAudit', n, name) for n, name in enumerate(['hyd', 'set', 'mhr', 'myu'])]
    plan += [('save-load-'+name, 'TyDesktopHoldoutAudit', n, name) for n, name in enumerate(['hyd', 'set', 'mhr', 'myu'])]
    plan += [('selector', 'TySelectorRasterAudit', -1, ''), ('scroll', 'TyScrollRasterAudit', -1, '')]
    plan += [('music-mode', 'TyMusicModeAudit', -1, ''), ('replay-captions', 'TySceneReplayCaptionAudit', -1, '')]
    if args.full_routes:
        plan += [('route-'+name, 'DesktopRouteAudit', n, name) for n, name in enumerate(['hyd', 'set', 'mhr', 'myu'])]

    def run(item):
        name, audit, index, story = item
        dest = out/name
        dest.mkdir()
        command = [str(java), '-Xmx192m', '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8',
                   '-Ddc.saveRoot='+str(dest/'saves'), '-Ddc.cacheRoot='+str(dest/'cache'),
                   '-cp', classpath, 'org.circus.core.'+audit, str(game)]
        if audit == 'NativeStartupAudit':
            command += ['--exe', 'dc2ty_chinese(simplified)_patch.exe', '--lines', '20', '--steps', '1000000']
        else:
            command += [str(dest)]
            if audit == 'TyDesktopHoldoutAudit':
                command += [story]
            elif audit == 'DesktopRouteAudit':
                command += ['first', '--exe', 'dc2ty_chinese(simplified)_patch.exe']
        if audit in ['NativeStartupAudit', 'DesktopRouteAudit']:
            for action in ['02', 'start', 'ty:'+str(index), 'ty:'+str(index)]:
                command += ['--action', action]
        with (dest/'stdout.log').open('wb') as stdout, (dest/'stderr.log').open('wb') as stderr:
            result = subprocess.run(command, cwd=ROOT, stdout=stdout, stderr=stderr, timeout=900)
        row = {'exit_code': result.returncode, 'command': command}
        report = next((dest/n for n in ['report.json', 'result.json'] if (dest/n).exists()), None)
        if report is None and audit == 'NativeStartupAudit':
            report = dest/'stdout.log'
        if report is not None:
            raw = report.read_bytes()
            row.update(result=json.loads(raw), result_sha256=hashlib.sha256(raw).hexdigest())
        print(name, result.returncode, flush=True)
        return name, row

    with ThreadPoolExecutor(max_workers=3) as pool:
        checks = dict(pool.map(run, plan))
    (out/'result.json').write_text(json.dumps({'checks': checks, 'original_executables_executed': False,
        'original_game_directories_written': False, 'scope': 'Original menus and first legal story choices; runtime portable saves, not native story-file import. Media/time simulated; native-derived surface rules do not prove original EXE full-screen equivalence.'}, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print('TY_WORKFLOWS', out, flush=True)
    raise SystemExit(any(row['exit_code'] for row in checks.values()))


if __name__ == '__main__':
    main()
