"""Private PC1/PC2 Android startup subset. Never packages user assets into the APK.

Copy exact files referenced by PcStartupAudit and rebuild only the old voice index.
Use full original folders for desktop audits; this subset is only for device capacity.
"""
import argparse, hashlib, json, shutil, struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument('game', type=Path)
    cli.add_argument('log', type=Path)
    cli.add_argument('--id', choices=['DCPC', 'DC2PC'], required=True)
    args = cli.parse_args()
    source = args.game.resolve()
    out = ROOT / 'build/pc-port/fixtures' / args.id
    paths = {p.relative_to(source).as_posix().lower(): p for p in source.rglob('*') if p.is_file()}
    selected = {p for p in paths if p.startswith('advdata/mes/') or p.startswith('advdata/grp/system/')}
    selected.add('advdata/dat/names.dat')
    if args.id == 'DCPC': selected.add('advdata/op.mpg')
    voices = set()
    for line in args.log.read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('IMAGE '):
            name = line[6:].rsplit(' ', 1)[0].replace('\\', '/').lower()
            selected.add('advdata/grp/' + name + ('' if name.endswith('.crx') else '.crx'))
        if line.startswith('AUDIO '):
            name = line[6:].rsplit(' ', 1)[0].replace('\\', '/').lower()
            if name.startswith('voice/'):
                if name.endswith('.wav'): name = name[:-4]
                voices.add((name[6:] + ('' if name.endswith('.pcm') else '.pcm')).replace('/', '\\'))
            else: selected.add('advdata/' + name + ('' if name.endswith('.pcm') else '.pcm'))
    manifest = []
    for key in sorted(selected):
        p = paths[key]; target = out / p.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(p, target)
        manifest.append({'path': key, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(), 'bytes': target.stat().st_size})
    primary = paths['advdata/voice.dat']; secondary = paths.get('advdata/voice2.dat')
    with primary.open('rb') as f:
        count, = struct.unpack('<I', f.read(4)); table = f.read(count * 64)
    entries = []; bounds = [set(), set()]
    for n in range(count):
        row = table[n*64:(n+1)*64]; name = row[:60].split(b'\0')[0]; packed, = struct.unpack_from('<I', row, 60)
        part = packed & 1; offset = packed & ~1
        entries.append((name, part, offset)); bounds[part].add(offset)
    ends = []
    for part, file in enumerate([primary, secondary]):
        offsets = sorted(bounds[part]); ends.append(dict(zip(offsets, offsets[1:] + ([file.stat().st_size] if file else []))))
    chosen = [e for e in entries if e[0].decode('cp932').lower() in voices]
    if len(chosen) != len(voices): raise RuntimeError('Missing/ambiguous voice entries')
    offset = (4 + len(chosen)*64 + 0x7ff) & ~0x7ff
    header = bytearray(struct.pack('<I', len(chosen))); payload = bytearray(offset - 4 - len(chosen)*64)
    for name, part, at in chosen:
        with [primary, secondary][part].open('rb') as f:
            f.seek(at); raw = f.read(ends[part][at] - at)
        header += name.ljust(60, b'\0') + struct.pack('<I', offset)
        payload += raw; offset += len(raw)
        pad = (-offset) % 0x800; payload += bytes(pad); offset += pad
        manifest.append({'voice': name.decode('cp932'), 'part': part, 'source_offset': at, 'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw)})
    (out/'AdvData/VOICE.DAT').write_bytes(header + payload)
    (out.parent/(args.id+'-manifest.json')).write_text(json.dumps({'private_fixture': True, 'not_for_distribution': True, 'records': manifest}, indent=2), encoding='utf-8')
    print(json.dumps({'output': str(out), 'voices': len(chosen), 'bytes': sum(p.stat().st_size for p in out.rglob('*') if p.is_file())}))

if __name__ == '__main__': main()
