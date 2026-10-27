"""Bounds checks, hashing and output isolation shared by all readers."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import BinaryIO


class FormatError(ValueError):
    pass


def read_at(stream: BinaryIO, offset: int, size: int) -> bytes:
    if offset < 0 or size < 0 or size > 128 * 1024 * 1024:
        raise FormatError(f"invalid read range: {offset}+{size}")
    stream.seek(offset)
    data = stream.read(size)
    if len(data) != size:
        raise FormatError(f"short read at 0x{offset:X}: wanted {size}, got {len(data)}")
    return data


def check_range(offset: int, size: int, total: int, minimum: int = 0) -> None:
    if offset < minimum or size < 0 or offset > total or size > total - offset:
        raise FormatError(f"out-of-bounds span: {offset}+{size}, file={total}, min={minimum}")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def isolated_output(output: Path, source: Path) -> Path:
    output, source = output.resolve(), source.resolve()
    if output == source or source in output.parents or output in source.parents:
        raise ValueError("output and source trees must not overlap")
    output.mkdir(parents=True, exist_ok=True)
    return output


def protected_input_tree(file: Path) -> Path:
    """Protect the enclosing game, not just the immediate MES/GRP directory."""
    file = file.resolve()
    for parent in file.parents:
        if parent.name.casefold() == "advdata":
            return parent.parent
    if file.parent.name.casefold() in {"mes", "grp"}:
        return file.parent.parent
    return file.parent


def child_output(root: Path, relative: str | Path) -> Path:
    root = root.resolve()
    result = (root / relative).resolve()
    if result == root or root not in result.parents:
        raise ValueError(f"output path escapes destination: {relative}")
    result.parent.mkdir(parents=True, exist_ok=True)
    return result


def write_json(path: Path, value: object) -> None:
    # Callers must pass a path returned by child_output.
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
