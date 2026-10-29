"""Bindings reviewed against DCPCDVD.EXE and dc2pc.EXE; never run either PE.

Evidence addresses and limitations are described in docs/PC_COMPATIBILITY.md.
"""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
f=ROOT/'profiles/dc123.json';data=json.loads(f.read_text(encoding='utf-8'))
if not any(p['id']=='dcpc-2063' for p in data['profiles']):
    base=next(p for p in data['profiles'] if p['id']=='dcpc-3d63').copy()
    base.update(id='dcpc-2063',version=0x2063,title='D.C.P.C. DVD (local 63 20)',evidence='DCPCDVD.EXE 41172D..411751; 960 local scripts')
    data['profiles'].append(base)
f.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
common={0:'stream.stop',1:'native.no_effect',2:'control.marker',3:'pc.history',4:'pc.checkpoint',7:'dm.wait.message',8:'pc.choice.begin',9:'pc.choice.wait',10:'pc.ui.wait',11:'pc.grid.wait',12:'pc.grid.begin',13:'pc.transition',14:'system.exit',15:'pc.reset',17:'dm.audio.fade_music',18:'pc.wait',23:'pc.button_template',24:'pc.fullscreen',25:'pc.sprite_opaque',0x4b:'text.speaker',0x4c:'text.body',0x4d:'choice.caption',0x4e:'native.no_effect',0x4f:'host.command_with_string',0x50:'pc.button_sprite',0x51:'ui.button_text',0x52:'arguments.extend',0x53:'pc.quad'}
pc2={0x05:'dm.audio.music',0x06:'dm.audio.music_attenuation',0x14:'dm.ui.button_sound',0x15:'pc.grid.sound',0x16:'pc.persist',0x1a:'pc.save_context',0x1b:'dm.system.return',0x1c:'pc.command',0x1e:'block.marker',0x20:'dm.ui.commit',0x21:'pc.quad_template',0x22:'pc.random',0x23:'pc.voice_group.play',0x46:'pc.voice_group.add',0x13:'pc.wait.media_mask',0x25:'dm.ui.binding',0x26:'pc.effect',0x2a:'pc.resource',0x2b:'pc.character',0x2c:'pc.character_overlay',0x2d:'pc.save_caption',0x2e:'pc.script.transfer',0x30:'pc.background',0x31:'pc.image6',0x32:'pc.image7',0x33:'pc.image8',0x35:'label.named',0x36:'dm.audio.voice',0x37:'script.entry_label',0x38:'pc.movie',0x39:'pc.external',0x3a:'jump.named_required',0x3b:'pc.action0',0x3c:'pc.action1',0x3d:'dm.ui.button_voice',0x3e:'pc.button_hover_voice',0x3f:'pc.action2',0x40:'pc.action3',0x42:'pc.grid.voice',0x43:'pc.confirm',0x44:'choice.item',0x45:'choice.caption',0x49:'system.version',0x54:'pc.confirm_position',0x56:'dm.ui.grid_rect',0x59:'dm.ui.navigation',0x5b:'dm.audio.sound',0x5c:'variable.update',0x5d:'variable.copy',0x5e:'variable.add_variable',0x5f:'branch.compare',0x60:'dm.ui.text',0x61:'pc.origin',0x62:'pc.scroll',0x64:'pc.actor_position',0x65:'pc.actor_transform',0x66:'dm.ui.palette'}
pc1={0x1a:'pc.prompt_position',0x05:'pc.sound8',0x06:'dm.audio.music',0x13:'dm.ui.button_sound',0x14:'pc.grid.sound',0x15:'pc.persist',0x1b:'pc.save_context',0x1c:'dm.system.return',0x1d:'pc.command',0x1f:'block.marker',0x21:'dm.ui.commit',0x22:'pc.quad_template',0x23:'pc.random',0x24:'pc.voice_group.play',0x47:'pc.voice_group.add',0x25:'pc.resource_payload',0x2a:'pc.rapid_binding',0x2b:'pc.effect',0x2e:'pc.background',0x2f:'pc.image6',0x30:'pc.image7',0x31:'pc.image8',0x32:'label.named',0x33:'pc.character_pair',0x34:'pc.portrait_pair',0x35:'dm.audio.voice',0x36:'dm.script.transfer',0x37:'dm.script.transfer_alt',0x38:'script.entry_label',0x39:'pc.movie',0x3a:'pc.external',0x3b:'jump.named_required',0x3c:'pc.action0',0x3d:'pc.action1',0x3e:'dm.ui.button_voice',0x3f:'pc.button_hover_voice',0x40:'pc.action2',0x41:'pc.action3',0x43:'pc.grid.voice',0x44:'pc.confirm',0x45:'choice.item',0x46:'choice.caption',0x55:'dm.ui.grid_rect',0x58:'variable.update',0x59:'variable.copy',0x5a:'variable.sum_list',0x5b:'branch.compare',0x5c:'dm.ui.text',0x5d:'pc.origin',0x5e:'pc.scroll',0x60:'pc.actor_position',0x61:'pc.actor_transform',0x62:'dm.ui.palette'}
pc2[0x58]='pc.grid.caption'  # 41D696: preceding list item caption and normal/hover palettes.
f=ROOT/'profiles/mes-semantics.json';data=json.loads(f.read_text(encoding='utf-8'))
for pid,ops,model,label,block in [('dc2pc-5769',pc2,'PC_WIDE',0x35,0x1e),('dcpc-2063',pc1,'PC_BYTE',0x32,0x1f)]:
    data['profiles'][pid]={'runtime_variable_model':model,'host_status':'original startup/title and reviewed primitives; unknown effects stop',
      'decoder':{'version_bytes':2,'extension_opcode':0x52,'encoded_string_postmap':{}},
      'control':{'label_opcode':label,'block_marker_opcode':block,'companion_opcode':2,'block_scan_signed_opcode':True},
      'opcodes':{f'0x{k:02X}':v for k,v in sorted((common|ops).items())}}
f.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
