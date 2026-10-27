"""Reproducible static inventories, extraction manifests and bytecode statistics."""
from __future__ import annotations

from collections import Counter
from dataclasses import asdict
from datetime import datetime, timezone
import json
from pathlib import Path

from .archives import entry_destination, inspect_or_extract, read_archive
from .common import FormatError, child_output, isolated_output, sha256_file, write_json
from .formats import metadata
from .mes import candidate_profiles, collection_profile, parse


def _counter(counter: Counter) -> dict:
    return dict(sorted(counter.items()))


def scan(source: Path, output: Path, extract: bool = False) -> dict:
    source = source.resolve()
    if not source.is_dir():
        raise ValueError("input must be a directory")
    output = isolated_output(output, source)
    data_root = source / "AdvData" if (source / "AdvData").is_dir() else source
    files = sorted((p for p in data_root.rglob("*") if p.is_file()
                    and not any(s.casefold() in {"savedata", "backups", ".git"}
                                for s in p.relative_to(data_root).parts)), key=lambda p: str(p).casefold())
    for path in files:
        if data_root not in path.resolve().parents:
            raise ValueError(f"input symlink escapes data root: {path}")
    mes_names = {p.stem.casefold(): p.relative_to(data_root).as_posix()
                 for p in files if p.suffix.casefold() == ".mes"}
    common_profile = collection_profile([p for p in files if p.suffix.casefold() == ".mes"])
    inventory, scripts, archives, errors, references, links = [], [], [], [], [], []
    formats, audio_modes, image_modes, versions, opcode_counts = (Counter() for _ in range(5))
    extension_counts, entry_extensions = Counter(), Counter()
    entry_count = extraction_bytes = 0
    entries_path = child_output(output, "archive-entries.jsonl")
    with entries_path.open("w", encoding="utf-8", newline="\n") as entries_file:
        for file_index, path in enumerate(files):
            relative = path.relative_to(data_root).as_posix()
            stat = path.stat()
            item = {"path": relative, "size": stat.st_size, "mtime_ns": stat.st_mtime_ns,
                    "sha256": sha256_file(path)}
            inventory.append(item)
            extension_counts[path.suffix.casefold()] += 1
            if file_index % 200 == 0:
                print(f"[{source.name}] static scan {file_index}/{len(files)}", flush=True)
            try:
                suffix = path.suffix.casefold()
                if suffix == ".mes":
                    data = path.read_bytes()
                    info, tokens = parse(data, common_profile)
                    info["header_candidates"] = [p[0]["id"] for p in candidate_profiles(data)]
                    versions[info["profile"]] += 1
                    opcode_counts.update(info["opcodes"])
                    scripts.append({"path": relative, **{k: v for k, v in info.items()
                                   if k not in {"raw_label_words", "raw_label_ids_hex"}}})
                    for token in tokens:
                        if token.kind not in {"string", "u8str"}:
                            continue
                        raw = token.raw[2:-1] if token.kind == "u8str" else token.raw[1:-1]
                        try:
                            candidate = raw.decode("ascii").replace("\\", "/").casefold()
                        except UnicodeDecodeError:
                            continue
                        if candidate.endswith(".mes"):
                            candidate = candidate[:-4]
                        if candidate in mes_names:
                            references.append({"source": relative, "offset": token.offset,
                                "opcode": f"0x{token.opcode:02X}", "target": mes_names[candidate],
                                "status": "literal_match_only_not_proven_control_flow"})
                elif suffix in {".pck", ".crm"} or path.name.casefold() == "voice.dat":
                    archive = read_archive(path)
                    destination = child_output(output, Path("unpacked") / relative) if extract else None
                    if destination:
                        destination.mkdir(exist_ok=True)
                    offsets = {e.offset for e in archive.entries}
                    print(f"[{source.name}] {archive.kind} {relative}: {len(archive.entries)} entries", flush=True)
                    for entry in archive.entries:
                        digest, prefix = inspect_or_extract(path, entry, destination)
                        meta = metadata(prefix, entry.size)
                        record = {"archive": relative, "archive_kind": archive.kind,
                                  **asdict(entry), "sha256": digest, "metadata": meta}
                        if destination:
                            record["extracted_path"] = entry_destination(destination, entry).relative_to(output).as_posix()
                            extraction_bytes += entry.size
                        entries_file.write(json.dumps(record, ensure_ascii=False) + "\n")
                        entry_count += 1
                        entry_extensions[Path(entry.name).suffix.casefold()] += 1
                        formats[meta["format"]] += 1
                        if meta["format"] == "XPCM":
                            audio_modes[str(meta["mode"])] += 1
                        elif meta["format"] == "CRXG":
                            image_modes[str(meta["compression"])] += 1
                        elif meta["format"] == "CRXD":
                            link = {"archive": relative, "entry_index": entry.index,
                                    "base_offset": meta["base_offset"],
                                    "base_name": meta["base_name"],
                                    "base_offset_in_archive": meta["base_offset"] in offsets}
                            if "diff_offset" in meta:
                                link["diff_offset"] = meta["diff_offset"]
                                link["diff_offset_in_archive"] = meta["diff_offset"] in offsets
                            links.append(link)
                    archives.append({"path": relative, "format": archive.kind,
                                     "entry_count": len(archive.entries), **archive.metadata})
                elif suffix in {".crx", ".pcm"}:
                    with path.open("rb") as stream:
                        meta = metadata(stream.read(128), stat.st_size)
                    item["metadata"] = meta
                    formats[meta["format"]] += 1
                    if meta["format"] == "XPCM":
                        audio_modes[str(meta["mode"])] += 1
                    elif meta["format"] == "CRXG":
                        image_modes[str(meta["compression"])] += 1
                elif path.name.casefold() == "names.dat":
                    item["classification"] = "resource_name_catalog_not_a_PCK_archive_not_yet_parsed"
            except (FormatError, OSError, ValueError) as error:
                errors.append({"path": relative, "error": str(error)})
                print(f"[{source.name}] ERROR {relative}: {error}", flush=True)
    patch_info = {}
    config = source / "uif_config.json"
    if config.is_file():
        try:
            settings = json.loads(config.read_text(encoding="utf-8-sig"))
            mapping = settings.get("character_substitution", {})
            a, b = mapping.get("source_characters", ""), mapping.get("target_characters", "")
            patch_info = {"config_sha256": sha256_file(config), "enabled": mapping.get("enable", False),
                          "source_length": len(a), "target_length": len(b),
                          "equal_length": len(a) == len(b),
                          "duplicate_source_characters": len(a) - len(set(a)),
                          "status": "detected_only_not_applied"}
            if len(a) != len(b):
                errors.append({"path": "uif_config.json", "error": "character mapping length mismatch"})
        except (ValueError, OSError) as error:
            errors.append({"path": "uif_config.json", "error": str(error)})
    summary = {
        "schema_version": 1, "created_utc": datetime.now(timezone.utc).isoformat(),
        "source": str(source), "data_root": str(data_root), "output": str(output),
        "mode": "static_only_no_game_execution", "extract": extract,
        "source_file_count": len(files), "source_bytes": sum(i["size"] for i in inventory),
        "extensions": _counter(extension_counts), "script_files_parsed": len(scripts),
        "collection_profile": common_profile,
        "ambiguous_headers_resolved_by_collection": sum(len(s["header_candidates"]) > 1 for s in scripts),
        "profiles": _counter(versions), "instruction_count": sum(opcode_counts.values()),
        "distinct_opcodes": len(opcode_counts), "opcodes": _counter(opcode_counts),
        "archive_count": len(archives), "archive_entries": entry_count,
        "extracted_bytes": extraction_bytes, "entry_extensions": _counter(entry_extensions),
        "resource_formats": _counter(formats), "xpcm_modes": _counter(audio_modes),
        "crx_compression_modes": _counter(image_modes),
        "crm_diff_links": len(links),
        "crm_unresolved_base_offsets": sum(not x["base_offset_in_archive"] for x in links),
        "crm_unresolved_diff_offsets": sum(not x.get("diff_offset_in_archive", True) for x in links),
        "candidate_script_references": len(references), "translation_mapping": patch_info,
        "errors": errors, "exclusions": ["SaveData", "backups", ".git"],
        "limitations": ["No opcode semantics implemented", "No control-flow edges proven",
                        "Image pixels and audio not decoded", "NAMES.DAT is analyzed separately with the names command",
                        "Only catalogued MES versions are recognized", "No game or runtime executed"],
    }
    for filename, content in [("inventory.json", inventory), ("scripts.json", scripts),
                              ("archives.json", archives), ("candidate-script-references.json", references),
                              ("crm-links.json", links), ("summary.json", summary)]:
        write_json(child_output(output, filename), content)
    print(json.dumps({k: summary[k] for k in ["source", "script_files_parsed", "archive_count",
          "archive_entries", "extracted_bytes", "errors"]}, ensure_ascii=False), flush=True)
    return summary


def verify(output: Path) -> dict:
    output = output.resolve()
    summary = json.loads((output / "summary.json").read_text(encoding="utf-8"))
    root = Path(summary["data_root"]).resolve()
    errors, source_count, extracted_count = [], 0, 0
    for record in json.loads((output / "inventory.json").read_text(encoding="utf-8")):
        path = (root / record["path"]).resolve()
        if root not in path.parents:
            raise ValueError("invalid source path in manifest")
        source_count += 1
        if not path.is_file() or sha256_file(path) != record["sha256"]:
            errors.append({"path": record["path"], "error": "source content changed"})
    config_digest = summary.get("translation_mapping", {}).get("config_sha256")
    if config_digest:
        config = Path(summary["source"]) / "uif_config.json"
        if not config.is_file() or sha256_file(config) != config_digest:
            errors.append({"path": "uif_config.json", "error": "source content changed"})
    with (output / "archive-entries.jsonl").open(encoding="utf-8") as stream:
        for line in stream:
            record = json.loads(line)
            if "extracted_path" not in record:
                continue
            path = (output / record["extracted_path"]).resolve()
            if output not in path.parents:
                raise ValueError("invalid extracted path in manifest")
            extracted_count += 1
            if not path.is_file() or sha256_file(path) != record["sha256"]:
                errors.append({"path": record["extracted_path"], "error": "extracted content changed"})
    if summary["extract"] and extracted_count != summary["archive_entries"]:
        errors.append({"error": "extracted count differs from manifest"})
    result = {"source_files_verified": source_count, "extracted_files_verified": extracted_count,
              "scan_error_count": len(summary["errors"]), "errors": errors,
              "passed": not errors and not summary["errors"]}
    write_json(child_output(output, "verification.json"), result)
    return result
