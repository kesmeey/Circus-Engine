"""Test the installed APK against already granted, private emulator fixtures.

Launches only our interpreter, never a Windows game executable. Fixtures must
already be copied and selected through the Android directory picker.
"""
import argparse, hashlib, json, re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GAMES = {
    'DD': ('Download%2FDC3DD', 'dc3dd-a5a8'),
    'DM': ('Documents%2FDC2DM', 'dc2dm-9d72'),
    'PC1': ('Documents%2FDCPC', 'dcpc-2063'),
    'PC2': ('Documents%2FDC2PC', 'dc2pc-5769'),
}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', default='emulator-5584')
    p.add_argument('--games', nargs='+', choices=GAMES, default=list(GAMES))
    p.add_argument('--no-core', action='store_true')
    p.add_argument('--upgrade', action='store_true', help='Use normal app saves, not the audit namespace')
    p.add_argument('--out', type=Path, default=ROOT/'build/android/verification-011')
    a = p.parse_args()
    adb = [str(Path.home()/'.cache/ethornell-android/sdk/platform-tools/adb.exe'), '-s', a.serial]
    a.out.mkdir(parents=True, exist_ok=True)

    def run(*cmd, timeout=300):
        return subprocess.check_output([*adb, *cmd], timeout=timeout)

    results = {}
    if not a.no_core:
        log = run('shell', 'am', 'instrument', '-w', 'org.circus.runtime/.CoreInstrumentation').decode('utf-8')
        (a.out/'art-core.txt').write_text(log, encoding='utf-8')
        results['core'] = 'PASS 325 assertions' in log and '7 pixel contracts PASS' in log and 'FAIL' not in log
        print('CORE', results['core'], flush=True)
    for game in a.games:
        tree, profile = GAMES[game]
        name = profile + ('-upgrade' if a.upgrade else '')
        directory = a.out/name
        directory.mkdir(exist_ok=True)
        log = run('shell', 'am', 'instrument', '-w', '-e', 'tree', 'content://com.android.externalstorage.documents/tree/primary%3A'+tree,
                  '-e', 'audit', 'false' if a.upgrade else 'true', 'org.circus.runtime/.SharedRuntimeInstrumentation').decode('utf-8')
        (directory/'instrumentation.txt').write_text(log, encoding='utf-8')
        ok = f'PROFILE {profile} CHECKS ' in log and 'FAIL' not in log
        results[game] = {'pass': ok, 'checks': int(re.search(r'CHECKS (\d+)', log)[1]) if ok else 0}
        remote = 'files/android-011-audit/'+name
        for filename in run('shell', 'run-as', 'org.circus.runtime', 'ls', remote).decode('utf-8').splitlines():
            if re.fullmatch(r'[\w.-]+\.(png|txt)', filename):
                (directory/filename).write_bytes(run('exec-out', 'run-as', 'org.circus.runtime', 'cat', remote+'/'+filename))
        print(game, results[game], flush=True)
        if not ok: print(log, flush=True)
    apk = ROOT/'build/android/circus-core-debug.apk'
    result = {'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'serial': a.serial,
              'android': run('shell', 'getprop', 'ro.build.version.release').decode().strip(),
              'upgrade': a.upgrade, 'results': results, 'windows_game_executed': False,
              'scope': 'Original title, 12 dialogue advances, native name input (PC1), save pixels, backlog, original save grid, real audio start. Not a full-route Android certification.'}
    (a.out/('result-'+('-'.join(a.games))+('-upgrade' if a.upgrade else '')+'.json')).write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    if not all(v if isinstance(v, bool) else v['pass'] for v in results.values()): raise SystemExit(1)


if __name__ == '__main__':
    main()
