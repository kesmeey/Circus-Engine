"""Native handler-owned payloads are distinct from fetch-time extensions."""
import struct
import unittest

from circus_tools.common import FormatError
from circus_tools.semantics import analyze
from circus_tools.mes import parse
from circus_tools.state_schema import describe_access


VERSIONS = {'dcpc-2063': 0x2063, 'dc2pc-5769': 0x5769,
            'dc2-3363': 0x3363, 'dc3dd-a5a8': 0xA5A8, 'dc3pp-9872': 0x9872}


def fixture(profile, code):
    return struct.pack('<IH', 0, VERSIONS[profile]) + (b'\0' if profile in ('dc3dd-a5a8', 'dc3pp-9872') else b'') + code


def words(opcode, *args):
    return bytes([opcode]) + struct.pack('<4h', *args)


class HandlerSemanticsTests(unittest.TestCase):
    def test_pc1_ui_producers_own_raw_nul_for_every_marker(self):
        for owner in (0x4f, 0x51, 0x57):
            for marker in range(256):
                with self.subTest(owner=owner, marker=marker):
                    raw = b'A\xe0B'
                    data = fixture('dcpc-2063', words(owner, 1, 2, 3, 4) + bytes([marker]) + raw + b'\0\0\0\0')
                    _, tokens = parse(data, 'dcpc-2063')
                    self.assertEqual(len(tokens), 3)
                    self.assertEqual((tokens[1].offset, tokens[1].consumed_by, tokens[1].raw),
                                     (15, 6, bytes([marker]) + raw + b'\0'))
                    report = analyze(data, 'dcpc-2063')
                    self.assertEqual(report['annotations'][0]['string_payload']['bytes_hex'], raw.hex())
                    self.assertEqual(report['annotations'][0]['native_next_offset'], 20)

    def test_pc1_system_call_owns_raw_string_for_every_companion_marker(self):
        for marker in range(256):
            with self.subTest(marker=marker):
                data = fixture('dcpc-2063', b'\x26\0\0' + bytes([marker]) + b'advmenu\0\0\0\0')
                header, tokens = parse(data, 'dcpc-2063')
                self.assertEqual(len(tokens), 3)
                self.assertEqual((tokens[1].kind, tokens[1].consumed_by, tokens[1].raw),
                                 ('string', 6, bytes([marker]) + b'advmenu\0'))
                report = analyze(data, 'dcpc-2063')
                self.assertEqual(len(report['annotations']), 2)
                self.assertEqual(report['annotations'][0]['string_payload']['bytes_hex'], b'advmenu'.hex())
                self.assertEqual(report['annotations'][0]['native_next_offset'], 18)
                self.assertEqual(report['semantic_counts']['handler_string_payloads_absorbed'], 1)

    def test_empty_owned_string_is_not_a_terminal_fragment(self):
        header, tokens = parse(fixture('dcpc-2063', b'\x26\0\0\0\0\0\0\0'), 'dcpc-2063')
        self.assertEqual(tokens[1].raw, b'\0\0')
        self.assertEqual(tokens[1].to_dict()['consumed_by'], 6)
        self.assertNotIn('terminal_fragment', tokens[1].to_dict())
        self.assertEqual(header['terminal_fragments'], [])

    def test_missing_owned_string_is_rejected_independently_of_marker(self):
        for tail in (b'', b'\x46', b'\x46advmenu', b'\0advmenu', b'\xffadvmenu'):
            with self.subTest(tail=tail), self.assertRaises(FormatError):
                parse(fixture('dcpc-2063', b'\x26\0\0' + tail), 'dcpc-2063')

    def test_binding_without_review_status_is_not_reported_as_verified(self):
        report = analyze(fixture('dcpc-2063', b'\x03\0\0\0\0\0'), 'dcpc-2063')
        history = report['annotations'][0]
        self.assertEqual(history['operation'], 'pc.history')
        self.assertEqual(history['status'], 'bound_status_unspecified')

    def test_nonowner_does_not_swallow_a_following_extension(self):
        data = fixture('dcpc-2063', words(0x58, 0, 1, 42, 0) + words(0x52, 1, 2, 3, 4) + b'\0\0\0')
        report = analyze(data, 'dcpc-2063')
        self.assertEqual([a['operation'] for a in report['annotations']],
                         ['variable.update', 'arguments.extend', 'stream.stop'])
        first = report['annotations'][0]
        self.assertNotIn('extension', first)
        self.assertEqual(first['native_next_offset'], first['offset'] + 9)
        self.assertNotIn('arguments.extend_absorbed', report['semantic_counts'])

    def test_signed_default_and_independent_extension_both_remain_visible(self):
        # This instruction shape occurs in PC2's sce_other.mes. BF takes the
        # signed-fetch/unsigned-dispatch default; it owns no extension record.
        data = fixture('dc2pc-5769', b'\xbf\x01\x02' + words(0x52, 1, 2, 3, 4) + b'\0\0\0')
        report = analyze(data, 'dc2pc-5769')
        self.assertEqual([a['operation'] for a in report['annotations']],
                         ['native.no_effect', 'arguments.extend', 'stream.stop'])
        self.assertEqual(report['annotations'][0]['native_next_offset'], 9)
        self.assertNotIn('arguments.extend_absorbed', report['semantic_counts'])
        self.assertNotIn('unknown', report['semantic_counts'])

    def test_all_verified_owners_read_words_without_testing_the_payload_marker(self):
        owners = {'dcpc-2063': [0x50, 0x53, 0x54, 0x56],
                  'dc2pc-5769': [0x50, 0x53, 0x55, 0x57],
                  'dc2-3363': [0x4F, 0x52, 0x54, 0x56]}
        for profile, opcodes in owners.items():
            for opcode in opcodes:
                with self.subTest(profile=profile, opcode=opcode):
                    # 3B is a real named-jump opcode in PC1. It is raw payload
                    # here and must not create an independently executed jump.
                    data = fixture(profile, words(opcode, 1, 2, 3, 4) + words(0x3B, -5, 6, -7, 8) + b'\0\0\0')
                    report = analyze(data, profile)
                    first = report['annotations'][0]
                    self.assertEqual(first['extension']['operands'], [-5, 6, -7, 8])
                    self.assertEqual(first['extension']['raw_opcode'], 0x3B)
                    self.assertEqual(first['extension']['consumed_by'], first['offset'])
                    self.assertEqual(first['extension']['consumption'], 'handler_payload')
                    self.assertEqual(first['native_next_offset'], first['offset'] + 18)
                    self.assertEqual(report['semantic_counts']['handler_word_payloads_absorbed'], 1)
                    self.assertFalse(any(edge['source'] == first['offset'] + 9 for edge in report['edges']))

    def test_a_payload_byte_that_looks_like_an_owner_does_not_recursively_consume(self):
        data = fixture('dcpc-2063', words(0x50, 1, 2, 3, 4) + words(0x53, 5, 6, 7, 8) + b'\0\0\0')
        report = analyze(data, 'dcpc-2063')
        self.assertEqual(len(report['annotations']), 2)
        self.assertEqual(report['semantic_counts']['handler_word_payloads_absorbed'], 1)
        self.assertEqual(report['annotations'][-1]['operation'], 'stream.stop')

    def test_zero_payload_marker_is_not_an_independent_stop(self):
        data = fixture('dc2pc-5769', words(0x55, 1, 2, 3, 4) + words(0, 5, 6, 7, 8) + b'\0\0\0')
        report = analyze(data, 'dc2pc-5769')
        self.assertEqual(report['semantic_counts']['stream.stop'], 1)
        self.assertEqual(report['annotations'][0]['extension']['raw_opcode'], 0)
        self.assertEqual(report['annotations'][0]['extension']['operands'], [5, 6, 7, 8])

    def test_sum_terms_that_look_like_quad_and_stop_are_never_dispatched(self):
        data = fixture('dcpc-2063', words(0x5A, 0, 1, 0, 0) +
                       words(0x53, 1, 2, 0, 0) + words(0, 3, 4, 0, 0) +
                       words(0x32, 5, 6, 0, 0) + b'\x02\0\0\0\0\0')
        report = analyze(data, 'dcpc-2063')
        first = report['annotations'][0]
        self.assertEqual([t['raw_opcode'] for t in first['terms']], [0x53, 0, 0x32])
        self.assertEqual(report['semantic_counts']['sum_terms_absorbed'], 3)
        self.assertEqual(report['semantic_counts']['stream.stop'], 1)
        self.assertNotIn('handler_word_payloads_absorbed', report['semantic_counts'])
        self.assertNotIn('label.named', report['semantic_counts'])

    def test_global_extension_precedes_sum_term_collection(self):
        data = fixture('dc3dd-a5a8', words(0x7E, 0, 1, 0, 0) +
                       words(0x71, 1, 2, 0, 0) + words(0x74, 3, 4, 0, 0) + b'\x02\0\0\0\0\0')
        report = analyze(data, 'dc3dd-a5a8')
        first = report['annotations'][0]
        self.assertEqual(first['extension']['raw_opcode'], 0x71)
        self.assertEqual([term['raw_opcode'] for term in first['terms']], [0x74])
        self.assertEqual(report['semantic_counts']['arguments.extend_absorbed'], 1)
        self.assertEqual(report['semantic_counts']['sum_terms_absorbed'], 1)

    def test_missing_handler_payload_remains_a_format_error(self):
        data = fixture('dcpc-2063', words(0x50, 1, 2, 3, 4) + b'\x52\x01\x02')
        with self.assertRaises(FormatError):
            analyze(data, 'dcpc-2063')

    def test_newer_fetcher_still_absorbs_exactly_one_matching_extension(self):
        data = fixture('dc3dd-a5a8', words(0x7C, 0, 1, 42, 0) +
                       words(0x71, 1, 2, 3, 4) + words(0x71, 5, 6, 7, 8) + b'\0\0\0')
        report = analyze(data, 'dc3dd-a5a8')
        first = report['annotations'][0]
        self.assertEqual(first['extension']['consumption'], 'fetch_lookahead')
        self.assertEqual(first['extension']['operands'], [1, 2, 3, 4])
        self.assertEqual(report['semantic_counts']['arguments.extend_absorbed'], 1)
        self.assertEqual(report['semantic_counts']['arguments.extend'], 1)
        self.assertNotIn('handler_word_payloads_absorbed', report['semantic_counts'])

    def test_pc_models_describe_only_their_verified_ordinary_storage(self):
        for model in ('PC_BYTE', 'PC_WIDE'):
            semantic = {'runtime_variable_model': model}
            self.assertEqual(describe_access('pc', semantic, 9, 99, 'write')['rule'], 'clamp_0_255')
            self.assertEqual(describe_access('pc', semantic, 0, 100, 'read')['rule'], 'read_zero')
            self.assertEqual(describe_access('pc', semantic, 10, 0, 'read')['kind'], 'unmapped')
            self.assertEqual(describe_access('pc', semantic, 99, 0, 'write')['kind'], 'unmapped')
            self.assertEqual(describe_access('pc', semantic, 0, -1, 'read')['kind'], 'unsafe_native_index')
        self.assertEqual(describe_access('pc', {'runtime_variable_model':'PC_BYTE'}, 200, 0, 'read')['kind'], 'unmapped')
        view = describe_access('pc', {'runtime_variable_model':'PC_WIDE'}, 209, 96, 'write')
        self.assertEqual((view['width'], view['byte_order'], view['base_bank'], view['rule']),
                         (32, 'big', 9, 'store_i32_bytes'))
        self.assertEqual(describe_access('pc', {'runtime_variable_model':'PC_WIDE'}, 210, 0, 'read')['rule'], 'read_zero')

    def test_packed_memory_model_does_not_inherit_wrapping_ordinary_bytes(self):
        semantic = {'runtime_variable_model':'DC2_PACKED',
                    'variables':{'ordinary':{'banks':[0, 10], 'indices':[0, 99], 'write':'low_8_bits'}}}
        self.assertEqual(describe_access('pp', semantic, 0, 0, 'write')['rule'], 'clamp_0_255')
        self.assertEqual(describe_access('unknown', {}, 1, 2, 'read')['status'], 'native_schema_unavailable')

    def test_persistent_word_read_does_not_inherit_the_setter_boundary(self):
        semantic = {'runtime_variable_model':'DC2_PACKED'}
        read = describe_access('pp', semantic, 400, 100, 'read')
        self.assertEqual((read['kind'], read['width'], read['base_bank']), ('packed_view', 16, 100))
        self.assertIsNone(read['max_index'])
        self.assertEqual(read['read_bounds'], 'verified_contiguous_persistent_owner')
        self.assertEqual(describe_access('pp', semantic, 400, 99, 'write')['max_index'], 99)

    def test_pp_music_consumes_one_optional_duck_record(self):
        report = analyze(fixture('dc3pp-9872', b'\x05\x02\x01\x06\x21\x63\0\0\0'), 'dc3pp-9872')
        first = report['annotations'][0]
        self.assertEqual(first['music_request'], {'track':2, 'mode':1, 'duck':33})
        self.assertEqual(first['duck_companion']['ignored_byte'], 99)
        self.assertEqual(first['native_next_offset'], first['offset'] + 6)
        self.assertEqual(report['semantic_counts']['pp.music_duck_companions_absorbed'], 1)
        self.assertNotIn('pp.audio.duck', report['semantic_counts'])

    def test_pp_independent_duck_is_not_a_message_wait(self):
        report = analyze(fixture('dc3pp-9872', b'\x06\x2c\x58\0\0\0'), 'dc3pp-9872')
        self.assertEqual(report['annotations'][0]['operation'], 'pp.audio.duck')
        self.assertEqual(report['annotations'][0]['operands'], [44, 88])
        self.assertNotIn('wait.message', report['semantic_counts'])

    def test_pp_music_without_duck_does_not_consume_another_operation(self):
        report = analyze(fixture('dc3pp-9872', b'\x05\x07\x01\x07\0\0\0\0\0'), 'dc3pp-9872')
        first = report['annotations'][0]
        self.assertEqual(first['music_request']['duck'], 0)
        self.assertEqual(first['native_next_offset'], first['offset'] + 3)
        self.assertEqual(report['semantic_counts']['wait.message'], 1)

    def test_pp_global_extension_precedes_optional_music_duck(self):
        data = fixture('dc3pp-9872', b'\x05\x07\x01' + words(0x57, 1, 2, 3, 4) + b'\x06\x0b\x63\0\0\0')
        report = analyze(data, 'dc3pp-9872')
        first = report['annotations'][0]
        self.assertEqual(first['extension']['consumption'], 'fetch_lookahead')
        self.assertEqual(first['music_request'], {'track':7, 'mode':1, 'duck':11})
        self.assertEqual(first['native_next_offset'], first['offset'] + 15)
        self.assertEqual(report['semantic_counts']['arguments.extend_absorbed'], 1)

    def test_pp_music_absorbs_only_the_first_duck_record(self):
        report = analyze(fixture('dc3pp-9872', b'\x05\x02\x01\x06\x0b\x63\x06\x16\x58\0\0\0'), 'dc3pp-9872')
        self.assertEqual(report['annotations'][0]['music_request']['duck'], 11)
        self.assertEqual(report['semantic_counts']['pp.music_duck_companions_absorbed'], 1)
        self.assertEqual(report['semantic_counts']['pp.audio.duck'], 1)


if __name__ == '__main__':
    unittest.main()
