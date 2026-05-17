#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/server/core/paper.json"
RUNTIME_DIR="$ROOT/server/runtime"
JAR_PATH="$RUNTIME_DIR/paper.jar"

if [[ ! -f "$MANIFEST" ]]; then
  echo "Paper manifest not found: $MANIFEST" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to read $MANIFEST" >&2
  exit 1
fi

DOWNLOAD_URL="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["downloadUrl"])' "$MANIFEST")"
EXPECTED_SHA256="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["sha256"])' "$MANIFEST")"
MC_VERSION="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["minecraftVersion"])' "$MANIFEST")"
PAPER_BUILD="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["paperBuild"])' "$MANIFEST")"

mkdir -p "$RUNTIME_DIR"
echo "Downloading Paper $MC_VERSION build $PAPER_BUILD..."
curl -fL "$DOWNLOAD_URL" -o "$JAR_PATH"

ACTUAL_SHA256="$(sha256sum "$JAR_PATH" | awk '{print $1}')"
if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
  rm -f "$JAR_PATH"
  echo "SHA256 mismatch. Expected $EXPECTED_SHA256, got $ACTUAL_SHA256" >&2
  exit 1
fi

echo "Paper downloaded and verified:"
echo "  $JAR_PATH"
