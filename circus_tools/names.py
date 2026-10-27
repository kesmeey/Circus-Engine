"""Observed NAMES.DAT layouts: modern DC2DM/DC3DD and explicit legacy PC1/PC2.

Category names follow literal resource paths. Numeric fields are preserved but
their runtime semantics are deliberately not assigned (loop points, flags, etc.).
"""
from __future__ import annotations

import struct

from .common import FormatError, check_range

TABLES = [("system_scripts", 32), ("music", 48), ("sound", 36),
          ("images", 32), ("scenario_scripts", 32)]


def parse_names(data: bytes, legacy: bool = False) -> dict:
    position, tables = 0, []
    width = 128 if legacy else 32
    layouts = list(zip([c for c, _ in TABLES], [128, 192, 132, 128, 188])) if legacy else TABLES
    for category, stride in layouts:
        check_range(position, 4, len(data))
        count = struct.unpack_from("<I", data, position)[0]
        count_offset = position
        position += 4
        if count > 100_000:
            raise FormatError("NAMES table count exceeds limit")
        check_range(position, count * stride, len(data))
        entries = []
        for i in range(count):
            offset = position + i * stride
            raw = data[offset:offset + width]
            if b"\0" not in raw:
                raise FormatError(f"NAMES string has no terminator at {offset}")
            name = raw.split(b"\0", 1)[0]
            extra = data[offset + width:offset + stride]
            entries.append({"index": i, "offset": offset,
                            "name": name.decode("cp932", errors="replace"),
                            "name_hex": name.hex(), "extra_hex": extra.hex(),
                            "extra_u32": list(struct.unpack(f"<{len(extra) // 4}I", extra))})
        tables.append({"category": category, "count_offset": count_offset,
                       "count": count, "stride": stride, "entries": entries})
        position += count * stride
    if position != len(data):
        raise FormatError(f"NAMES unknown tail at {position}, size={len(data)}")
    return {"layout": "observed_dcpc_dc2pc_128_192_132_128_188" if legacy else "observed_dc2dm_dc3dd_32_48_36_32_32",
            "category_semantics": "inferred_from_resource_names",
            "extra_field_semantics": "unknown", "tables": tables,
            "bytes_consumed": position}
