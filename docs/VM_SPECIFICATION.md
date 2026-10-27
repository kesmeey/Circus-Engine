# D.C. Engine VM Specification

这是工作规范，分发表已定位不等于所有 handler 已完整实现。原 EXE 没有运行。`reviewed_primitive` 表示已核对的基本子规则；`partial_binding` 仍可能缺少宿主副作用；`unknown` 和 `unimplemented` 不得按 NOP 执行。

## 证据与坐标

PC1/2 使用 REA 6.8.0 + Ghidra 12.1.4 静态分析；表中每一个 handler 地址又与原 PE 的 u32 跳转表以及 REA 恢复出的 switch 后继集合交叉核对。REA 原始报告在 `build/pc-port/rea-*.json`，不属于游戏源码。DD/DM 沿用之前已锁定哈希的静态证据，不伪称本轮 REA 重新验证。

PC1/2 MES 头是 u32 count + count 个 u32 + 两字节版本；文件代码起点=4+4*count+2。原 EXE 的 PC 是从版本标记所在缓冲区开始的偏移，故文件偏移=4+4*count+原生 PC。地址均为相应 SHA-256 对应 EXE 的首选 VA，不能直接套在汉化 EXE 或其他版本上。

## 取指与复合记录

- `u8x2`: opcode + 两个字节；`u8str`: opcode + u8 + NUL 字符串；`string`: opcode + NUL 字符串；`encoded_string`: 字符串每字节加 0x20 后按版本后处理；`u16x4`: opcode + 四个 LE 16 位字，数值指令按 i16 解释。
- PC1 004125F4 使用 MOVSX，00412618 有符号比较把 0x80..0xFF 按三字节记录取出；0041264A 无符号越界分发到 0041570F，继续执行下一记录。此行为已复现；没有将任意未知指令当 NOP。PC2 高位字节仍未开放。PC1 首个 STOP 后不再解码；本地 PC2 两个脚本有 STOP 后的 `ad` 尾巴。
- 0x52 扩展、选项 caption、IF companion、求和记录和 UI 字符串可能由前一条指令消费。物理记录边界和逻辑指令边界必须分别保存。
- 标签按原字节匹配；文本 GBK/CP932 显示和角色姓名替换不得影响资源路径或标签。

## 状态与调度

变量模型按版本独立。PC1/2 普通 bank 0..9 各 100 字节，写入饱和到 0..255；PC2 bank 200..209 是对应普通区四字节大端视图。bank 99 是宿主寄存器，PC1/2 的 248/249/250 分别表示 SE/语音/BGM 已结束，极性不同于 DM。持久区、CG 标志和阅读标志另存，不能复用 DD/DM 的内存布局。

WAIT 将控制权交给平台；文本渲染完成、点击推进、时间到、音频完成和菜单点击是不同信号。系统菜单调用需要保存调用者状态；跨 MES 转移不是统一的 CALL。存档须保存版本、脚本哈希、PC、变量、宿主场景和等待状态。

本地 `0422a_t01.mes` 含 `81 83 00` 默认记录，依据上述有符号取指规则已完整解析，无需修改资源。原版存读档、回看和 PC1 姓名输入已接入；部分鉴赏组合、设置副作用及演出仍未逐项核验。PC1 4189D0 / PC2 422DA0 的回看是在原消息窗口逐条浏览；不可错误跳到不存在的 LOG.MES。

## 原脚本列表与音乐回调（0.9）

PC2 0x57/0x42 与 DD 0x79/0x54 构造独立于屏幕格子的条目及动作标签。DD 0x6E 公共图层、0x6F 条目图层和 0x7A 文字状态共同绘制列表；0x6E 的 state=-1 是滚动条滑块（4346E0、435447），不是选中条目标记。DD 0xA7 设置列数。99:246 是滚动偏移，99:253 是可见行，99:254 是条目 ID；不能复用保存页的槽位换算。

PC2 0x47（423571）在菜单等待且 BGM 结束时触发，触发后清空；DD 0x5C（4340D7）保持绑定，并以大于 500ms 的间隔检查，直到脚本清空标签。DD CG 查看在可中断 WAIT 中由 ui.action[3] 响应右键。所有目标按原字节匹配。

DD 服务 200/201/202 使用 4AAEC8 的 88 首曲目表、400 项收藏和播放顺序。MUSIC.MES 的随机按钮写 100:26，原生 408FF0 却读取旧 9:30；当前兼容层明确用脚本的 100:26 作为模式输入，保留当前曲并打乱其余顺序。这是可复核的兼容修正，不声称伪随机序列与原 EXE 逐值一致。

证据在 build/pc-port/dd-list-native.asm、rea-dd-grid-sprite.json、rea-dd-system-command.c；新增交互验收在 DesktopExtrasAudit，不运行原 EXE。

## 原生宿主规则补充（0.10）

- DD bank 94/95 是按 voice_register 登记的角色音量/启用表，不是选项数组。40D824/40D84D 以 state+5CDBC 登记数限定范围，索引50为其他角色，记录偏移5BE9C/5BE98。选择支初始化不能清空角色表。默认值100/1，系统语音另有98:9规则；40E670/40E760/43C8F0。
- DM 94/95 是128组角色偏好，音量默认99；436360 按最后下划线后的角色标识匹配，分号前的字母可指定角色，#强制播放。<、>、= 的声像及原始参数保留在回看记录。PC2 427460/50EA80 的角色表与 DM 不同；PC1 41CA30/43DBE8 按目录匹配。
- DD 首次运行和恢复默认使用40ED10、4A5100默认表；菜单修改设置与剧情快照分开保存。台词速度按22/(2*speed+16)帧/字解释；DM按原速度分段；PC1/2 99:13偶数慢速、奇数立即，2/3自动。跨平台定时器与字体不能据此声称逐帧一致。
- PC1 姓名编辑从用户的DCPCDVD.EXE中只读提取原字符表与矩形，不启动EXE。取消保留旧姓名，确认后用于$s/$n文本替换并持久保存；输入法组合交互尚未进行可见窗口验收。
- DD回看406670将原画面按12.5%缩放，围绕512,288中心施加(-400,row*130-196)，得出预览左上角(48,56+row*130)，外框由4066DF裁切LOGCHIP(696,532)-(847,616)。预览随悬停记录行变化。原生按钮状态来自401110/405680。DM预览位置仍需进一步校对。
- 桌面存档容器保存嵌入VM、校验、时间、caption、9:20角色图标、场景缩略图、六路资源与播放位置、文字已显示时长；保留0.9解释器原始快照迁移。原Windows存档格式导入尚未实现。菜单期间保存使用剧情调用者和对应音乐位置。

## 兼容层画面捕获与存档（0.11）

效果9保留捕获前的纹理值，连续捕获不能删掉旧引用或递归引用自身。UiCapture是兼容层的数据表示，不是新增原生opcode：无平台时保留不可变逻辑依赖；PC平台将当前画面合成为一张压缩ARGB纹理。绑定条件、颜色、动画与控件状态在捕获时固定。逻辑依赖最多256层/8MiB，PC合成后只有一层。宿主存档DM版本444D4836、DD原系统4444333E，继续读取此前格式。原Windows存档不在此格式范围。

四游戏截图测试比较捕获前后及保存恢复后的像素，并由发布的0.10程序生成截图存档验证迁移；这不替代与原Windows画面的逐屏核对。

## 版本指令表

### dc3dd-a5a8

EXE: `DC3DD.EXE`；SHA-256: `ff119f4396cc1382e349fb1ed2c2eb77191db8ec699cb1352ca021cc73407e8d`。

分发表 `0x4326a8`，共 168 项。

| Opcode | 参数布局 | Handler VA | 语义 | 状态 | 行为 |
|---|---|---|---|---|---|
| 0x00 | u8x2 | 0x0042AE49 | stream.stop | partial_binding | 终止/归还宿主；不能简单视为 pc++ 的 NOP。 |
| 0x01 | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x02 | u8x2 | 0x00431CC2 | control.marker | partial_binding | Context-dependent 3-byte record: IF companion uses signed first byte as block ID; sum list terminator. |
| 0x03 | u8x2 | 0x004309B7 | checkpoint | partial_binding | Combine bytes little-endian into checkpoint index; updates state and reads header u16 metadata. Not itself a jump. |
| 0x04 | u8x2 | 0x00430A8C | history.range | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x05 | u8x2 | 0x0042FEF1 | audio.restore_volume | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x06 | u8x2 | 0x0042B1CC | wait.message | partial_binding | DD 06: state+444F4 nonzero suppresses wait; otherwise consume state+196FC once or yield native reason 6. Operands are unused in this handler. Requires a flow-aware host. |
| 0x07 | u8x2 | 0x0042B1AB | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x08 | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x09 | u8x2 | 0x0042BB87 | choice.begin | partial_binding | Clear registered choices; native 42BB87. |
| 0x0A | u8x2 | 0x0042BBC5 | choice.show | partial_binding | Normal mode 0 presents registered choices and waits; replay modes unsupported. Native 42BBC5. |
| 0x0B | u8x2 | 0x0042BB1C | ui.wait | partial_binding | 0B flushes button construction and yields for an input action. A button label resumes the original MES, without falling through. |
| 0x0C | u8x2 | 0x0042C189 | dd.grid.wait | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x0D | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x0E | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x0F | u8x2 | 0x0042C1C7 | scene.transition | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x10 | u8x2 | 0x0042AF48 | system.exit | partial_binding | Sends WM_DESTROY to the game window and yields; portable host ends the game Activity. |
| 0x11 | u8x2 | 0x0042B0AB | state.reset_scenario | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x12 | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x13 | u8x2 | 0x0042D065 | audio.music_stop | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x14 | u8x2 | 0x0042B20A | wait.duration | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x15 | u8x2 | 0x0043017F | wait.audio | partial_binding | Native asynchronous completion event; wait.audio absorbs successive opcode 15 masks. |
| 0x16 | u8x2 | 0x0042B738 | ui.button_sound | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x17 | u8x2 | 0x0042DB26 | system.persist | partial_binding | DD 17 的 u8 子模式：26 经 42E0FC 调用 40EE90 重读系统持久设置；文件缺失时 40ED10(1) 初始化。不是剧情存档读取。其他子模式见宿主和原版系统文档。 |
| 0x18 | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x19 | u8x2 | 0x0042B3C6 | ui.panel | partial_binding | 19/0 clears current UI and action bindings; other reviewed selectors save current UI in a template slot. |
| 0x1A | u8x2 | 0x0042B80B | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x1B | u8x2 | 0x00430AD1 | scene.checkpoint | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x1C | u8x2 | 0x004306E0 | dd.system.return | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1D | u8x2 | 0x0042AECB | system.command | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x1E | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x1F | u8x2 | 0x0042D228 | block.marker | reviewed_primitive | a0=0 向前扫描相同有符号块编号；非零直接继续。 |
| 0x20 | u8x2 | 0x0042B4B1 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x21 | u8x2 | 0x0042BE49 | ui.flush | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x22 | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x23 | u8x2 | 0x0042D63B | ui.template | partial_binding | 23 saves the current UI panel to a selector-dependent template and clears construction state. 0 and unused selector cases are native fallbacks. |
| 0x24 | u8x2 | 0x004302FA | state.random | partial_binding | 24 stores MSVCRT rand() modulo p0 in special bank 99 index 2 (4302FA). Seed is portable and saved, not synchronized to native wall time. |
| 0x25 | u8x2 | 0x00431B48 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x26 | u8x2 | 0x00431CC2 | native.no_effect | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x27 | u8x2 | 0x0042CC61 | scene.effect | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x28 | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x29 | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2A | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2B | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2C | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2D | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2E | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2F | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x30 | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x31 | u8x2 | 0x0043177F | ui.focus | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x32 | u8x2 | 0x0043178F | ui.option_flag | partial_binding | 43178F calls 412DB0/412DC0: set UI object fields +348/+34C to 1, selected by operand 0; platform pointer constraints still under review. |
| 0x33 | u8x2 | 0x004317B3 | ui.navigation | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x34 | u8x2 | 0x00431899 | ui.navigation_extra | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x35 | u8x2 | 0x004318A9 | scene.stop_motion | partial_binding | Stop all render-object motion tracks (4318A9). Current host tracks reviewed shake animations only. |
| 0x36 | u8x2 | 0x004318CA | scene.center_background | partial_binding | Center the pending background at publication (4318CA, 431FAE..432066); renderer uses image dimensions. |
| 0x37 | u8x2 | 0x00430FA8 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x38 | u8x2 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x39 | u8str | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x3A | u8str | 0x0042E193 | image.load | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x3B | u8str | 0x0042B331 | ui.resource | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x3C | u8str | 0x004306CA | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x3D | u8str | 0x0042E9F0 | scene.character | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x3E | u8str | 0x0042EDE6 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x3F | u8str | 0x0042E85B | scene.character_overlay | partial_binding | Clear queued overlay / face with literal minus; other strings are native no-effect except overlay double-minus restore. |
| 0x40 | u8str | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x41 | u8str | 0x0043033F | ui.action | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x42 | u8str | 0x0043172D | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x43 | u8str | 0x00431853 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x44 | string | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x45 | string | 0x0042D254 | scene.background | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x46 | string | 0x0042D3D0 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x47 | string | 0x00431CC2 | label.named | reviewed_primitive | NUL 结尾的原始字节标签；跳转匹配不使用汉化显示字符串。 |
| 0x48 | string | 0x004300BA | audio.system_voice | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x49 | string | 0x0043011F | audio.voice | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x4A | string | 0x00431CC2 | native.no_effect | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x4B | string | 0x0043084F | script.transfer | partial_binding | Load named script with mode 0, clear several UI/state fields; full transition effects unresolved. |
| 0x4C | string | 0x00430961 | script.transfer_alt | partial_binding | Load named script with mode 1; has different reset effects from mode 0. |
| 0x4D | string | 0x0043082C | script.entry_label | partial_binding | 保存下次跨脚本入口的字节标签，应用后清空。 |
| 0x4E | string | 0x00430226 | media.movie | partial_binding | Native asynchronous completion event; wait.audio absorbs successive opcode 15 masks. |
| 0x4F | string | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x50 | string | 0x004306A4 | jump.named | partial_binding | Resolve label from start PC 3; if missing or empty, leave PC unchanged. Dispatch has mode gates. |
| 0x51 | string | 0x0042B756 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x52 | string | 0x0042B76E | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x53 | string | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x54 | string | 0x0042BFB7 | grid.item_action | partial_binding | Attach original byte-exact action label to current data entry (PC2 41D66D; DD 434530). |
| 0x55 | string | 0x00430571 | ui.confirm | partial_binding | Instantiate original template 12 with indexed caption; yes/no use action slots 0/1, checkbox updates bank93; an enabled remember flag jumps directly to action0. |
| 0x56 | string | 0x00430606 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x57 | string | 0x0042B82B | choice.item | partial_binding | 跳转标签字符串，消费紧随其后的 caption 记录，建立原版按钮。 |
| 0x58 | string | 0x00431CC2 | control.marker | partial_binding | Context-dependent 3-byte record: IF companion uses signed first byte as block ID; sum list terminator. |
| 0x59 | string | 0x00430B11 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x5A | string | 0x0042BAED | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x5B | string | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x5C | string | 0x0043031C | ui.music_end_action | partial_binding | Menu-wait BGM-end callback: PC2 423571 consumes binding; DD 4340D7 retains binding and polls at >500ms until script clears it. |
| 0x5D | string | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x5E | string | 0x0042AE56 | system.version | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x5F | string | 0x0042E97A | scene.face | partial_binding | Clear queued overlay / face with literal minus; other strings are native no-effect except overlay double-minus restore. |
| 0x60 | string | 0x00431701 | audio.voice_register | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x61 | string | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x62 | string | 0x00431C80 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x63 | encoded_string | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x64 | encoded_string | 0x00430B11 | text.speaker | partial_binding | 原始解码字符串设置说话人；不加载资源、不推进下一句。 |
| 0x65 | encoded_string | 0x00430BCB | text.body | partial_binding | 发布场景并呈现正文；文本完成与点击推进是不同等待状态。 |
| 0x66 | encoded_string | 0x00431CC2 | choice.caption | partial_binding | Encoded caption consumed by choice.item; independent dispatch uses the native fallback. |
| 0x67 | encoded_string | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x68 | u16x4 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x69 | u16x4 | 0x0042FCA5 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x6A | u16x4 | 0x0042FD56 | audio.music | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x6B | u16x4 | 0x0042AF68 | audio.sound | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x6C | u16x4 | 0x0042B4DD | host.command_with_string | partial_binding | Consumes the next opcode-plus-NUL-string record as a payload and advances PC past it; passes four words and the string to a UI helper. Payload is not dispatched as a named jump. UI effects unresolved. |
| 0x6D | u16x4 | 0x0042B56E | ui.button_sprite | partial_binding | 6D + 71 register cropped atlas quads for the pending button: reviewed states 99 normal, 1 hover, 2 pressed. Other native modes are partial. |
| 0x6E | u16x4 | 0x0042C07B | dd.grid.common_sprite | partial_binding | Common relative list sprite; state -1 configures scrollbar thumb, positioned from viewport bounds and offset (4346E0,435447). |
| 0x6F | u16x4 | 0x0042C12E | dd.grid.item_sprite | partial_binding | Current entry overlay, relative destination, source XYWH and state (434550). |
| 0x70 | u16x4 | 0x0042B786 | ui.button_text | partial_binding | Consumes the next string record and registers state-specific button text metadata. Active text rendering remains unimplemented. |
| 0x71 | u16x4 | 0x00431CC2 | arguments.extend | reviewed_primitive | 0x52 在相邻主记录后追加四个有符号 i16，不能当成独立业务指令。 |
| 0x72 | u16x4 | 0x0042B706 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x73 | u16x4 | 0x00430678 | ui.confirm_position | partial_binding | Set confirmation origin and caption/remember ID; third operand is ignored by native dispatch. |
| 0x74 | u16x4 | 0x0042D7AB | ui.image_quad | partial_binding | UI origin / texture subrectangle at relative destination; 74 requires 71 extension (width,height,slot flags,unused). |
| 0x75 | u16x4 | 0x0042D828 | ui.text_style | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x76 | u16x4 | 0x0042B6D2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x77 | u16x4 | 0x0042BECE | dd.grid.configure | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x78 | u16x4 | 0x0042BF3E | dd.grid.rect | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x79 | u16x4 | 0x0042BF6E | dd.grid.item | partial_binding | Begin list data entry with ID and source rectangle (436070). |
| 0x7A | u16x4 | 0x0042BFCF | dd.grid.text | partial_binding | State 99 sets text color; other states consume next string as entry caption (4345F0). |
| 0x7B | u16x4 | 0x0042BF1E | dd.grid.sound | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x7C | u16x4 | 0x0042CE53 | variable.update | reviewed_primitive | 四个 i16: bank,index,value,mode；mode 0=set,1=add,2=sub。 |
| 0x7D | u16x4 | 0x0042CEE4 | variable.copy | reviewed_primitive | 四个 i16: dstBank,dstIndex,srcBank,srcIndex。 |
| 0x7E | u16x4 | 0x0042CF14 | variable.sum_list | reviewed_primitive | PC1 后续九字节记录组成求和表，到 companion 0x02 为止。 |
| 0x7F | u16x4 | 0x0042D028 | variable.add_variable | reviewed_primitive | PC2 将源变量值加到目标变量。 |
| 0x80 | u16x4 | 0x0042D0DB | branch.compare | reviewed_primitive | 四个 i16: bank,index,rhs,mode；0..5 为 == != > < >= <=；mode&0x80 时 rhs 是变量引用。紧邻 0x02 提供块编号，失败时扫描块尾。 |
| 0x81 | u16x4 | 0x0042D929 | ui.text | partial_binding | p2=0 leaves the following record for dispatch; otherwise consumes the following encoded-string record and registers UI text. Active text rendering remains unimplemented. |
| 0x82 | u16x4 | 0x0042D90B | ui.origin | partial_binding | UI origin / texture subrectangle at relative destination; 74 requires 71 extension (width,height,slot flags,unused). |
| 0x83 | u16x4 | 0x0042D9CC | scene.background_move | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x84 | u16x4 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x85 | u16x4 | 0x0042F2A1 | scene.character_transform | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x86 | u16x4 | 0x0042F749 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x87 | u16x4 | 0x0042F27F | ui.message_position | partial_binding | Set original message surface origin, 42F27F / 401000. |
| 0x88 | u16x4 | 0x0042FA90 | scene.character_move | partial_binding | 88 linearly moves character p2 to p0,p1 over p3*6 ticks, or 60 when p3=0. p2 bit16 is native ignored path. 42FA90 / 443FF0. |
| 0x89 | u16x4 | 0x0042FB6E | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x8A | u16x4 | 0x0042E152 | ui.palette | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x8B | u16x4 | 0x00431CC2 | native.no_effect | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x8C | u16x4 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x8D | u16x4 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x8E | u16x4 | 0x00430DAA | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x8F | u16x4 | 0x00430DFE | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x90 | u16x4 | 0x004316DF | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x91 | u16x4 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x92 | u16x4 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x93 | u16x4 | 0x0042EEE8 | scene.camera_speed | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x94 | u16x4 | 0x0042EEFA | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x95 | u16x4 | 0x0042EF40 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x96 | u16x4 | 0x0042EFFB | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x97 | u16x4 | 0x0042F0B9 | scene.camera | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x98 | u16x4 | 0x00431767 | scene.image_tone | partial_binding | 98 stores the pending actor color variant (0 unchanged, 1 *, 2 @); 431767, 41FCC0 and 44A1A0/44A2D0. |
| 0x99 | u16x4 | 0x004318DA | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x9A | u16x4 | 0x004319CA | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x9B | u16x4 | 0x00431AA5 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x9C | u16x4 | 0x00431ACF | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x9D | u16x4 | 0x00431CC2 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x9E | u16x4 | 0x0042CB85 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x9F | u16x4 | 0x0042CB21 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0xA0 | u16x4 | 0x0042B8AE | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0xA1 | u16x4 | 0x00431B9C | scene.record_motion | partial_binding | A1 0 records object animation tracks; nonzero ends recording. 431B9C / 442810. Current implementation supports the corpus's recorded shake tracks. |
| 0xA2 | u16x4 | 0x00431BEA | scene.replay_motion | partial_binding | A2 repeats recorded animation tracks, 255 loops. 431BEA / 444C80. |
| 0xA3 | u16x4 | 0x0042FC2C | dd.audio.seek | partial_binding | 43B9A0/42FC2C: set playback cursor by percentage; mode 0 music, 1 sound, otherwise movie audio. |
| 0xA4 | u16x4 | 0x00431C1E | ui.pointer | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0xA5 | u16x4 | 0x00431C40 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0xA6 | u16x4 | 0x0042D520 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0xA7 | u16x4 | 0x00431CAF | dd.grid.columns | partial_binding | Set list scroll stride/column count (434520). |

### dc2dm-9d72

EXE: `DC2DM.EXE`；SHA-256: `7414de26db3e9ce4fec331df7cb517947af7957fbc85abe96c51d6968a9ee0ee`。

分发表 `0x430ef8`，共 113 项。

| Opcode | 参数布局 | Handler VA | 语义 | 状态 | 行为 |
|---|---|---|---|---|---|
| 0x00 | u8x2 | 0x0042CD31 | stream.stop | partial_binding | 终止/归还宿主；不能简单视为 pc++ 的 NOP。 |
| 0x01 | u8x2 | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x02 | u8x2 | 0x00430EBC | control.marker | partial_binding | Context-dependent 3-byte record: IF companion uses signed first byte as block ID; sum list terminator. |
| 0x03 | u8x2 | 0x00430CFF | checkpoint | partial_binding | Combine bytes little-endian into checkpoint index; updates state and reads header u16 metadata. Not itself a jump. |
| 0x04 | u8x2 | 0x00430D7F | dm.history.mark | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x05 | u8x2 | 0x004301CA | dm.audio.music_attenuation | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x06 | u8x2 | 0x0042CF01 | dm.wait.message | partial_binding | 等待文本显示/用户推进；主循环必须归还控制权给平台。 |
| 0x07 | u8x2 | 0x0042DC6C | dm.choice.begin | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x08 | u8x2 | 0x0042DCE9 | dm.wait.mode | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x09 | u8x2 | 0x0042DB8A | dm.ui.wait | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x0A | u8x2 | 0x0042E0CC | dm.ui.grid_wait | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x0B | u8x2 | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x0C | u8x2 | 0x0042DD32 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x0D | u8x2 | 0x0042E136 | dm.scene.transition | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x0E | u8x2 | 0x0042CDC9 | system.exit | partial_binding | Sends WM_DESTROY to the game window and yields; portable host ends the game Activity. |
| 0x0F | u8x2 | 0x0042CE60 | dm.state.reset | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x10 | u8x2 | 0x0042E945 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x11 | u8x2 | 0x0042E91C | dm.audio.fade_music | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x12 | u8x2 | 0x0042D013 | dm.wait.duration | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x13 | u8x2 | 0x00430428 | wait.audio | partial_binding | Native asynchronous completion event; wait.audio absorbs successive opcode 15 masks. |
| 0x14 | u8x2 | 0x0042D803 | dm.ui.button_sound | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x15 | u8x2 | 0x0042F68F | dm.system.persist | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x16 | u8x2 | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x17 | u8x2 | 0x0042D314 | dm.ui.button_template | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x18 | u8x2 | 0x0042D8E8 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x19 | u8x2 | 0x00430DB4 | dm.save.checkpoint | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1A | u8x2 | 0x004308C1 | dm.system.return | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1B | u8x2 | 0x0042CDAC | dm.system.command | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1C | u8x2 | 0x00430511 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x1D | u8x2 | 0x0042EA88 | block.marker_unsigned | partial_binding | DM p0==0 scans using unsigned p1 against a signed marker byte. Conditional companion IDs are signed separately; high-bit unconditional IDs must not be coerced. |
| 0x1E | u8x2 | 0x0042D613 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x1F | u8x2 | 0x0042DD46 | dm.ui.commit | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x20 | u8x2 | 0x0042DC06 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x21 | u8x2 | 0x0042EBA1 | dm.ui.quad_template | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x22 | u8x2 | 0x004304B4 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x23 | u8x2 | 0x00430365 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x24 | u8x2 | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x25 | u8x2 | 0x0042D62C | dm.ui.binding | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x26 | u8x2 | 0x0042E708 | dm.scene.effect | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x27 | u8x2 | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x28 | u8x2 | 0x0042F479 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x29 | u8x2 | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2A | u8str | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2B | u8str | 0x0042FA01 | dm.scene.preload | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x2C | u8str | 0x0042D148 | dm.ui.resource | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x2D | u8str | 0x004308A1 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2E | u8str | 0x0042FBF6 | dm.scene.character | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x2F | u8str | 0x0042FAEE | dm.scene.character_overlay | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x30 | u8str | 0x0042FACE | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x31 | u8str | 0x004305EB | dm.ui.action | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x32 | string | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x33 | string | 0x0042EAAE | dm.scene.background | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x34 | string | 0x00430EBC | label.named | reviewed_primitive | NUL 结尾的原始字节标签；跳转匹配不使用汉化显示字符串。 |
| 0x35 | string | 0x004301E2 | dm.audio.voice | partial_binding | 版本独立的角色开关：DM 使用 94/95，PC1/2 使用 100:60+角色号；保留位置前缀的声像及回看重播参数。PC1/2 是 64 字节旧索引，PC2 可跨 VOICE2.DAT。 |
| 0x36 | string | 0x004302A8 | dm.audio.voice_wait | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x37 | string | 0x004302F0 | dm.audio.voice_prepare | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x38 | string | 0x00430C84 | dm.script.transfer | partial_binding | PC1 跨脚本转移模式 1；不是通用 CPU CALL；系统返回由独立上下文保存实现。 |
| 0x39 | string | 0x00430CEE | dm.script.transfer_alt | partial_binding | PC1 跨脚本转移模式 2；重置语义与模式 1 有别。 |
| 0x3A | string | 0x00430C64 | script.entry_label | partial_binding | 保存下次跨脚本入口的字节标签，应用后清空。 |
| 0x3B | string | 0x0043046F | dm.movie | partial_binding | 43046F 接收文件名；432D17..432D31 用 MOVIE 子目录和 .mpg 扩展名交给 422C20 构建路径。宿主暂停直到影片完成事件。 |
| 0x3C | string | 0x004304DE | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x3D | string | 0x0043088F | jump.named_required | partial_binding | 从代码起点按原生规则扫描标签；兼容层对缺失标签报错。 |
| 0x3E | string | 0x0042D834 | dm.ui.button_voice | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x3F | string | 0x0042D85D | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x40 | string | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x41 | string | 0x0042DF5D | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x42 | string | 0x00430754 | dm.ui.confirm | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x43 | string | 0x0042D918 | choice.item | partial_binding | 跳转标签字符串，消费紧随其后的 caption 记录，建立原版按钮。 |
| 0x44 | string | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x45 | string | 0x00430324 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x46 | string | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x47 | string | 0x0042DB22 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x48 | string | 0x0043055E | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x49 | string | 0x004305CA | dm.audio.completion_action | partial_binding | DC2DM 4305CA copies a music-event label to C22490 and resets C29250 after synchronizing the music event counter. Runtime currently implements the empty cancellation used by MUSIC exit; nonempty labels remain explicit failures. |
| 0x4A | string | 0x0042F456 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x4B | string | 0x0042CD41 | system.version | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x4C | string | 0x0042FB6B | dm.scene.portrait | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x4D | encoded_string | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x4E | encoded_string | 0x00430E18 | text.speaker | partial_binding | 原始解码字符串设置说话人；不加载资源、不推进下一句。 |
| 0x4F | encoded_string | 0x00430E4A | text.body | partial_binding | 发布场景并呈现正文；文本完成与点击推进是不同等待状态。 |
| 0x50 | encoded_string | 0x00430EBC | choice.caption | partial_binding | Encoded caption consumed by choice.item; independent dispatch uses the native fallback. |
| 0x51 | u16x4 | 0x00430EBC | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x52 | u16x4 | 0x00430059 | dm.audio.music | partial_binding | NAMES music 索引；停止值及播放状态由版本宿主处理。 |
| 0x53 | u16x4 | 0x0042CDD8 | dm.audio.sound | partial_binding | 音效索引/参数；不是文本指令。 |
| 0x54 | u16x4 | 0x0042D640 | host.command_with_string | partial_binding | Consumes the next opcode-plus-NUL-string record as a payload and advances PC past it; passes four words and the string to a UI helper. Payload is not dispatched as a named jump. UI effects unresolved. |
| 0x55 | u16x4 | 0x0042D6D9 | dm.ui.button_sprite | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x56 | u16x4 | 0x0042D886 | ui.button_text | partial_binding | Consumes the next string record and registers state-specific button text metadata. Active text rendering remains unimplemented. |
| 0x57 | u16x4 | 0x00430EBC | arguments.extend | reviewed_primitive | 0x52 在相邻主记录后追加四个有符号 i16，不能当成独立业务指令。 |
| 0x58 | u16x4 | 0x0042D7AE | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x59 | u16x4 | 0x004307AF | dm.ui.confirm_position | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x5A | u16x4 | 0x0042EFB9 | dm.ui.image_quad | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x5B | u16x4 | 0x0042F197 | dm.ui.animate | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x5C | u16x4 | 0x0042D78F | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x5D | u16x4 | 0x0042DD98 | dm.ui.grid_begin | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x5E | u16x4 | 0x0042DE83 | dm.ui.grid_rect | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x5F | u16x4 | 0x0042DEC8 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x60 | u16x4 | 0x0042DF8E | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x61 | u16x4 | 0x0042DE64 | dm.ui.grid_sound | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x62 | u16x4 | 0x0042E7B8 | variable.update_indirect | partial_binding | Modes 0/1/2 assign/add/subtract. Mode 3 reads (p2, read(p0,p1)) into native result register 99:255; other modes have no effect. |
| 0x63 | u16x4 | 0x0042E850 | variable.copy | reviewed_primitive | 四个 i16: dstBank,dstIndex,srcBank,srcIndex。 |
| 0x64 | u16x4 | 0x0042E861 | variable.sum_list | reviewed_primitive | PC1 后续九字节记录组成求和表，到 companion 0x02 为止。 |
| 0x65 | u16x4 | 0x0042E8E0 | variable.add_variable | reviewed_primitive | PC2 将源变量值加到目标变量。 |
| 0x66 | u16x4 | 0x0042E9AD | branch.compare | reviewed_primitive | 四个 i16: bank,index,rhs,mode；0..5 为 == != > < >= <=；mode&0x80 时 rhs 是变量引用。紧邻 0x02 提供块编号，失败时扫描块尾。 |
| 0x67 | u16x4 | 0x0042F33E | dm.ui.text | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x68 | u16x4 | 0x0042F2A2 | dm.ui.origin | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x69 | u16x4 | 0x0042F520 | dm.scene.scroll | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x6A | u16x4 | 0x0042E06F | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x6B | u16x4 | 0x0042FDE4 | dm.scene.character_transform | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x6C | u16x4 | 0x0042FDC5 | dm.scene.background_origin | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x6D | u16x4 | 0x0042FE95 | dm.scene.character_move | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x6E | u16x4 | 0x0042F9E2 | dm.ui.palette | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x6F | u16x4 | 0x0042D815 | dm.ui.navigation | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x70 | u16x4 | 0x0042D0B1 | unknown | unknown | 需继续核对参数及宿主副作用。 |

### dc2pc-5769

EXE: `dc2pc.EXE`；SHA-256: `f0b3be701f3c8c94dad143db927d824d398325be57a5da7514556f862f505e46`。

分发表 `0x41f480`，共 105 项。

| Opcode | 参数布局 | Handler VA | 语义 | 状态 | 行为 |
|---|---|---|---|---|---|
| 0x00 | u8x2 | 0x0041C647 | stream.stop | partial_binding | 终止/归还宿主；不能简单视为 pc++ 的 NOP。 |
| 0x01 | u8x2 | 0x0041F434 | native.no_effect | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x02 | u8x2 | 0x0041F434 | control.marker | partial_binding | Context-dependent 3-byte record: IF companion uses signed first byte as block ID; sum list terminator. |
| 0x03 | u8x2 | 0x0041F29E | pc.history | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x04 | u8x2 | 0x0041F31B | pc.checkpoint | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x05 | u8x2 | 0x0041EA07 | dm.audio.music | partial_binding | NAMES music 索引；停止值及播放状态由版本宿主处理。 |
| 0x06 | u8x2 | 0x0041EAF7 | dm.audio.music_attenuation | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x07 | u8x2 | 0x0041C89C | dm.wait.message | partial_binding | 等待文本显示/用户推进；主循环必须归还控制权给平台。 |
| 0x08 | u8x2 | 0x0041D32D | pc.choice.begin | partial_binding | 初始化选项集合，使用脚本先前定义的按钮模板。 |
| 0x09 | u8x2 | 0x0041D38C | pc.choice.wait | partial_binding | 发布选项并暂停；点击根据按钮 action 跳回同一 MES 标签。 |
| 0x0A | u8x2 | 0x0041D28D | pc.ui.wait | partial_binding | a1=1 为 200*a0 毫秒轮询等待；其他模式等待原版按钮输入。 |
| 0x0B | u8x2 | 0x0041D750 | pc.grid.wait | partial_binding | 发布原版存读档网格并等待点击；PC1/2 保留槽 0/1；PC2 读档进入 selsave2，保存进入 selsave。 |
| 0x0C | u8x2 | 0x0041D3BD | pc.grid.begin | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x0D | u8x2 | 0x0041D7AE | pc.transition | partial_binding | 转场类别/时长；部分原效果尚由近似淡入实现。 |
| 0x0E | u8x2 | 0x0041C81D | system.exit | partial_binding | Sends WM_DESTROY to the game window and yields; portable host ends the game Activity. |
| 0x0F | u8x2 | 0x0041C889 | pc.reset | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x10 | u8x2 | 0x0041DD08 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x11 | u8x2 | 0x0041DCDD | dm.audio.fade_music | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x12 | u8x2 | 0x0041C9A1 | pc.wait | partial_binding | a0=0 无限输入等待，否则 200*a0 毫秒；鼠标绑定可跳转标签。 |
| 0x13 | u8x2 | 0x0041ED22 | pc.wait.media_mask | partial_binding | PC2 41ED22 / 4230E0：位 1 等 BGM，2 等语音，4 等音效；位 8 原生计时分支尚未接入。 |
| 0x14 | u8x2 | 0x0041CE81 | dm.ui.button_sound | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x15 | u8x2 | 0x0041D52F | pc.grid.sound | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x16 | u8x2 | 0x0041E5DD | pc.persist | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x17 | u8x2 | 0x0041CAC0 | pc.button_template | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x18 | u8x2 | 0x0041CE45 | pc.fullscreen | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x19 | u8x2 | 0x0041CF42 | pc.sprite_opaque | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1A | u8x2 | 0x0041F331 | pc.save_context | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1B | u8x2 | 0x0041EF75 | dm.system.return | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1C | u8x2 | 0x0041C80E | pc.command | partial_binding | 版本独立的原生服务：1 日历，2 好感排序，6 存档模式，10/11 片尾，PC2 20 地图；8 是 PC1 姓名输入：原14个按钮、字符页、汉字索引、四字姓名和确认模板；40C2E0/40C6D0/40D340/40D400。仍有未接入服务。 |
| 0x1D | u8x2 | 0x0041EE2C | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x1E | u8x2 | 0x0041DE35 | block.marker | reviewed_primitive | a0=0 向前扫描相同有符号块编号；非零直接继续。 |
| 0x1F | u8x2 | 0x0041F434 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x20 | u8x2 | 0x0041D3D1 | dm.ui.commit | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x21 | u8x2 | 0x0041DFE4 | pc.quad_template | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x22 | u8x2 | 0x0041EDA4 | pc.random | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x23 | u8x2 | 0x0041EC59 | pc.voice_group.play | partial_binding | 模式 0 随机播一条并走普通角色开关，1 同时混音，2 清空；组可存十条，但同时播放上限 PC1 七条、PC2 六条（41CC10 通道3..9，427680 通道4..9）。同时播放只受全局开关限制。 |
| 0x24 | u8x2 | 0x0041F434 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x25 | u8x2 | 0x0041CCA1 | dm.ui.binding | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x26 | u8x2 | 0x0041DB86 | pc.effect | partial_binding | 版本独立的效果编号及参数；已绑定纹理，运动/时间近似，不能声称像素一致。 |
| 0x27 | u8x2 | 0x0041E4D1 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x28 | u8x2 | 0x0041F434 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x29 | u8str | 0x0041F434 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2A | u8str | 0x0041CA51 | pc.resource | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x2B | u8str | 0x0041E8A4 | pc.character | partial_binding | u8 槽位 + 资源字符串；槽号和角色位置分离。 |
| 0x2C | u8str | 0x0041E885 | pc.character_overlay | partial_binding | u8 槽位 + 叠加资源字符串。 |
| 0x2D | u8str | 0x0041EF62 | pc.save_caption | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x2E | u8str | 0x0041F226 | pc.script.transfer | partial_binding | PC2 u8 模式+脚本名：4→2，3/5→3，其他→1。 |
| 0x2F | string | 0x0041F434 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x30 | string | 0x0041DE57 | pc.background | partial_binding | 资源字符串；写入背景及预载槽；场景发布由后续指令控制。 |
| 0x31 | string | 0x0041E3AE | pc.image6 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x32 | string | 0x0041E3DB | pc.image7 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x33 | string | 0x0041E408 | pc.image8 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x34 | string | 0x0041E435 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x35 | string | 0x0041F434 | label.named | reviewed_primitive | NUL 结尾的原始字节标签；跳转匹配不使用汉化显示字符串。 |
| 0x36 | string | 0x0041EB0B | dm.audio.voice | partial_binding | 版本独立的角色开关：DM 使用 94/95，PC1/2 使用 100:60+角色号；保留位置前缀的声像及回看重播参数。PC1/2 是 64 字节旧索引，PC2 可跨 VOICE2.DAT。 |
| 0x37 | string | 0x0041F1FD | script.entry_label | partial_binding | 保存下次跨脚本入口的字节标签，应用后清空。 |
| 0x38 | string | 0x0041ED71 | pc.movie | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x39 | string | 0x0041EDCE | pc.external | unimplemented | 原宿主外部程序操作；本兼容层未执行，也没有通用 NOP 兜底。 |
| 0x3A | string | 0x0041EF50 | jump.named_required | partial_binding | 从代码起点按原生规则扫描标签；兼容层对缺失标签报错。 |
| 0x3B | string | 0x0041EEFE | pc.action0 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x3C | string | 0x0041EF27 | pc.action1 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x3D | string | 0x0041CEAD | dm.ui.button_voice | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x3E | string | 0x0041CED4 | pc.button_hover_voice | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x3F | string | 0x0041EE6B | pc.action2 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x40 | string | 0x0041EE94 | pc.action3 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x41 | string | 0x0041F1D9 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x42 | string | 0x0041D66D | grid.item_action | partial_binding | Attach original byte-exact action label to current data entry (PC2 41D66D; DD 434530). |
| 0x43 | string | 0x0041EEBD | pc.confirm | partial_binding | 原版确认模板与 caption；肯定/否定跳转 action2/action3，不能覆写普通点击 action0/action1。 |
| 0x44 | string | 0x0041CF51 | choice.item | partial_binding | 跳转标签字符串，消费紧随其后的 caption 记录，建立原版按钮。 |
| 0x45 | string | 0x0041F434 | choice.caption | partial_binding | Encoded caption consumed by choice.item; independent dispatch uses the native fallback. |
| 0x46 | string | 0x0041EC11 | pc.voice_group.add | partial_binding | 向临时语音组追加资源，最多十条；PC1 414C84 / PC2 41EC11。 |
| 0x47 | string | 0x0041EE03 | ui.music_end_action | partial_binding | Menu-wait BGM-end callback: PC2 423571 consumes binding; DD 4340D7 retains binding and polls at >500ms until script clears it. |
| 0x48 | string | 0x0041D21E | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x49 | string | 0x0041C7B4 | system.version | partial_binding | DC3DD original UI host operation. Static native handler evidence: analysis/system-ui; implementation scope is strict and partial. |
| 0x4A | encoded_string | 0x0041F434 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x4B | encoded_string | 0x0041F392 | text.speaker | partial_binding | 原始解码字符串设置说话人；不加载资源、不推进下一句。 |
| 0x4C | encoded_string | 0x0041F3A9 | text.body | partial_binding | 发布场景并呈现正文；文本完成与点击推进是不同等待状态。 |
| 0x4D | encoded_string | 0x0041F434 | choice.caption | partial_binding | Encoded caption consumed by choice.item; independent dispatch uses the native fallback. |
| 0x4E | u16x4 | 0x0041F434 | native.no_effect | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x4F | u16x4 | 0x0041CCB1 | host.command_with_string | partial_binding | Consumes the next opcode-plus-NUL-string record as a payload and advances PC past it; passes four words and the string to a UI helper. Payload is not dispatched as a named jump. UI effects unresolved. |
| 0x50 | u16x4 | 0x0041CDA9 | pc.button_sprite | partial_binding | 按钮图片参数与静态 quad 不同：src=(a0-a2,a1-a3)，尺寸=(a4,a5)。滑条配置不按图片裁切转换：PC1 state=5/7；PC2 20/21/22/23 对应配置/分段/进度/滑块，24/25/26 是分段图集正常/悬停/按下。 |
| 0x51 | u16x4 | 0x0041CEFB | ui.button_text | partial_binding | Consumes the next string record and registers state-specific button text metadata. Active text rendering remains unimplemented. |
| 0x52 | u16x4 | 0x0041F434 | arguments.extend | reviewed_primitive | 0x52 在相邻主记录后追加四个有符号 i16，不能当成独立业务指令。 |
| 0x53 | u16x4 | 0x0041E102 | pc.quad | partial_binding | 原生图片裁切：dest=(a0+originX,a1+originY)，src=(a2,a3)，尺寸=(a4-a2,a5-a3)；扩展字段含纹理槽及标志。 |
| 0x54 | u16x4 | 0x0041EED9 | pc.confirm_position | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x55 | u16x4 | 0x0041D42D | pc.grid.configure | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x56 | u16x4 | 0x0041D561 | dm.ui.grid_rect | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x57 | u16x4 | 0x0041D5A5 | pc.grid.item | partial_binding | Append native list data entry: ID, reserved, source rectangle, resource selector, reserved (41D5A5). |
| 0x58 | u16x4 | 0x0041D696 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x59 | u16x4 | 0x0041CE90 | dm.ui.navigation | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x5A | u16x4 | 0x0041D540 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x5B | u16x4 | 0x0041C82C | dm.audio.sound | partial_binding | 音效索引/参数；不是文本指令。 |
| 0x5C | u16x4 | 0x0041DC0D | variable.update | reviewed_primitive | 四个 i16: bank,index,value,mode；mode 0=set,1=add,2=sub。 |
| 0x5D | u16x4 | 0x0041DC84 | variable.copy | reviewed_primitive | 四个 i16: dstBank,dstIndex,srcBank,srcIndex。 |
| 0x5E | u16x4 | 0x0041DCAB | variable.add_variable | reviewed_primitive | PC2 将源变量值加到目标变量。 |
| 0x5F | u16x4 | 0x0041DD66 | branch.compare | reviewed_primitive | 四个 i16: bank,index,rhs,mode；0..5 为 == != > < >= <=；mode&0x80 时 rhs 是变量引用。紧邻 0x02 提供块编号，失败时扫描块尾。 |
| 0x60 | u16x4 | 0x0041E2F9 | dm.ui.text | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x61 | u16x4 | 0x0041E2C1 | pc.origin | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x62 | u16x4 | 0x0041E52B | pc.scroll | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x63 | u16x4 | 0x0041D711 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x64 | u16x4 | 0x0041E8C5 | pc.actor_position | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x65 | u16x4 | 0x0041E993 | pc.actor_transform | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x66 | u16x4 | 0x0041E870 | dm.ui.palette | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x67 | u16x4 | 0x0041E493 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x68 | u16x4 | 0x0041E4B2 | unknown | unknown | 需继续核对参数及宿主副作用。 |

### dcpc-2063

EXE: `DCPCDVD.EXE`；SHA-256: `5f84e2fdd6f442509fd0e6a00ff8aa20f5c23b0afc43c811bb615598d4c8b67a`。

分发表 `0x415754`，共 99 项。

| Opcode | 参数布局 | Handler VA | 语义 | 状态 | 行为 |
|---|---|---|---|---|---|
| 0x00 | u8x2 | 0x0041265B | stream.stop | partial_binding | 终止/归还宿主；不能简单视为 pc++ 的 NOP。 |
| 0x01 | u8x2 | 0x0041570F | native.no_effect | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x02 | u8x2 | 0x0041570F | control.marker | partial_binding | Context-dependent 3-byte record: IF companion uses signed first byte as block ID; sum list terminator. |
| 0x03 | u8x2 | 0x00415341 | pc.history | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x04 | u8x2 | 0x00415395 | pc.checkpoint | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x05 | u8x2 | 0x00412826 | pc.sound8 | partial_binding | u8 音效索引和通道参数；255 映射停止值。 |
| 0x06 | u8x2 | 0x00414B64 | dm.audio.music | partial_binding | NAMES music 索引；停止值及播放状态由版本宿主处理。 |
| 0x07 | u8x2 | 0x00412898 | dm.wait.message | partial_binding | 等待文本显示/用户推进；主循环必须归还控制权给平台。 |
| 0x08 | u8x2 | 0x004133AF | pc.choice.begin | partial_binding | 初始化选项集合，使用脚本先前定义的按钮模板。 |
| 0x09 | u8x2 | 0x00413413 | pc.choice.wait | partial_binding | 发布选项并暂停；点击根据按钮 action 跳回同一 MES 标签。 |
| 0x0A | u8x2 | 0x00413334 | pc.ui.wait | partial_binding | a1=1 为 200*a0 毫秒轮询等待；其他模式等待原版按钮输入。 |
| 0x0B | u8x2 | 0x004136E4 | pc.grid.wait | partial_binding | 发布原版存读档网格并等待点击；PC1/2 保留槽 0/1；PC2 读档进入 selsave2，保存进入 selsave。 |
| 0x0C | u8x2 | 0x0041343C | pc.grid.begin | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x0D | u8x2 | 0x0041371B | pc.transition | partial_binding | 转场类别/时长；部分原效果尚由近似淡入实现。 |
| 0x0E | u8x2 | 0x00412817 | system.exit | partial_binding | Sends WM_DESTROY to the game window and yields; portable host ends the game Activity. |
| 0x0F | u8x2 | 0x00412885 | pc.reset | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x10 | u8x2 | 0x00413B39 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x11 | u8x2 | 0x00413B0E | dm.audio.fade_music | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x12 | u8x2 | 0x0041297B | pc.wait | partial_binding | a0=0 无限输入等待，否则 200*a0 毫秒；鼠标绑定可跳转标签。 |
| 0x13 | u8x2 | 0x00412EF6 | dm.ui.button_sound | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x14 | u8x2 | 0x0041354E | pc.grid.sound | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x15 | u8x2 | 0x0041434C | pc.persist | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x16 | u8x2 | 0x0041570F | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x17 | u8x2 | 0x00412AF5 | pc.button_template | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x18 | u8x2 | 0x00412EBB | pc.fullscreen | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x19 | u8x2 | 0x00412FE0 | pc.sprite_opaque | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1A | u8x2 | 0x00414EF5 | pc.prompt_position | partial_binding | PC1 确认框坐标为参数乘 8；随后 pc.confirm 使用原版模板 2。 |
| 0x1B | u8x2 | 0x004153AB | pc.save_context | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1C | u8x2 | 0x00414FDA | dm.system.return | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x1D | u8x2 | 0x00412808 | pc.command | partial_binding | 版本独立的原生服务：1 日历，2 好感排序，6 存档模式，10/11 片尾，PC2 20 地图；8 是 PC1 姓名输入：原14个按钮、字符页、汉字索引、四字姓名和确认模板；40C2E0/40C6D0/40D340/40D400。仍有未接入服务。 |
| 0x1E | u8x2 | 0x00414E48 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x1F | u8x2 | 0x00413C61 | block.marker | reviewed_primitive | a0=0 向前扫描相同有符号块编号；非零直接继续。 |
| 0x20 | u8x2 | 0x00412CB7 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x21 | u8x2 | 0x00413450 | dm.ui.commit | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x22 | u8x2 | 0x00413DCE | pc.quad_template | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x23 | u8x2 | 0x00414DC0 | pc.random | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x24 | u8x2 | 0x00414CCC | pc.voice_group.play | partial_binding | 模式 0 随机播一条并走普通角色开关，1 同时混音，2 清空；组可存十条，但同时播放上限 PC1 七条、PC2 六条（41CC10 通道3..9，427680 通道4..9）。同时播放只受全局开关限制。 |
| 0x25 | u8x2 | 0x00412A1A | pc.resource_payload | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x26 | u8x2 | 0x00414F82 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x27 | u8x2 | 0x0041540C | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x28 | u8x2 | 0x00415521 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x29 | u8x2 | 0x0041570F | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2A | u8x2 | 0x00412D6E | pc.rapid_binding | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x2B | u8x2 | 0x004139D4 | pc.effect | partial_binding | 版本独立的效果编号及参数；已绑定纹理，运动/时间近似，不能声称像素一致。 |
| 0x2C | u8x2 | 0x0041570F | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2D | string | 0x0041570F | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x2E | string | 0x00413C7D | pc.background | partial_binding | 资源字符串；写入背景及预载槽；场景发布由后续指令控制。 |
| 0x2F | string | 0x0041426F | pc.image6 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x30 | string | 0x0041429C | pc.image7 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x31 | string | 0x004142C9 | pc.image8 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x32 | string | 0x0041570F | label.named | reviewed_primitive | NUL 结尾的原始字节标签；跳转匹配不使用汉化显示字符串。 |
| 0x33 | string | 0x00414670 | pc.character_pair | partial_binding | 第一个字符串给角色槽 0；紧邻 0x46 时消费其字符串给角色槽 1。 |
| 0x34 | string | 0x00414578 | pc.portrait_pair | partial_binding | 头像槽 0/1；同样可消费紧邻 0x46 字符串。 |
| 0x35 | string | 0x00414BF1 | dm.audio.voice | partial_binding | 版本独立的角色开关：DM 使用 94/95，PC1/2 使用 100:60+角色号；保留位置前缀的声像及回看重播参数。PC1/2 是 64 字节旧索引，PC2 可跨 VOICE2.DAT。 |
| 0x36 | string | 0x004152C6 | dm.script.transfer | partial_binding | PC1 跨脚本转移模式 1；不是通用 CPU CALL；系统返回由独立上下文保存实现。 |
| 0x37 | string | 0x0041532D | dm.script.transfer_alt | partial_binding | PC1 跨脚本转移模式 2；重置语义与模式 1 有别。 |
| 0x38 | string | 0x0041529D | script.entry_label | partial_binding | 保存下次跨脚本入口的字节标签，应用后清空。 |
| 0x39 | string | 0x00414D8D | pc.movie | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x3A | string | 0x00414DEA | pc.external | unimplemented | 原宿主外部程序操作；本兼容层未执行，也没有通用 NOP 兜底。 |
| 0x3B | string | 0x00414F70 | jump.named_required | partial_binding | 从代码起点按原生规则扫描标签；兼容层对缺失标签报错。 |
| 0x3C | string | 0x00414F1E | pc.action0 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x3D | string | 0x00414F47 | pc.action1 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x3E | string | 0x00412F05 | dm.ui.button_voice | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x3F | string | 0x00412F54 | pc.button_hover_voice | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x40 | string | 0x00414E87 | pc.action2 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x41 | string | 0x00414EB0 | pc.action3 | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x42 | string | 0x00415279 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x43 | string | 0x0041363E | pc.grid.voice | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x44 | string | 0x00414ED9 | pc.confirm | partial_binding | 原版确认模板与 caption；肯定/否定跳转 action2/action3，不能覆写普通点击 action0/action1。 |
| 0x45 | string | 0x00412FEF | choice.item | partial_binding | 跳转标签字符串，消费紧随其后的 caption 记录，建立原版按钮。 |
| 0x46 | string | 0x0041570F | choice.caption | partial_binding | Encoded caption consumed by choice.item; independent dispatch uses the native fallback. |
| 0x47 | string | 0x00414C84 | pc.voice_group.add | partial_binding | 向临时语音组追加资源，最多十条；PC1 414C84 / PC2 41EC11。 |
| 0x48 | string | 0x00414E1F | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x49 | string | 0x004132CA | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x4A | encoded_string | 0x0041570F | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x4B | encoded_string | 0x00415681 | text.speaker | partial_binding | 原始解码字符串设置说话人；不加载资源、不推进下一句。 |
| 0x4C | encoded_string | 0x00415695 | text.body | partial_binding | 发布场景并呈现正文；文本完成与点击推进是不同等待状态。 |
| 0x4D | encoded_string | 0x0041570F | choice.caption | partial_binding | Encoded caption consumed by choice.item; independent dispatch uses the native fallback. |
| 0x4E | u16x4 | 0x0041570F | native.no_effect | partial_binding | DD normal-play host binding; unimplemented submodes fail in Dc3Host. See docs/DC3DD_PLAYBACK.md. |
| 0x4F | u16x4 | 0x00412D7D | host.command_with_string | partial_binding | Consumes the next opcode-plus-NUL-string record as a payload and advances PC past it; passes four words and the string to a UI helper. Payload is not dispatched as a named jump. UI effects unresolved. |
| 0x50 | u16x4 | 0x00412E28 | pc.button_sprite | partial_binding | 按钮图片参数与静态 quad 不同：src=(a0-a2,a1-a3)，尺寸=(a4,a5)。滑条配置不按图片裁切转换：PC1 state=5/7；PC2 20/21/22/23 对应配置/分段/进度/滑块，24/25/26 是分段图集正常/悬停/按下。 |
| 0x51 | u16x4 | 0x00412FA1 | ui.button_text | partial_binding | Consumes the next string record and registers state-specific button text metadata. Active text rendering remains unimplemented. |
| 0x52 | u16x4 | 0x0041570F | arguments.extend | reviewed_primitive | 0x52 在相邻主记录后追加四个有符号 i16，不能当成独立业务指令。 |
| 0x53 | u16x4 | 0x00414031 | pc.quad | partial_binding | 原生图片裁切：dest=(a0+originX,a1+originY)，src=(a2,a3)，尺寸=(a4-a2,a5-a3)；扩展字段含纹理槽及标志。 |
| 0x54 | u16x4 | 0x00413478 | pc.grid.configure | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x55 | u16x4 | 0x0041355F | dm.ui.grid_rect | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x56 | u16x4 | 0x0041358E | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x57 | u16x4 | 0x00413661 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x58 | u16x4 | 0x004139F6 | variable.update | reviewed_primitive | 四个 i16: bank,index,value,mode；mode 0=set,1=add,2=sub。 |
| 0x59 | u16x4 | 0x00413A61 | variable.copy | reviewed_primitive | 四个 i16: dstBank,dstIndex,srcBank,srcIndex。 |
| 0x5A | u16x4 | 0x00413A83 | variable.sum_list | reviewed_primitive | PC1 后续九字节记录组成求和表，到 companion 0x02 为止。 |
| 0x5B | u16x4 | 0x00413B97 | branch.compare | reviewed_primitive | 四个 i16: bank,index,rhs,mode；0..5 为 == != > < >= <=；mode&0x80 时 rhs 是变量引用。紧邻 0x02 提供块编号，失败时扫描块尾。 |
| 0x5C | u16x4 | 0x004141A1 | dm.ui.text | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x5D | u16x4 | 0x0041416D | pc.origin | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x5E | u16x4 | 0x004142F6 | pc.scroll | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x5F | u16x4 | 0x004136C7 | unknown | unknown | 需继续核对参数及宿主副作用。 |
| 0x60 | u16x4 | 0x00414A81 | pc.actor_position | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x61 | u16x4 | 0x00414A9E | pc.actor_transform | partial_binding | 需继续核对参数及宿主副作用。 |
| 0x62 | u16x4 | 0x00414567 | dm.ui.palette | partial_binding | 需继续核对参数及宿主副作用。 |

