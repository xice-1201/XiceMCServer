#!/usr/bin/env bash
set -euo pipefail

RUNTIME_DIR="${XICEMC_RUNTIME_DIR:-/opt/xicemc/runtime}"
BACKUP_DIR="${XICEMC_BACKUP_DIR:-/opt/xicemc/backups}"

timestamp="$(date +%Y%m%d-%H%M%S)"
weekday="$(date +%u)"
backup_name="xicemc-backup-${timestamp}-w${weekday}.tar.gz"
backup_path="${BACKUP_DIR}/${backup_name}"

mkdir -p "${BACKUP_DIR}"

if [[ ! -d "${RUNTIME_DIR}" ]]; then
  echo "Runtime directory does not exist: ${RUNTIME_DIR}" >&2
  exit 1
fi

tmp_path="${backup_path}.tmp"
rm -f "${tmp_path}"

tar \
  --create \
  --gzip \
  --file "${tmp_path}" \
  --directory "${RUNTIME_DIR}" \
  --exclude='./cache' \
  --exclude='./libraries' \
  --exclude='./versions' \
  --exclude='./logs' \
  --exclude='./crash-reports' \
  --exclude='./paper.jar' \
  --exclude='./plugins/spark' \
  .

mv "${tmp_path}" "${backup_path}"
echo "Backup created: ${backup_path}"
