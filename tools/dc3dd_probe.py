"""Prepare a physical DC3DD copy; launching requires separate explicit authorization."""
import argparse,hashlib,json,shutil,sys,time,threading,ctypes
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
SOURCE=Path(r'D:\gal\DC3DD')
DEST=ROOT/'build/dynamic-probe/DC3DD'
SHA='ff119f4396cc1382e349fb1ed2c2eb77191db8ec699cb1352ca021cc73407e8d'
ALTERNATE_SHA='3e9544f6dab92c871f6ea2ae414b39234ef961dc917b6256c613d8c253b2fee9'

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for b in iter(lambda:stream.read(1024*1024),b''):h.update(b)
    return h.hexdigest()

def prepare():
    if digest(SOURCE/'DC3DD.EXE')!=SHA:raise SystemExit('Native evidence hash mismatch')
    DEST.mkdir(parents=True,exist_ok=True)
    paths=[p for p in SOURCE.iterdir() if p.is_file() and p.name in {'DC3DD.EXE','config.ini','winmm.dll','system.dat','uif_config.json','MSGothic_WenQuanYi_cnjp.ttf'}]
    paths.extend(p for p in (SOURCE/'AdvData').rglob('*') if p.is_file() and not any(part.lower() in {'savedata','backup','backups'} for part in p.relative_to(SOURCE).parts))
    manifest=[]
    for i,path in enumerate(paths):
        if path.is_symlink() or not path.resolve().is_relative_to(SOURCE.resolve()):raise SystemExit('Linked source outside game root refused')
        relative=path.relative_to(SOURCE);target=DEST/relative;target.parent.mkdir(parents=True,exist_ok=True);source_hash=digest(path)
        if not target.exists():shutil.copyfile(path,target)
        if digest(target)!=source_hash:raise SystemExit(f'Existing probe copy differs; do not overwrite: {relative}')
        manifest.append({'path':relative.as_posix(),'sha256':source_hash,'bytes':path.stat().st_size})
        if i%200==0:print('Prepared',i,'files',flush=True)
    plan={'source':str(SOURCE),'isolated_copy':str(DEST),'files':manifest,'original_save_copied':False,'launched':False,'capture':'MES dispatch PC/opcode/arguments, ordinary variable changes, UI registration and script/label transfers','time_limit_seconds':180,'record_limit':10000,'purpose':'Confirm title/menu scheduling and initial script flow; no full game execution claims'}
    (DEST.parent/'prepared.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8');print('Prepared only. No game launched.',flush=True)

def launch(seconds,launcher):
    # This path must not be called by the agent until the human grants permission.
    if not (DEST.parent/'prepared.json').is_file() or digest(DEST/'DC3DD.EXE')!=SHA:raise SystemExit('Prepare and verify the isolated copy first')
    import frida
    plan=json.loads((DEST.parent/'prepared.json').read_text(encoding='utf-8'))
    for row in plan['files']:
        if digest(DEST/row['path'])!=row['sha256']:raise SystemExit('Probe copy has changed; prepare a fresh reviewed copy before launch')
    executable='DC3DD.EXE'
    if launcher=='alternate':
        # User identified this existing executable as the normal working launcher.
        # Copy it verbatim, keep the original EXE, and verify every hook's nearby code.
        import pefile
        executable='DC3DD_crack.EXE';source=SOURCE/executable;target=DEST/executable
        if digest(source)!=ALTERNATE_SHA:raise SystemExit('Alternate launcher hash mismatch')
        original=(SOURCE/'DC3DD.EXE').read_bytes();alternate=source.read_bytes();pe=pefile.PE(data=original)
        ranges=[(0x42aba0,0x42ae60),(0x438ab0,0x438df0),(0x417530,0x417640),(0x4277c0,0x427850),(0x420c00,0x420c80)]
        for start,end in ranges:
            offset=pe.get_offset_from_rva(start-0x400000)
            if original[offset:offset+end-start]!=alternate[offset:offset+end-start]:raise SystemExit('Alternate native probe range differs')
        if not target.exists():shutil.copyfile(source,target)
        if digest(target)!=ALTERNATE_SHA:raise SystemExit('Alternate probe copy differs')
        plan['files'].append({'path':executable,'sha256':ALTERNATE_SHA,'bytes':len(alternate)})
        (DEST.parent/'alternate-probe-evidence.json').write_text(json.dumps({'sha256':ALTERNATE_SHA,'hook_ranges_identical':ranges,'user_selected_launcher':executable},indent=2),encoding='utf-8')
    # The native first-run prompt asks to create this exact directory (VA 0x4b7300).
    # Creating an empty workspace directory avoids a modal without touching old saves.
    (DEST/'AdvData/savedata').mkdir(exist_ok=True)
    device=frida.get_local_device();pid=device.spawn([str(DEST/executable)],cwd=str(DEST));session=None
    output=DEST.parent/('trace-'+time.strftime('%Y%m%d-%H%M%S')+'.jsonl')
    def stop_owned_process():
        # Frida's kill RPC can deadlock with a stopping hook. The watchdog must
        # remain independent of that RPC and only target the process we spawned.
        kernel=ctypes.WinDLL('kernel32',use_last_error=True)
        kernel.OpenProcess.restype=ctypes.c_void_p
        kernel.TerminateProcess.argtypes=[ctypes.c_void_p,ctypes.c_uint]
        kernel.CloseHandle.argtypes=[ctypes.c_void_p]
        handle=kernel.OpenProcess(0x0001,False,pid)
        if handle:
            try:kernel.TerminateProcess(handle,0)
            finally:kernel.CloseHandle(handle)
    watchdog=threading.Timer(seconds,stop_owned_process);watchdog.daemon=True
    try:
        watchdog.start()
        session=device.attach(pid);script=session.create_script((ROOT/'tools/dc3dd_trace.js').read_text(encoding='utf-8'))
        with output.open('x',encoding='utf-8') as stream:
            def on_message(message,data):stream.write(json.dumps(message,ensure_ascii=True)+'\n');stream.flush()
            def detached(reason,crash):
                if not stream.closed:on_message({'type':'session_detached','reason':reason,'crash':str(crash) if crash else None},None)
            session.on('detached',detached)
            script.on('message',on_message);script.load();device.resume(pid)
            print('Isolated DC3DD PID',pid,'started for',seconds,'seconds. Trace:',output,flush=True)
            for _ in range(seconds):
                if session.is_detached:break
                time.sleep(1)
    finally:
        watchdog.cancel();stop_owned_process()
        if session and not session.is_detached:session.detach()
        changed=[row['path'] for row in plan['files'] if digest(SOURCE/row['path'])!=row['sha256']]
        (DEST.parent/'post-session-source-integrity.json').write_text(json.dumps({'changed_source_files':changed,'originals_unchanged':not changed},indent=2),encoding='utf-8')

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace');parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare',action='store_true');parser.add_argument('--allow-launch',action='store_true',help='Only after explicit user permission to launch the isolated game')
    parser.add_argument('--seconds',type=int,default=180,choices=range(1,601),metavar='1..600')
    parser.add_argument('--launcher',choices=['original','alternate'],default='original')
    args=parser.parse_args()
    if args.prepare:prepare()
    if args.allow_launch:launch(args.seconds,args.launcher)
    if not args.prepare and not args.allow_launch:parser.print_help()

if __name__=='__main__':main()
