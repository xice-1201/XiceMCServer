#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${XICEMC_SERVICE_NAME:-xicemc.service}"
REPO_DIR="${XICEMC_REPO_DIR:-/opt/xicemc/repo}"
LOCK_FILE="${XICEMC_MAINTENANCE_LOCK:-/run/xicemc-maintenance.lock}"

exec 9>"${LOCK_FILE}"
if ! flock -n 9; then
  echo "Another XiceMC maintenance task is already running." >&2
  exit 1
fi

server_was_stopped="false"

ensure_server_started() {
  local exit_code=$?
  if [[ "${server_was_stopped}" == "true" ]] && ! systemctl is-active --quiet "${SERVICE_NAME}"; then
    echo "Starting ${SERVICE_NAME} after maintenance exit code ${exit_code}..."
    systemctl start "${SERVICE_NAME}" || true
  fi
  exit "${exit_code}"
}
trap ensure_server_started EXIT

echo "Stopping ${SERVICE_NAME}..."
systemctl stop "${SERVICE_NAME}"
server_was_stopped="true"

echo "Creating backup..."
"${REPO_DIR}/scripts/backup.sh"

echo "Pruning expired backups..."
"${REPO_DIR}/scripts/prune-backups.sh"

echo "Deploying latest GitHub content..."
"${REPO_DIR}/scripts/deploy.sh"

echo "Starting ${SERVICE_NAME}..."
systemctl start "${SERVICE_NAME}"
systemctl is-active "${SERVICE_NAME}"

server_was_stopped="false"
echo "Daily maintenance completed."
