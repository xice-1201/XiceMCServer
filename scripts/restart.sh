#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${XICEMC_SERVICE_NAME:-xicemc.service}"

systemctl restart "${SERVICE_NAME}"
systemctl is-active "${SERVICE_NAME}"
