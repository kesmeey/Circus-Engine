"""Compile the portable core and desktop tools, without Android SDK or Gradle."""
import argparse
import hashlib
import json
from pathlib import Path
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent

def build(java_home=None, with_tests=False):
    javac = str(Path(java_home)/'bin'/('javac.exe' if os.name == 'nt' else 'javac')) if java_home else shutil.which('javac')
    if not javac:
        raise RuntimeError('A JDK 17+ is required; set JAVA_HOME or pass --java-home')
    java = str(Path(javac).with_name('java.exe' if os.name == 'nt' else 'java'))
    desktop = sorted((ROOT/'runtime/desktop/src').rglob('*.java'))
    files = sorted((ROOT/'runtime/core/src').rglob('*.java'))
    if with_tests:
        files += desktop + sorted((ROOT/'runtime/tests').rglob('*.java'))
    else:
        # Physical audits borrow test fixtures and must not enter the player
        # package. The explicit test build below retains all audit entrypoints.
        files += [p for p in desktop if not p.stem.endswith(('Audit','AuditFixtures','Tests','Test'))]
        files += [ROOT/'runtime/tests/org/circus/core/LocalGameStorage.java',ROOT/'runtime/tests/org/circus/core/RangeStorage.java']
    resources = sorted(p for p in (ROOT/'runtime/core/resources').rglob('*') if p.is_file())
    captured={p:p.read_bytes() for p in files+resources}
    digest = hashlib.sha256(b'desktop-class-output-v4\0'+b''.join(str(p).encode()+captured[p] for p in files+resources)).hexdigest()
    scope=ROOT/'build'/('desktop-tests' if with_tests else 'desktop')
    output=scope/'classes'; marker=scope/'source.sha256'
    if not marker.exists() or marker.read_text(encoding='utf-8') != digest:
        # A failed compile invalidates the previous output. Restoring its old
        # source must rebuild rather than trust a marker for deleted classes.
        marker.unlink(missing_ok=True)
        output.mkdir(parents=True, exist_ok=True)
        # javac does not remove deleted/renamed inputs. Clear prior classes
        # and copied resources so packaging cannot retain obsolete entries.
        generated_root=output.resolve()
        if not generated_root.is_relative_to((ROOT/'build').resolve()):
            raise RuntimeError('Desktop class output escaped the workspace build directory')
        generated=[p for p in output.rglob('*') if p.is_file()]
        if any(not p.resolve().is_relative_to(generated_root) for p in generated):
            raise RuntimeError('Desktop class output contains an external path')
        for path in generated:
            path.unlink()
        # Compile these exact captured bytes even if another worker edits the
        # working tree while javac is running. The fingerprint and evidence
        # manifest describe the actual inputs, not an earlier directory scan.
        frozen=scope/'inputs'
        for path,raw in captured.items():
            target=frozen/path.relative_to(ROOT)
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(raw)
        manifest={p.relative_to(ROOT).as_posix():hashlib.sha256(raw).hexdigest() for p,raw in captured.items()}
        (scope/'inputs-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
        arguments=['-encoding','UTF-8','--release','8','-d',str(output)]+[str(frozen/p.relative_to(ROOT)) for p in files]
        response=scope/'javac-inputs.txt'
        response.write_text('\n'.join('"'+str(p).replace('\\','/').replace('"','\\"')+'"' for p in arguments)+'\n',encoding='utf-8')
        command=[javac,'-J-Dfile.encoding=UTF-8','-J-Duser.language=en','-Xlint:-options','@'+str(response)]
        subprocess.run(command,check=True)
        for resource in resources:
            target=output/resource.relative_to(ROOT/'runtime/core/resources')
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(frozen/resource.relative_to(ROOT),target)
        marker.write_text(digest,encoding='utf-8')
    return java, output

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--with-tests',action='store_true',help='Compile all core tests and physical audit entrypoints into a separate output')
    args=parser.parse_args()
    print(build(args.java_home,args.with_tests))
