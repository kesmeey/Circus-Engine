"""Package project source, synthetic demo assets, user-supplied icon and license notices."""
import argparse,hashlib,json,re,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--version',default='0.4-ui-dev');args=parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9._-]+',args.version):raise SystemExit('Invalid source package version')
    out=ROOT/'build/distribution';out.mkdir(parents=True,exist_ok=True)
    files=[ROOT/name for name in ['README.md','LICENSE','THIRD_PARTY.md','pyproject.toml','.gitignore','.gitattributes','dc_unpack.py','dc_unpack.cmd','dc_runtime.py','dc_runtime.cmd']]
    for directory in ['circus_tools','docs','profiles','runtime','tests','tools']:
        files += [p for p in (ROOT/directory).rglob('*') if p.is_file() and not any(x in {'__pycache__','.git'} for x in p.relative_to(ROOT).parts) and p.suffix not in {'.pyc','.class'}]
    # Original data and audit outputs never enter this allowlisted source archive.
    archive=out/('circus-runtime-'+args.version+'-source.zip')
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(files):z.write(p,'circus-runtime/'+p.relative_to(ROOT).as_posix())
    report={'file':str(archive),'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'files':len(files),'game_data_archives_included':False,'user_supplied_launcher_icon_included':True}
    (out/'source-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))

if __name__=='__main__':main()
