"""Prepare a physical, hash-checked DM copy; launch only with human authorization."""
import argparse, ctypes, hashlib, json, shutil, threading, time
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
SOURCE=Path(r'D:\gal\DC2DM')
DEST=ROOT/'build/dynamic-probe/DC2DM'
REPORT=ROOT/'build/dynamic-probe/dc2dm-evidence'
HASHES={'DC2DM.EXE':'7414de26db3e9ce4fec331df7cb517947af7957fbc85abe96c51d6968a9ee0ee',
        'DC2DM_chs_c3.7s.EXE':'77ab7c010369e7ea6dbc466e8a3b9a1f8a7cc2fdab1ddf5154301a1708d912c4'}

def digest(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    return h.hexdigest()

def prepare():
    REPORT.mkdir(parents=True,exist_ok=True)
    for name,h in HASHES.items():
        if digest(SOURCE/name)!=h: raise ValueError('Unreviewed executable '+name)
    paths=[p for p in SOURCE.iterdir() if p.name in {*HASHES,'winmm.dll','uif_config.json','MSGothic_WenQuanYi_cnjp.ttf'}]
    paths += [p for p in (SOURCE/'AdvData').rglob('*') if p.is_file() and not any(x.lower() in ('savedata','backup','backups') for x in p.relative_to(SOURCE).parts)]
    rows=[]
    for p in paths:
        if p.is_symlink() or not p.resolve().is_relative_to(SOURCE.resolve()): raise ValueError('Linked input')
        rel=p.relative_to(SOURCE);dst=DEST/rel;dst.parent.mkdir(parents=True,exist_ok=True)
        h=digest(p)
        if not dst.exists(): shutil.copyfile(p,dst)
        if digest(dst)!=h: raise ValueError('Existing copy differs: '+str(rel))
        rows.append({'path':rel.as_posix(),'sha256':h,'bytes':p.stat().st_size})
    (REPORT/'prepared.json').write_text(json.dumps({'source':str(SOURCE),'copy':str(DEST),'files':rows,'launched':False,'max_seconds':600,'max_events':10000},indent=2),encoding='utf-8')
    print('Prepared only:',len(rows),'files;',sum(r['bytes'] for r in rows),'bytes. No game launched.',flush=True)

def launch(executable,seconds):
    import frida
    rows=json.loads((REPORT/'prepared.json').read_text())['files']
    for r in rows:
        if digest(DEST/r['path'])!=r['sha256']: raise ValueError('Probe copy changed: '+r['path'])
    (DEST/'AdvData/savedata').mkdir(exist_ok=True)
    device=frida.get_local_device();pid=device.spawn([str(DEST/executable)],cwd=str(DEST))
    def stop():
        k=ctypes.WinDLL('kernel32',use_last_error=True);k.OpenProcess.restype=ctypes.c_void_p
        k.TerminateProcess.argtypes=[ctypes.c_void_p,ctypes.c_uint];k.CloseHandle.argtypes=[ctypes.c_void_p]
        h=k.OpenProcess(1,False,pid)
        if h:
            try:k.TerminateProcess(h,0)
            finally:k.CloseHandle(h)
    timer=threading.Timer(seconds,stop);timer.daemon=True;timer.start();session=None
    try:
        session=device.attach(pid);script=session.create_script((ROOT/'tools/dc2dm_trace.js').read_text())
        out=REPORT/('trace-'+time.strftime('%Y%m%d-%H%M%S')+'.jsonl')
        with out.open('x',encoding='utf-8') as f:
            script.on('message',lambda message,data:(f.write(json.dumps(message)+'\n'),f.flush()))
            script.load();device.resume(pid);print('Owned DM PID',pid,'for',seconds,'seconds;',out,flush=True)
            for _ in range(seconds):
                if session.is_detached:break
                time.sleep(1)
    finally:
        timer.cancel();stop()
        if session and not session.is_detached:session.detach()
        changed=[r['path'] for r in rows if digest(SOURCE/r['path'])!=r['sha256']]
        (REPORT/'source-integrity.json').write_text(json.dumps({'changed':changed,'originals_unchanged':not changed}),encoding='utf-8')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--prepare',action='store_true');p.add_argument('--allow-launch',action='store_true')
    p.add_argument('--launcher',choices=list(HASHES),default='DC2DM_chs_c3.7s.EXE');p.add_argument('--seconds',type=int,choices=range(1,601),default=600)
    a=p.parse_args()
    if a.prepare:prepare()
    if a.allow_launch:launch(a.launcher,a.seconds)
