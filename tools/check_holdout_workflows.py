"""Check SC/FL against read-only installations, with explicit original menu actions."""
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
    parser.add_argument('--spring', type=Path, required=True)
    parser.add_argument('--fall', type=Path, required=True)
    parser.add_argument('--spring-exe', default='dc2sc_CHS.exe')
    parser.add_argument('--fall-exe', default='DC2FL.exe')
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--classes', type=Path, help='Use already frozen full desktop test classes')
    parser.add_argument('--player-jar', type=Path, help='Place an actual packaged player first on the test classpath')
    parser.add_argument('--out', type=Path)
    parser.add_argument('--build-root',type=Path,help='Generated output root; defaults to CIRCUS_BUILD_ROOT or workspace build')
    parser.add_argument('--full-routes', action='store_true', help='Also run three SC story groups and all 15 FL stories from fresh defaults with first legal choices')
    args = parser.parse_args()
    games = [args.spring.resolve(strict=True), args.fall.resolve(strict=True)]
    generated_build=build_root(args.build_root)
    env=build_environment(generated_build)
    out = (args.out or generated_build/'holdout-workflows'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')).resolve()
    if any(out == game or game in out.parents for game in games):
        parser.error('Output must be outside both original game directories')
    if out.exists():
        parser.error('Use a new output directory to preserve earlier evidence')
    out.mkdir(parents=True)
    if args.classes:
        classes = args.classes.resolve(strict=True)
        java = str(args.java_home/'bin/java.exe')
    else:
        java, classes = build(args.java_home, with_tests=True, build_dir=generated_build)
    classpath = str(classes)
    if args.player_jar:
        classpath = str(args.player_jar.resolve(strict=True))+';'+classpath
    plan = []
    sc_actions = ['03', 'start', 'after_story', 'after_otm', 'read_after']
    fl_actions = ['02', 'start', 'sce01', 'sce01_set', 'sce01']
    for name, game, exe, actions in [('sc', games[0], args.spring_exe, sc_actions), ('fl', games[1], args.fall_exe, fl_actions)]:
        plan.append((name+'-startup', 'NativeStartupAudit', game, exe, actions))
        plan.append((name+'-save-load', name.capitalize()+'DesktopHoldoutAudit', game, exe, []))
    plan.append(('fl-credits', 'FlCreditsAudit', games[1], args.fall_exe, []))
    plan.append(('fl-composition', 'FlCompositionAudit', games[1], args.fall_exe, []))
    if args.full_routes:
        for group, selection, read in [('after_story', 'after_otm', 'read_after'), ('geki_story', 'geki_otm', 'read_geki'), ('ultimate_story', 'ultimate_haru', 'read_ultimate')]:
            plan.append(('sc-route-'+group, 'DesktopRouteAudit', games[0], args.spring_exe, ['03', 'start', group, selection, read]))
        for group, count in [('sce01', 6), ('sce02', 4), ('sce03', 5)]:
            for selection in range(1, count+1):
                label = f'sce{selection:02d}'
                plan.append((f'fl-route-{group}-{selection}', 'DesktopRouteAudit', games[1], args.fall_exe, ['02', 'start', group, label+'_set', label]))
    def run(item):
        name, audit, game, exe, actions = item
        dest = out/name
        dest.mkdir()
        command = [str(java), '-Xmx192m', '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8', '-Ddc.saveRoot='+str(dest/'saves'), '-Ddc.cacheRoot='+str(dest/'cache'), '-cp', classpath, 'org.circus.core.'+audit, str(game)]
        if audit == 'NativeStartupAudit':
            command += ['--exe', exe, '--lines', '20', '--steps', '1000000']
        elif audit == 'DesktopRouteAudit':
            command += [str(dest), 'first', '--exe', exe]
        else:
            command += [str(dest)]
        for action in actions:
            command += ['--action', action]
        with (dest/'stdout.log').open('wb') as stdout, (dest/'stderr.log').open('wb') as stderr:
            result = subprocess.run(command, cwd=ROOT, stdout=stdout, stderr=stderr, timeout=1800,env=env)
        record = {'exit_code': result.returncode, 'command': command}
        report = dest/'result.json'
        if not report.exists():
            report = dest/'report.json'
        if report.exists():
            raw = report.read_bytes()
            record.update(result=json.loads(raw), result_sha256=hashlib.sha256(raw).hexdigest())
        elif audit == 'NativeStartupAudit':
            record['result'] = json.loads((dest/'stdout.log').read_text(encoding='utf8'))
        print(name, result.returncode, flush=True)
        return name, record
    with ThreadPoolExecutor(max_workers=3) as pool:
        checks = dict(pool.map(run, plan))
    report = {'checks': checks, 'heap_mib': 192, 'original_executables_launched': False, 'original_game_directories_written': False, 'scope': 'Original menus and first legal choices; time and media completion simulated. No full native audio/video timing equivalence claim.'}
    (out/'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print('HOLDOUT_WORKFLOWS', out, flush=True)
    raise SystemExit(any(check['exit_code'] for check in checks.values()))


if __name__ == '__main__':
    main()
