"""Compare a local DC3DD dispatch trace to original MES bytes, without executing them."""
import argparse,collections,hashlib,json,struct,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from circus_tools.mes import identify,instructions

def audit(trace,mes_root):
    cache={};counts=collections.Counter();scripts=collections.Counter();events=collections.Counter();errors=[];checked=0;extensions=0
    for line in trace.read_text(encoding='utf-8').splitlines():
        envelope=json.loads(line);row=envelope.get('payload',{});events[row.get('type',envelope.get('type','unknown'))]+=1
        if row.get('type')!='mes_dispatch':continue
        name=bytes.fromhex(row['script_name_hex']).decode('cp932');key=name.lower()
        if key not in cache:
            paths=[p for p in mes_root.iterdir() if p.stem.lower()==key and p.suffix.lower()=='.mes']
            if len(paths)!=1:raise ValueError(f'Ambiguous/missing script {name}')
            data=paths[0].read_bytes();profile,version_offset,version_length=identify(data,'dc3dd-a5a8')
            tokens={t.offset:t for t in instructions(data,profile,version_offset+version_length)}
            cache[key]=(data,tokens,version_offset,hashlib.sha256(data).hexdigest())
        data,tokens,vo,_=cache[key];pc=vo+row['pc'];token=tokens.get(pc);checked+=1;counts[f"{row['opcode']:02X}"]+=1;scripts[key]+=1
        def fail(kind,**details):errors.append({'sequence':row['sequence'],'script':name,'native_pc':row['pc'],'kind':kind,**details})
        if token is None:fail('PC is not a physical boundary');continue
        if token.opcode!=row['opcode']:fail('Opcode mismatch',expected=token.opcode,actual=row['opcode'])
        next_pc=token.offset+len(token.raw);args=[];raw=b''
        if token.kind=='u8x2':args=list(token.raw[1:])
        elif token.kind=='u16x4':args=list(struct.unpack('<4h',token.raw[1:]))
        else:
            if token.kind=='u8str':args=[token.raw[1]];raw=token.raw[2:-1]
            else:raw=token.raw[1:-1]
            if token.kind=='encoded_string':raw=bytes(32 if (x+32)&255==36 else (x+32)&255 for x in raw)
        if token.opcode!=0 and next_pc in tokens and tokens[next_pc].opcode==0x71:
            extension=tokens[next_pc];args+=list(struct.unpack('<4h',extension.raw[1:]));next_pc+=len(extension.raw);extensions+=1
        if row['next_pc']!=next_pc-vo:fail('Fetch PC mismatch',expected=next_pc-vo,actual=row['next_pc'])
        if row['arguments_i32'][:len(args)]!=args:fail('Signed argument mismatch',expected=args,actual=row['arguments_i32'])
        if bytes.fromhex(row['string_prefix_hex'])!=raw[:128]:fail('Decoded string prefix mismatch')
    return {'trace':str(trace),'trace_sha256':hashlib.sha256(trace.read_bytes()).hexdigest(),'exe_variant':'user-selected DC3DD_crack.EXE; see alternate-probe-evidence.json','dispatches_checked':checked,'extensions_observed':extensions,'events':dict(events),'scripts':dict(scripts),'opcodes':dict(sorted(counts.items())),'script_sha256':{k:v[3] for k,v in cache.items()},'errors':errors,'scope':'Fetch/framing validation only. Observed execution does not establish full opcode semantics.'}

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace');p=argparse.ArgumentParser(description=__doc__);p.add_argument('trace',type=Path);p.add_argument('--mes-root',type=Path,default=Path(r'D:\gal\DC3DD\AdvData\MES'));p.add_argument('--out',type=Path,required=True);args=p.parse_args()
    report=audit(args.trace,args.mes_root);args.out.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in report.items() if k not in {'script_sha256','errors','opcodes'}},ensure_ascii=True));print('Errors:',len(report['errors']));print(json.dumps(report['errors'][:10],ensure_ascii=True))
    if report['errors']:sys.exit(1)

if __name__=='__main__':main()
