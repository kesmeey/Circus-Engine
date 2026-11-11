"""Run self-authored native core fixtures without any original game files."""
import argparse
import hashlib
import json
import os
import subprocess
from pathlib import Path

from build_desktop import ROOT, build, build_environment, build_root


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--heap-mb', type=int, default=192)
    parser.add_argument('--build-root', type=Path, help='Generated output root; defaults to CIRCUS_BUILD_ROOT or workspace build')
    args = parser.parse_args()
    if args.heap_mb < 64:
        parser.error('--heap-mb must be at least 64')
    generated_build=build_root(args.build_root)
    java, classes = build(args.java_home, with_tests=True, build_dir=generated_build)
    demo=ROOT/'runtime/android/assets/demo'
    captured={p.relative_to(demo):p.read_bytes() for p in sorted(demo.rglob('*')) if p.is_file()}
    identity=hashlib.sha256(b''.join(p.as_posix().encode('utf-8')+b'\0'+raw for p,raw in captured.items())).hexdigest()
    fixture=classes.parent/'fixtures'/identity
    for path,raw in captured.items():
        target=fixture/path
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(raw)
    manifest={p.as_posix():hashlib.sha256(raw).hexdigest() for p,raw in captured.items()}
    (classes.parent/'fixtures-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    subprocess.run([java, '-Xmx'+str(args.heap_mb)+'m', '-Dfile.encoding=UTF-8',
                    '-cp', str(classes), 'org.circus.core.CoreTests',
                    str(fixture)], cwd=ROOT, check=True, env=build_environment(generated_build))


if __name__ == '__main__':
    main()
