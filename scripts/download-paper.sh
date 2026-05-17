#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/server/core/paper.json"
RUNTIME_DIR="${XICEMC_RUNTIME_DIR:-$ROOT/server/runtime}"
JAR_PATH="$RUNTIME_DIR/paper.jar"
TMP_JAR_PATH="$RUNTIME_DIR/paper.jar.tmp"

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

if [[ -f "$JAR_PATH" ]]; then
  ACTUAL_SHA256="$(sha256sum "$JAR_PATH" | awk '{print $1}')"
  if [[ "$ACTUAL_SHA256" == "$EXPECTED_SHA256" ]]; then
    echo "Paper already present and verified:"
    echo "  $JAR_PATH"
    exit 0
  fi
fi

echo "Downloading Paper $MC_VERSION build $PAPER_BUILD..."
rm -f "$TMP_JAR_PATH"
curl -fL "$DOWNLOAD_URL" -o "$TMP_JAR_PATH"

ACTUAL_SHA256="$(sha256sum "$TMP_JAR_PATH" | awk '{print $1}')"
if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
  rm -f "$TMP_JAR_PATH"
  echo "SHA256 mismatch. Expected $EXPECTED_SHA256, got $ACTUAL_SHA256" >&2
  exit 1
fi

mv "$TMP_JAR_PATH" "$JAR_PATH"
echo "Paper downloaded and verified:"
echo "  $JAR_PATH"
