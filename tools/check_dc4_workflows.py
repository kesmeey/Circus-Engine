"""Check PH's original menus, initial story, text, graphics and portable cold restore."""
import argparse
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
    parser.add_argument('--classes', type=Path)
    parser.add_argument('--player-jar', type=Path)
    parser.add_argument('--out', type=Path)
    parser.add_argument('--build-root', type=Path)
    parser.add_argument('--discover', action='store_true', help='Stop at the first unsupplied original menu and report its actual actions')
    parser.add_argument('--music-gate-fixture', action='store_true', help='Isolated synthetic music-menu availability flag only; explicitly reported, never ordinary cold validation')
    parser.add_argument('--action', action='append', default=[])
    parser.add_argument('--story-action', action='append', default=[], help='Click an actual on-screen story control after --lines body waits; repeat for an immediate sequence')
    parser.add_argument('--lines', type=int, default=20)
    parser.add_argument('--require-actor', action='store_true', help='Continue beyond --lines until a real visible cast image passes independent geometry comparison')
    parser.add_argument('--steps', type=int, default=30000)
    args = parser.parse_args()
    game = args.game.resolve(strict=True)
    generated = build_root(args.build_root)
    out = (args.out or generated/'dc4-workflows'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')).resolve()
    if out == game or game in out.parents or out.exists():
        parser.error('Use a new output directory outside the original game')
    out.mkdir(parents=True)
    env = build_environment(generated)
    if args.classes:
        java, classes = args.java_home/'bin/java.exe', args.classes.resolve(strict=True)
    else:
        java, classes = build(args.java_home, with_tests=True, build_dir=generated)
    classpath = str(classes)
    if args.player_jar:
        classpath = str(args.player_jar.resolve(strict=True))+';'+classpath
    command = [str(java), '-Xmx256m', '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8',
               '-Ddc.saveRoot='+str(out/'saves'), '-Ddc.cacheRoot='+str(out/'cache'),
               '-cp', classpath, 'org.circus.core.Dc4DesktopAudit', str(game), str(out),
               '--lines', str(args.lines), '--steps', str(args.steps)]
    if args.discover:
        command.append('--discover')
    if args.music_gate_fixture:
        command.append('--music-gate-fixture')
    if args.require_actor:
        command.append('--require-actor')
    for action in args.action:
        command += ['--action', action]
    for action in args.story_action:
        command += ['--story-action', action]
    with (out/'stdout.log').open('wb') as stdout, (out/'stderr.log').open('wb') as stderr:
        result = subprocess.run(command, cwd=ROOT, stdout=stdout, stderr=stderr, timeout=900, env=env)
    row = {'exit_code': result.returncode, 'command': command, 'classes': str(classes),
           'original_executables_executed': False, 'original_game_directories_written': False}
    report = out/'report.json'
    if report.exists():
        raw = report.read_bytes()
        row.update(result=json.loads(raw), result_sha256=hashlib.sha256(raw).hexdigest())
    (out/'result.json').write_text(json.dumps(row, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print('DC4_WORKFLOWS', out, result.returncode, flush=True)
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
