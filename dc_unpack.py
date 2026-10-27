"""Extract CIRCUS data using the same decoders as the portable runtime."""
import os
import subprocess
import sys
from tools.build_desktop import build
from pathlib import Path
from tools.index_unpacked import index

if __name__ == '__main__':
    java,classes=build(os.environ.get('JAVA_HOME'))
    existed=len(sys.argv)>=3 and (Path(sys.argv[2])/'summary.json').exists()
    status=subprocess.call([java,'-Xmx768m','-Dfile.encoding=UTF-8','-Djava.awt.headless=true','-cp',str(classes),'org.circus.core.DcUnpack',*sys.argv[1:]])
    if not existed and len(sys.argv)>=3 and (Path(sys.argv[2])/'summary.json').is_file() and status in (0,1):index(sys.argv[2])
    raise SystemExit(status)
