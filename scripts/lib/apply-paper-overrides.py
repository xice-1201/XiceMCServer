#!/usr/bin/env python3
from pathlib import Path
import sys


def replace_line(text: str, key: str, value: str) -> str:
    lines = text.splitlines()
    changed = False
    output: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith(f"{key}:"):
            indent = line[: len(line) - len(line.lstrip(" "))]
            output.append(f"{indent}{key}: {value}")
            changed = True
        else:
            output.append(line)
    if not changed:
        output.append(f"{key}: {value}")
    return "\n".join(output) + "\n"


def set_nested_scalar(text: str, path: list[str], value: str) -> str:
    lines = text.splitlines()
    stack: list[tuple[int, str]] = []
    output: list[str] = []
    changed = False

    for line in lines:
        stripped = line.strip()
        indent = len(line) - len(line.lstrip(" "))
        if stripped and not stripped.startswith("#"):
            while stack and indent <= stack[-1][0]:
                stack.pop()
            key = stripped.split(":", 1)[0]
            current_path = [item[1] for item in stack] + [key]
            if current_path == path:
                output.append(" " * indent + f"{key}: {value}")
                changed = True
                continue
            if stripped.endswith(":"):
                stack.append((indent, key))
        output.append(line)

    if not changed:
        indent = ""
        for key in path[:-1]:
            output.append(f"{indent}{key}:")
            indent += "  "
        output.append(f"{indent}{path[-1]}: {value}")

    return "\n".join(output) + "\n"


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: apply-paper-overrides.py TEMPLATE_DIR TARGET_CONFIG_DIR", file=sys.stderr)
        return 2

    template_dir = Path(sys.argv[1])
    target_dir = Path(sys.argv[2])

    global_target = target_dir / "paper-global.yml"
    if (template_dir / "paper-global.yml.template").exists() and global_target.exists():
        text = global_target.read_text(encoding="utf-8")
        text = replace_line(text, "allow-permanent-block-break-exploits", "true")
        text = replace_line(text, "allow-piston-duplication", "true")
        global_target.write_text(text, encoding="utf-8")

    world_target = target_dir / "paper-world-defaults.yml"
    if (template_dir / "paper-world-defaults.yml.template").exists() and world_target.exists():
        text = world_target.read_text(encoding="utf-8")
        text = set_nested_scalar(text, ["anticheat", "anti-xray", "enabled"], "true")
        world_target.write_text(text, encoding="utf-8")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
