"""Static CLI. There is intentionally no run/play command."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from .common import child_output, isolated_output, protected_input_tree, write_json
from .mes import parse, profiles
from .names import parse_names
from .scan import scan, verify
from .semantics import analyze_source


def main() -> int:
    cli = argparse.ArgumentParser(description="D.C.1/2/3 static analysis (never executes games)")
    commands = cli.add_subparsers(dest="command", required=True)
    commands.add_parser("catalog", help="list reference-only framing profiles")
    scan_parser = commands.add_parser("scan", help="inventory, hash, parse and optionally unpack data")
    scan_parser.add_argument("source", type=Path)
    scan_parser.add_argument("--out", required=True, type=Path)
    scan_parser.add_argument("--extract", action="store_true")
    verify_parser = commands.add_parser("verify", help="rehash original and extracted data")
    verify_parser.add_argument("output", type=Path)
    mes_parser = commands.add_parser("mes", help="write structural disassembly, without execution")
    mes_parser.add_argument("source", type=Path)
    mes_parser.add_argument("--out", required=True, type=Path)
    mes_parser.add_argument("--profile")
    semantics_parser = commands.add_parser("semantics", help="annotate a verified MES subset and potential edges; never execute")
    semantics_parser.add_argument("source", type=Path)
    semantics_parser.add_argument("--out", required=True, type=Path)
    semantics_parser.add_argument("--profile", required=True)
    names_parser = commands.add_parser("names", help="inspect the DC2DM/DC3DD NAMES.DAT layout")
    names_parser.add_argument("source", type=Path)
    names_parser.add_argument("--out", required=True, type=Path)
    args = cli.parse_args()
    if args.command == "catalog":
        print(json.dumps(profiles(), ensure_ascii=False, indent=2))
    elif args.command == "scan":
        return int(bool(scan(args.source, args.out, args.extract)["errors"]))
    elif args.command == "verify":
        result = verify(args.output)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return int(not result["passed"])
    elif args.command == "names":
        out = isolated_output(args.out, protected_input_tree(args.source))
        result = parse_names(args.source.read_bytes())
        write_json(child_output(out, "names.json"), result)
        print(json.dumps({"bytes_consumed": result["bytes_consumed"],
                          "tables": {t["category"]: t["count"] for t in result["tables"]}}))
    elif args.command == "mes":
        out = isolated_output(args.out, protected_input_tree(args.source))
        info, tokens = parse(args.source.read_bytes(), args.profile)
        write_json(child_output(out, "header.json"), info)
        with child_output(out, "instructions.jsonl").open("w", encoding="utf-8", newline="\n") as stream:
            for token in tokens:
                stream.write(json.dumps(token.to_dict()) + "\n")
        print(json.dumps({"profile": info["profile"], "instructions": len(tokens), "output": str(out)}))
    elif args.command == "semantics":
        result = analyze_source(args.source, args.out, args.profile)
        print(json.dumps({k: v for k, v in result.items() if k not in ("files", "physical_opcodes", "variable_access_counts")}, ensure_ascii=True))
        return int(bool(result["failures"]))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
