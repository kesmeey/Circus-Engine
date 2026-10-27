"""Run original MES inside the portable core, with read-only data and bounded steps."""
import argparse,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
def main():
    sys.stdout.reconfigure(encoding='utf-8')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('game',type=Path);p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--entry',default='DD_1_0611_A1');p.add_argument('--steps',type=int,default=100000);p.add_argument('--media',action='store_true')
    p.add_argument('--last-choice',action='store_true')
    p.add_argument('--single',action='store_true');p.add_argument('--refs',action='store_true')
    a=p.parse_args();out=ROOT/'build/playback-audit/classes';out.mkdir(parents=True,exist_ok=True)
    files=list((ROOT/'runtime/core/src').rglob('*.java'))+list((ROOT/'runtime/tests').rglob('*.java'))
    subprocess.run([str(a.java_home/'bin/javac.exe'),'-encoding','UTF-8','--release','8','-d',str(out),*map(str,files)],check=True)
    command=[str(a.java_home/'bin/java.exe'),'-Dfile.encoding=UTF-8','-cp',str(out),'org.circus.core.PlaybackAudit',str(a.game),str(ROOT/'build/dialogue-audit/local-character-map.bin'),a.entry,str(a.steps)]
    if a.media:command+=['--media']
    if a.last_choice:command+=['--last-choice']
    if a.single:command+=['--single']
    if a.refs:command+=['--refs']
    r=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    result=r.stdout.decode('utf-8',errors='replace');print(result);(out.parent/'latest.txt').write_text(result,encoding='utf-8');sys.exit(r.returncode)
if __name__=='__main__':main()
