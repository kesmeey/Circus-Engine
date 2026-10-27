"""Fetch isolated official analysis dependencies; never run or change a game binary."""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parent.parent/'build/toolchain'

def get(url):
    with urllib.request.urlopen(url,timeout=90) as r:return r.read()

def download(url,sha=None):
    path=ROOT/url.rsplit('/',1)[1]
    if not path.exists():
        temp=path.with_suffix(path.suffix+'.part')
        with urllib.request.urlopen(url,timeout=90) as r,temp.open('wb') as f:
            while b:=r.read(1024*1024):f.write(b)
        temp.rename(path)
    actual=hashlib.sha256(path.read_bytes()).hexdigest()
    if sha and actual!=sha:raise RuntimeError('Checksum mismatch: '+str(path))
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            target=(ROOT/info.filename).resolve()
            if not target.is_relative_to(ROOT.resolve()):raise RuntimeError('Unsafe ZIP path')
        z.extractall(ROOT)
    print(json.dumps({'path':str(path),'sha256':actual,'expected_sha256':sha}),flush=True)

def main():
    ROOT.mkdir(parents=True,exist_ok=True)
    node='node-v24.22.1-win-x64.zip'
    checks=get('https://nodejs.org/dist/v24.22.1/SHASUMS256.txt').decode()
    (ROOT/'node-SHASUMS256.txt').write_text(checks,encoding='utf-8')
    sha=next(line.split()[0] for line in checks.splitlines() if line.split()[-1]==node)
    jdk={'link':'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jdk_x64_windows_hotspot_21.0.9_10.zip','checksum':'1c67df516e9795c0b09f5714bfe151da2e3cc988082042f5bbb60d75e4e63fb5'}
    (ROOT/'jdk-source.json').write_text(json.dumps(jdk,indent=2),encoding='utf-8')
    jobs=[('https://nodejs.org/dist/v24.22.1/'+node,sha),(jdk['link'],jdk['checksum']),('https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_12.1.4_build/ghidra_12.1.4_PUBLIC_20260921.zip','ddac49f903da9d5bac833e5cc79395098b9c33cfd3279be5f31bd00387d2d4db')]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for result in pool.map(lambda args: download(*args),jobs):pass

if __name__=='__main__':main()
