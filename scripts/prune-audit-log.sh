#!/usr/bin/env bash
set -euo pipefail

export XICE_AUDIT_RETENTION_DAYS="${XICE_AUDIT_RETENTION_DAYS:-3}"

python3 - <<'PY'
import os
import time

import psycopg2

retention_days = int(os.environ.get("XICE_AUDIT_RETENTION_DAYS", "3"))
cutoff_ms = int((time.time() - retention_days * 86400) * 1000)

connection = psycopg2.connect(
    host=os.environ.get("XICE_AUDIT_DB_HOST", "127.0.0.1"),
    port=int(os.environ.get("XICE_AUDIT_DB_PORT", "5432")),
    dbname=os.environ.get("XICE_AUDIT_DB_NAME", "xicemc_audit"),
    user=os.environ.get("XICE_AUDIT_DB_USER", "xicemc_audit"),
    password=os.environ["XICE_AUDIT_DB_PASSWORD"],
    connect_timeout=5,
)

with connection:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT COUNT(*)
            FROM audit_log
            WHERE created_at < %s
              AND action IN ('CONTAINER_ADD', 'CONTAINER_REMOVE')
              AND details IS NOT NULL
            """,
            (cutoff_ms,),
        )
        expired_container_details = cursor.fetchone()[0]
        cursor.execute("DELETE FROM audit_log WHERE created_at < %s", (cutoff_ms,))
        deleted_rows = cursor.rowcount

connection.close()
print(
    f"Deleted {deleted_rows} expired audit rows older than {retention_days} days; "
    f"expired container detail rows cleaned: {expired_container_details}."
)
PY
