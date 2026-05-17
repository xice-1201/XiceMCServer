#!/usr/bin/env bash
set -euo pipefail

RUNTIME_DIR="${XICEMC_RUNTIME_DIR:-/opt/xicemc/runtime}"
BACKUP_DIR="${XICEMC_BACKUP_DIR:-/opt/xicemc/backups}"
SERVICE_NAME="${XICEMC_SERVICE_NAME:-xicemc.service}"
SERVER_USER="${XICEMC_SERVER_USER:-minecraft}"
DRY_RUN="false"

usage() {
  cat <<USAGE
Usage: restore-backup.sh [--dry-run] <backup-file>

Restore a full XiceMCServer runtime backup.
The Minecraft service must be stopped during restore; this script stops it,
moves the current runtime aside, extracts the selected backup, fixes ownership,
and starts the service again.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      BACKUP_FILE="$1"
      shift
      ;;
  esac
done

if [[ -z "${BACKUP_FILE:-}" ]]; then
  usage >&2
  exit 2
fi

if [[ "${BACKUP_FILE}" != /* ]]; then
  BACKUP_FILE="${BACKUP_DIR}/${BACKUP_FILE}"
fi

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "Backup file does not exist: ${BACKUP_FILE}" >&2
  exit 1
fi

if ! tar -tzf "${BACKUP_FILE}" > /dev/null; then
  echo "Backup file is not a readable tar.gz archive: ${BACKUP_FILE}" >&2
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
saved_runtime="${RUNTIME_DIR}.before-restore-${timestamp}"

echo "Backup file: ${BACKUP_FILE}"
echo "Runtime directory: ${RUNTIME_DIR}"
echo "Saved current runtime: ${saved_runtime}"
echo "Service: ${SERVICE_NAME}"

if [[ "${DRY_RUN}" == "true" ]]; then
  echo "[dry-run] Would stop ${SERVICE_NAME}"
  echo "[dry-run] Would move ${RUNTIME_DIR} to ${saved_runtime}"
  echo "[dry-run] Would extract ${BACKUP_FILE} into ${RUNTIME_DIR}"
  echo "[dry-run] Would chown ${RUNTIME_DIR} to ${SERVER_USER}:${SERVER_USER}"
  echo "[dry-run] Would start ${SERVICE_NAME}"
  echo "[dry-run] Archive top-level entries:"
  tar -tzf "${BACKUP_FILE}" | sed -n '1,40p'
  exit 0
fi

if [[ -e "${saved_runtime}" ]]; then
  echo "Refusing to overwrite existing saved runtime: ${saved_runtime}" >&2
  exit 1
fi

echo "Stopping ${SERVICE_NAME}..."
systemctl stop "${SERVICE_NAME}"

if [[ -d "${RUNTIME_DIR}" ]]; then
  mv "${RUNTIME_DIR}" "${saved_runtime}"
fi

mkdir -p "${RUNTIME_DIR}"
tar -xzf "${BACKUP_FILE}" --directory "${RUNTIME_DIR}"
chown -R "${SERVER_USER}:${SERVER_USER}" "${RUNTIME_DIR}"

echo "Starting ${SERVICE_NAME}..."
systemctl start "${SERVICE_NAME}"
systemctl is-active "${SERVICE_NAME}"

echo "Restore completed."
echo "Previous runtime was saved at: ${saved_runtime}"
