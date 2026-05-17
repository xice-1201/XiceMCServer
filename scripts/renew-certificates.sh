#!/usr/bin/env bash
set -euo pipefail

CERT_NAME="${XICEMC_CERT_NAME:-xicemc.site}"
CERT_PATH="${XICEMC_CERT_PATH:-/etc/letsencrypt/live/${CERT_NAME}/fullchain.pem}"
RENEW_WINDOW_SECONDS="${XICEMC_CERT_RENEW_WINDOW_SECONDS:-604800}"

if [[ ! -f "${CERT_PATH}" ]]; then
  echo "Certificate does not exist, running certbot renew: ${CERT_PATH}"
  certbot renew --quiet --deploy-hook "systemctl reload nginx"
  exit 0
fi

if openssl x509 -checkend "${RENEW_WINDOW_SECONDS}" -noout -in "${CERT_PATH}" >/dev/null; then
  end_date="$(openssl x509 -enddate -noout -in "${CERT_PATH}" | cut -d= -f2-)"
  echo "Certificate ${CERT_NAME} is still valid beyond renewal window. NotAfter=${end_date}"
  exit 0
fi

echo "Certificate ${CERT_NAME} expires within ${RENEW_WINDOW_SECONDS} seconds, renewing..."
certbot renew --quiet --deploy-hook "systemctl reload nginx"
