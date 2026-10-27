"""Create a gallery view from original NAMES indices; keep lossless source classification."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import sys
from collections import Counter

ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from circus_tools.names import parse_names
from circus_tools.common import child_output,sha256_file

def index(output):
    output=Path(output).resolve();summary=json.loads((output/'summary.json').read_text(encoding='utf-8'))
    source=Path(summary['input']);pid=summary['profile']
    if not source.is_dir() or not pid:return
    direct=[p for p in source.iterdir() if p.is_dir() and p.name.lower()=='advdata']
    resource_root=direct[0] if len(direct)==1 else source
    names=[p for p in resource_root.rglob('*') if p.is_file() and p.name.lower()=='names.dat' and 'old_source_circus_dcpc' not in p.parts]
    if len(names)!=1:raise ValueError('Expected one original NAMES.DAT')
    catalogue=parse_names(names[0].read_bytes(),pid in ['dcpc-2063','dc2pc-5769'])
    (child_output(output,'catalog/names.json')).write_text(json.dumps(catalogue,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    rows=[json.loads(l) for l in (output/'manifest.jsonl').read_text(encoding='utf-8').splitlines()]
    summary['categories']=dict(sorted(Counter(r['category'] for r in rows if 'category' in r).items()))
    lookup={}
    for row in rows:
        if not row.get('decoded','').endswith('.png'):continue
        path=row['source'].replace('\\','/').lower();entry=row['entry'].replace('\\','/').split('/')[-1].lower()
        if path.endswith('.crx'):key=(path,'')
        else:key=(path,entry)
        lookup.setdefault(key,row)
    result=[];missing=[];matched=0
    for e in next(t for t in catalogue['tables'] if t['category']=='images')['entries']:
        ref=e['name'].replace('\\','/').lower()
        prefix='//' if ref.startswith('//') else ''
        if prefix:ref=ref[2:]  # Preserve the literal prefix separately, never interpret it as a filesystem root.
        if not ref or ref=='--' or ref=='-':continue
        parts=ref.split(',')
        if len(parts)==1:key=('advdata/grp/'+ref+('' if ref.endswith('.crx') else '.crx'),'')
        else:key=('advdata/grp/'+parts[1]+('' if parts[1].endswith('.crm') else '.crm'),parts[0].split('/')[-1]+('' if parts[0].endswith('.crx') else '.crx'))
        row=lookup.get(key)
        if not row:missing.append({'index':e['index'],'reference':e['name']});continue
        stem=re.sub(r'[^a-zA-Z0-9._-]','_',parts[0].split('/')[-1])
        target=child_output(output,f"cg/{e['index']:04d}_{stem}.png");existing=child_output(output,row['decoded'])
        if not target.exists():
            try:os.link(existing,target)
            except OSError:shutil.copyfile(existing,target)
        if sha256_file(target)!=row['decoded_sha256']:raise ValueError('Gallery copy hash mismatch')
        result.append({'index':e['index'],'reference':e['name'],'catalog_prefix':prefix,'png':target.relative_to(output).as_posix(),'manifest_decoded':row['decoded'],'sha256':row['decoded_sha256']});matched+=1
    child_output(output,'catalog/gallery.json').write_text(json.dumps({'basis':'original NAMES images indices; gallery view, source categories retained','matched':matched,'unmatched':missing,'entries':result},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    summary['gallery_view']={'matched':matched,'unmatched':len(missing),'index':'catalog/gallery.json'}
    (output/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'output':str(output),'gallery':summary['gallery_view']}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);index(p.parse_args().output)
