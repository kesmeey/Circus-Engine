"""Inspect a headless test emulator; optional tap of one freshly resolved text label."""
import argparse,subprocess,xml.etree.ElementTree as ET,re,sys
from pathlib import Path
def main():
    sys.stdout.reconfigure(encoding='utf-8');p=argparse.ArgumentParser(description=__doc__);p.add_argument('--tap');p.add_argument('--screenshot',type=Path);a=p.parse_args()
    adb=[str(Path.home()/'.cache/ethornell-android/sdk/platform-tools/adb.exe'),'-s','emulator-5584']
    def run(*args):return subprocess.check_output([*adb,*args],timeout=30)
    def nodes():run('shell','uiautomator','dump','/sdcard/circus-window.xml');return list(ET.fromstring(run('shell','cat','/sdcard/circus-window.xml')).iter('node'))
    n=nodes()
    if a.tap:
        matches=[x for x in n if x.get('text')==a.tap or x.get('content-desc')==a.tap]
        if len(matches)!=1:raise RuntimeError('Expected exactly one current UI element: '+a.tap)
        x,y,r,b=map(int,re.findall(r'\d+',matches[0].get('bounds')));run('shell','input','tap',str((x+r)//2),str((y+b)//2));n=nodes()
    if a.screenshot:a.screenshot.parent.mkdir(parents=True,exist_ok=True);a.screenshot.write_bytes(run('exec-out','screencap','-p'))
    print([(x.get('text'),x.get('content-desc'),x.get('bounds')) for x in n if x.get('text') or x.get('content-desc')])
if __name__=='__main__':main()
