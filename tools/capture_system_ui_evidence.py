"""Read-only DD PE evidence for original-system compatibility; never load/execute it."""
import argparse
import hashlib
import json
from pathlib import Path

NATIVE_SHA256 = 'ff119f4396cc1382e349fb1ed2c2eb77191db8ec699cb1352ca021cc73407e8d'
RANGES = {
    'version': (0x42AE56, 0x42AECB),
    'resource_panel_buttons': (0x42B331, 0x42B806),
    'menu_wait': (0x42BB1C, 0x42BBC5),
    'panel_flush': (0x42BE49, 0x42BF10),
    'template_static_quad': (0x42D63B, 0x42D828),
    'ui_text': (0x42D929, 0x42D9CC),
    'background_move': (0x42D9CC, 0x42DB26),
    'persist': (0x42DB26, 0x42E152),
    'palette': (0x42E152, 0x42E193),
    'background_replace': (0x42E193, 0x42E425),
    'system_voice': (0x4300BA, 0x43011F),
    'voice_registry': (0x431701, 0x43177F),
    'button_drawable': (0x417A10, 0x417C50),
    'background_reset': (0x441E00, 0x441E43),
    'render_object_defaults': (0x441AF0, 0x441B92),
    'config_and_save_queries': (0x40A4E9, 0x40A770),
    'confirmation_dispatch': (0x430571, 0x430606),
    'confirmation_position': (0x430678, 0x4306A4),
    'confirmation_template': (0x417E20, 0x418110),
    'confirmation_events': (0x43332F, 0x43355B),
    'confirmation_checkbox_render': (0x416477, 0x4164D0),
    'game_exit': (0x42AF48, 0x42AF68),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('exe', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    data = args.exe.read_bytes()
    if hashlib.sha256(data).hexdigest() != NATIVE_SHA256:
        raise SystemExit('Unrecognized native executable; refusing to reuse DD addresses.')
    import capstone
    import pefile
    pe = pefile.PE(data=data)
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    args.out.mkdir(parents=True, exist_ok=True)
    report = {'sha256': NATIVE_SHA256, 'original_executed': False, 'ranges': {}}
    for name, (start, end) in RANGES.items():
        lines = [f'{i.address:08X} {i.mnemonic:7} {i.op_str}' for i in
                 decoder.disasm(pe.get_data(start-pe.OPTIONAL_HEADER.ImageBase, end-start), start)]
        path = args.out / (name+'.asm')
        path.write_text('\n'.join(lines)+'\n', encoding='utf-8')
        report['ranges'][name] = {'start': hex(start), 'end_exclusive': hex(end), 'file': path.name}
    (args.out/'manifest.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'sha256': NATIVE_SHA256, 'ranges': len(RANGES), 'original_executed': False}))


if __name__ == '__main__':
    main()
