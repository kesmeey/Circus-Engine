"""Select generated output storage without moving source or existing toolchains."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def build_root(value=None):
    """An explicit path wins over CIRCUS_BUILD_ROOT; old commands keep ROOT/build."""
    root = Path(value or os.environ.get('CIRCUS_BUILD_ROOT') or ROOT/'build').resolve()
    if root == Path(root.anchor) or root == ROOT.resolve():
        raise ValueError('Use a dedicated build directory, not a drive or source root')
    for name in ('runtime', 'profiles', 'tools', 'circus_tools', 'docs', 'tests', '.git'):
        source = (ROOT/name).resolve()
        if root == source or root.is_relative_to(source):
            raise ValueError('Build directory must be outside source inputs: '+str(root))
    return root


def build_environment(root, environment=None):
    """Keep child-process temporary files beside the selected generated outputs."""
    root = build_root(root)
    temporary = root/'tmp'
    temporary.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ if environment is None else environment)
    env.update(CIRCUS_BUILD_ROOT=str(root), TEMP=str(temporary), TMP=str(temporary),
               TMPDIR=str(temporary), PYTHONDONTWRITEBYTECODE='1')
    return env
