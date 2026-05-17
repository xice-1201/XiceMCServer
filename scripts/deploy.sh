#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="${XICEMC_REPO_DIR:-/opt/xicemc/repo}"
RUNTIME_DIR="${XICEMC_RUNTIME_DIR:-/opt/xicemc/runtime}"
SERVER_USER="${XICEMC_SERVER_USER:-minecraft}"
SERVER_HOME="$(getent passwd "${SERVER_USER}" | cut -d: -f6)"
BUILD_JAVA_HOME="${XICEMC_BUILD_JAVA_HOME:-/usr/lib/jvm/java-21-konajdk-21.0.10-1.oc9}"

run_as_server_user() {
  if [[ "$(id -un)" == "${SERVER_USER}" ]]; then
    "$@"
  else
    runuser -u "${SERVER_USER}" -- env HOME="${SERVER_HOME}" USER="${SERVER_USER}" LOGNAME="${SERVER_USER}" "$@"
  fi
}

if [[ ! -d "${REPO_DIR}/.git" ]]; then
  echo "Repository directory is not a Git checkout: ${REPO_DIR}" >&2
  exit 1
fi

cd "${REPO_DIR}"

echo "Updating repository from GitHub..."
run_as_server_user git -C "${REPO_DIR}" fetch origin main
run_as_server_user git -C "${REPO_DIR}" pull --ff-only origin main

echo "Applying server.properties template..."
python3 "${REPO_DIR}/scripts/lib/apply-server-properties.py" \
  "${REPO_DIR}/server/config/server.properties.template" \
  "${RUNTIME_DIR}/server.properties"

echo "Applying Paper configuration overrides..."
python3 "${REPO_DIR}/scripts/lib/apply-paper-overrides.py" \
  "${REPO_DIR}/server/config" \
  "${RUNTIME_DIR}/config"

echo "Ensuring Paper core is present..."
XICEMC_RUNTIME_DIR="${RUNTIME_DIR}" "${REPO_DIR}/scripts/download-paper.sh"

if [[ -f "${REPO_DIR}/server/assets/server-icon.png" ]]; then
  echo "Installing server icon..."
  install -o "${SERVER_USER}" -g "${SERVER_USER}" -m 0644 \
    "${REPO_DIR}/server/assets/server-icon.png" \
    "${RUNTIME_DIR}/server-icon.png"
fi

if compgen -G "${REPO_DIR}/plugins/*/pom.xml" > /dev/null; then
  if ! command -v mvn > /dev/null 2>&1; then
    echo "Maven is required to build server plugins, but mvn was not found." >&2
    exit 1
  fi

  mkdir -p "${RUNTIME_DIR}/plugins"
  for pom in "${REPO_DIR}"/plugins/*/pom.xml; do
    plugin_dir="$(dirname "${pom}")"
    plugin_name="$(basename "${plugin_dir}")"
    echo "Building plugin: ${plugin_name}"
    if [[ -d "${BUILD_JAVA_HOME}" ]]; then
      run_as_server_user env JAVA_HOME="${BUILD_JAVA_HOME}" PATH="${BUILD_JAVA_HOME}/bin:${PATH}" mvn -q -f "${pom}" clean package
    else
      run_as_server_user mvn -q -f "${pom}" clean package
    fi
    jar_path="$(find "${plugin_dir}/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)"
    if [[ -z "${jar_path}" ]]; then
      echo "No plugin jar found for ${plugin_name}" >&2
      exit 1
    fi
    install -o "${SERVER_USER}" -g "${SERVER_USER}" -m 0644 "${jar_path}" "${RUNTIME_DIR}/plugins/${plugin_name}.jar"
    if [[ -f "${plugin_dir}/src/main/resources/config.yml" ]]; then
      install -d -o "${SERVER_USER}" -g "${SERVER_USER}" -m 0755 "${RUNTIME_DIR}/plugins/${plugin_name}"
      install -o "${SERVER_USER}" -g "${SERVER_USER}" -m 0644 \
        "${plugin_dir}/src/main/resources/config.yml" \
        "${RUNTIME_DIR}/plugins/${plugin_name}/config.yml"
    fi
  done
fi

chown -R "${SERVER_USER}:${SERVER_USER}" "${RUNTIME_DIR}"
echo "Deployment completed."
