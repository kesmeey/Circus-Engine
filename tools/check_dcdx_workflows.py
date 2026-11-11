"""Check DCDX startup, portable saves and independent raster contracts read-only."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess

from build_desktop import ROOT, build, build_environment, build_root


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('game', type=Path)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--classes', type=Path, help='Already frozen full test classes')
    parser.add_argument('--player-jar', type=Path, help='Actual player first on classpath')
    parser.add_argument('--out', type=Path)
    parser.add_argument('--build-root',type=Path,help='Generated output root; defaults to CIRCUS_BUILD_ROOT or workspace build')
    args = parser.parse_args()
    game = args.game.resolve(strict=True)
    generated_build=build_root(args.build_root)
    env=build_environment(generated_build)
    out = (args.out or generated_build/'dcdx-workflows'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')).resolve()
    if out == game or game in out.parents or out.exists():
        parser.error('Use a new output directory outside the original game')
    out.mkdir(parents=True)
    if args.classes:
        java, classes = str(args.java_home/'bin/java.exe'), args.classes.resolve(strict=True)
    else:
        java, classes = build(args.java_home, with_tests=True, build_dir=generated_build)
    classpath = str(classes)
    if args.player_jar:
        classpath = str(args.player_jar.resolve(strict=True))+';'+classpath
    plan = [('startup-'+branch, 'NativeStartupAudit', branch) for branch in ['dc', 'dc2']]
    plan += [('save-load-'+branch, 'DcdxDesktopHoldoutAudit', branch) for branch in ['dc', 'dc2']]
    plan += [(name, audit, '') for name, audit in [
        ('actors', 'DcdxActorRasterAudit'),
        ('menu', 'DcdxMenuRasterAudit'), ('banners', 'DcdxBannerRasterAudit')]]

    def run(item):
        name, audit, branch = item
        dest = out/name
        dest.mkdir()
        command = [str(java), '-Xmx192m', '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8',
                   '-Ddc.saveRoot='+str(dest/'saves'), '-Ddc.cacheRoot='+str(dest/'cache'),
                   '-cp', classpath, 'org.circus.core.'+audit, str(game)]
        if audit == 'NativeStartupAudit':
            command += ['--exe', 'DCDX.EXE', '--lines', '20', '--steps', '1000000']
            for action in ['window', 'story', 'ystory', 'sce', branch, 'y'+branch]:
                command += ['--action', action]
        else:
            command += [str(dest)]
            if branch:
                command += [branch]
        with (dest/'stdout.log').open('wb') as stdout, (dest/'stderr.log').open('wb') as stderr:
            result = subprocess.run(command, cwd=ROOT, stdout=stdout, stderr=stderr, timeout=900,env=env)
        record = {'exit_code': result.returncode, 'command': command}
        report = next((dest/p for p in ['result.json', 'report.json'] if (dest/p).exists()), None)
        if report is None and audit == 'NativeStartupAudit':
            report = dest/'stdout.log'
        if report is not None:
            raw = report.read_bytes()
            record.update(result=json.loads(raw), result_sha256=hashlib.sha256(raw).hexdigest())
        print(name, result.returncode, flush=True)
        return name, record

    with ThreadPoolExecutor(max_workers=3) as pool:
        checks = dict(pool.map(run, plan))
    report = {'checks': checks, 'heap_mib': 192, 'original_executables_launched': False,
              'original_game_directories_written': False,
              'scope': 'Two natural story starts, runtime portable saves through original menus, and independent integer raster oracles. Media/time simulated; no complete-route or original EXE screen equivalence claim.'}
    (out/'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print('DCDX_WORKFLOWS', out, flush=True)
    raise SystemExit(any(check['exit_code'] for check in checks.values()))


if __name__ == '__main__':
    main()
