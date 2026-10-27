"""Generate reviewed bank-99 storage contracts; addresses stay in evidence files."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
def generate():
    entries=json.loads((ROOT/'profiles/dc3dd-state.json').read_text(encoding='utf-8'))['banks']['99']['entries']
    lines=['package org.circus.core;','// Generated from profiles/dc3dd-state.json.','final class Dc3StateRules {',
           'static final int[] READ=new int[256],WRITE=new int[256];','static {']
    for key,e in entries.items():
        for side in ['read','write']:
            kind=e[side]['kind'];value=0 if kind in ['constant','ignored'] else 1 if kind in ['state_i32','complement_99_i32'] else 2
            if side=='write' and 'host_effects' in e[side]:value=3
            if value:lines.append(f'{side.upper()}[{key}]={value};')
    settings=[int(k) for k,e in entries.items() if e['read']['kind'] in ['state_i32','complement_99_i32'] and int(e['read']['offset'],16)<0x4b74]
    lines+=['}', 'static final int[] SETTINGS={'+','.join(map(str,settings))+'};','}'];(ROOT/'runtime/core/src/org/circus/core/Dc3StateRules.java').write_text('\n'.join(lines)+'\n',encoding='utf-8')
if __name__=='__main__':generate()
