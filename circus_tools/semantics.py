"""Static semantic annotations and potential edges; no VM or game execution.

Physical framing stays lossless in mes.py. This layer records compound operands
and possible control transfers without evaluating variables or following paths.
Unknown opcodes remain unknown; a native switch default is NOT a generic NOP.
"""
from __future__ import annotations

from collections import Counter
import hashlib
import json
from pathlib import Path
import struct

from .common import FormatError, child_output, isolated_output, protected_input_tree, write_json
from .mes import parse, profiles
from .state_schema import state_profile, variable_accesses

CATALOG = Path(__file__).resolve().parent.parent / "profiles" / "mes-semantics.json"


def catalog():
    return json.loads(CATALOG.read_text(encoding="utf-8"))


def signed8(value):
    return value - 256 if value & 128 else value


def native_string_bytes(raw, semantic):
    mapping = semantic["decoder"].get("encoded_string_postmap")
    if mapping is None:
        raise FormatError("native string postprocessing is not verified for this profile")
    transformed = ((v + semantic["decoder"]["encoded_string_key"]) & 255 for v in raw)
    return bytes(mapping.get(str(v), v) for v in transformed)


def scan_block(data, start, marker, semantic, framing):
    """Bounded reproduction of the native *static byte scan*, not a VM.

WY sign-extends the opcode in this helper, DD zero-extends it. Keep that
observable difference even where it produces a target off framing boundaries.
"""
    pos = start
    u8str = next(a for kind, a, _ in framing["ranges"] if kind == "u8str")
    string = next(a for kind, a, _ in framing["ranges"] if kind == "string")
    words = next(a for kind, a, _ in framing["ranges"] if kind == "u16x4")
    while 0 <= pos < len(data):
        op = signed8(data[pos]) if semantic["control"]["block_scan_signed_opcode"] else data[pos]
        if op == semantic["control"]["block_marker_opcode"] and pos + 3 <= len(data):
            if signed8(data[pos + 2]) == marker:
                return pos + 3, None
        if op == 0:
            return None, "end_before_block_marker"
        if op < u8str:
            pos += 3
        elif op < words:
            end = data.find(b"\0", pos + (2 if op < string else 1))
            if end < 0:
                return None, "unterminated_string_in_block_scan"
            pos = end + 1
        else:
            pos += 9
    return None, "block_scan_out_of_bounds"


def scan_required_label(data, start, name, semantic, framing):
    """DC2DM 424280: signed-opcode search, never searches past opcode 00."""
    if not name:
        return None
    starts={kind:a for kind,a,_ in framing['ranges']}
    pos=start
    while pos<len(data):
        op=signed8(data[pos])
        if op==0:
            return None
        if op==semantic['control']['label_opcode']:
            end=data.find(b'\0',pos+1)
            if end<0:return None
            if data[pos+1:end]==name:return pos
        if op<starts['u8str']:
            pos+=3
        elif op<starts['u16x4']:
            end=data.find(b'\0',pos+(2 if op<starts['string'] else 1))
            if end<0:return None
            pos=end+1
        else:
            pos+=9
    return None


def analyze(data: bytes, profile_id: str):
    cat = catalog()
    if profile_id not in cat["profiles"]:
        raise FormatError(f"no verified semantic subset for {profile_id}")
    sem = cat["profiles"][profile_id]
    state = state_profile(profile_id)
    if state and (state['profile'] != profile_id or state['native_sha256'] != sem['native_sha256']):
        raise FormatError('state contract does not match the native semantic profile')
    framing = next(p for p in profiles() if p["id"] == profile_id)
    header, tokens = parse(data, profile_id)
    ops = sem["opcodes"]
    by_offset = {t.offset: i for i, t in enumerate(tokens)}
    boundaries = set(by_offset)
    labels = {}
    issues, annotations, edges = [], [], []
    deferred_ui = []
    access_counts = Counter()
    counts, physical_counts = Counter(), Counter(t.opcode for t in tokens)
    for t in tokens:
        if t.opcode == sem["control"]["label_opcode"]:
            labels.setdefault(t.raw[1:-1], []).append(t.offset)

    def issue(kind, offset, **extra):
        issues.append({"kind": kind, "offset": offset, **extra})

    def edge(source, target, kind, **extra):
        edges.append({"source": source, "target": target, "kind": kind, **extra})
        if target is not None and target not in boundaries:
            issue("target_not_instruction_boundary", source, target=target, edge_kind=kind)

    def block_edge(source, start, marker, kind):
        target, error = scan_block(data, start, marker, sem, framing)
        edge(source, target, kind, block_id=marker, scanner="native")
        if error:
            issue(error, source, scan_start=start, block_id=marker)

    for name, locations in labels.items():
        if len(locations) > 1:
            issue("duplicate_named_label_first_wins", locations[0], name_hex=name.hex(), occurrences=locations)

    # The high bit is used by native checkpoint bookkeeping. The low 31-bit
    # offset interpretation is validated here, not promoted to a jump rule.
    header_checks = Counter()
    for word in header["raw_label_words"]:
        location = header["version_offset"] + (word & 0x7FFFFFFF)
        header_checks["entries"] += 1
        header_checks["high_bit_set"] += bool(word & 0x80000000)
        header_checks["instruction_boundaries"] += location in boundaries
        if location not in boundaries:
            issue("header_offset_candidate_not_boundary", location, raw_word=word)

    i = 0
    while i < len(tokens):
        t = tokens[i]
        op = f"0x{t.opcode:02X}"
        operation = ops.get(op, "unknown")
        if operation == "unknown":
            counts["unknown"] += 1
        else:
            counts[operation] += 1
        record = {"offset": t.offset, "pc": t.offset - header["version_offset"],
                  "opcode": op, "operation": operation,
                  "status": cat["operations"].get(operation, {}).get("status", "static_verified" if operation != "unknown" else "unknown")}
        p = list(struct.unpack("<4h", t.raw[1:])) if t.kind == "u16x4" else list(t.raw[1:]) if t.kind == "u8x2" else None
        if p is not None:
            record["operands"] = p
        elif operation != "unknown":
            record["string_bytes_hex"] = t.raw[1:-1].hex()
        j = i + 1
        next_offset = t.offset + len(t.raw)
        # The native fetcher consumes at most ONE extension, not an arbitrary chain.
        if t.opcode != 0 and j < len(tokens) and tokens[j].opcode == sem["decoder"]["extension_opcode"]:
            ext = tokens[j]
            record["extension"] = {"offset": ext.offset, "operands": list(struct.unpack("<4h", ext.raw[1:]))}
            counts["arguments.extend_absorbed"] += 1
            next_offset = ext.offset + len(ext.raw)
            j += 1
        if t.kind == "encoded_string" and sem["decoder"].get("encoded_string_postmap") is not None:
            record["native_string_bytes_hex"] = native_string_bytes(t.raw[1:-1], sem).hex()
        if operation == "branch.compare":
            mode = p[3] & 127 if p[3] & 128 else p[3]
            record["condition"] = {"left": {"bank": p[0], "index": p[1]}, "comparison": {0:"eq",1:"ne",2:"gt",3:"lt",4:"ge",5:"le"}.get(mode, "unknown"),
                                   "right": {"bank": p[2] >> 8, "index": p[2] & 255} if p[3] & 128 else {"literal": p[2]}}
            if mode not in range(6):
                issue("unknown_comparison_mode", t.offset, mode=mode)
            if j >= len(tokens) or tokens[j].opcode != sem["control"]["companion_opcode"]:
                issue("if_missing_02_companion", t.offset)
            else:
                companion = tokens[j]
                record["companion_offset"] = companion.offset
                record["block_id"] = signed8(companion.raw[1])
                next_offset = companion.offset + 3
                j += 1
                edge(t.offset, next_offset, "if_true")
                block_edge(t.offset, next_offset, record["block_id"], "if_false")
                counts["if_companions"] += 1
        elif operation in ("block.marker","block.marker_unsigned"):
            if p[0] == 0:
                block_edge(t.offset, next_offset, p[1], "skip_block")
            else:
                edge(t.offset, next_offset, "block_fallthrough")
        elif operation in ("jump.named","jump.named_required"):
            name = t.raw[1:-1]
            required=operation=="jump.named_required"
            target = scan_required_label(data,header['code_offset'],name,sem,framing) if required else labels.get(name, [None])[0] if name else None
            record["resolution"] = "first_exact_label" if target is not None else "native_error" if required else "empty_ignored" if not name else "missing_ignored"
            edge(t.offset, target if target is not None else None if required else next_offset, "named_jump" if target is not None else "jump_error" if required else "jump_ignored", mode_gates_not_evaluated=not required)
            if target is None and (name or operation == "jump.named_required"):
                issue("missing_named_label_native_error" if operation == "jump.named_required" else "missing_named_label_native_ignores", t.offset, name_hex=name.hex())
        elif operation == "variable.sum_list":
            terms = []
            while j < len(tokens) and tokens[j].opcode != sem["control"]["companion_opcode"]:
                term = tokens[j]
                if len(term.raw) != 9 or term.kind != "u16x4":
                    issue("sum_term_not_nine_bytes", t.offset, term_offset=term.offset)
                    break
                args = struct.unpack("<4h", term.raw[1:])
                terms.append({"offset": term.offset, "bank": args[0], "index": args[1], "raw_opcode": term.opcode})
                j += 1
            record["terms"] = terms
            counts["sum_terms_absorbed"] += len(terms)
            if j < len(tokens) and tokens[j].opcode == sem["control"]["companion_opcode"]:
                record["terminator_offset"] = tokens[j].offset
                next_offset = tokens[j].offset  # left unconsumed by native handler
            else:
                issue("sum_missing_02_terminator", t.offset)
        elif operation == "variable.update" and p[3] not in (0, 1, 2):
            issue("variable_update_mode_ignored", t.offset, mode=p[3])
        elif operation == "variable.update_indirect" and p[3] not in (0, 1, 2, 3):
            issue("variable_update_mode_ignored", t.offset, mode=p[3])
        elif operation == "dd.grid.text" and p[3] != 99:
            if j < len(tokens) and tokens[j].kind in ("string", "encoded_string"):
                payload = tokens[j]
                record["string_payload"] = {"offset": payload.offset, "raw_opcode": payload.opcode,
                                            "bytes_hex": payload.raw[1:-1].hex()}
                next_offset = payload.offset + len(payload.raw)
                counts["list_text_payloads_absorbed"] += 1
                j += 1
            else:
                issue("list_text_payload_missing", t.offset)
        elif operation in ("grid.item_action", "ui.music_end_action"):
            name = t.raw[1:-1]
            target = labels.get(name, [None])[0] if name else None
            binding = {"source": t.offset, "action_bytes_hex": name.hex(), "target": target,
                       "trigger": "list_item_activation" if operation == "grid.item_action" else "music_end_during_menu_wait",
                       "resolution": "local_label_candidate" if target is not None else "cleared" if not name else "unresolved_action"}
            deferred_ui.append(binding)
            record["ui_binding"] = binding
        elif operation == "host.command_with_string":
            if j < len(tokens) and tokens[j].kind in ("string", "encoded_string"):
                payload = tokens[j]
                record["string_payload"] = {"offset": payload.offset, "raw_opcode": payload.opcode,
                                            "bytes_hex": payload.raw[1:-1].hex()}
                next_offset = payload.offset + len(payload.raw)
                counts["host_string_payloads_absorbed"] += 1
                j += 1
                if state and 'ui_binding' in state:
                    name = payload.raw[1:-1]
                    target = labels.get(name, [None])[0] if name else None
                    binding = {'source': t.offset, 'rectangle': p,
                               'action_bytes_hex': name.hex(), 'target': target,
                               'resolution': 'local_label_candidate' if target is not None else 'unresolved_action' if name else 'empty_action',
                               'trigger': 'ui_activation', 'scope': 'main_ui_route_only_context_not_evaluated'}
                    deferred_ui.append(binding)
                    record['ui_binding'] = binding
            else:
                issue("host_string_payload_unrecognized", t.offset)
        elif state and operation in ('script.transfer', 'script.transfer_alt'):
            record['transition'] = {'mode': 0 if operation == 'script.transfer' else 1,
                                    'initial_pc': state['script_transition']['first_pc'],
                                    'applies_then_clears_pending_label': True,
                                    'status': 'partial_callee_effects_and_load_errors_unresolved'}
        accesses = variable_accesses(record, profile_id, sem)
        if accesses:
            record['variable_accesses'] = accesses
            for access in accesses:
                key = (access['bank'], access['index'], access['direction'], access['kind'])
                access_counts[key] += 1
                if access['kind'] == 'unsafe_native_index':
                    issue('unsafe_variable_index', t.offset, bank=access['bank'], index=access['index'])
        if operation == "stream.stop":
            record["native_next_offset"] = t.offset
            if j < len(tokens):
                issue("physical_records_after_stop", t.offset, count=len(tokens)-j)
        else:
            record["native_next_offset"] = next_offset
        if operation != "unknown" or "extension" in record:
            annotations.append(record)
        i = j

    return {"profile": profile_id, "sha256": hashlib.sha256(data).hexdigest(),
            "scope": "static subset; edges are potential, not a reachable/executed trace",
            "version_offset": header["version_offset"], "physical_instructions": len(tokens),
            "physical_opcodes": {f"0x{k:02X}": v for k, v in sorted(physical_counts.items())},
            "semantic_counts": dict(counts), "header_checks": dict(header_checks),
            "variable_access_counts": [{'bank': b, 'index': n, 'direction': d, 'kind': k, 'count': c}
                                       for (b,n,d,k),c in sorted(access_counts.items())],
            "deferred_ui_bindings": deferred_ui,
            "annotations": annotations, "edges": edges, "issues": issues}


def analyze_source(source: Path, output: Path, profile_id: str):
    source = source.resolve()
    paths = [source] if source.is_file() else sorted(source.glob("*.mes"))
    if not paths:
        raise FormatError("no MES files found (directory scan is non-recursive)")
    out = isolated_output(output, protected_input_tree(paths[0]))
    totals, physical, issues, headers = Counter(), Counter(), Counter(), Counter()
    accesses, ui = Counter(), Counter()
    files, failures = [], []
    for number, path in enumerate(paths):
        data = path.read_bytes()
        try:
            result = analyze(data, profile_id)
        except FormatError as error:
            failures.append({"path": str(path), "sha256": hashlib.sha256(data).hexdigest(), "error": str(error)})
            continue
        filename = f"scripts/{number:04d}-{path.stem}.json"
        write_json(child_output(out, filename), result)
        totals.update(result["semantic_counts"])
        physical.update(result["physical_opcodes"])
        issues.update(i["kind"] for i in result["issues"])
        headers.update(result["header_checks"])
        for row in result['variable_access_counts']:
            accesses[(row['bank'], row['index'], row['direction'], row['kind'])] += row['count']
        ui.update(row['resolution'] for row in result['deferred_ui_bindings'])
        files.append({"source": str(path), "sha256": result["sha256"], "report": filename, "issues": len(result["issues"])})
    result = {"profile": profile_id, "source": str(source), "files_analyzed": len(files),
              "physical_instructions": sum(physical.values()), "physical_opcodes": dict(physical),
              "semantic_counts": dict(totals), "header_checks": dict(headers),
              "issue_counts": dict(issues), "failures": failures, "files": files,
              "variable_access_counts": [{'bank': b, 'index': n, 'direction': d, 'kind': k, 'count': c}
                                         for (b,n,d,k),c in sorted(accesses.items())],
              "deferred_ui_counts": dict(ui),
              "execution_performed": False}
    write_json(child_output(out, "summary.json"), result)
    return result
