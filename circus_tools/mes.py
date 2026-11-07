"""Data-driven MES framing parser; no opcode execution or guessed semantics."""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from functools import lru_cache
import json
from pathlib import Path
import struct

from .common import FormatError

CATALOG = Path(__file__).resolve().parent.parent / "profiles" / "dc123.json"


@lru_cache(maxsize=1)
def _profile_contracts() -> dict[str, dict]:
    """Execution framing comes from native decoder evidence, not a game-name guess."""
    catalog = CATALOG.with_name("mes-semantics.json")
    bindings = json.loads(catalog.read_text(encoding="utf-8"))["profiles"]
    return bindings


@dataclass(frozen=True)
class Instruction:
    offset: int
    opcode: int
    kind: str
    raw: bytes
    consumed_by: int | None = None

    def to_dict(self, key: int = 0x20) -> dict:
        result = {"offset": self.offset, "opcode": f"0x{self.opcode:02X}",
                  "kind": self.kind, "size": len(self.raw), "raw_hex": self.raw.hex()}
        if self.consumed_by is None and self.opcode == 0 and len(self.raw) < 3:
            result["terminal_fragment"] = True
        if self.consumed_by is not None:
            result["consumed_by"] = self.consumed_by
        if self.kind == "u8x2":
            result["operands"] = list(self.raw[1:])
        elif self.kind == "u16x4":
            result["operands"] = list(struct.unpack("<4H", self.raw[1:]))
        else:
            if self.kind == "u8str":
                result["operand"] = self.raw[1]
            raw = self.raw[2:-1] if self.kind == "u8str" else self.raw[1:-1]
            if self.kind == "encoded_string":
                raw = bytes((x + key) & 255 for x in raw)
            # Explicit byte representation, not an assumed human-text encoding.
            result["string_bytes_hex"] = raw.hex()
        return result


def profiles() -> list[dict]:
    return json.loads(CATALOG.read_text(encoding="utf-8"))["profiles"]


def candidate_profiles(data: bytes, profile_id: str | None = None) -> list[tuple[dict, int, int]]:
    if len(data) < 4:
        raise FormatError("MES shorter than header")
    count = struct.unpack_from("<I", data)[0]
    if count > 1_000_000:
        raise FormatError("MES label count exceeds limit")
    matches = []
    for profile in profiles():
        if profile_id and profile["id"] != profile_id:
            continue
        offset = count * (6 if profile["layout"] == "offset2" else 4) + 4
        version = profile["version"]
        length = 3 if profile["layout"] == "offset2" or data[4:8] == b"\x03\0\0\0" else (1 if version < 256 else 2)
        width = 1 if length == 1 else 2
        if offset + length > len(data):
            continue
        accepted_versions = (version, *profile.get("alt_versions", ()))
        if int.from_bytes(data[offset:offset + width], "little") in accepted_versions:
            matches.append((profile, offset, length))
    return matches


def identify(data: bytes, profile_id: str | None = None) -> tuple[dict, int, int]:
    matches = candidate_profiles(data, profile_id)
    if len(matches) != 1:
        raise FormatError(f"MES profile not uniquely identified: {[m[0]['id'] for m in matches]}")
    return matches[0]


def collection_profile(paths: list[Path]) -> str | None:
    """Use intersection, never majority voting or silently forced game IDs.

An offset1 version can coincidentally occur inside an offset2 label table.
Select a common profile only when EVERY supplied script matches its marker.
Mixed or unknown collections fall back to per-file identification.
"""
    common = None
    for path in paths:
        try:
            candidates = {p[0]["id"] for p in candidate_profiles(path.read_bytes())}
        except FormatError:
            return None
        common = candidates if common is None else common & candidates
        if not common:
            return None
    return next(iter(common)) if common and len(common) == 1 else None


def instructions(data: bytes, profile: dict, start: int):
    pos = start
    binding = _profile_contracts().get(profile["id"], {})
    decoder = binding.get("decoder", {})
    extension_owners = frozenset(decoder.get("extension_owners", ()))
    string_owners = frozenset(decoder.get("string_payload_owners", ()))
    sum_opcodes = frozenset(int(opcode, 16) for opcode, operation in binding.get("opcodes", {}).items()
                            if operation == "variable.sum_list")
    while pos < len(data):
        opcode = data[pos]
        kind = next((kind for kind, begin, end in profile["ranges"]
                     if begin <= opcode <= end), None)
        if decoder.get("signed_opcode_fetch", False) and opcode >= 0x80:
            # MOVSX makes the framing comparison negative; the later unsigned
            # dispatch bound takes the native default. Keep the raw opcode.
            kind = "u8x2"
        if kind is None:
            raise FormatError(f"unknown framing at 0x{pos:X}, opcode=0x{opcode:02X}")
        if kind == "u8x2":
            end = pos + 3
        elif kind == "u16x4":
            end = pos + 9
        else:
            end = data.find(b"\0", pos + (2 if kind == "u8str" else 1))
            if end == -1:
                raise FormatError(f"unterminated string at 0x{pos:X}")
            end += 1
        if opcode == 0 and decoder.get("allow_terminal_fragment", False) and len(data)-pos < 3:
            end = len(data)
        if end > len(data):
            raise FormatError(f"truncated {kind} instruction at 0x{pos:X}")
        yield Instruction(pos, opcode, kind, data[pos:end])
        owner = pos
        pos = end
        if opcode in sum_opcodes:
            companion = binding["control"]["companion_opcode"]
            while pos < len(data) and data[pos] != companion:
                end = pos + 9
                if end > len(data):
                    raise FormatError(f"truncated sum term at 0x{pos:X}, owner=0x{owner:X}")
                yield Instruction(pos, data[pos], "u16x4", data[pos:end], consumed_by=owner)
                pos = end
        elif opcode in extension_owners:
            end = pos + 9
            if end > len(data):
                raise FormatError(f"truncated handler extension at 0x{pos:X}, owner=0x{owner:X}")
            # This byte is skipped, not dispatched or used to choose a layout.
            yield Instruction(pos, data[pos], "u16x4", data[pos:end], consumed_by=owner)
            pos = end
        elif opcode in string_owners:
            if pos >= len(data):
                raise FormatError(f"missing handler string at 0x{pos:X}, owner=0x{owner:X}")
            end = data.find(b"\0", pos + 1)
            if end == -1:
                raise FormatError(f"unterminated handler string at 0x{pos:X}, owner=0x{owner:X}")
            end += 1
            yield Instruction(pos, data[pos], "string", data[pos:end], consumed_by=owner)
            pos = end
        if profile["id"] == "dcpc-2063" and opcode == 0:
            break
        if profile["id"] == "dc2pc-5769" and opcode == 0 and data[pos:] == b"ad":
            break


def parse(data: bytes, profile_id: str | None = None) -> tuple[dict, list[Instruction]]:
    profile, version_offset, version_length = identify(data, profile_id)
    tokens = list(instructions(data, profile, version_offset + version_length))
    if not tokens:
        raise FormatError("Empty MES code")
    count = struct.unpack_from("<I", data)[0]
    # Preserve label words and ID table verbatim. Their runtime meaning must be
    # checked per version before treating them as jump targets.
    raw_labels = list(struct.unpack_from(f"<{count}I", data, 4)) if count else []
    marker = int.from_bytes(data[version_offset:version_offset + min(version_length, 2)], "little")
    metadata = {"profile": profile["id"], "version": f"0x{marker:04X}",
                "layout": profile["layout"], "header_count": count,
                "version_offset": version_offset, "code_offset": version_offset + version_length,
                "version_record_hex": data[version_offset:version_offset + version_length].hex(),
                "raw_label_words": raw_labels,
                "raw_label_ids_hex": data[4 + count * 4:version_offset].hex(),
                "instruction_count": len(tokens),
                "terminal_fragments": [{"offset": token.offset, "raw_hex": token.raw.hex(),
                                        "kind": "stop_operands_absent"}
                                       for token in tokens if token.consumed_by is None and token.opcode == 0 and len(token.raw) < 3],
                "opcodes": {f"0x{k:02X}": v for k, v in sorted(Counter(t.opcode for t in tokens).items())},
                "semantic_status": "not_implemented"}
    return metadata, tokens
