"""Synthetic counterexamples for static semantics; no game code is executed."""
import struct
import unittest

from circus_tools.common import FormatError
from circus_tools.semantics import analyze, catalog, native_string_bytes
from circus_tools.state_schema import describe_access, state_profile


def fixture(code, version=0xA5A8):
    return struct.pack('<IH', 0, version) + b'\0' + code


def words(op, *args):
    return bytes([op]) + struct.pack('<4h', *args)


class Dc2SemanticsTests(unittest.TestCase):
    def test_dd_list_caption_and_deferred_callback(self):
        code = words(0x7A, 135, 0, 0, 99) + words(0x7A, 4, -2, 0, 0)
        code += b'\x58caption\0\x54picked\0\x5cpicked\0\x47picked\0\0\0\0'
        result = analyze(fixture(code), 'dc3dd-a5a8')
        self.assertEqual(result['semantic_counts']['list_text_payloads_absorbed'], 1)
        self.assertEqual([b['trigger'] for b in result['deferred_ui_bindings']],
                         ['list_item_activation', 'music_end_during_menu_wait'])
        self.assertTrue(all(b['target'] is not None for b in result['deferred_ui_bindings']))
        self.assertFalse(any(e['kind'] == 'named_jump' for e in result['edges']))

    def test_indirect_mode_and_memory_views(self):
        result=analyze(fixture(words(0x62,300,2,-1,0)+words(0x62,0,4,100,3)+b'\0\0\0',0x9d72),'dc2dm-9d72')
        first,second=result['annotations'][:2]
        self.assertEqual(first['variable_accesses'][0]['kind'],'packed_view')
        self.assertEqual(first['variable_accesses'][0]['byte_order'],'big')
        self.assertEqual(second['indirect_read'],{'bank':100,'index_from':{'bank':0,'index':4},'result_register':'99:255'})
        self.assertFalse(any(i['kind']=='variable_update_mode_ignored' for i in result['issues']))

    def test_no_dd_text_postmap(self):
        self.assertEqual(native_string_bytes(b'\x04\x05\xff',catalog()['profiles']['dc2dm-9d72']),b'$%\x1f')

    def test_ordinary_and_persistent_write_rules_differ(self):
        s=catalog()['profiles']['dc2dm-9d72']
        self.assertEqual(describe_access('dc2dm-9d72',s,1,0,'write')['rule'],'clamp_0_255')
        self.assertEqual(describe_access('dc2dm-9d72',s,100,0,'write')['rule'],'low_8_bits_mirror_10_if_index_le_20')
        self.assertEqual(describe_access('dc2dm-9d72',s,10,21,'write')['rule'],'write_ignored')

    def test_required_jump_does_not_search_past_stop(self):
        result=analyze(fixture(b'\x3d:a\0\0\0\0\x34:a\0\0\0\0',0x9d72),'dc2dm-9d72')
        self.assertEqual(result['annotations'][0]['resolution'],'native_error')
        self.assertEqual(result['edges'][0]['kind'],'jump_error')
        self.assertIsNone(result['edges'][0]['target'])


class SemanticTests(unittest.TestCase):
    def test_signed_words_and_byte_storage_are_distinct(self):
        result = analyze(fixture(words(0x7C, 0, 1, -1, 0) + b'\0\0\0'), 'dc3dd-a5a8')
        self.assertEqual(result['annotations'][0]['operands'], [0, 1, -1, 0])
        self.assertEqual(result['annotations'][-1]['native_next_offset'], 16)

    def test_conditional_and_else_use_block_id_not_nesting(self):
        code = words(0x80, 0, 1, 7, 0) + b'\x02\x01\x00'
        code += b'\x1f\x01\x02' + b'\x1f\x00\x01' + b'\x1f\x01\x01' + b'\0\0\0'
        result = analyze(fixture(code), 'dc3dd-a5a8')
        self.assertEqual([(e['kind'], e['target']) for e in result['edges'][:2]], [('if_true', 19), ('if_false', 25)])
        self.assertEqual(next(e['target'] for e in result['edges'] if e['kind'] == 'skip_block'), 28)
        self.assertEqual(result['issues'], [])

    def test_extension_absorbed_only_once_and_pcs_remain_physical(self):
        code = words(0x6D, 1, 2, 3, 4) + words(0x71, -1, 2, 3, 4) + words(0x71, 5, 6, 7, 8) + b'\0\0\0'
        result = analyze(fixture(code), 'dc3dd-a5a8')
        self.assertEqual(result['annotations'][0]['extension']['operands'][0], -1)
        self.assertEqual(result['annotations'][0]['native_next_offset'], 25)
        self.assertEqual(result['semantic_counts']['arguments.extend'], 1)

    def test_sum_consumes_records_without_recursing(self):
        code = words(0x7E, 0, 1, 0, 0) + words(0x7E, 2, 3, 0, 0) + words(0x7E, 4, 5, 0, 0) + b'\x02\0\0\0\0\0'
        result = analyze(fixture(code), 'dc3dd-a5a8')
        self.assertEqual(result['semantic_counts']['variable.sum_list'], 1)
        self.assertEqual(result['semantic_counts']['sum_terms_absorbed'], 2)
        self.assertEqual(result['annotations'][0]['terminator_offset'], 34)

    def test_missing_companion_is_not_silently_accepted(self):
        result = analyze(fixture(words(0x80, 0, 1, 0, 0) + b'\0\0\0'), 'dc3dd-a5a8')
        self.assertIn('if_missing_02_companion', [i['kind'] for i in result['issues']])

    def test_named_jump_is_byte_exact_first_match_and_missing_ignored(self):
        code = b'\x50abc\0\x47abc\0\x47abc\0\x50ABC\0\0\0\0'
        result = analyze(fixture(code), 'dc3dd-a5a8')
        self.assertEqual(result['edges'][0]['target'], 12)
        self.assertEqual(result['edges'][1]['kind'], 'jump_ignored')
        self.assertIn('duplicate_named_label_first_wins', [i['kind'] for i in result['issues']])

    def test_native_string_postmap_dollar_to_space(self):
        self.assertEqual(native_string_bytes(b'\x04\x05\xff', catalog()['profiles']['dc3dd-a5a8']), b' %\x1f')

    def test_ui_payload_is_not_misclassified_as_jump(self):
        code = words(0x6C, 0, 0, 100, 100) + b'\x50:button\0\0\0\0'
        result = analyze(fixture(code), 'dc3dd-a5a8')
        self.assertEqual(result['semantic_counts']['host_string_payloads_absorbed'], 1)
        self.assertNotIn('jump.named', result['semantic_counts'])
        self.assertEqual(result['edges'], [])
        self.assertEqual(result['issues'], [])
        self.assertEqual(result['deferred_ui_bindings'][0]['resolution'], 'unresolved_action')

    def test_ui_local_target_is_deferred_not_an_immediate_jump(self):
        code = words(0x6C, 1, 2, 3, 4) + b'\x50target\0\x47target\0\0\0\0'
        result = analyze(fixture(code), 'dc3dd-a5a8')
        self.assertEqual(result['edges'], [])
        binding = result['deferred_ui_bindings'][0]
        self.assertEqual(binding['rectangle'], [1, 2, 3, 4])
        self.assertEqual(binding['target'], 24)
        self.assertEqual(binding['resolution'], 'local_label_candidate')

    def test_special_write_is_not_byte_truncation_and_can_have_coupled_effect(self):
        code = words(0x7C, 99, 254, 255, 0) + words(0x7C, 99, 10, 2, 0) + b'\0\0\0'
        result = analyze(fixture(code), 'dc3dd-a5a8')
        first, second = [a['variable_accesses'][0] for a in result['annotations'][:2]]
        self.assertEqual(first['rule']['transform'], '255_to_minus_one_otherwise_identity')
        self.assertEqual(second['rule']['also_write']['index'], 214)

    def test_readonly_and_noop_specials_are_distinguished_from_unknown_versions(self):
        dd = catalog()['profiles']['dc3dd-a5a8']
        self.assertEqual(describe_access('dc3dd-a5a8', dd, 99, 234, 'read')['rule']['kind'], 'state_i32')
        self.assertEqual(describe_access('dc3dd-a5a8', dd, 99, 234, 'write')['rule']['kind'], 'ignored')
        self.assertEqual(describe_access('dc3dd-a5a8', dd, 99, 242, 'read')['rule']['value'], 0)
        self.assertEqual(describe_access('dc3wy-a09f', catalog()['profiles']['dc3wy-a09f'], 99, 254, 'write')['kind'], 'unmapped')
        self.assertIsNone(state_profile('dc3pp-9872'))

    def test_negative_ordinary_index_is_reported_without_memory_access(self):
        result = analyze(fixture(words(0x7C, 0, -1, 1, 0) + b'\0\0\0'), 'dc3dd-a5a8')
        self.assertEqual(result['issues'][0]['kind'], 'unsafe_variable_index')

    def test_copy_add_and_packed_comparison_accesses(self):
        code = words(0x7D, 0, 1, 99, 245) + words(0x7C, 1, 2, 3, 1)
        code += words(0x80, 0, 1, (99 << 8) | 245, 128) + b'\x02\x01\0\x1f\x01\x01\0\0\0'
        result = analyze(fixture(code), 'dc3dd-a5a8')
        access = [a['variable_accesses'] for a in result['annotations'] if 'variable_accesses' in a]
        self.assertEqual([(a['bank'], a['index'], a['direction']) for a in access[0]], [(99,245,'read'),(0,1,'write')])
        self.assertEqual([a['direction'] for a in access[1]], ['read','write'])
        self.assertEqual(access[2][1]['index'], 245)

    def test_both_transfer_modes_apply_pending_label(self):
        result = analyze(fixture(b'\x4bnext\0\x4cother\0\0\0\0'), 'dc3dd-a5a8')
        transitions = [a['transition'] for a in result['annotations'][:2]]
        self.assertEqual([t['mode'] for t in transitions], [0,1])
        self.assertTrue(all(t['applies_then_clears_pending_label'] for t in transitions))

    def test_wy_signed_opcode_scan_is_not_replaced_with_dd_scan(self):
        # 0x85 is a nine-byte record to the decoder but a negative byte to WY's
        # block scanner, which skips 3 and sees a zero before the real end marker.
        code = words(0x7C, 0, 1, 0, 0) + b'\x02\x01\0' + words(0x85, 0, 0, 0, 0) + b'\x1e\x01\x01\0\0\0'
        result = analyze(fixture(code, 0xA09F), 'dc3wy-a09f')
        self.assertIsNone(result['edges'][1]['target'])
        self.assertIn('end_before_block_marker', [i['kind'] for i in result['issues']])

    def test_unverified_versions_fail_closed(self):
        with self.assertRaises(FormatError):
            analyze(fixture(b'\0\0\0'), 'dc3pp-9872')


if __name__ == '__main__':
    unittest.main()
