"""Run the portable CIRCUS core on the desktop (original Windows EXE is not run)."""
import os
import shutil
import subprocess
import sys
from pathlib import Path
from tools.build_desktop import build

if __name__=='__main__':
    java,classes=build(os.environ.get('JAVA_HOME'))
    args=sys.argv[1:]
    if args:args[0]=str(Path(args[0]).resolve())
    if len(args)==3 and args[1]=='--audit':args[2]=str(Path(args[2]).resolve())
    ffmpeg=os.environ.get('DC_FFMPEG') or shutil.which('ffmpeg')
    if not ffmpeg:
        try:
            import imageio_ffmpeg
            ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
        except ImportError:
            pass
    options=['-Ddc.ffmpeg='+ffmpeg] if ffmpeg else []
    raise SystemExit(subprocess.call([java,'-Xmx768m','-Dfile.encoding=UTF-8',*options,'-cp',str(classes),'org.circus.core.DesktopRuntime',*args],cwd=Path(__file__).resolve().parent))
