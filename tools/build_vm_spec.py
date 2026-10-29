"""Build a versioned opcode/handler specification from locked PE tables and REA evidence."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import pefile

ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from circus_tools.mes import profiles

PC={
 'dcpc-2063':dict(exe=Path('D:/gal/D.C.P.C【无密码】/D.C.P.C/DCPCDVD.EXE'),key='dcpc',sha='5f84e2fdd6f442509fd0e6a00ff8aa20f5c23b0afc43c811bb615598d4c8b67a',entry=0x4125b0,fetch=0x4125f4,site=0x412654,table=0x415754,count=99,loader=0x411670,read=0x411910,write=0x411b10,fallback=0x41570f),
 'dc2pc-5769':dict(exe=Path('D:/gal/D.C.II P.C/dc2pc.EXE'),key='dc2pc',sha='f0b3be701f3c8c94dad143db927d824d398325be57a5da7514556f862f505e46',entry=0x41c530,fetch=0x41c57b,site=0x41c640,table=0x41f480,count=105,loader=0x41a050,read=0x41a300,write=0x41a5c0,fallback=0x41f434),
}
EXES={'dc3dd-a5a8':Path('D:/gal/DC3DD/DC3DD.EXE'),'dc2dm-9d72':Path('D:/gal/DC2DM/DC2DM.EXE')}

NOTES={
 'dm.scene.character_overlay':'DM 42FAEE 设置/清除角色附属图。412504、432488、432699 调用 428990：先移除附属图的 _B 和色调标记，再继承人物资源的 _B 近景后缀及 @/* 色调，保留附属图归档名；更换人物而保留附属图时也重新匹配。',
 'dm.scene.preload':'DM 42FA01 预载资源槽；角色槽 2..5 与附属槽 6..9 成对，432488/432699 在任一方变化时匹配近景和色调。此匹配不适用于头像槽 18/19。',
 'dm.movie':'43046F 接收文件名；432D17..432D31 用 MOVIE 子目录和 .mpg 扩展名交给 422C20 构建路径。宿主暂停直到影片完成事件。',
 'system.persist':'DD 17 的 u8 子模式：26 经 42E0FC 调用 40EE90 重读系统持久设置；文件缺失时 40ED10(1) 初始化。不是剧情存档读取。其他子模式见宿主和原版系统文档。',
 'text.speaker':'原始解码字符串设置说话人；不加载资源、不推进下一句。',
 'text.body':'发布场景并呈现正文；文本完成与点击推进是不同等待状态。',
 'pc.background':'资源字符串；写入背景及预载槽；场景发布由后续指令控制。',
 'pc.character_pair':'第一个字符串给角色槽 0；紧邻 0x46 时消费其字符串给角色槽 1。',
 'pc.portrait_pair':'头像槽 0/1；同样可消费紧邻 0x46 字符串。',
 'pc.character':'u8 槽位 + 资源字符串；槽号和角色位置分离。',
 'pc.character_overlay':'u8 槽位 + 叠加资源字符串。',
 'dm.audio.voice':'版本独立的角色开关：DM 使用 94/95，PC1/2 使用 100:60+角色号；保留位置前缀的声像及回看重播参数。PC1/2 是 64 字节旧索引，PC2 可跨 VOICE2.DAT。',
 'dm.audio.music':'NAMES music 索引；停止值及播放状态由版本宿主处理。',
 'pc.sound8':'u8 音效索引和通道参数；255 映射停止值。',
 'dm.audio.sound':'音效索引/参数；不是文本指令。',
 'pc.wait':'a0=0 无限输入等待，否则 200*a0 毫秒；鼠标绑定可跳转标签。',
 'pc.action0':'原始标签绑定到右键/Esc；PC1 414F1E 写 9D95DC，419310..419379 消费；PC2 41EEFE 写 B0B6F0，423A4C..423AB5 消费。接受跳转后清空该绑定。',
 'pc.action1':'原始标签绑定到左键/Enter；PC1 9D95FC / PC2 B0B710。与右键槽独立，确认框另用 action2/action3。',
 'pc.ui.wait':'a1=1 为 200*a0 毫秒轮询等待；其他模式等待原版按钮输入。',
 'dm.wait.message':'等待文本显示/用户推进；主循环必须归还控制权给平台。',
 'label.named':'NUL 结尾的原始字节标签；跳转匹配不使用汉化显示字符串。',
 'jump.named_required':'从代码起点按原生规则扫描标签；兼容层对缺失标签报错。',
 'variable.update':'四个 i16: bank,index,value,mode；mode 0=set,1=add,2=sub。',
 'variable.copy':'四个 i16: dstBank,dstIndex,srcBank,srcIndex。',
 'variable.add_variable':'PC2 将源变量值加到目标变量。',
 'variable.sum_list':'PC1 后续九字节记录组成求和表，到 companion 0x02 为止。',
 'branch.compare':'四个 i16: bank,index,rhs,mode；0..5 为 == != > < >= <=；mode&0x80 时 rhs 是变量引用。紧邻 0x02 提供块编号，失败时扫描块尾。',
 'block.marker':'a0=0 向前扫描相同有符号块编号；非零直接继续。',
 'script.entry_label':'保存下次跨脚本入口的字节标签，应用后清空。',
 'dm.script.transfer':'PC1 跨脚本转移模式 1；不是通用 CPU CALL；系统返回由独立上下文保存实现。',
 'dm.script.transfer_alt':'PC1 跨脚本转移模式 2；重置语义与模式 1 有别。',
 'pc.script.transfer':'PC2 u8 模式+脚本名：4→2，3/5→3，其他→1。',
 'choice.item':'跳转标签字符串，消费紧随其后的 caption 记录，建立原版按钮。',
 'pc.choice.begin':'初始化选项集合，使用脚本先前定义的按钮模板。',
 'pc.choice.wait':'发布选项并暂停；点击根据按钮 action 跳回同一 MES 标签。',
 'arguments.extend':'0x52 在相邻主记录后追加四个有符号 i16，不能当成独立业务指令。',
 'pc.quad':'原生图片裁切：dest=(a0+originX,a1+originY)，src=(a2,a3)，尺寸=(a4-a2,a5-a3)；扩展字段含纹理槽及标志。',
 'pc.button_sprite':'按钮图片参数与静态 quad 不同：src=(a0-a2,a1-a3)，尺寸=(a4,a5)。滑条配置不按图片裁切转换：PC1 state=5/7；PC2 20/21/22/23 对应配置/分段/进度/滑块，24/25/26 是分段图集正常/悬停/按下。',
 'pc.effect':'版本独立的效果编号及参数；已绑定纹理，运动/时间近似，不能声称像素一致。',
 'pc.transition':'转场类别/时长；部分原效果尚由近似淡入实现。',
 'pc.external':'原宿主外部程序操作；本兼容层未执行，也没有通用 NOP 兜底。',
 'pc.grid.wait':'发布原版存读档网格并等待点击；PC1/2 保留槽 0/1；PC2 读档进入 selsave2，保存进入 selsave。',
 'pc.grid.caption':'PC2 41D696 消费后继原始字符串，为前一个列表条目写 x,y,正常/悬停调色板；41169A..41177D 按 cell 相对坐标绘制，x=-1 居中、y=-1 不显示。无前项仍消费字符串。',
 'pc.voice_group.add':'向临时语音组追加资源，最多十条；PC1 414C84 / PC2 41EC11。',
 'pc.voice_group.play':'模式 0 随机播一条并走普通角色开关，1 同时混音，2 清空；组可存十条，但同时播放上限 PC1 七条、PC2 六条（41CC10 通道3..9，427680 通道4..9）。同时播放只受全局开关限制。',
 'pc.wait.media_mask':'PC2 41ED22 / 4230E0：位 1 等 BGM，2 等语音，4 等音效；位 8 原生计时分支尚未接入。',
 'pc.prompt_position':'PC1 确认框坐标为参数乘 8；随后 pc.confirm 使用原版模板 2。',
 'pc.confirm':'原版确认模板与 caption；肯定/否定跳转 action2/action3，不能覆写普通点击 action0/action1。',
 'pc.command':'版本独立的原生服务：1 日历，2 好感排序，6 存档模式，10/11 片尾，PC2 20 地图；8 是 PC1 姓名输入：原14个按钮、字符页、汉字索引、四字姓名和确认模板；40C2E0/40C6D0/40D340/40D400。仍有未接入服务。',
 'stream.stop':'终止/归还宿主；不能简单视为 pc++ 的 NOP。',
}

LOGICAL={
 'ui.music_end_action':['label:bytes_z'], 'grid.item_action':['label:bytes_z'],
 'pc.grid.item':['id:i16','reserved:i16','source_x:i16','source_y:i16','source_right:i16','source_bottom:i16','selector:i16','reserved:i16'],
 'pc.grid.caption':['x:i16','y:i16','normal_palette:i16','hover_palette:i16','caption:following_raw_string'],
 'dd.grid.item':['id:i16','reserved:i16','source_x:i16','source_y:i16','source_right:i16','source_bottom:i16','slot:i16','reserved:i16'],
 'dd.grid.text':['x_or_style:i16','y:i16','reserved:i16','state:i16','caption:following_string_unless_state_99'],
 'dd.grid.columns':['columns:i16','reserved:i16','reserved:i16','reserved:i16'],
 'variable.update':['bank:i16','index:i16','value:i16','mode:i16'],
 'variable.copy':['dst_bank:i16','dst_index:i16','src_bank:i16','src_index:i16'],
 'variable.add_variable':['dst_bank:i16','dst_index:i16','src_bank:i16','src_index:i16'],
 'branch.compare':['bank:i16','index:i16','rhs:i16','mode:i16','companion:block_id_i8'],
 'text.speaker':['speaker:encoded_bytes_z'], 'text.body':['text:encoded_bytes_z'],
 'pc.background':['resource:bytes_z'], 'pc.character':['slot:u8','resource:bytes_z'],
 'pc.character_pair':['resource0:bytes_z','optional_0x46_resource1:bytes_z'],
 'pc.portrait_pair':['resource0:bytes_z','optional_0x46_resource1:bytes_z'],
 'dm.audio.voice':['voice_path:bytes_z'], 'dm.audio.music':['music_index:u8','mode:u8'],
 'label.named':['name:bytes_z'], 'jump.named_required':['name:bytes_z'],
 'script.entry_label':['name:bytes_z'],
 'dm.script.transfer':['script:bytes_z'], 'dm.script.transfer_alt':['script:bytes_z'],
 'pc.script.transfer':['mode:u8','script:bytes_z'],
 'pc.wait':['ticks:u8','mode:u8'], 'pc.ui.wait':['ticks:u8','mode:u8'],
 'pc.effect':['kind:u8','strength:u8'], 'pc.transition':['kind:u8','ticks:u8'],
 'choice.item':['label:bytes_z','following_caption:encoded_bytes_z'],
 'arguments.extend':['a4:i16','a5:i16','a6:i16','a7:i16'],
 'pc.quad':['dest_x:i16','dest_y:i16','source_x:i16','source_y:i16','source_right:i16','source_bottom:i16','flags:i16','state:i16'],
 'pc.button_sprite':['dest_x:i16','dest_y:i16','origin_x:i16','origin_y:i16','width:i16','height:i16','flags:i16','state:i16'],
}

def main():
    sem=json.loads((ROOT/'profiles/mes-semantics.json').read_text(encoding='utf-8'))
    framing={p['id']:p for p in profiles()};result={'schema':1,'title':'D.C. Engine VM Specification','scope':'DC1/2/3; version-specific native evidence, not one universal opcode numbering','profiles':{}}
    for pid,p in sem['profiles'].items():
        if pid in PC:
            cfg=PC[pid];exe=cfg['exe'];sha=cfg['sha'];count=cfg['count'];table=cfg['table'];fallback=cfg['fallback'];indices=list(range(count))
            ev=json.loads((ROOT/f"build/pc-port/rea-{cfg['key']}-function.json").read_text(encoding='utf-8-sig'))
            if ev['subject']['digest']['sha256']!=sha:raise ValueError('REA binary mismatch')
            observed=ev['normalized_result'];assembly=observed['assembly']
            dispatch=next(line for line in assembly if line.startswith(hex(cfg['site'])+':'))
            if f'{table:x}' not in dispatch or 'JMP' not in dispatch:raise ValueError('REA dispatch mismatch')
            block=next(b for b in observed['basic_blocks'] if b['start']==hex(cfg['site']))
            evidence={'provider':ev['provider'],'evidence_id':ev['evidence_id'],'dispatch_instruction':dispatch,'function_entry':observed['procedure']['address'],'fetch_site':hex(cfg['fetch']),'loader':hex(cfg['loader']),'read_variable':hex(cfg['read']),'write_variable':hex(cfg['write'])}
            loader=json.loads((ROOT/f"build/pc-port/rea-{cfg['key']}-loader.json").read_text(encoding='utf-8-sig'))
            if loader['subject']['digest']['sha256']!=sha or f"FUN_{cfg['loader']:08x}(" not in loader['normalized_result']:raise ValueError('REA loader mismatch')
            evidence['loader_evidence_id']=loader['evidence_id']
        elif pid in EXES:
            exe=EXES[pid];sha=p['native_sha256'];count=p['dispatch_count'];table=int(p['addresses']['dispatch_table'],16);fallback=int(p['addresses']['fallback'],16);indices=list(range(count));evidence={'provider':'pefile + Capstone, previous static evidence','addresses':p['addresses']}
        else:continue
        raw=exe.read_bytes()
        if hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Source SHA mismatch '+pid)
        pe=pefile.PE(data=raw);base=pe.OPTIONAL_HEADER.ImageBase;tablebytes=pe.get_data(table-base,count*4);handlers=struct.unpack('<'+str(count)+'I',tablebytes)
        if pid in PC and set(map(hex,handlers))-set(block['successors']):raise ValueError('PE table target absent from REA recovered switch')
        rows=[]
        for op,handler in enumerate(handlers):
            operation=p['opcodes'].get(f'0x{op:02X}','unknown')
            shape=next((kind for kind,lo,hi in framing[pid]['ranges'] if lo<=op<=hi),'unknown')
            description=NOTES.get(operation,sem['operations'].get(operation,{}).get('description','需继续核对参数及宿主副作用。'))
            status='unknown' if operation=='unknown' else 'unimplemented' if operation in ['pc.external'] else 'partial_binding'
            if operation in ['variable.update','variable.copy','variable.sum_list','variable.add_variable','branch.compare','arguments.extend','label.named','block.marker']:status='reviewed_primitive'
            logical=LOGICAL.get(operation)
            rows.append({'opcode':f'0x{op:02X}','handler_va':f'0x{handler:08X}','args':shape,'logical_operands':logical,'operation':operation,'behavior':description,'semantic_status':status,'shared_fallback':handler==fallback})
        result['profiles'][pid]={'exe_name':exe.name,'exe_sha256':sha,'image_base':hex(base),'dispatch_table':hex(table),'dispatch_table_sha256':hashlib.sha256(tablebytes).hexdigest(),'dispatch_count':count,'framing':framing[pid],'evidence':evidence,'opcodes':rows}
        if pid=='dcpc-2063':result['profiles'][pid]['outside_dispatch']={'range':'0x80..0xFF','record_bytes':3,'operation':'native.no_effect','evidence':['004125F4 MOVSX','00412618 signed JGE','0041263A PC += 3','0041264A unsigned JA default','0041570F continuation'],'note':'Signed fetch precedes unsigned dispatch bound; does not authorize arbitrary unknown opcodes.'}
    target=ROOT/'profiles/vm-specification.json';target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    lines=['# D.C. Engine VM Specification','',
      '这是工作规范，分发表已定位不等于所有 handler 已完整实现。原 EXE 没有运行。`reviewed_primitive` 表示已核对的基本子规则；`partial_binding` 仍可能缺少宿主副作用；`unknown` 和 `unimplemented` 不得按 NOP 执行。',
      '', '## 证据与坐标','',
      'PC1/2 使用 REA 6.8.0 + Ghidra 12.1.4 静态分析；表中每一个 handler 地址又与原 PE 的 u32 跳转表以及 REA 恢复出的 switch 后继集合交叉核对。REA 原始报告在 `build/pc-port/rea-*.json`，不属于游戏源码。DD/DM 沿用之前已锁定哈希的静态证据，不伪称本轮 REA 重新验证。',
      '', 'PC1/2 MES 头是 u32 count + count 个 u32 + 两字节版本；文件代码起点=4+4*count+2。原 EXE 的 PC 是从版本标记所在缓冲区开始的偏移，故文件偏移=4+4*count+原生 PC。地址均为相应 SHA-256 对应 EXE 的首选 VA，不能直接套在汉化 EXE 或其他版本上。',
      '', '## 取指与复合记录','',
      '- `u8x2`: opcode + 两个字节；`u8str`: opcode + u8 + NUL 字符串；`string`: opcode + NUL 字符串；`encoded_string`: 字符串每字节加 0x20 后按版本后处理；`u16x4`: opcode + 四个 LE 16 位字，数值指令按 i16 解释。',
      '- PC1 004125F4 使用 MOVSX，00412618 有符号比较把 0x80..0xFF 按三字节记录取出；0041264A 无符号越界分发到 0041570F，继续执行下一记录。此行为已复现；没有将任意未知指令当 NOP。PC2 高位字节仍未开放。PC1 首个 STOP 后不再解码；本地 PC2 两个脚本有 STOP 后的 `ad` 尾巴。',
      '- 0x52 扩展、选项 caption、IF companion、求和记录和 UI 字符串可能由前一条指令消费。物理记录边界和逻辑指令边界必须分别保存。',
      '- 标签按原字节匹配；文本 GBK/CP932 显示和角色姓名替换不得影响资源路径或标签。',
      '', '## 状态与调度','',
      '变量模型按版本独立。PC1/2 普通 bank 0..9 各 100 字节，写入饱和到 0..255；PC2 bank 200..209 是对应普通区四字节大端视图。bank 99 是宿主寄存器，PC1/2 的 248/249/250 分别表示 SE/语音/BGM 已结束，极性不同于 DM。持久区、CG 标志和阅读标志另存，不能复用 DD/DM 的内存布局。',
      '', 'WAIT 将控制权交给平台；文本渲染完成、点击推进、时间到、音频完成和菜单点击是不同信号。系统菜单调用需要保存调用者状态；跨 MES 转移不是统一的 CALL。存档须保存版本、脚本哈希、PC、变量、宿主场景和等待状态。',
      '', '本地 `0422a_t01.mes` 含 `81 83 00` 默认记录，依据上述有符号取指规则已完整解析，无需修改资源。原版存读档、回看和 PC1 姓名输入已接入；部分鉴赏组合、设置副作用及演出仍未逐项核验。PC1 4189D0 / PC2 422DA0 的回看是在原消息窗口逐条浏览；不可错误跳到不存在的 LOG.MES。',
      '', '## 原脚本列表与音乐回调（0.9）','',
      'PC2 0x57/0x42 与 DD 0x79/0x54 构造独立于屏幕格子的条目及动作标签。DD 0x6E 公共图层、0x6F 条目图层和 0x7A 文字状态共同绘制列表；0x6E 的 state=-1 是滚动条滑块（4346E0、435447），不是选中条目标记。DD 0xA7 设置列数。99:246 是滚动偏移，99:253 是可见行，99:254 是条目 ID；不能复用保存页的槽位换算。','',
      'PC2 0x47（423571）在菜单等待且 BGM 结束时触发，触发后清空；DD 0x5C（4340D7）保持绑定，并以大于 500ms 的间隔检查，直到脚本清空标签。DD CG 查看在可中断 WAIT 中由 ui.action[3] 响应右键。所有目标按原字节匹配。','',
      'DD 服务 200/201/202 使用 4AAEC8 的 88 首曲目表、400 项收藏和播放顺序。MUSIC.MES 的随机按钮写 100:26，原生 408FF0 却读取旧 9:30；当前兼容层明确用脚本的 100:26 作为模式输入，保留当前曲并打乱其余顺序。这是可复核的兼容修正，不声称伪随机序列与原 EXE 逐值一致。','',
      '证据在 build/pc-port/dd-list-native.asm、rea-dd-grid-sprite.json、rea-dd-system-command.c；新增交互验收在 DesktopExtrasAudit，不运行原 EXE。','',
      '## 原生宿主规则补充（0.10）','',
      '- DD bank 94/95 是按 voice_register 登记的角色音量/启用表，不是选项数组。40D824/40D84D 以 state+5CDBC 登记数限定范围，索引50为其他角色，记录偏移5BE9C/5BE98。选择支初始化不能清空角色表。默认值100/1，系统语音另有98:9规则；40E670/40E760/43C8F0。',
      '- DM 94/95 是128组角色偏好，音量默认99；436360 按最后下划线后的角色标识匹配，分号前的字母可指定角色，#强制播放。<、>、= 的声像及原始参数保留在回看记录。PC2 427460/50EA80 的角色表与 DM 不同；PC1 41CA30/43DBE8 按目录匹配。',
      '- DD 首次运行和恢复默认使用40ED10、4A5100默认表；菜单修改设置与剧情快照分开保存。台词速度按22/(2*speed+16)帧/字解释；DM按原速度分段；PC1/2 99:13偶数慢速、奇数立即，2/3自动。跨平台定时器与字体不能据此声称逐帧一致。',
      '- PC1 姓名编辑从用户的DCPCDVD.EXE中只读提取原字符表与矩形，不启动EXE。取消保留旧姓名，确认后用于$s/$n文本替换并持久保存；输入法组合交互尚未进行可见窗口验收。',
      '- DD回看406670将原画面按12.5%缩放，围绕512,288中心施加(-400,row*130-196)，得出预览左上角(48,56+row*130)，外框由4066DF裁切LOGCHIP(696,532)-(847,616)。预览随悬停记录行变化。原生按钮状态来自401110/405680。DM预览位置仍需进一步校对。',
      '- 桌面存档容器保存嵌入VM、校验、时间、caption、9:20角色图标、场景缩略图、六路资源与播放位置、文字已显示时长；保留0.9解释器原始快照迁移。原Windows存档格式导入尚未实现。菜单期间保存使用剧情调用者和对应音乐位置。',
      '', '## 兼容层画面捕获与存档（0.11）','',
      '效果9保留捕获前的纹理值，连续捕获不能删掉旧引用或递归引用自身。UiCapture是兼容层的数据表示，不是新增原生opcode：无平台时保留不可变逻辑依赖；PC平台将当前画面合成为一张压缩ARGB纹理。绑定条件、颜色、动画与控件状态在捕获时固定。逻辑依赖最多256层/8MiB，PC合成后只有一层。宿主存档DM版本444D4836、DD原系统4444333E，继续读取此前格式。原Windows存档不在此格式范围。','',
      '四游戏截图测试比较捕获前后及保存恢复后的像素，并由发布的0.10程序生成截图存档验证迁移；这不替代与原Windows画面的逐屏核对。','',
      '## 版本指令表','']
    for pid,p in result['profiles'].items():
        lines += [f'### {pid}','',f"EXE: `{p['exe_name']}`；SHA-256: `{p['exe_sha256']}`。",'',f"分发表 `{p['dispatch_table']}`，共 {p['dispatch_count']} 项。",'', '| Opcode | 参数布局 | Handler VA | 语义 | 状态 | 行为 |','|---|---|---|---|---|---|']
        for r in p['opcodes']:lines.append(f"| {r['opcode']} | {r['args']} | {r['handler_va']} | {r['operation']} | {r['semantic_status']} | {r['behavior'].replace('|','/')} |")
        lines.append('')
    (ROOT/'docs/VM_SPECIFICATION.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps({'profiles':{k:len(v['opcodes']) for k,v in result['profiles'].items()},'json':str(target)}))

if __name__=='__main__':main()
