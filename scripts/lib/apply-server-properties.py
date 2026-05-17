#!/usr/bin/env python3
from pathlib import Path
import sys

SKIP_KEYS = {
    "rcon.password",
    "server-ip",
}


def parse_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value
    return values


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: apply-server-properties.py TEMPLATE TARGET", file=sys.stderr)
        return 2

    template_path = Path(sys.argv[1])
    target_path = Path(sys.argv[2])
    template = parse_properties(template_path)
    updates = {key: value for key, value in template.items() if key not in SKIP_KEYS}

    existing_lines = target_path.read_text(encoding="utf-8").splitlines() if target_path.exists() else []
    seen: set[str] = set()
    output: list[str] = []

    for line in existing_lines:
        if line and not line.lstrip().startswith("#") and "=" in line:
            key = line.split("=", 1)[0].strip()
            if key in updates:
                output.append(f"{key}={updates[key]}")
                seen.add(key)
                continue
        output.append(line)

    for key, value in updates.items():
        if key not in seen:
            output.append(f"{key}={value}")

    target_path.parent.mkdir(parents=True, exist_ok=True)
    target_path.write_text("\n".join(output) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
