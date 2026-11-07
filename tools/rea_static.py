"""Use a selected REA checkout with the local Ghidra toolchain, without MCP setup."""
import argparse
import os
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parent.parent

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__,add_help=False)
    parser.add_argument('--rea-root',type=Path,help='REA checkout/package root; all other arguments are passed through to REA')
    args,rea_args=parser.parse_known_args()
    node=ROOT/'build/toolchain/node-v24.22.1-win-x64/node.exe'
    rea_root=args.rea_root.resolve() if args.rea_root else ROOT/'build/rea-cli/node_modules/rea-agents'
    cli=rea_root/'scripts/rea.mjs'
    if not node.is_file():raise SystemExit('Project-local Node toolchain is missing; see docs/PC_COMPATIBILITY.md')
    if not cli.is_file():raise SystemExit(f'REA entry point is missing: {cli}')
    if not (rea_root/'dist/cli-runtime/cli.js').is_file():raise SystemExit(f'REA checkout has not been built: {rea_root}; run npm ci and npm run build:cached there')
    env=os.environ.copy();temp=ROOT/'build/rea-temp';temp.mkdir(exist_ok=True)
    env.update(GHIDRA_INSTALL_DIR=str(ROOT/'build/toolchain/ghidra_12.1.4_PUBLIC'),JAVA_HOME=str(ROOT/'build/toolchain/jdk-21.0.9+10'),TEMP=str(temp),TMP=str(temp))
    raise SystemExit(subprocess.call([str(node),str(cli),*rea_args],env=env,cwd=ROOT))
