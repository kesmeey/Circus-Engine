"""CIRCUS PCK and CRM readers; see THIRD_PARTY.md for format references.

Payloads are copied byte-for-byte. CRXD links are not flattened or rendered.
No subprocesses, dynamic libraries or game executables are used.
"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
from pathlib import Path
import struct
from urllib.parse import quote

from .common import FormatError, check_range, child_output, read_at, sha256_file


@dataclass(frozen=True)
class Entry:
    index: int
    name: str
    name_hex: str
    offset: int
    size: int


@dataclass
class Archive:
    kind: str
    entries: list[Entry]
    metadata: dict


def _name(raw: bytes) -> tuple[str, str]:
    raw = raw.split(b"\0", 1)[0]
    if not raw:
        raise FormatError("empty archive entry name")
    # Preserve original bytes even if a translated filename uses another encoding.
    return raw.decode("cp932", errors="replace"), raw.hex()


def read_archive(path: Path) -> Archive:
    total = path.stat().st_size
    with path.open("rb") as stream:
        magic = read_at(stream, 0, 4)
        if magic == b"CRXB":
            head = read_at(stream, 0, 16)
            count = struct.unpack_from("<I", head, 8)[0]
            if not 0 < count <= 1_000_000:
                raise FormatError(f"invalid CRM count {count}")
            table_end = 16 + count * 32
            check_range(16, count * 32, total)
            table = read_at(stream, 16, count * 32)
            items, empty_slots = [], []
            for i in range(count):
                row = table[i * 32:(i + 1) * 32]
                if not any(row):
                    empty_slots.append(i)
                    continue
                offset = struct.unpack_from("<I", table, i * 32)[0]
                name, raw = _name(table[i * 32 + 8:i * 32 + 32])
                check_range(offset, 1, total, table_end)
                items.append((i, name, raw, offset))
            offsets = sorted({item[3] for item in items})
            if not offsets:
                raise FormatError("CRM has no populated entries")
            ends = dict(zip(offsets, offsets[1:] + [total]))
            entries = [Entry(i, name, raw, off, ends[off] - off)
                       for i, name, raw, off in items]
            return Archive("CRM/CRXB", entries, {
                "index_end": table_end,
                "index_slot_count": count,
                "empty_slots": empty_slots,
                "unique_payloads": len(offsets),
                "offset_aliases": len(items) - len(offsets),
                "header_hex": head.hex(),
            })
        count = struct.unpack("<I", magic)[0]
        if not 0 < count <= 1_000_000:
            raise FormatError("not a recognized PCK/CRM archive")
        table_end = 4 + count * 72
        check_range(4, count * 72, total)
        table = read_at(stream, 4, count * 72)
        entries = []
        for i in range(count):
            short_off, short_size = struct.unpack_from("<II", table, i * 8)
            pos = count * 8 + i * 64
            name, raw = _name(table[pos:pos + 56])
            off, size = struct.unpack_from("<II", table, pos + 56)
            if (off, size) != (short_off, short_size):
                raise FormatError(f"PCK duplicate index mismatch, entry {i}")
            check_range(off, size, total, table_end)
            entries.append(Entry(i, name, raw, off, size))
        spans = sorted({(x.offset, x.size) for x in entries})
        for (off, size), (next_off, _) in zip(spans, spans[1:]):
            if off + size > next_off:
                raise FormatError("overlapping non-identical PCK entries")
        return Archive("PCK/CIRCUS", entries, {"index_end": table_end})


def entry_destination(root: Path, entry: Entry) -> Path:
    # Ordinal avoids aliases/case collisions; encoding avoids Windows reserved
    # characters, absolute paths and ../ in untrusted names. Original name stays
    # in the manifest and is never used as a filesystem path.
    label = quote(entry.name, safe="._-")[:160].rstrip(".")
    return child_output(root, f"{entry.index:06d}__{label}")


def inspect_or_extract(path: Path, entry: Entry, destination: Path | None) -> tuple[str, bytes]:
    """Hash original slice; optionally extract and independently reread-verify it."""
    digest = hashlib.sha256()
    prefix = b""
    target = entry_destination(destination, entry) if destination else None
    temporary = target.with_name(target.name + ".part") if target else None
    output = None
    try:
        if target and not target.exists():
            output = temporary.open("xb")
        with path.open("rb") as source:
            source.seek(entry.offset)
            remaining = entry.size
            while remaining:
                block = source.read(min(remaining, 1024 * 1024))
                if not block:
                    raise FormatError(f"truncated payload: {entry.name}")
                if len(prefix) < 128:
                    prefix += block[:128 - len(prefix)]
                digest.update(block)
                if output:
                    output.write(block)
                remaining -= len(block)
        if output:
            output.close()
            output = None
            temporary.replace(target)
        value = digest.hexdigest()
        if target and sha256_file(target) != value:
            raise FormatError(f"existing/extracted payload hash mismatch: {target}")
        return value, prefix
    except Exception:
        if output:
            output.close()
            temporary.unlink(missing_ok=True)
        raise
