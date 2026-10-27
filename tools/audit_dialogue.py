"""Run the Java text-subset audit against local DD files. No game execution or prose export."""
import argparse,hashlib,json,struct,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent

def main():
    sys.stdout.reconfigure(encoding='utf-8')
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument('game',type=Path);cli.add_argument('--java-home',type=Path,required=True)
    args=cli.parse_args();out=ROOT/'build/dialogue-audit';out.mkdir(parents=True,exist_ok=True)
    config=args.game/'uif_config.json';files=sorted((args.game/'AdvData/MES').glob('*.[mM][eE][sS]'))+[config]
    before={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.exists()}
    source=target=''
    if config.exists():
        data=json.loads(config.read_text(encoding='utf-8-sig'))
        if data.get('tunnel_decoder',{}).get('enable',False):raise ValueError('Tunnel decoder unsupported')
        m=data.get('character_substitution',{})
        if m.get('enable',False):source=m['source_characters'];target=m['target_characters']
    mapping=out/'local-character-map.bin';encoded=source.encode('utf-8');mapping.write_bytes(struct.pack('>I',len(encoded))+encoded+target.encode('utf-8'))
    result=subprocess.run([str(args.java_home/'bin/java.exe'),'-Dfile.encoding=UTF-8','-cp',str(ROOT/'build/android/jvm'),'org.circus.core.DialogueAudit',str(args.game/'AdvData/MES'),str(mapping)],capture_output=True,check=True)
    report=json.loads(result.stdout.decode('utf-8'))
    report['source_integrity_unchanged']=all(hashlib.sha256(Path(p).read_bytes()).hexdigest()==h for p,h in before.items())
    report['checked_source_files']=len(before);report['mapping_pairs']=len(source)
    (out/'summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (out/'inputs.json').write_text(json.dumps(before,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    if not report['source_integrity_unchanged']:raise RuntimeError('Input files changed during audit')

if __name__=='__main__':main()
