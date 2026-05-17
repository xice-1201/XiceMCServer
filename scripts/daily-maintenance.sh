#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${XICEMC_SERVICE_NAME:-xicemc.service}"
REPO_DIR="${XICEMC_REPO_DIR:-/opt/xicemc/repo}"
LOCK_FILE="${XICEMC_MAINTENANCE_LOCK:-/run/xicemc-maintenance.lock}"
WARNING_ENABLED="${XICEMC_MAINTENANCE_WARNING_ENABLED:-true}"

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

send_restart_warning() {
  local time_text="$1"
  if [[ "${WARNING_ENABLED}" != "true" ]]; then
    return 0
  fi
  if ! systemctl is-active --quiet "${SERVICE_NAME}"; then
    echo "Skip restart warning because ${SERVICE_NAME} is not active: ${time_text}"
    return 0
  fi
  if ! python3 "${REPO_DIR}/scripts/lib/rcon-command.py" \
    "xicebroadcast restart-warning time=${time_text}"; then
    echo "Failed to broadcast restart warning: ${time_text}" >&2
  fi
}

echo "Waiting 10 minutes before maintenance..."
send_restart_warning "10分钟"
sleep 300
send_restart_warning "5分钟"
sleep 240
send_restart_warning "1分钟"
sleep 50
send_restart_warning "10秒"
sleep 7
send_restart_warning "3秒"
sleep 3

echo "Stopping ${SERVICE_NAME}..."
systemctl stop "${SERVICE_NAME}"
server_was_stopped="true"

echo "Checking HTTPS certificate renewal window..."
if ! "${REPO_DIR}/scripts/renew-certificates.sh"; then
  echo "HTTPS certificate renewal check failed; continuing maintenance." >&2
fi

echo "Creating backup..."
"${REPO_DIR}/scripts/backup.sh"

echo "Pruning expired backups..."
"${REPO_DIR}/scripts/prune-backups.sh"

echo "Pruning expired audit logs..."
if ! "${REPO_DIR}/scripts/prune-audit-log.sh"; then
  echo "Audit log pruning failed; continuing maintenance." >&2
fi

echo "Deploying latest GitHub content..."
"${REPO_DIR}/scripts/deploy.sh"

echo "Starting ${SERVICE_NAME}..."
systemctl start "${SERVICE_NAME}"
systemctl is-active "${SERVICE_NAME}"

server_was_stopped="false"
echo "Daily maintenance completed."
