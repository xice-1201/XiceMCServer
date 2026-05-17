#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${XICEMC_BACKUP_DIR:-/opt/xicemc/backups}"
DAILY_RETENTION_DAYS="${XICEMC_DAILY_RETENTION_DAYS:-3}"
MONDAY_RETENTION_DAYS="${XICEMC_MONDAY_RETENTION_DAYS:-21}"

if [[ ! -d "${BACKUP_DIR}" ]]; then
  echo "Backup directory does not exist, nothing to prune: ${BACKUP_DIR}"
  exit 0
fi

now_epoch="$(date +%s)"

find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'xicemc-backup-*-w*.tar.gz' -print0 |
while IFS= read -r -d '' backup_file; do
  file_name="$(basename "${backup_file}")"
  if [[ ! "${file_name}" =~ ^xicemc-backup-([0-9]{8})-([0-9]{6})-w([1-7])\.tar\.gz$ ]]; then
    echo "Skip unrecognized backup name: ${backup_file}"
    continue
  fi

  date_part="${BASH_REMATCH[1]}"
  time_part="${BASH_REMATCH[2]}"
  weekday="${BASH_REMATCH[3]}"
  backup_epoch="$(date -d "${date_part:0:4}-${date_part:4:2}-${date_part:6:2} ${time_part:0:2}:${time_part:2:2}:${time_part:4:2}" +%s)"
  age_days="$(( (now_epoch - backup_epoch) / 86400 ))"

  retention_days="${DAILY_RETENTION_DAYS}"
  if [[ "${weekday}" == "1" ]]; then
    retention_days="${MONDAY_RETENTION_DAYS}"
  fi

  if (( age_days >= retention_days )); then
    rm -f -- "${backup_file}"
    echo "Deleted expired backup: ${backup_file}"
  fi
done
