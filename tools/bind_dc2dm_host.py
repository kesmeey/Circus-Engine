"""Update reviewed DM bindings; this does not mark unimplemented host submodes as supported."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
p=ROOT/'profiles/mes-semantics.json';data=json.loads(p.read_text(encoding='utf-8'));dm=data['profiles']['dc2dm-9d72']
bindings={
 0x49:'dm.audio.completion_action',
 0x3e:'dm.ui.button_voice',0x05:'dm.audio.music_attenuation',0x6d:'dm.scene.character_move',0x0a:'dm.ui.grid_wait',0x0e:'system.exit',0x1a:'dm.system.return',0x26:'dm.scene.effect',0x42:'dm.ui.confirm',0x59:'dm.ui.confirm_position',0x5d:'dm.ui.grid_begin',0x5e:'dm.ui.grid_rect',0x61:'dm.ui.grid_sound',
 0x03:'checkpoint',0x04:'dm.history.mark',0x06:'dm.wait.message',0x07:'dm.choice.begin',0x08:'dm.wait.mode',0x09:'dm.ui.wait',0x43:'choice.item',
 0x0d:'dm.scene.transition',0x0f:'dm.state.reset',0x11:'dm.audio.fade_music',0x12:'dm.wait.duration',0x13:'wait.audio',
 0x14:'dm.ui.button_sound',0x15:'dm.system.persist',0x17:'dm.ui.button_template',0x19:'dm.save.checkpoint',0x1b:'dm.system.command',0x1f:'dm.ui.commit',
 0x21:'dm.ui.quad_template',0x25:'dm.ui.binding',0x2b:'dm.scene.preload',0x2c:'dm.ui.resource',0x31:'dm.ui.action',
 0x2e:'dm.scene.character',0x2f:'dm.scene.character_overlay',0x33:'dm.scene.background',0x35:'dm.audio.voice',0x36:'dm.audio.voice_wait',0x37:'dm.audio.voice_prepare',0x38:'dm.script.transfer',0x39:'dm.script.transfer_alt',0x3a:'script.entry_label',0x3b:'dm.movie',
 0x4b:'system.version',0x4c:'dm.scene.portrait',0x4e:'text.speaker',0x4f:'text.body',0x52:'dm.audio.music',0x53:'dm.audio.sound',0x54:'host.command_with_string',0x55:'dm.ui.button_sprite',0x56:'ui.button_text',
 0x5a:'dm.ui.image_quad',0x5b:'dm.ui.animate',0x67:'dm.ui.text',0x68:'dm.ui.origin',0x69:'dm.scene.scroll',0x6b:'dm.scene.character_transform',0x6c:'dm.scene.background_origin',0x6e:'dm.ui.palette',0x6f:'dm.ui.navigation',
}
dm['opcodes'].update({f'0x{k:02X}':v for k,v in bindings.items()})
dm['host_status']='partial: native UI and startup bindings; unsupported submodes stop'
p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
