"""Read-only x86 PE disassembly helper. Never loads or executes the input PE."""
import argparse
from pathlib import Path
import struct

import capstone
import pefile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("exe", type=Path)
    parser.add_argument("--range", nargs=2, type=lambda s: int(s, 0))
    parser.add_argument("--xref", type=lambda s: int(s, 0))
    parser.add_argument("--table", nargs=2, type=lambda s: int(s, 0))
    args = parser.parse_args()
    pe = pefile.PE(str(args.exe))
    base = pe.OPTIONAL_HEADER.ImageBase
    md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    md.skipdata = True
    if args.table:
        address, count = args.table
        data = pe.get_data(address - base, count * 4)
        for i, value in enumerate(struct.unpack(f"<{count}I", data)):
            print(f"{i:02X} {address + i * 4:08X}: {value:08X}")
    elif args.range:
        start, end = args.range
        for insn in md.disasm(pe.get_data(start - base, end - start), start):
            print(f"{insn.address:08X} {insn.bytes.hex():22s} {insn.mnemonic:8s} {insn.op_str}")
    elif args.xref is not None:
        needle = struct.pack("<I", args.xref)
        for section in pe.sections:
            if not section.Characteristics & 0x20000000:
                continue
            for insn in md.disasm(section.get_data(), base + section.VirtualAddress):
                if needle in insn.bytes or insn.op_str == hex(args.xref):
                    print(f"{insn.address:08X} {insn.bytes.hex():22s} {insn.mnemonic:8s} {insn.op_str}")


if __name__ == "__main__":
    main()
