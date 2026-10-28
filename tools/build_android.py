"""Offline SDK build of the shared Java core and Android host; never runs game data."""
import argparse, hashlib, json, os, shutil, subprocess, sys, zipfile
from pathlib import Path
from generate_runtime_profiles import generate as profiles
from generate_demo import generate as demo
from generate_dc3_state import generate as state_rules
from generate_android_backend import generate as android_backend

ROOT=Path(__file__).resolve().parent.parent

def run(args,env):
    print('RUN',str(args[0]),flush=True)
    result=subprocess.run([str(a) for a in args],cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    text=result.stdout.decode('utf-8',errors='replace');print(text,flush=True)
    if result.returncode:raise RuntimeError(f'Command failed ({result.returncode}): {args[0]}')
    return text

def main():
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument('--sdk',type=Path,default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or Path.home()/'.cache/ethornell-android/sdk')
    cli.add_argument('--java-home',type=Path,default=os.environ.get('JAVA_HOME'))
    cli.add_argument('--build-tools',default='36.0.0');cli.add_argument('--platform',default='android-36')
    args=cli.parse_args();sdk=args.sdk.resolve();bt=sdk/'build-tools'/args.build_tools;android=sdk/'platforms'/args.platform/'android.jar'
    java=args.java_home/'bin' if args.java_home else Path(shutil.which('java')).parent
    for path in [bt/'aapt.exe',bt/'lib/d8.jar',bt/'lib/apksigner.jar',android,java/'javac.exe']:
        if not path.is_file():raise SystemExit(f'Missing build input: {path}')
    out=ROOT/'build/android';out.mkdir(parents=True,exist_ok=True)
    # javac's ZIP filesystem may canonicalize the read-only SDK path on close.
    # Compile against an identical workspace copy, leaving the installed SDK untouched.
    local_platform=out/'android-platform.jar'
    if not local_platform.exists() or hashlib.sha256(local_platform.read_bytes()).digest()!=hashlib.sha256(android.read_bytes()).digest():
        shutil.copyfile(android,local_platform)
    android=local_platform
    # All mutable build/signing state belongs to this workspace.
    env=os.environ.copy();env['JAVA_HOME']=str(java.parent)
    profiles();state_rules();demo();shared=android_backend()
    legal=ROOT/'runtime/android/assets/legal';legal.mkdir(exist_ok=True)
    for name in ['LICENSE','THIRD_PARTY.md']:shutil.copyfile(ROOT/name,legal/name)
    core=list((ROOT/'runtime/core/src').rglob('*.java'));tests=list((ROOT/'runtime/tests').rglob('*.java'));app=list((ROOT/'runtime/android/src').rglob('*.java'))
    resources=sorted(p for p in (ROOT/'runtime/core/resources').rglob('*') if p.is_file())
    classes=out/'classes';classes.mkdir(exist_ok=True);jvm=out/'jvm';jvm.mkdir(exist_ok=True)
    # javac output dirs are unique to this project. Remove only previously generated class files.
    for directory in [classes,jvm]:
        for path in directory.rglob('*.class'):path.unlink()
    run([java/'javac.exe','-encoding','UTF-8','--release','8','-d',jvm,*core,*tests],env)
    for resource in resources:
        target=jvm/resource.relative_to(ROOT/'runtime/core/resources');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(resource,target)
    test_log=run([java/'java.exe','-cp',jvm,'org.circus.core.CoreTests',ROOT/'runtime/android/assets/demo'],env)
    run([java/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-classpath',android,'-d',classes,*core,*tests,*app,*shared],env)
    jar=out/'classes.jar'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        for path in sorted(classes.rglob('*.class')):z.write(path,path.relative_to(classes).as_posix())
    dex=out/'dex';dex.mkdir(exist_ok=True)
    for path in dex.glob('classes*.dex'):path.unlink()
    run([java/'java.exe','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','26','--output',dex,jar],env)
    ndk=sdk/'ndk/29.0.14206865/toolchains/llvm/prebuilt/windows-x86_64'
    native=out/'native';native.mkdir(exist_ok=True)
    for abi,target in [('arm64-v8a','aarch64-linux-android26'),('armeabi-v7a','armv7a-linux-androideabi26'),('x86_64','x86_64-linux-android26')]:
        dest=native/abi;dest.mkdir(exist_ok=True)
        run([ndk/'bin/clang.exe','--target='+target,'--sysroot='+str(ndk/'sysroot'),'-shared','-fPIC','-O2','-fstack-protector-strong','-D_FORTIFY_SOURCE=2','-Wl,-z,relro,-z,now,-z,max-page-size=16384',ROOT/'runtime/native/mpeg_jni.c','-lm','-o',dest/'libcircus_mpeg.so'],env)
    unsigned=out/'unsigned.apk';aligned=out/'aligned.apk';apk=out/'circus-core-debug.apk'
    run([bt/'aapt.exe','package','-f','-M',ROOT/'runtime/android/AndroidManifest.xml','-S',ROOT/'runtime/android/res','-I',android,'-A',ROOT/'runtime/android/assets','-F',unsigned],env)
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
        for path in dex.glob('classes*.dex'):z.write(path,path.name)
        for path in native.glob('*/*.so'):z.write(path,'lib/'+path.relative_to(native).as_posix())
        for path in resources:z.write(path,path.relative_to(ROOT/'runtime/core/resources').as_posix())
    run([bt/'zipalign.exe','-f','4',unsigned,aligned],env)
    key=out/'debug.keystore'
    if not key.exists():run([java/'keytool.exe','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=CIRCUS Core Development,O=Local,C=CN','-noprompt'],env)
    signer=[java/'java.exe','-jar',bt/'lib/apksigner.jar']
    run([*signer,'sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned],env)
    signed=run([*signer,'verify','--verbose',apk],env)
    badging=run([bt/'aapt.exe','dump','badging',apk],env)
    native_sources=list((ROOT/'runtime/native').rglob('*'));inputs=core+tests+app+[ROOT/'runtime/desktop/src/org/circus/core'/p.name for p in shared]+[p for p in (ROOT/'runtime/android/res').rglob('*') if p.is_file()]+[p for p in native_sources if p.is_file()]+[ROOT/'LICENSE',ROOT/'THIRD_PARTY.md',ROOT/'runtime/android/AndroidManifest.xml',ROOT/'tools/generate_android_backend.py',Path(__file__)]
    report={'apk':str(apk),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'jvm_tests':test_log.strip(),'signature_verified':True,'min_sdk':26,'target_sdk':35,'original_game_assets_included':False,'user_supplied_launcher_icon':hashlib.sha256((ROOT/'runtime/android/res/drawable-nodpi/launcher_logo.png').read_bytes()).hexdigest(),'original_game_executed':False,'sdk':str(sdk),'java_home':str(java.parent),'sources':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs+resources}}
    (out/'build-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (out/'apk-verification.txt').write_text(signed+'\n'+badging,encoding='utf-8');print(json.dumps({k:v for k,v in report.items() if k!='sources'},ensure_ascii=True))

if __name__=='__main__':main()
