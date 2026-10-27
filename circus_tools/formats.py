"""Metadata-only inspection. No media playback or image rendering."""
from __future__ import annotations

import struct

from .common import FormatError


def metadata(head: bytes, size: int) -> dict:
    magic = head[:4]
    result = {"signature_hex": magic.hex(), "size": size}
    if magic == b"CRXG":
        if len(head) < 20:
            raise FormatError("short CRXG header")
        x, y, width, height, comp, flags, depth, mode = struct.unpack_from("<hhHHHHhH", head, 4)
        if not width or not height or comp not in (1, 2, 3):
            raise FormatError("invalid CRXG geometry/compression")
        result.update(format="CRXG", x=x, y=y, width=width, height=height,
                      compression=comp, flags=flags, depth_field=depth, mode=mode,
                      bits_per_pixel=24 if depth == 0 else 32 if depth == 1 else 8,
                      pixels_decoded=False)
    elif magic == b"CRXD":
        if len(head) < 36:
            raise FormatError("short CRXD header")
        result.update(format="CRXD", base_offset=struct.unpack_from("<I", head, 8)[0],
                      base_name=head[12:32].split(b"\0", 1)[0].decode("cp932", errors="replace"),
                      diff_magic=head[32:36].decode("ascii", errors="replace"), pixels_decoded=False)
        if head[32:36] == b"CRXJ":
            if len(head) < 44:
                raise FormatError("short CRXJ reference")
            result["diff_offset"] = struct.unpack_from("<I", head, 40)[0]
        elif head[32:36] == b"CRXG":
            result["diff_image"] = metadata(head[32:], size - 32)
    elif magic == b"XPCM":
        if len(head) < 16:
            raise FormatError("short XPCM header")
        decoded_size, mode_word = struct.unpack_from("<II", head, 4)
        mode = mode_word & 255
        result.update(format="XPCM", mode=mode, extra=(mode_word >> 8) & 255,
                      decoded_bytes=decoded_size, audio_decoded=False)
        if mode == 5:
            packed_size = struct.unpack_from("<I", head, 12)[0]
            if packed_size > size - 16 or head[16:20] != b"OggS":
                raise FormatError("invalid XPCM Ogg payload")
            result.update(payload_offset=16, payload_bytes=packed_size, codec="ogg")
        elif mode in (0, 1, 3):
            if len(head) < 28:
                raise FormatError("short XPCM WAVEFORMAT")
            tag, channels, hz, avg, align, bits = struct.unpack_from("<HHIIHH", head, 12)
            if not channels or not hz or not align:
                raise FormatError("invalid XPCM audio format")
            result.update(wave_format_tag=tag, channels=channels, sample_rate=hz,
                          bits_per_sample=bits, block_align=align, average_bytes_per_second=avg)
            if mode == 0:
                if decoded_size > size - 28:
                    raise FormatError("truncated uncompressed XPCM")
            else:
                if len(head) < 32:
                    raise FormatError("short compressed XPCM")
                packed_size = struct.unpack_from("<I", head, 28)[0]
                if packed_size > size - 32:
                    raise FormatError("truncated compressed XPCM")
                result["payload_bytes"] = packed_size
        else:
            result["unsupported_mode"] = True
    elif magic == b"CRXB":
        result["format"] = "CRM/CRXB"
    else:
        result["format"] = "unclassified"
    return result
