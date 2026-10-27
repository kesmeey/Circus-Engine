"""Build a private, local Android test data subset from playback_audit --refs.

The APK and source distribution never include these user-provided assets.
Archive payloads are copied unchanged; the small voice index is rebuilt.
"""
import argparse,hashlib,json,shutil,struct,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from circus_tools.archives import read_archive

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('game',type=Path);p.add_argument('refs',type=Path);a=p.parse_args()
    source=a.game.resolve();out=ROOT/'build/android-original-fixture/DC3DD';out.mkdir(parents=True,exist_ok=True)
    paths={f.relative_to(source).as_posix().lower():f for f in source.rglob('*') if f.is_file()}
    selected={'advdata/grp/names.dat','uif_config.json'};voices=set()
    for line in a.refs.read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('IMAGE\t'):
            parts=line[6:].replace('\\','/').split(',');selected.add(('AdvData/GRP/'+(parts[-1]+('.CRM' if len(parts)==2 else '.CRX'))).lower())
        elif line.startswith('AUDIO\t'):
            name=line[6:].replace('\\','/')
            if name.lower().startswith('voice/'):voices.add((name[6:].replace('/','\\')+'.PCM').lower())
            else:selected.add(('AdvData/'+name+'.PCM').lower())
    selected.update(k for k in paths if k.startswith('advdata/mes/') and k.endswith('.mes'))
    manifest=[]
    for key in sorted(selected):
        f=paths[key];relative=f.relative_to(source);target=out/relative;target.parent.mkdir(parents=True,exist_ok=True)
        before=hashlib.sha256(f.read_bytes()).hexdigest();shutil.copyfile(f,target)
        if hashlib.sha256(target.read_bytes()).hexdigest()!=before:raise RuntimeError('Copy mismatch')
        manifest.append({'path':relative.as_posix(),'sha256':before,'bytes':target.stat().st_size})
    archive=source/'AdvData/VOICE.DAT';index=read_archive(archive);entries=[e for e in index.entries if e.name.lower() in voices]
    if len(entries)!=len(voices):raise RuntimeError('Missing or ambiguous voice entries')
    small=bytearray();large=bytearray();payload=bytearray();offset=4+len(entries)*72
    with archive.open('rb') as f:
        for e in entries:
            f.seek(e.offset);data=f.read(e.size);name=bytes.fromhex(e.name_hex)
            small+=struct.pack('<II',offset,len(data));large+=name.ljust(56,b'\0')+struct.pack('<II',offset,len(data));payload+=data;offset+=len(data)
            manifest.append({'archive':'AdvData/VOICE.DAT','entry_hex':e.name_hex,'source_offset':e.offset,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    (out/'AdvData/VOICE.DAT').write_bytes(struct.pack('<I',len(entries))+small+large+payload)
    (out.parent/'manifest.json').write_text(json.dumps({'source':str(source),'private_fixture':True,'not_for_distribution':True,'records':manifest},indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'output':str(out),'files':len(selected)+1,'voice_entries':len(entries),'bytes':sum(f.stat().st_size for f in out.rglob('*') if f.is_file())}))
if __name__=='__main__':main()
