#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${XICEMC_RUNTIME_DIR:-$ROOT/server/runtime}"
JAR_PATH="$SERVER_DIR/paper.jar"
TEMPLATE_PROPERTIES="$ROOT/server/config/server.properties.template"
TEMPLATE_EULA="$ROOT/server/config/eula.txt.template"
XMS="${XICEMC_XMS:-1G}"
XMX="${XICEMC_XMX:-3G}"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Paper jar not found. Run scripts/download-paper.sh first." >&2
  exit 1
fi

mkdir -p "$SERVER_DIR"

if [[ ! -f "$SERVER_DIR/server.properties" && -f "$TEMPLATE_PROPERTIES" ]]; then
  cp "$TEMPLATE_PROPERTIES" "$SERVER_DIR/server.properties"
fi

if [[ ! -f "$SERVER_DIR/eula.txt" && -f "$TEMPLATE_EULA" ]]; then
  cp "$TEMPLATE_EULA" "$SERVER_DIR/eula.txt"
  echo "Created eula.txt with eula=false. Read and accept the Minecraft EULA before starting." >&2
  echo "Edit: $SERVER_DIR/eula.txt" >&2
  exit 1
fi

cd "$SERVER_DIR"
exec java -Xms"$XMS" -Xmx"$XMX" -jar paper.jar --nogui
