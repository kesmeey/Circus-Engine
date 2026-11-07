"""Counterexamples for native sample discovery and evidence classification."""
import hashlib
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

from tools.audit_native_corpus import audit, discover, evidence_catalog, inspect_executable, inspect_game, main


def fixture(version=0xA5A8, code=b'\0\0\0'):
    return struct.pack('<IH', 0, version) + b'\0' + code


class NativeCorpusTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def game(self, name, scripts):
        directory = self.root / name / 'AdvData/MES'
        directory.mkdir(parents=True)
        for filename, raw in scripts.items():
            (directory / filename).write_bytes(raw)
        return directory.parent.parent

    def test_unity_and_savedata_are_not_native_installations(self):
        native = self.game('native', {'START.mes': fixture()})
        self.game('saves', {'not-a-game.mes': fixture()})
        unity = self.game('unity', {'unrelated.mes': fixture()})
        (unity / 'DC3_Data/Managed').mkdir(parents=True)
        (self.root / 'save-copy/AdvData').mkdir(parents=True)
        result = discover(self.root)
        self.assertEqual([g['root'] for g in result['games']], [native])
        self.assertEqual({r['reason'] for r in result['skipped']},
                         {'unity_installation_excluded', 'advdata_without_mes_scripts'})

    def test_synthetic_hook_hosts_are_excluded_and_selection_is_explicit(self):
        native = self.game('native', {'START.mes': fixture()})
        self.game('other', {'START.mes': fixture(0x9D72)})
        synthetic = self.game('hook', {'START.mes': fixture()})
        (synthetic / 'hook_smoke.exe').write_bytes(b'test')
        result = audit(self.root, game_roots=[native])
        self.assertEqual([g['root'] for g in result['games']], [str(native)])
        self.assertEqual(result['selection'], 'explicit_game_directories')
        self.assertIn('synthetic_test_host_excluded', {s['reason'] for s in result['skipped']})
        self.assertIn(str(synthetic), result['protected_game_directories'])
        with self.assertRaisesRegex(ValueError, 'not discovered or were excluded'):
            audit(self.root, game_roots=[synthetic])

    def test_mixed_versions_never_receive_a_majority_profile(self):
        self.game('mixed', {'START.mes': fixture(), 'a.mes': fixture(),
                            'other.mes': fixture(0x9D72)})
        game = audit(self.root)['games'][0]
        self.assertIsNone(game['collection_profile'])
        self.assertEqual(game['framing_parsed_scripts'], 3)
        self.assertFalse(game['operation_binding_present'])
        self.assertFalse(game['complete_engine_fidelity_verified'])

    def test_format_only_profile_is_not_a_runtime_compatibility_claim(self):
        # KTLEP has a known format but no executable semantics in this project.
        self.game('format-only', {'START.mes': struct.pack('<IH', 0, 0x6E69) + b'\0\0\0'})
        game = audit(self.root)['games'][0]
        self.assertEqual(game['collection_profile'], 'ktlep-6e69')
        self.assertEqual(game['framing_parsed_scripts'], 1)
        self.assertFalse(game['operation_binding_present'])
        self.assertFalse(game['complete_engine_fidelity_verified'])

    def test_unreadable_executable_stays_unidentified(self):
        path = self.root / 'blocked.exe'
        path.write_bytes(b'original')
        evidence = {'native': {'expected_native_sha256': [hashlib.sha256(b'original').hexdigest()]}}
        with patch.object(Path, 'open', side_effect=OSError(22, 'blocked by system')):
            result = inspect_executable(path, evidence)
        self.assertEqual(result['read_status'], 'unreadable')
        self.assertEqual(result['matching_evidence_profiles'], [])
        self.assertNotIn('sha256', result)

    def test_evidence_requires_exact_hash_not_filename(self):
        path = self.root / 'DC3DD.EXE'
        path.write_bytes(b'patched')
        evidence = {'native': {'expected_native_sha256': [hashlib.sha256(b'original').hexdigest()]}}
        self.assertEqual(inspect_executable(path, evidence)['matching_evidence_profiles'], [])
        path.write_bytes(b'original')
        self.assertEqual(inspect_executable(path, evidence)['matching_evidence_profiles'], ['native'])

    def test_translated_identity_keeps_its_limited_rule_scope(self):
        path = self.root / 'translated.exe'
        path.write_bytes(b'translated')
        digest = hashlib.sha256(b'translated').hexdigest()
        evidence = {'native': {'expected_native_sha256': [digest],
                              'native_identity_evidence': [{'sha256': digest,
                                  'role':'translated_executable', 'verified_rules':['signed fetch'],
                                  'scope':'decoder only'}]}}
        result = inspect_executable(path, evidence)
        self.assertEqual(result['matching_evidence_profiles'], ['native'])
        matched = result['matching_native_evidence'][0]
        self.assertEqual(matched['role'], 'translated_executable')
        self.assertEqual(matched['verified_rules'], ['signed fetch'])
        self.assertEqual(matched['scope'], 'decoder only')

    def test_scripts_only_never_reprobes_executables(self):
        game = self.game('native', {'START.mes': fixture()})
        (game / 'original.exe').write_bytes(b'original')
        with patch('tools.audit_native_corpus.inspect_executable', side_effect=AssertionError('EXE re-probed')):
            report = audit(self.root, probe_executables=False)
        self.assertFalse(report['native_executable_probing'])
        entry = report['games'][0]
        self.assertEqual(entry['native_identity_status'], 'not_revalidated_scripts_only')
        self.assertEqual(entry['native_executables'][0]['read_status'], 'not_probed_scripts_only')

    def test_renamed_original_executable_is_included(self):
        root = self.game('patched', {'START.mes': fixture()})
        (root / 'game.OLD').write_bytes(b'original')
        (root / 'game.exe').write_bytes(b'patched')
        game = audit(self.root)['games'][0]
        self.assertEqual({Path(e['path']).name for e in game['native_executables']},
                         {'game.OLD', 'game.exe'})

    def test_signed_fetch_default_is_distinct_from_an_unknown_opcode(self):
        self.game('signed', {'START.mes': struct.pack('<IH', 0, 0x5769) + b'\xbf\x01\x02\0\0\0'})
        source = discover(self.root)['games'][0]
        evidence = {'dc2pc-5769': {'native_signed_opcode_fetch': True,
                                 'operation_bindings': {'0x00': 'stream.stop'}}}
        game = inspect_game(source, evidence)
        self.assertEqual(game['framing_parsed_scripts'], 1)
        self.assertEqual(game['native_default_physical_opcodes'], {'0xBF': 1})
        self.assertEqual(game['unbound_physical_opcodes'], {})

    def test_structural_alt_stamp_does_not_grant_runtime_admission(self):
        self.game('alt', {'START.mes': struct.pack('<IH', 0, 0x3363) + b'\0\0\0',
                          'other.mes': struct.pack('<IH', 0, 0x1A63) + b'\0\0\0'})
        source = discover(self.root)['games'][0]
        evidence = {'dc2-3363': {'runtime_exact_version': 0x3363,
                               'operation_bindings': {'0x00': 'stream.stop'}}}
        game = inspect_game(source, evidence)
        scripts = {Path(s['path']).name: s for s in game['scripts']}
        self.assertTrue(scripts['START.mes']['structural_valid'])
        self.assertTrue(scripts['START.mes']['runtime_marker_accepted'])
        self.assertTrue(scripts['other.mes']['structural_valid'])
        self.assertFalse(scripts['other.mes']['runtime_marker_accepted'])
        self.assertEqual(game['runtime_marker_rejected_scripts'], ['AdvData/MES/other.mes'])

    def test_owned_raw_string_is_counted_by_owner_not_payload_marker(self):
        #25 consumes any marker, including STOP, before scanning the NUL string.
        self.game('pc1', {'START.mes': struct.pack('<IH', 0, 0x2063) +
                         bytes([0x25, 2, 0, 0, ord('x'), 0, 0, 0, 0])})
        game = inspect_game(discover(self.root)['games'][0], {})
        self.assertEqual(game['framing_parsed_scripts'], 1)
        self.assertEqual(game['payload_owner_opcode_counts'], {'0x25': 1})
        self.assertEqual(game['scripts'][0]['payload_owner_opcode_counts'], {'0x25': 1})

    def test_duplicate_scripts_do_not_imply_identical_executables(self):
        a = self.game('a', {'START.mes': fixture()})
        b = self.game('b', {'START.mes': fixture()})
        (a / 'game.exe').write_bytes(b'one')
        (b / 'game.exe').write_bytes(b'two')
        originals = {p: p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        games = audit(self.root)['games']
        self.assertEqual(games[1]['duplicate_script_collection_of'], str(a))
        self.assertNotEqual(games[0]['native_executables'][0]['sha256'],
                            games[1]['native_executables'][0]['sha256'])
        self.assertEqual(originals, {p: p.read_bytes() for p in originals})

    def test_output_cannot_overwrite_original_game(self):
        game = self.game('original', {'START.mes': fixture()})
        target = game / 'report.json'
        with patch.object(sys, 'argv', ['audit_native_corpus.py', str(self.root), '--out', str(target)]):
            with self.assertRaises(SystemExit) as raised:
                main()
        self.assertEqual(raised.exception.code, 2)
        self.assertFalse(target.exists())

    def test_specification_keeps_partial_and_unknown_statuses(self):
        record = evidence_catalog()['dc3dd-a5a8']
        self.assertGreater(record['specification_opcode_statuses']['partial_binding'], 0)
        self.assertGreater(record['specification_opcode_statuses']['unknown'], 0)
        self.assertFalse(record['complete_engine_fidelity_verified'])


if __name__ == '__main__':
    unittest.main()
