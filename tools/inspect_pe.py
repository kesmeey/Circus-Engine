"""Read PE headers/imports using pefile. NEVER execute or load the input module.

Optional forensic tool; pefile is not needed for the main static CLI.
"""
from pathlib import Path
import argparse
import hashlib
import json

import pefile

cli = argparse.ArgumentParser(description=__doc__)
cli.add_argument("inputs", nargs="+", type=Path)
cli.add_argument("--out", required=True, type=Path)
args = cli.parse_args()
output = args.out.resolve()
for source in args.inputs:
    root = source.resolve().parent
    if output == root or root in output.parents or output in root.parents:
        raise ValueError("output and source directories overlap")
output.mkdir(parents=True, exist_ok=True)
results = []
for source in args.inputs:
    raw = source.read_bytes()
    pe = pefile.PE(data=raw, fast_load=True)
    pe.parse_data_directories(directories=[pefile.DIRECTORY_ENTRY['IMAGE_DIRECTORY_ENTRY_IMPORT']])
    imports = []
    for library in getattr(pe, 'DIRECTORY_ENTRY_IMPORT', []):
        imports.append({"dll": library.dll.decode('ascii', errors='replace'),
                        "symbols": [i.name.decode('ascii', errors='replace') if i.name else f"ordinal:{i.ordinal}"
                                    for i in library.imports]})
    results.append({"source": str(source.resolve()), "sha256": hashlib.sha256(raw).hexdigest(),
                    "machine": f"0x{pe.FILE_HEADER.Machine:04X}",
                    "image_base": f"0x{pe.OPTIONAL_HEADER.ImageBase:X}",
                    "entrypoint_rva": f"0x{pe.OPTIONAL_HEADER.AddressOfEntryPoint:X}",
                    "sections": [{"name": s.Name.rstrip(b'\0').decode('ascii', errors='replace'),
                                  "rva": s.VirtualAddress, "virtual_size": s.Misc_VirtualSize,
                                  "file_offset": s.PointerToRawData, "file_size": s.SizeOfRawData,
                                  "characteristics": f"0x{s.Characteristics:08X}"} for s in pe.sections],
                    "imports": imports, "parser_warnings": pe.get_warnings(),
                    "executed": False})
    pe.close()
(output / 'pe-headers-imports.json').write_text(json.dumps({"pefile_version": pefile.__version__,
    "mode": "read_bytes_only", "files": results}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps([{"source": r['source'], "machine": r['machine'],
                  "libraries": [i['dll'] for i in r['imports']]} for r in results]))
