"""Reproduce address-tagged MES evidence from a SHA-identified PE, read-only."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from circus_tools.common import child_output, isolated_output, write_json
from circus_tools.semantics import catalog
from circus_tools.state_schema import state_profile
import capstone
import pefile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('exe', type=Path)
    parser.add_argument('--profile', required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    sem = catalog()['profiles'][args.profile]
    raw = args.exe.read_bytes()
    sha = hashlib.sha256(raw).hexdigest()
    if sha != sem['native_sha256']:
        raise SystemExit('PE hash does not match evidence profile; refuse fixed native addresses')
    state = state_profile(args.profile)
    if state and state['native_sha256'] != sha:
        raise SystemExit('State contract does not match PE hash')
    pe = pefile.PE(data=raw)
    if pe.FILE_HEADER.Machine != 0x14c:
        raise SystemExit('Only x86 PE evidence profiles are supported')
    base = pe.OPTIONAL_HEADER.ImageBase
    out = isolated_output(args.out, args.exe.parent)
    md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    md.skipdata = True
    spans = []
    for name, (start, end) in sem['evidence_ranges'].items():
        data = pe.get_data(start-base, end-start)
        lines = [f'{i.address:08X} {i.bytes.hex():22} {i.mnemonic:8} {i.op_str}' for i in md.disasm(data, start)]
        child_output(out, name+'.asm').write_text('\n'.join(lines)+'\n', encoding='utf-8')
        spans.append({'name': name, 'start_va': hex(start), 'end_va_exclusive': hex(end), 'sha256': hashlib.sha256(data).hexdigest()})
    count = sem['dispatch_count']
    addr = sem['addresses']
    indices = list(pe.get_data(int(addr['dispatch_index'],16)-base,count)) if 'dispatch_index' in addr else list(range(count))
    table_size = max(indices)+1
    table = struct.unpack(f'<{table_size}I', pe.get_data(int(addr['dispatch_table'],16)-base,table_size*4))
    rows = {f'0x{i:02X}': {'handler_va': f'0x{table[index]:08X}',
                            'switch_index': index,
                            'semantic_id': sem['opcodes'].get(f'0x{i:02X}', 'unknown'),
                            'shared_fallback': table[index] == int(addr['fallback'],16)} for i,index in enumerate(indices)}
    write_json(child_output(out,'dispatch.json'),rows)
    manifest = {'source': str(args.exe.resolve()), 'source_sha256': sha, 'profile':args.profile,
                'image_base':hex(base), 'pefile_version':pefile.__version__, 'capstone_version':capstone.__version__,
                'spans':spans,'dispatch_count':len(rows),'warning':'Only listed semantic subset is interpreted. Shared fallback does not imply NOP.', 'execution_performed':False}
    if state:
        path = Path(__file__).resolve().parent.parent / 'profiles' / sem['state_contract']
        manifest['state_contract'] = {'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
    write_json(child_output(out,'manifest.json'),manifest)
    print(json.dumps({'profile':args.profile,'dispatch_count':len(rows),'source_sha256':sha}))


if __name__ == '__main__':
    main()
