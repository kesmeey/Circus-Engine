"""Independently rehash every raw/converted output and every source container."""
import argparse
import json
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from circus_tools.common import sha256_file

def inside(root,relative):
    p=(root/relative).resolve()
    if not p.is_relative_to(root.resolve()):raise ValueError('Manifest path escapes tree')
    return p

def verify(output):
    output=output.resolve();s=json.loads((output/'summary.json').read_text(encoding='utf-8'));source=Path(s['input']).resolve()
    if source.is_file():source=source.parent
    elif not any(p.name.lower()=='advdata' for p in source.iterdir()):
        roots=[p.parent for p in source.rglob('*') if p.is_dir() and p.name.lower()=='advdata' and 'old_source_circus_dcpc' not in p.parts]
        if len(roots)!=1:raise ValueError('Ambiguous source tree')
        source=roots[0]
    failures=[];raw=decoded=0;sources=0
    for line in (output/'sources.jsonl').read_text(encoding='utf-8').splitlines():
        r=json.loads(line);p=inside(source,r['path'])
        if p.stat().st_size!=r['bytes'] or sha256_file(p)!=r['sha256']:failures.append({'source':r['path']})
        sources+=1
    for line in (output/'manifest.jsonl').read_text(encoding='utf-8').splitlines():
        r=json.loads(line)
        if 'raw' not in r:failures.append({'missing_raw':r.get('source'),'error':r.get('error')});continue
        p=inside(output,r['raw'])
        if p.stat().st_size!=r['bytes'] or sha256_file(p)!=r['sha256']:failures.append({'raw':r['raw']})
        raw+=1
        if 'decoded_sha256' in r:
            if sha256_file(inside(output,r['decoded']))!=r['decoded_sha256']:failures.append({'decoded':r['decoded']})
            decoded+=1
        if 'extracted' in r and sha256_file(inside(output,r['extracted']))!=r['sha256']:failures.append({'extracted':r['extracted']})
    result={'source_files':sources,'raw_files':raw,'decoded_files':decoded,'integrity_pass':not failures,'failures':failures,'decode_errors':s['errors'],'semantic_completeness_implied':False}
    (output/'verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,ensure_ascii=True))
    return not failures

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);raise SystemExit(0 if verify(p.parse_args().output) else 1)
