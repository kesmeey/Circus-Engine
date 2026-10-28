"""Rebuild the public MIT codec-setup resource from python-fsb5/vorbis_headers.py.

Download the input separately from https://github.com/HearthSim/python-fsb5.
Only ast.literal_eval is used; no third-party Python code is executed.
The table contains codec configuration, not compressed game audio.
"""
import argparse
import ast
import gzip
import hashlib
from pathlib import Path
import struct


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'runtime/core/resources/org/circus/core/fsb5-vorbis-headers.bin.gz')
    args = parser.parse_args()
    raw = args.input.read_bytes()
    tree = ast.parse(raw.decode('utf-8'))
    if len(tree.body) != 1 or not isinstance(tree.body[0], ast.Assign):
        raise ValueError('Expected a single codec-header dictionary assignment')
    headers = ast.literal_eval(tree.body[0].value)
    if not isinstance(headers, dict) or not 1 <= len(headers) <= 10000:
        raise ValueError('Invalid codec-header table')
    out = bytearray(b'FVH1' + struct.pack('<I', len(headers)))
    for key, data in sorted(headers.items()):
        if not isinstance(key, int) or not 0 <= key <= 0xffffffff or not isinstance(data, bytes) or not 7 <= len(data) <= 65536 or not data.startswith(b'\x05vorbis'):
            raise ValueError('Invalid Vorbis setup entry')
        out += struct.pack('<II', key, len(data)) + data
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(gzip.compress(out, mtime=0))
    print(f'{len(headers)} headers; input SHA-256 {hashlib.sha256(raw).hexdigest()}; output {args.output.stat().st_size} bytes')


if __name__ == '__main__':
    main()
