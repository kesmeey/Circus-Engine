"""Import only DC1/2/3 framing facts from a local MesTextTool source checkout.

The derived catalog retains GPL-3.0 attribution. It is not a runtime opcode map.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil

ALLOWED = {
    "dcxx", "dcmems", "ktlep", "dcws0", "dcws", "dcsv", "dcpc", "dcdx", "dcas",
    "dcbs", "dc2fl", "dc2bs", "dc2dm", "dc2fy", "dc2cckko", "dc2ccotm",
    "dc2sc", "dc2ty", "dc2pc", "dc3rx", "dc3pp", "dc3wy", "dc3dd",
}
parser = argparse.ArgumentParser()
parser.add_argument("source", type=Path)
args = parser.parse_args()
project = Path(__file__).resolve().parents[1]
source = args.source.resolve()
text = source.read_text(encoding="utf-8-sig")
result = []
for line in text.splitlines():
    match = re.search(r'\{\s*"([^"\n]+)"\s*,\s*(offset[12])\s*,\s*(0x[0-9A-Fa-f]+)', line)
    if not match or match[1] not in ALLOWED:
        continue
    numbers = [int(s, 16) for s in re.findall(r'0x[0-9A-Fa-f]+', line[match.start(3):].split('//')[0])]
    if len(numbers) < 12:
        raise ValueError(f"cannot parse profile {match[1]}")
    alias, layout, version = match[1], match[2], numbers[0]
    kinds = ["u8x2", "u8str", "string", "encoded_string", "u16x4"]
    ranges = [[kind, *numbers[1 + i * 2:3 + i * 2]] for i, kind in enumerate(kinds)]
    # 0xFF..0xFF is a historical sentinel for an absent ParamStr section.
    ranges = [r for r in ranges if not (r[0] == "u8str" and r[1:] == [255, 255])]
    result.append({"id": f"{alias}-{version:04x}", "alias": alias, "layout": layout,
                   "version": version, "ranges": ranges, "string_key": numbers[11],
                   "plain_text_opcodes": numbers[12:],
                   "title": line.split('//', 1)[1].strip() if '//' in line else alias,
                   "evidence": "reference_format_only", "execution_supported": False})
if not result or any(p["alias"].startswith(("dc4", "dc5")) for p in result):
    raise ValueError("invalid scope")
target = project / "profiles/dc123.json"
target.parent.mkdir(exist_ok=True)
target.write_text(json.dumps({"schema_version": 1, "scope": "DC1/DC2/DC3 and related fandiscs only",
    "source": "https://github.com/cokkeijigen/MesTextTool/blob/master/MesTextTool/src/mes/script_info.cpp",
    "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "profiles": result},
    ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
license_file = source.parents[3] / "LICENSE"
if not license_file.is_file():
    raise FileNotFoundError(license_file)
shutil.copyfile(license_file, project / "LICENSE")
print(json.dumps({"catalog": str(target), "profile_count": len(result)}))

