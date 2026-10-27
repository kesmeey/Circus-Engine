"""Use the project-local REA/Ghidra toolchain without changing global MCP settings."""
import os
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parent.parent

if __name__=='__main__':
    node=ROOT/'build/toolchain/node-v24.22.1-win-x64/node.exe'
    cli=ROOT/'build/rea-cli/node_modules/rea-agents/scripts/rea.mjs'
    if not node.is_file() or not cli.is_file():raise SystemExit('Project-local REA toolchain is missing; see docs/PC_COMPATIBILITY.md')
    env=os.environ.copy();temp=ROOT/'build/rea-temp';temp.mkdir(exist_ok=True)
    env.update(GHIDRA_INSTALL_DIR=str(ROOT/'build/toolchain/ghidra_12.1.4_PUBLIC'),JAVA_HOME=str(ROOT/'build/toolchain/jdk-21.0.9+10'),TEMP=str(temp),TMP=str(temp))
    raise SystemExit(subprocess.call([str(node),str(cli),*sys.argv[1:]],env=env,cwd=ROOT))
