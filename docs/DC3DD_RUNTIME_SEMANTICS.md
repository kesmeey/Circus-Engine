# DC3DD 运行时绑定（0.3）

基础字节、参数、变量与跳转语义见 MES_SEMANTICS.md；此表补充实际 Android 播放使用的绑定。
绑定由 tools/generate_runtime_profiles.py 生成到 Profiles.java；每个游戏使用独立 Profile，禁止按数值差平移其他作品的 opcode。

## 分层与复用

- Profile 负责版本、framing、opcode → semantic；MesVm 负责通用控制流、快照、带代次的输入和媒体回调。
- Dc3Host / Dc3State 负责 DD 的资源编号、图层、等待与特殊变量规则；不是 DC1/2 的默认宿主。
- Dc3Scene、Dc3Camera、Dc3Transition、Dc3Petals、Dc3Performance、Dc3Credits 保存逻辑状态；Android 仅提供绘制与播放。
- 文件、封包和图像解码通过 GameResources.Storage 读取。PC 目录与 Android SAF 共用同一核心。

## 数值绑定

下表表示存在语义绑定，不表示支持该指令的所有子模式。未实现子模式由宿主严格拒绝。

| DD opcode | 语义 |
| --- | --- |
| `00` | `stream.stop` |
| `02` | `control.marker` |
| `03` | `checkpoint` |
| `04` | `history.range` |
| `05` | `audio.restore_volume` |
| `06` | `wait.message` |
| `09` | `choice.begin` |
| `0A` | `choice.show` |
| `0F` | `scene.transition` |
| `11` | `state.reset_scenario` |
| `13` | `audio.music_stop` |
| `14` | `wait.duration` |
| `15` | `wait.audio` |
| `17` | `system.persist` |
| `1B` | `scene.checkpoint` |
| `1D` | `system.command` |
| `1F` | `block.marker` |
| `24` | `state.random` |
| `27` | `scene.effect` |
| `35` | `scene.stop_motion` |
| `36` | `scene.center_background` |
| `3A` | `image.load` |
| `3D` | `scene.character` |
| `3F` | `scene.character_overlay` |
| `45` | `scene.background` |
| `47` | `label.named` |
| `49` | `audio.voice` |
| `4A` | `native.no_effect` |
| `4B` | `script.transfer` |
| `4C` | `script.transfer_alt` |
| `4D` | `script.entry_label` |
| `4E` | `media.movie` |
| `50` | `jump.named` |
| `57` | `choice.item` |
| `5F` | `scene.face` |
| `64` | `text.speaker` |
| `65` | `text.body` |
| `6A` | `audio.music` |
| `6B` | `audio.sound` |
| `6C` | `host.command_with_string` |
| `71` | `arguments.extend` |
| `74` | `ui.image_quad` |
| `7C` | `variable.update` |
| `7D` | `variable.copy` |
| `7E` | `variable.sum_list` |
| `7F` | `variable.add_variable` |
| `80` | `branch.compare` |
| `82` | `ui.origin` |
| `83` | `scene.background_move` |
| `85` | `scene.character_transform` |
| `88` | `scene.character_move` |
| `93` | `scene.camera_speed` |
| `97` | `scene.camera` |
| `98` | `scene.image_tone` |
| `A1` | `scene.record_motion` |
| `A2` | `scene.replay_motion` |

## 本轮确认的边界

- `427620` / `4240C0`：跨脚本释放剧情图层；逻辑槽 36..42 的原生 UI 资源保留。结束进行中的移动，普通变量保持；选择结果重置为 -1。
- `44C420..44C436` / `44C7C2..44C7D8`：24 位绘制跳过 RGB FF00FF。Android 普通图层使用透明色，明确不透明的矩形使用原像素。
- `41E40D..41E431`：粒子图集每行 28 像素；图集底行和屏幕边缘在绘制时裁剪。
- 原始剧情 MES 中的普通变量 9:87 / 9:91 没有直接访问；已定位的引用位于 MAIN、LOAD、SAVE、CONFIG 和鉴赏菜单。它们不应仅因原进程残留为 1 就作为所有新剧情的通用初始值。
- 本地 NAMES 的 400 条音乐附加数值均为 `(0,0,0,3)`；不是已确认的非零循环区间。当前播放器按文件循环，字段完整解释仍单独保留。

## 验证契约

- BranchAudit 从每个路线的默认状态，在每个可到达选择点保存完整状态，对每个选项分别继续执行，直到返回标题。
- 该检查穷举当前固定随机初值下的可到达选择序列；不穷举所有随机种子、任意修改变量或原生回放菜单状态。
- 音视频完成回调在流程审计中模拟，媒体解码和 Android 实时播放分别测试。
- 快照只保存在 build 中，包含私有游戏数据，不进入源码分发包。

剩余设备和原版对照边界见 DC3DD_PLAYBACK.md。
