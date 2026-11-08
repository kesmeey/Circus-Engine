"""Build an immutable, workspace-owned Android mirror without rewriting live sources.
The installed SDK is read-only; the existing debug signing key is copied so an
explicit later adb install-r can retain application data. No emulator reset.
"""
import argparse,datetime,hashlib,json,os,shutil,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument('--output',type=Path,required=True)
    cli.add_argument('--sdk',type=Path,default=Path.home()/'.cache/ethornell-android/sdk')
    cli.add_argument('--java-home',type=Path,default=ROOT/'build/toolchain/jdk-21.0.9+10')
    args=cli.parse_args();out=args.output.resolve();build=(ROOT/'build').resolve()
    if out==build or not out.is_relative_to(build):raise SystemExit('Frozen Android output must be a new child of this workspace build directory')
    workspace=out/'workspace'
    if workspace.exists():raise SystemExit('Refusing to overwrite an existing frozen mirror')
    out.mkdir(parents=True,exist_ok=True);workspace.mkdir()
    selected=[]
    for name in ['runtime','profiles','circus_tools']:
        selected.extend(p for p in (ROOT/name).rglob('*') if p.is_file() and '__pycache__' not in p.parts and p.suffix!='.pyc')
    selected.extend((ROOT/'tools').glob('*.py'));selected.extend(ROOT/name for name in ['LICENSE','THIRD_PARTY.md'])
    captured={}
    for source in sorted(set(selected)):
        if not source.resolve().is_relative_to(ROOT.resolve()):raise SystemExit('Source symlink escapes workspace: '+str(source))
        rel=source.relative_to(ROOT);target=workspace/rel;target.parent.mkdir(parents=True,exist_ok=True);data=source.read_bytes();target.write_bytes(data);captured[rel.as_posix()]=hashlib.sha256(data).hexdigest()
    key=ROOT/'build/android/debug.keystore'
    if not key.is_file():raise SystemExit('Existing signing key required; no replacement key is generated')
    target_key=workspace/'build/android/debug.keystore';target_key.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(key,target_key)
    manifest={'captured_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'inputs_sha256':captured,'signing_key_sha256':sha(key),'sdk':str(args.sdk.resolve()),'java_home':str(args.java_home.resolve()),'original_game_files_copied':False}
    (out/'input-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    command=[sys.executable,'-X','utf8',str(workspace/'tools/build_android.py'),'--sdk',str(args.sdk.resolve()),'--java-home',str(args.java_home.resolve())]
    (out/'command.json').write_text(json.dumps(command,indent=2)+'\n',encoding='utf-8')
    env=os.environ.copy();temporary=workspace/'build/tmp';temporary.mkdir(parents=True,exist_ok=True);env['TEMP']=env['TMP']=str(temporary)
    print('Frozen Android inputs',len(captured),'mirror',workspace,flush=True)
    with (out/'build.log').open('wb') as log:completed=subprocess.run(command,cwd=workspace,env=env,stdout=log,stderr=subprocess.STDOUT)
    result={'exit_code':completed.returncode,'workspace_differences_from_frozen_inputs':[p for p,h in captured.items() if sha(workspace/p)!=h],'live_workspace_differences_since_capture':[p for p,h in captured.items() if sha(ROOT/p)!=h],'original_workspace_written_by_build':False}
    report=workspace/'build/android/build-report.json'
    if report.exists():
        r=json.loads(report.read_text(encoding='utf-8'));result.update(apk=r['apk'],apk_sha256=r['sha256'],apk_bytes=r['bytes'],jvm_tests=r['jvm_tests'],source_changes_during_build=r['source_changes_during_build'],signature_verified=r['signature_verified'])
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result),flush=True)
    raise SystemExit(completed.returncode)
if __name__=='__main__':main()
