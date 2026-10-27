"""Decode user's original DM movies in our Android codec, on an explicitly selected test emulator."""
import argparse, subprocess
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb',type=Path,required=True);p.add_argument('--serial',required=True)
    p.add_argument('--game',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args()
    if not a.serial.startswith('emulator-'):p.error('Use a dedicated test emulator')
    a.out.mkdir(parents=True,exist_ok=True)
    def adb(*args,timeout=180):
        r=subprocess.run([str(a.adb),'-s',a.serial,*args],capture_output=True,timeout=timeout)
        if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
        return r.stdout
    for n in range(1,4):
        name=f'DC2DM_OP{n:02}.mpg';temp='/data/local/tmp/circus-audit-'+name;private='files/'+name
        try:
            adb('push',str(a.game/'AdvData/MOVIE'/name),temp)
            adb('shell','run-as','org.circus.runtime','cp',temp,private)
            adb('shell','rm',temp)
            result=adb('shell','am','instrument','-w','-e','movie_file',name,'org.circus.runtime/.MovieInstrumentation').decode(errors='replace')
            (a.out/(name+'.txt')).write_text(result,encoding='utf8');print(result,flush=True)
            if 'PASS MPEG' not in result:raise RuntimeError('Movie decoding failed')
        finally:
            adb('shell','run-as','org.circus.runtime','rm','-f',private)
            adb('shell','rm','-f',temp)

if __name__=='__main__':main()
