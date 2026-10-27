"""Create an isolated AVD and test only the bundled original demo. Never launch game EXEs."""
import argparse,hashlib,json,os,re,subprocess,sys,time,xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    cli=argparse.ArgumentParser(description=__doc__);cli.add_argument('--sdk',type=Path,default=Path.home()/'.cache/ethornell-android/sdk');cli.add_argument('--port',type=int,default=5584);cli.add_argument('--keep',action='store_true');args=cli.parse_args()
    out=ROOT/'build/android-smoke';out.mkdir(parents=True,exist_ok=True);avds=out/'avd';avds.mkdir(exist_ok=True);avd=avds/'circus_core_api35.avd';avd.mkdir(exist_ok=True)
    image=args.sdk/'system-images/android-35/default/x86_64';adb=args.sdk/'platform-tools/adb.exe';emulator=args.sdk/'emulator/emulator.exe';serial=f'emulator-{args.port}'
    env=os.environ.copy();env['ANDROID_AVD_HOME']=str(avds);env['ANDROID_EMULATOR_HOME']=str(out/'emulator-home');env['ANDROID_USER_HOME']=str(out/'user-home');env['ANDROID_SDK_ROOT']=str(args.sdk)
    for name in ['ANDROID_EMULATOR_HOME','ANDROID_USER_HOME']:Path(env[name]).mkdir(exist_ok=True)
    def command(*parts,check=True,binary=False,timeout=30):
        r=subprocess.run([str(adb),'-s',serial,*parts],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
        if check and r.returncode:raise RuntimeError(r.stderr.decode('utf-8',errors='replace')+r.stdout.decode('utf-8',errors='replace'))
        return r.stdout if binary else r.stdout.decode('utf-8',errors='replace').strip()
    devices=subprocess.run([str(adb),'devices'],stdout=subprocess.PIPE,env=env,timeout=15).stdout.decode()
    if serial in devices:raise SystemExit(f'{serial} already exists; refusing to replace another emulator')
    (avds/'circus_core_api35.ini').write_text(f'avd.ini.encoding=UTF-8\npath={avd}\ntarget=android-35\n',encoding='utf-8')
    config={'AvdId':'circus_core_api35','abi.type':'x86_64','avd.ini.encoding':'UTF-8','avd.ini.displayname':'CIRCUS isolated core test','disk.dataPartition.size':'2G','fastboot.forceColdBoot':'yes','hw.accelerometer':'no','hw.audioInput':'no','hw.battery':'yes','hw.cpu.arch':'x86_64','hw.cpu.ncore':'2','hw.gps':'no','hw.gpu.enabled':'yes','hw.gpu.mode':'swiftshader','hw.keyboard':'yes','hw.lcd.density':'160','hw.lcd.height':'720','hw.lcd.width':'1280','hw.mainKeys':'no','hw.ramSize':'2048','hw.sdCard':'no','image.sysdir.1':image.as_posix()+'/','showDeviceFrame':'no','tag.display':'Default','tag.id':'default','vm.heapSize':'256'}
    (avd/'config.ini').write_text(''.join(f'{k}={v}\n' for k,v in config.items()),encoding='utf-8')
    log=(out/'emulator.log').open('wb');proc=subprocess.Popen([str(emulator),'-avd','circus_core_api35','-port',str(args.port),'-no-window','-no-audio','-no-snapshot','-no-boot-anim','-gpu','swiftshader','-no-metrics'],env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    print('Started isolated emulator',proc.pid,serial,flush=True)
    try:
        boot=False
        for i in range(180):
            if proc.poll() is not None:raise RuntimeError('Emulator exited; inspect emulator.log')
            if command('shell','getprop','sys.boot_completed',check=False,timeout=10)=='1':boot=True;break
            if i%15==0:print('Waiting for Android boot',i,flush=True)
            time.sleep(1)
        if not boot:raise RuntimeError('Android boot timed out')
        print(command('install','-r',str(ROOT/'build/android/circus-core-debug.apk'),timeout=60),flush=True)
        tests=command('shell','am','instrument','-w','org.circus.runtime/.CoreInstrumentation',timeout=60);(out/'instrumentation.txt').write_text(tests,encoding='utf-8');print(tests,flush=True)
        if 'PASS ' not in tests or 'FAIL ' in tests:raise RuntimeError('On-device core tests failed')
        command('logcat','-c');command('shell','am','start','-W','-n','org.circus.runtime/.MainActivity');time.sleep(2)
        def screenshot(name):
            (out/name).write_bytes(command('exec-out','screencap','-p',binary=True))
        screenshot('01-intro.png')
        command('shell','input','tap','640','440');time.sleep(1);screenshot('02-choices.png')
        command('shell','input','tap','850','335');time.sleep(1);screenshot('03-route.png')
        def button(label):
            command('shell','uiautomator','dump','/sdcard/circus-window.xml');xml=command('shell','cat','/sdcard/circus-window.xml');(out/'window.xml').write_text(xml,encoding='utf-8')
            matches=[n for n in ET.fromstring(xml).iter('node') if n.get('text')==label]
            if len(matches)!=1:raise RuntimeError('Expected one visible button: '+label)
            x1,y1,x2,y2=map(int,re.findall(r'\d+',matches[0].get('bounds')))
            command('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.3)
        button('保存');saved=command('exec-out','run-as','org.circus.runtime','cat','files/slot1.cvs',binary=True);(out/'demo-slot1.cvs').write_bytes(saved)
        button('重新开始');time.sleep(1);screenshot('04-restarted.png')
        button('读取');time.sleep(1);screenshot('05-restored.png')
        command('shell','input','keyevent','3');time.sleep(.3);command('shell','am','start','-W','-n','org.circus.runtime/.MainActivity');time.sleep(1);screenshot('06-resumed.png')
        command('shell','am','force-stop','org.circus.runtime');command('shell','am','start','-W','-n','org.circus.runtime/.MainActivity');time.sleep(.5);button('读取');time.sleep(1);screenshot('07-process-restart-load.png')
        button('保存');again=command('exec-out','run-as','org.circus.runtime','cat','files/slot1.cvs',binary=True)
        if saved!=again:raise RuntimeError('Saved state differs after process death and reload')
        button('正文测试');time.sleep(.5);screenshot('08-native-dialogue.png')
        def require_text(fragment):
            command('shell','uiautomator','dump','/sdcard/circus-window.xml');xml=command('shell','cat','/sdcard/circus-window.xml')
            if fragment not in xml:raise RuntimeError('Native dialogue display missing: '+fragment)
        require_text('Page 1.');button('保存正文')
        native_saved=command('exec-out','run-as','org.circus.runtime','cat','files/dialogue.cvs',binary=True)
        button('继续');time.sleep(.5);require_text('Page 3.');screenshot('09-native-implicit-boundary.png')
        button('读取正文');time.sleep(.5);require_text('Page 1.');button('保存正文')
        native_loaded=command('exec-out','run-as','org.circus.runtime','cat','files/dialogue.cvs',binary=True)
        if native_saved!=native_loaded:raise RuntimeError('Native dialogue state changed after load')
        command('shell','am','force-stop','org.circus.runtime');command('shell','am','start','-W','-n','org.circus.runtime/.MainActivity');time.sleep(.5)
        button('正文测试');button('读取正文');time.sleep(.5);require_text('Page 1.');screenshot('10-native-restored.png')
        logcat=command('logcat','-d','-t','500');(out/'logcat.txt').write_text(logcat,encoding='utf-8')
        if 'FATAL EXCEPTION' in logcat:raise RuntimeError('App crash in logcat')
        report={'serial':serial,'emulator_pid':proc.pid,'apk_sha256':hashlib.sha256((ROOT/'build/android/circus-core-debug.apk').read_bytes()).hexdigest(),'instrumentation_passed':True,'save_restore_byte_exact':True,'saved_bytes':len(saved),'native_dialogue_save_byte_exact':True,'native_dialogue_implicit_boundary':True,'native_dialogue_process_death_restore':True,'ui_crash_detected':False,'original_game_executed':False,'scope':'Self-authored demo and native DD dialogue fixtures; screenshots require visual inspection','kept_running':args.keep}
        (out/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report),flush=True)
    finally:
        if not args.keep:
            command('emu','kill',check=False)
            try:proc.wait(timeout=15)
            except subprocess.TimeoutExpired:proc.terminate()
        log.close()

if __name__=='__main__':main()
