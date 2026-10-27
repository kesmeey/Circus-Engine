"""Small, artificial files test parsing without distributing game assets."""
from pathlib import Path
import struct
import tempfile
import unittest

from circus_tools.archives import Entry, entry_destination, inspect_or_extract, read_archive
from circus_tools.common import FormatError, child_output, isolated_output, protected_input_tree
from circus_tools.formats import metadata
from circus_tools.mes import candidate_profiles, collection_profile, parse, profiles
from circus_tools.names import parse_names


def pck_fixture() -> bytes:
    # Two independent directory copies, padding, and two distinct payloads.
    result = bytearray(260)
    struct.pack_into("<I", result, 0, 2)
    struct.pack_into("<4I", result, 4, 192, 4, 256, 4)
    for position, name, offset in [(20, b"a.bin", 192), (84, b"b.bin", 256)]:
        result[position:position + len(name)] = name
        struct.pack_into("<II", result, position + 56, offset, 4)
    result[192:196], result[256:260] = b"ABCD", b"WXYZ"
    return bytes(result)


def mes_fixture(version: int, code: bytes, layout: int = 6, count: int = 0) -> bytes:
    return struct.pack("<I", count) + bytes(count * layout) + struct.pack("<H", version) + (b"\0" if layout == 6 else b"") + code


class StaticTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def archive(self, raw):
        path = self.root / "test.pck"
        path.write_bytes(raw)
        return path

    def test_pck_indices_and_payloads(self):
        path = self.archive(pck_fixture())
        archive = read_archive(path)
        self.assertEqual([(e.name, e.offset, e.size) for e in archive.entries],
                         [("a.bin", 192, 4), ("b.bin", 256, 4)])
        for entry, expected in zip(archive.entries, (b"ABCD", b"WXYZ")):
            _, prefix = inspect_or_extract(path, entry, self.root / "out")
            self.assertEqual(prefix, expected)
            self.assertEqual(entry_destination(self.root / "out", entry).read_bytes(), expected)

    def test_pck_rejects_disagreeing_indices(self):
        raw = bytearray(pck_fixture())
        struct.pack_into("<I", raw, 4, 200)
        with self.assertRaisesRegex(FormatError, "index mismatch"):
            read_archive(self.archive(raw))

    def test_pck_rejects_out_of_bounds(self):
        with self.assertRaises(FormatError):
            read_archive(self.archive(pck_fixture()[:-1]))

    def test_crm_aliases_preserve_shared_span(self):
        raw = bytearray(84)
        raw[:4] = b"CRXB"
        struct.pack_into("<I", raw, 8, 2)
        for i, name in enumerate((b"a.crx", b"b.crx")):
            struct.pack_into("<I", raw, 16 + i * 32, 80)
            raw[24 + i * 32:24 + i * 32 + len(name)] = name
        raw[80:84] = b"DATA"
        archive = read_archive(self.archive(raw))
        self.assertEqual(archive.metadata["offset_aliases"], 1)
        self.assertEqual([e.size for e in archive.entries], [4, 4])

    def test_untrusted_names_cannot_escape(self):
        for name in ("../../outside", "C:\\windows\\file", "/absolute", "CON", "a:b"):
            out = self.root / "out"
            target = entry_destination(out, Entry(0, name, "", 0, 1))
            self.assertEqual(target.parent, out.resolve())

    def test_crm_zero_index_slots_are_reported(self):
        raw = bytearray(84)
        raw[:4] = b"CRXB"
        struct.pack_into("<I", raw, 8, 2)
        struct.pack_into("<I", raw, 16, 80)
        raw[24:29], raw[80:84] = b"a.crx", b"DATA"
        archive = read_archive(self.archive(raw))
        self.assertEqual(archive.metadata["empty_slots"], [1])
        self.assertEqual(len(archive.entries), 1)

    def test_existing_extraction_must_match(self):
        path = self.archive(pck_fixture())
        entry = read_archive(path).entries[0]
        out = self.root / "out"
        entry_destination(out, entry).write_bytes(b"evil")
        with self.assertRaisesRegex(FormatError, "hash mismatch"):
            inspect_or_extract(path, entry, out)
        self.assertEqual(entry_destination(out, entry).read_bytes(), b"evil")

    def test_output_never_inside_source(self):
        with self.assertRaises(ValueError):
            isolated_output(self.root / "game/out", self.root / "game")
        with self.assertRaises(ValueError):
            isolated_output(self.root, self.root / "game")
        with self.assertRaises(ValueError):
            child_output(self.root / "out", "../escaped")

    def test_file_commands_protect_entire_game_directory(self):
        source = self.root / "game/AdvData/MES/main.mes"
        self.assertEqual(protected_input_tree(source), (self.root / "game").resolve())
        with self.assertRaises(ValueError):
            isolated_output(self.root / "game/analysis", protected_input_tree(source))

    def test_dc3dd_all_operand_shapes(self):
        # 3-byte fixed, parameter+string, string, encoded text, four u16 values.
        code = b"\x01\x02\x03\x39\x07BG\0\x44title\0\x63A\0\x68" + struct.pack("<4H", 1, 2, 65535, 4)
        info, tokens = parse(mes_fixture(0xA5A8, code))
        self.assertEqual(info["profile"], "dc3dd-a5a8")
        self.assertEqual([t.kind for t in tokens], ["u8x2", "u8str", "string", "encoded_string", "u16x4"])
        self.assertEqual(tokens[-1].to_dict()["operands"], [1, 2, 65535, 4])
        self.assertEqual(tokens[-2].to_dict()["string_bytes_hex"], "61")

    def test_version_specific_ranges(self):
        _, tokens = parse(mes_fixture(0x9D72, b"\x2a\x01BG\0"))
        self.assertEqual(tokens[0].kind, "u8str")
        _, tokens = parse(mes_fixture(0xA5A8, b"\x2a\x01\x02"))
        self.assertEqual(tokens[0].kind, "u8x2")

    def test_pc1_signed_default_framing(self):
        _, tokens = parse(mes_fixture(0x2063, b"\x81\x83\0\x07\0\0\0", 4), 'dcpc-2063')
        self.assertEqual([(t.opcode, len(t.raw)) for t in tokens], [(0x81, 3), (7, 3), (0, 1)])
        with self.assertRaises(FormatError):
            parse(mes_fixture(0x2063, b"\x81\x83", 4), 'dcpc-2063')
        with self.assertRaises(FormatError):
            parse(mes_fixture(0x5769, b"\x81\x83\0\0", 4), 'dc2pc-5769')

    def test_offset1_profile(self):
        info, tokens = parse(mes_fixture(0x6E69, b"\x29\x01BG\0", 4, 1))
        self.assertEqual(info["profile"], "ktlep-6e69")
        self.assertEqual(info["code_offset"], 10)
        self.assertEqual(tokens[0].kind, "u8str")

    def test_malformed_scripts_are_errors(self):
        for data in (b"", mes_fixture(0xA5A8, b"\x39\x01unterminated"),
                     mes_fixture(0xA5A8, b"\x68\0"), mes_fixture(0x1234, b"\x01\0\0")):
            with self.assertRaises(FormatError):
                parse(data)

    def test_dc45_excluded(self):
        self.assertFalse(any(p["alias"].startswith(("dc4", "dc5")) for p in profiles()))
        with self.assertRaises(FormatError):
            parse(mes_fixture(0xAAB6, b"\x01\0\0"))

    def test_collection_disambiguates_but_rejects_mixed_versions(self):
        # offset1 marker falls inside the offset2 label-ID table.
        ambiguous = struct.pack("<II", 1, 0) + b"\x6c\x01" + b"\xa8\xa5\0" + b"\x01\0\0"
        self.assertEqual(len(candidate_profiles(ambiguous)), 2)
        with self.assertRaises(FormatError):
            parse(ambiguous)
        a, b = self.root / "a.mes", self.root / "b.mes"
        a.write_bytes(ambiguous)
        b.write_bytes(mes_fixture(0xA5A8, b"\x01\0\0"))
        self.assertEqual(collection_profile([a, b]), "dc3dd-a5a8")
        b.write_bytes(mes_fixture(0x9D72, b"\x01\0\0"))
        self.assertIsNone(collection_profile([a, b]))

    def test_xpcm_ogg_size_validation(self):
        data = b"XPCM" + struct.pack("<3I", 100, 5, 4) + b"OggS"
        self.assertEqual(metadata(data, len(data))["codec"], "ogg")
        with self.assertRaises(FormatError):
            metadata(data, 19)

    def test_crxd_preserves_offset_dependencies(self):
        data = bytearray(44)
        data[:4], data[32:36] = b"CRXD", b"CRXJ"
        struct.pack_into("<I", data, 8, 1234)
        struct.pack_into("<I", data, 40, 5678)
        info = metadata(data, 44)
        self.assertEqual((info["base_offset"], info["diff_offset"]), (1234, 5678))

    def test_names_catalog_record_sizes_and_unknown_fields(self):
        data = b""
        for stride, name in [(32, b"MAIN"), (48, b"music\\001"), (36, b"se\\001.pcm"),
                             (32, b"bg\\test"), (32, b"scene1")]:
            data += struct.pack("<I", 1) + name.ljust(32, b"\0") + b"\x01\0\0\0" * ((stride - 32) // 4)
        result = parse_names(data)
        self.assertEqual(result["bytes_consumed"], len(data))
        self.assertEqual(result["tables"][1]["entries"][0]["extra_u32"], [1, 1, 1, 1])
        with self.assertRaises(FormatError):
            parse_names(data + b"trailing")
        with self.assertRaises(FormatError):
            parse_names(data[:-1])


if __name__ == "__main__":
    unittest.main()
