"""Build a repository-local status report from completed static artifacts."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
labels = {"dc3dd": "D.C.III DreamDays", "dc2dm": "D.C.II Dearest Marriage", "ktlep": "ことり Love Ex P（仅脚本）"}
summaries = {}
verifications = {}
for key in labels:
    summaries[key] = json.loads((root / "analysis" / key / "summary.json").read_text(encoding="utf-8"))
    verifications[key] = json.loads((root / "analysis" / key / "verification.json").read_text(encoding="utf-8"))
profiles = json.loads((root / "profiles/dc123.json").read_text(encoding="utf-8"))["profiles"]
seen = {p for s in summaries.values() for p in s["profiles"]}
catalogs = {key: json.loads((root / "analysis" / key / "names/names.json").read_text(encoding="utf-8"))
            for key in ("dc3dd", "dc2dm")}
pe = json.loads((root / "analysis/executables/pe-headers-imports.json").read_text(encoding="utf-8"))
lines = ["# 第一轮静态拆包与分析", "", "范围：D.C.1／2／3；不包含 D.C.4／5。",
         "本轮没有运行游戏、播放器、兼容层或 Android 应用。EXE 仅作为数据读取 PE 头和导入表。", "",
         "## 实际样本", "", "| 样本 | MES profile | 脚本数 | 指令实例 | 不同 opcode | 封包数 | 解包条目 |",
         "|---|---|---:|---:|---:|---:|---:|"]
for key, label in labels.items():
    s = summaries[key]
    lines.append(f"| {label} | {', '.join(s['profiles'])} | {s['script_files_parsed']} | {s['instruction_count']:,} | {s['distinct_opcodes']} | {s['archive_count']} | {s['archive_entries']:,} |")
lines += ["", "MES 数据仅完成结构解析。这里的 opcode 数量不是已实现指令数量。", "",
          "解包载荷总大小：{:,.0f} 字节；保留原始压缩数据，没有生成最终图片或播放声音。".format(sum(s['extracted_bytes'] for s in summaries.values())),
          "KTLEP 是已有的 DC1 相关作品脚本样本，不能代替 DC1 本篇各发行版验证。", "",
          "## 校验", "", "| 样本 | 原文件内容哈希复核 | 解包文件哈希复核 | 扫描错误 | 结果 |", "|---|---:|---:|---:|---|"]
for key, label in labels.items():
    v = verifications[key]
    lines.append(f"| {label} | {v['source_files_verified']} | {v['extracted_files_verified']:,} | {v['scan_error_count']} | {'通过' if v['passed'] else '失败'} |")
lines += ["", "原文件的复核以 inventory.json 中扫描时的 SHA-256 为基准；不包含存档与备份目录。",
          "每个拆包条目在复制时计算源切片哈希，随后重读输出核对，再由 verify 命令独立重读复核。", "",
          "## 资源格式", "", "| 样本 | 资源格式（散文件与包内条目） | XPCM 模式分布 |", "|---|---|---|"]
for key in ("dc3dd", "dc2dm"):
    s = summaries[key]
    lines.append(f"| {labels[key]} | {s['resource_formats']} | {s['xpcm_modes']} |")
lines += ["", "XPCM 模式 5 的 Ogg 标识和载荷长度已校验；模式 0／1／3 的元数据和长度已检查。",
          "不能仅处理 Ogg：两作都存在其他音频模式，DC2DM 还包含模式 1。尚未验证音频解码结果。",
          "本批 CRXG 的 compression 字段均为 3；像素数据仍未解码。", "",
          "## CRM 依赖与特殊情况", "",
          "- DC3DD 有 18,563 条 CRXD 记录，DC2DM 有 8,932 条。",
          "- 已检查这些记录的基础图偏移，以及存在的 CRXJ 差分偏移；均能在各自 CRM 索引中找到目标。",
          "- 这只证明引用目标存在，不证明差分链无环或像素合成正确。",
          "- DC3DD 的 GRP/BST/MOT_D.CRM 有 90 个索引槽，其中 88、89 两槽全零；保留槽位记录，提取 88 个有效条目。",
          "- 两个 DC3DD MES 的 offset1 候选位置碰巧出现 0x016C。使用全部 214 个脚本候选的交集确认 dc3dd-a5a8，避免按单个标识误判。", "",
          "## NAMES.DAT", "", "| 样本 | 系统脚本 | 音乐 | 音效 | 图像 | 剧情脚本 |", "|---|---:|---:|---:|---:|---:|"]
for key, catalog in catalogs.items():
    lines.append(f"| {labels[key]} | " + " | ".join(str(t['count']) for t in catalog['tables']) + " |")
lines += ["", "五张表均以 u32 条目数开头，记录长度依次为 32、48、36、32、32 字节，读取后恰好到文件结尾。",
          "类别依据目录中的资源路径推断。音乐记录额外 16 字节、音效记录额外 4 字节已原样保留，具体含义尚未证明。",
          "目录中存在预留条目，条目数不等于安装目录中实际音频文件数。",
          "该布局目前只在这两份样本验证；独立分析结果保存在 names/names.json。", "",
          "## 汉化", "",
          "DC3DD 的字符替换表有 1,154 对，DC2DM 有 1,065 对；两份表的源目标长度相同，源字符未重复。",
          "只做了检测，没有应用替换或改写文本。原生文字层需要保留这类配置。", "",
          "## Windows 实现差异（仅 PE 静态证据）", ""]
for item in pe['files']:
    dlls = [i['dll'] for i in item['imports']]
    lines.append(f"- {Path(item['source']).name}：机器类型 {item['machine']}；导入库包括 `{', '.join(dlls)}`。")
lines += ["", "两者都是 PE32 x86（0x014C）。DC3DD 导入 Direct3D 9／D3DX9，DC2DM 导入 DirectDraw。",
          "导入表只能证明静态依赖，不能单独证明运行时实际采用了哪条绘制路径。", "",
          "## 版本配置覆盖", "",
          "下表是格式资料清单，不是可玩兼容名单。共 26 份配置，3 份得到本地脚本验证，其余只有参考资料。",
          "没有列入的发行版仍需补资料；不宣称覆盖全部 DC1／2／3。", "",
          "| Profile | 布局 | 状态 |", "|---|---|---|"]
for p in profiles:
    lines.append(f"| {p['id']} | {p['layout']} | {'已验证脚本结构' if p['id'] in seen else '仅参考格式，缺少本地验证'} |")
lines += ["", "## 下一步（仍然只做静态分析）", "",
          "1. 定位 DC3DD 的 MES 读取与分发函数，把每条指令的语义、参数和证据地址记录下来。",
          "2. 核对标签偏移、变量与表达式，再与 DC2DM／KTLEP 对照，建立版本语义适配表。",
          "3. 从 NAMES.DAT 入手核对资源编号的解析规则；分析 CRX 压缩和差分链。",
          "4. 继续补充 DC1 本篇和其他 DC2／DC3 发行版的静态样本。",
          "5. 在指令语义得到证据前，不把空实现或顺序显示文本当成游戏运行成功。", ""]
report = root / "docs/STATIC_ANALYSIS.md"
report.parent.mkdir(exist_ok=True)
report.write_text("\n".join(lines), encoding="utf-8")
matrix = {"schema_version": 1, "scope": "DC1/DC2/DC3", "runtime_implemented": False,
          "profiles": [{**p, "local_framing_verified": p['id'] in seen} for p in profiles],
          "samples": summaries, "verification": verifications}
(root / "analysis/compatibility-matrix.json").write_text(json.dumps(matrix, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(report)
