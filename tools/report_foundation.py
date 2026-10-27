"""Collect completed local evidence without embedding game content."""
import hashlib
import json
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parent.parent

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    games={}
    for name in ['DCPC','DC2PC','DC3DD','DC2DM']:
        base=ROOT/'build/unpack'/name;s=read(base/'summary.json')
        games[name]={k:s[k] for k in ['profile','entries','raw_verified','decoded','raw_complete','complete','errors','text_encoding_errors','gallery_view']}
        games[name]['manifest_sha256']=sha(base/'manifest.jsonl')
        games[name]['sources_sha256']=sha(base/'sources.jsonl')
        games[name]['independent_verify']=read(base/'verification.json') if (base/'verification.json').exists() else None
    desktop={}
    for name in ['dcpc','dc2pc']:
        base=ROOT/'build/desktop'/('audit-'+name);r=read(base/'result.json')
        desktop[name]={k:v for k,v in r.items() if k not in ['images','audio_decoded']}
        desktop[name].update(images_decoded=len(r['images']),audio_decoded=len(r['audio_decoded']),screenshots={p.name:sha(p) for p in sorted(base.glob('*.png'))})
    spec=read(ROOT/'profiles/vm-specification.json');python=(ROOT/'build/pc-port/python-tests.log').read_text(encoding='utf-8-sig');core=(ROOT/'build/pc-port/core-tests.log').read_text(encoding='utf-8-sig')
    report={'stage':'resource extraction / VM specification / PC frontend','game_exes_executed':False,'new_apk_released':False,'raw_entries':sum(g['entries'] for g in games.values()),'games':games,'desktop':desktop,'audio_platform_probe':read(ROOT/'build/pc-port/audio-probe.json'),'vm_specification':{k:{'exe_sha256':v['exe_sha256'],'dispatch_count':v['dispatch_count'],'evidence':v['evidence']} for k,v in spec['profiles'].items()},'tests':{'python':re.search(r'Ran \d+ tests',python).group(),'python_pass':'\nOK\n' in python.replace('\r',''),'jvm':core.strip()}}
    out=ROOT/'build/distribution/pc-foundation-verification.json';out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({'path':str(out),'raw_entries':report['raw_entries'],'sha256':sha(out)}))

if __name__=='__main__':main()
