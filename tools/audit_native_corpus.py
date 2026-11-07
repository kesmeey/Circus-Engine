"""Inventory native CIRCUS samples without running games or claiming compatibility.

Discovery excludes Unity installations and does not follow directory links. Every
MES is hashed and framed independently of opcode bindings. Native hashes are
compared to the project's evidence catalog; a binding is not proof of a complete
host implementation. Reports belong outside all discovered game directories.
"""
from __future__ import annotations

import argparse
from collections import Counter, deque
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from circus_tools.common import FormatError
from circus_tools.mes import candidate_profiles, parse


SKIP_DIRS = {'.git', '.agents', 'node_modules', '__pycache__', 'build',
             'analysis', 'savedata', 'saves', 'backups'}


def _under(path: Path, root: Path) -> bool:
    return path == root or root in path.parents


def _read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding='utf-8-sig'))


def discover(root: Path, max_depth: int = 4, excluded: tuple[Path, ...] = ()) -> dict:
    """Find installations by data layout, never by an assumed game title."""
    root = root.resolve()
    excluded = tuple(p.resolve() for p in excluded)
    queue = deque([(root, 0)])
    games, skipped, errors = [], [], []
    while queue:
        directory, depth = queue.popleft()
        try:
            children = sorted(directory.iterdir(), key=lambda p: p.name.casefold())
            subdirs = [p for p in children if p.is_dir()]
            unity = [p for p in subdirs if p.name.casefold().endswith('_data')
                     and (p / 'Managed').is_dir()]
            if unity:
                skipped.append({'path': str(directory), 'reason': 'unity_installation_excluded'})
                continue
            adv = next((p for p in subdirs if p.name.casefold() == 'advdata'), None)
            if adv is not None:
                if any(p.name.casefold() == 'hook_smoke.exe' for p in children):
                    skipped.append({'path': str(directory), 'reason': 'synthetic_test_host_excluded'})
                    continue
                mes = next((p for p in adv.iterdir() if p.is_dir()
                            and p.name.casefold() == 'mes'), None)
                scripts = sorted((p for p in mes.iterdir() if p.is_file()
                                  and p.suffix.casefold() == '.mes'),
                                 key=lambda p: p.name.casefold()) if mes else []
                if scripts:
                    if any(p.is_symlink() or not _under(p.resolve(), directory.resolve())
                           for p in [adv, mes, *scripts]):
                        skipped.append({'path': str(directory), 'reason': 'linked_game_data_excluded'})
                    else:
                        games.append({'root': directory, 'mes': mes, 'scripts': scripts})
                else:
                    skipped.append({'path': str(directory), 'reason': 'advdata_without_mes_scripts'})
                continue
            if depth >= max_depth:
                continue
            for path in subdirs:
                if path.is_symlink() or not _under(path.resolve(), root):
                    skipped.append({'path': str(path), 'reason': 'directory_link_excluded'})
                elif path.name.casefold() in SKIP_DIRS or any(_under(path.resolve(), p) for p in excluded):
                    continue
                else:
                    queue.append((path, depth + 1))
        except OSError as error:
            errors.append({'path': str(directory), 'error': str(error), 'errno': error.errno})
    return {'games': games, 'skipped': skipped, 'errors': errors}


def evidence_catalog(project: Path = ROOT) -> dict:
    """Index claims and their provenance without treating reports as verification."""
    semantics_path = project / 'profiles/mes-semantics.json'
    spec_path = project / 'profiles/vm-specification.json'
    semantics = _read_json(semantics_path)['profiles']
    specification = _read_json(spec_path)['profiles']
    versions = {p['id']: p['version'] for p in _read_json(project / 'profiles/dc123.json')['profiles']}
    records = {}
    for pid in sorted(set(semantics) | set(specification)):
        sem, spec = semantics.get(pid, {}), specification.get(pid, {})
        exact_version = sem.get('decoder', {}).get('runtime_exact_version')
        if exact_version is True:
            exact_version = versions[pid]
        elif exact_version is False:
            exact_version = None
        identities = []
        for field, value in (('mes-semantics/native_sha256', sem.get('native_sha256')),
                             ('vm-specification/exe_sha256', spec.get('exe_sha256'))):
            if value:
                identities.append({'sha256': value, 'role': 'historical_native_reference',
                                   'source': field, 'verified_rules': [],
                                   'scope': 'Historical identity; rule coverage remains in the referenced catalog and is not revalidated by this inventory.'})
        for variant in sem.get('native_variants', []):
            identities.append({**variant, 'source': 'mes-semantics/native_variants'})
        digests = sorted({entry['sha256'] for entry in identities})
        records[pid] = {
            'expected_native_sha256': digests,
            'native_identity_evidence': identities,
            'operation_bindings': sem.get('opcodes', {}),
            'binding_count': len(sem.get('opcodes', {})),
            'native_signed_opcode_fetch': sem.get('decoder', {}).get('signed_opcode_fetch', False),
            'runtime_exact_version': exact_version,
            'catalog_evidence_status': sem.get('evidence_status'),
            'catalog_host_status': sem.get('host_status'),
            'specification_dispatch_count': spec.get('dispatch_count'),
            'specification_opcode_statuses': dict(Counter(v.get('semantic_status', 'unspecified')
                                                         for v in spec.get('opcodes', []))),
            'static_manifests': [],
            'complete_engine_fidelity_verified': False,
        }
    for dirname in ('analysis/evidence', 'analysis/native'):
        for path in sorted((project / dirname).rglob('manifest.json')):
            try:
                value = _read_json(path)
            except (OSError, ValueError):
                continue
            digest = value.get('source_sha256')
            for pid, entry in records.items():
                if value.get('profile') == pid or digest in entry['expected_native_sha256']:
                    entry['static_manifests'].append({
                        'path': str(path.relative_to(project)),
                        'source_sha256': digest,
                        'recorded_execution_performed': value.get('execution_performed'),
                        'status': 'existing_evidence_not_revalidated_by_this_inventory',
                    })
    return records


def inspect_executable(path: Path, evidence: dict) -> dict:
    result = {'path': str(path)}
    digest = hashlib.sha256()
    try:
        result['size'] = path.stat().st_size
        with path.open('rb') as stream:
            prefix = stream.read(4096)
            digest.update(prefix)
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
        result.update(read_status='readable', sha256=digest.hexdigest())
        pe = struct.unpack_from('<I', prefix, 0x3c)[0] if len(prefix) >= 0x40 and prefix[:2] == b'MZ' else -1
        if 0 <= pe <= len(prefix) - 24 and prefix[pe:pe + 4] == b'PE\0\0':
            result['pe_machine'] = f'0x{struct.unpack_from("<H", prefix, pe + 4)[0]:04X}'
            result['pe_header_status'] = 'valid'
        else:
            result['pe_header_status'] = 'not_pe_or_header_beyond_initial_4096_bytes'
        result['matching_evidence_profiles'] = [pid for pid, record in evidence.items()
                                                if result['sha256'] in record['expected_native_sha256']]
        result['matching_native_evidence'] = [
            {'profile': pid, **identity} for pid, record in evidence.items()
            for identity in record.get('native_identity_evidence', [])
            if result['sha256'] == identity['sha256']]
    except OSError as error:
        result.update(read_status='unreadable', error=str(error), errno=error.errno,
                      matching_evidence_profiles=[], hash_status='unavailable')
    return result


def inspect_game(game: dict, evidence: dict, probe_executables: bool = True) -> dict:
    root, scripts = game['root'], game['scripts']
    records, common = [], None
    collection_hash = hashlib.sha256()
    for path in scripts:
        item = {'path': path.relative_to(root).as_posix()}
        try:
            data = path.read_bytes()
            sha = hashlib.sha256(data).hexdigest()
            item.update(read_status='readable', size=len(data), sha256=sha)
            collection_hash.update(path.name.casefold().encode('utf-8') + b'\0' + bytes.fromhex(sha))
            matches = candidate_profiles(data)
            candidates = {p[0]['id'] for p in matches}
            item['profile_candidates'] = sorted(candidates)
            item['header_marker_candidates'] = [
                {'profile': p['id'], 'offset': offset, 'raw_hex': data[offset:offset + length].hex()}
                for p, offset, length in matches]
            common = candidates if common is None else common & candidates
        except (OSError, FormatError) as error:
            item.update(read_status='unreadable' if isinstance(error, OSError) else 'invalid_header', error=str(error))
            common = set()
        records.append(item)
    pid = next(iter(common)) if common and len(common) == 1 else None
    sem = evidence.get(pid, {})
    exact_runtime_version = sem.get('runtime_exact_version')
    if isinstance(exact_runtime_version, str):
        exact_runtime_version = int(exact_runtime_version, 0)
    opcode_counts, payload_owner_counts, parse_errors, terminal_fragments = Counter(), Counter(), [], []
    for path, item in zip(scripts, records):
        item['structural_valid'] = False
        if item['read_status'] != 'readable':
            parse_errors.append({'path': item['path'], 'stage': 'read_or_header', 'error': item['error']})
            continue
        try:
            raw = path.read_bytes()
            info, tokens = parse(raw, pid)
            layout = hashlib.sha256()
            owner_counts = Counter()
            for token in tokens:
                owner = getattr(token, 'consumed_by', None)
                if owner is not None:
                    owner_counts[f'0x{raw[owner]:02X}'] += 1
                layout.update(struct.pack('<IIBi', token.offset, token.offset + len(token.raw),
                                          token.opcode, owner if owner is not None else -1))
            fragments = info.get('terminal_fragments', [])
            item.update(framing_status='parsed', profile=info['profile'],
                        version=info['version'], instruction_count=info['instruction_count'],
                        record_layout_sha256=layout.hexdigest(),
                        record_layout_hash_schema='offset_u32,end_u32,opcode_u8,payload_owner_i32_le',
                        structural_valid=True,
                        payload_owner_opcode_counts=dict(sorted(owner_counts.items())),
                        runtime_marker_accepted=(int(info['version'], 16) == exact_runtime_version
                                                 if exact_runtime_version is not None else None),
                        terminal_fragments=fragments)
            terminal_fragments.extend({'path': item['path'], **fragment} for fragment in fragments)
            opcode_counts.update(info['opcodes'])
            payload_owner_counts.update(owner_counts)
        except (OSError, FormatError) as error:
            item.update(framing_status='failed', error=str(error))
            parse_errors.append({'path': item['path'], 'stage': 'framing', 'error': str(error)})
    bindings = sem.get('operation_bindings', {})
    native_defaults = {op: count for op, count in sorted(opcode_counts.items())
                       if sem.get('native_signed_opcode_fetch') and int(op, 16) >= 0x80}
    # Unknown instances include physical payloads which a preceding operation may
    # consume. Do not mislabel this count as executable unsupported instructions.
    unbound = {op: count for op, count in sorted(opcode_counts.items())
               if bindings.get(op, 'unknown') == 'unknown' and op not in native_defaults}
    executables = []
    for path in sorted(root.iterdir(), key=lambda p: p.name.casefold()):
        # Some original executables were renamed to .OLD by a game patch. Their
        # presence and PE signature must be inspected separately from .exe files.
        if path.is_file() and path.suffix.casefold() in {'.exe', '.old', '.bak'}:
            if path.is_symlink() or not _under(path.resolve(), root.resolve()):
                executables.append({'path': str(path), 'read_status': 'linked_executable_excluded'})
            elif not probe_executables:
                executables.append({'path': str(path), 'read_status': 'not_probed_scripts_only'})
            else:
                executables.append(inspect_executable(path, evidence))
    matches = [entry['path'] for entry in executables
               if pid in entry.get('matching_evidence_profiles', [])]
    return {
        'root': str(root), 'script_directory': str(game['mes']),
        'sample_classification': 'native_mes_data_sample',
        'script_count': len(scripts), 'script_collection_sha256': collection_hash.hexdigest(),
        'collection_profile': pid, 'common_profile_candidates': sorted(common or []),
        'observed_header_markers': dict(sorted(Counter(
            f"{marker['profile']}:{marker['raw_hex']}" for item in records
            for marker in item.get('header_marker_candidates', [])).items())),
        'framing_parsed_scripts': sum(item.get('framing_status') == 'parsed' for item in records),
        'physical_record_count': sum(opcode_counts.values()),
        'physical_opcode_counts': dict(sorted(opcode_counts.items())),
        'payload_owner_opcode_counts': dict(sorted(payload_owner_counts.items())),
        'terminal_fragment_count': len(terminal_fragments),
        'terminal_fragments': terminal_fragments,
        'native_default_physical_opcodes': native_defaults,
        'operation_binding_present': bool(bindings),
        'binding_count_in_catalog': sem.get('binding_count', 0),
        'runtime_exact_version': exact_runtime_version,
        'runtime_marker_rejected_scripts': [item['path'] for item in records
                                           if item.get('runtime_marker_accepted') is False],
        'unbound_physical_opcodes': unbound, 'framing_errors': parse_errors,
        'native_executables': executables, 'matching_native_evidence_files': matches,
        'native_identity_status': 'not_revalidated_scripts_only' if not probe_executables else
                                  'verified_hash_match' if matches else
                                  'no_readable_matching_original' if sem.get('expected_native_sha256') else
                                  'no_expected_native_hash_in_catalog',
        'complete_engine_fidelity_verified': False,
        'scripts': records,
    }


def audit(root: Path, max_depth: int = 4, project: Path = ROOT,
          game_roots: list[Path] | None = None, probe_executables: bool = True) -> dict:
    root, project = root.resolve(), project.resolve()
    discovered = discover(root, max_depth, excluded=(project,))
    selected = {p.resolve() for p in game_roots} if game_roots is not None else None
    available = {g['root'].resolve() for g in discovered['games']}
    if selected is not None and selected - available:
        raise ValueError('selected game directories were not discovered or were excluded: ' +
                         ', '.join(str(p) for p in sorted(selected - available)))
    evidence = evidence_catalog(project)
    games = []
    collections = {}
    for game in discovered['games']:
        if selected is not None and game['root'].resolve() not in selected:
            continue
        record = inspect_game(game, evidence, probe_executables)
        key = record['script_collection_sha256']
        # A broken collection cannot establish that two installations are equal.
        if all(s.get('read_status') == 'readable' for s in record['scripts']):
            if key in collections:
                record['duplicate_script_collection_of'] = collections[key]
            else:
                collections[key] = record['root']
        games.append(record)
    return {
        'schema_version': 1, 'created_utc': datetime.now(timezone.utc).isoformat(),
        'mode': 'static_inventory_no_game_execution', 'root': str(root),
        'max_discovery_depth': max_depth, 'unity_excluded': True,
        'native_executable_probing': probe_executables,
        'selection': 'explicit_game_directories' if selected is not None else 'all_discovered_native_samples',
        'selected_game_directories': sorted(str(p) for p in selected) if selected is not None else None,
        'protected_game_directories': sorted({str(g['root'].resolve()) for g in discovered['games']} |
                                             {str(Path(s['path']).resolve()) for s in discovered['skipped']
                                              if s['reason'] in {'unity_installation_excluded',
                                                                 'advdata_without_mes_scripts',
                                                                 'synthetic_test_host_excluded'}}),
        'games': games, 'skipped': discovered['skipped'], 'discovery_errors': discovered['errors'],
        'evidence_catalog': {pid: {k: v for k, v in entry.items() if k != 'operation_bindings'}
                             for pid, entry in evidence.items()},
        'limitations': [
            'Framing success and opcode bindings do not establish host semantics or game compatibility.',
            'Unbound physical records may be payloads consumed by earlier instructions.',
            'Hashes verify identity only; existing native manifests are indexed, not revalidated.',
            'Runtime marker admission is reported only for explicit decoder contracts; it does not verify opcode or host semantics.',
            'A matching script collection does not establish identical executables, assets, or patches.',
            'No original executable was started; no game data or security settings were modified.',
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', type=Path, help='Directory containing the original game installations')
    parser.add_argument('--out', type=Path, default=ROOT / 'build/native-rewrite/corpus.json')
    parser.add_argument('--max-depth', type=int, default=4)
    parser.add_argument('--game', type=Path, action='append',
                        help='Audit only this explicitly selected game directory; repeat for multiple games')
    parser.add_argument('--scripts-only', action='store_true',
                        help='Do not read executable contents or re-probe previously blocked binaries')
    args = parser.parse_args()
    if args.max_depth < 0 or args.max_depth > 12:
        parser.error('--max-depth must be between 0 and 12')
    if not args.root.is_dir():
        parser.error('root must be a directory')
    try:
        report = audit(args.root, args.max_depth, game_roots=args.game,
                       probe_executables=not args.scripts_only)
    except ValueError as error:
        parser.error(str(error))
    output = args.out.resolve()
    if any(_under(output, Path(path)) for path in report['protected_game_directories']):
        parser.error('output must be outside all original game directories')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'output': str(output), 'samples': len(report['games']),
                      'native_executable_probing': report['native_executable_probing'],
                      'scripts': sum(game['script_count'] for game in report['games']),
                      'framing_failures': sum(len(game['framing_errors']) for game in report['games']),
                      'unreadable_executables': sum(exe.get('read_status') == 'unreadable'
                                                   for game in report['games']
                                                   for exe in game['native_executables'])}, ensure_ascii=True))


if __name__ == '__main__':
    main()
