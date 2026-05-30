#!/usr/bin/env bash
set -euo pipefail

REPO_URL="${SHORTBRIDGE_REPO_URL:-git@github.com:superkkj/shortbridge.git}"
BRANCH="${SHORTBRIDGE_DEPLOY_BRANCH:-master}"
WORK_ROOT="${SHORTBRIDGE_DEPLOY_WORK_ROOT:-${HOME}/.shortbridge-nas-deploy}"
REPO_DIR="${SHORTBRIDGE_DEPLOY_REPO_DIR:-${WORK_ROOT}/repo}"
STATE_FILE="${SHORTBRIDGE_DEPLOY_STATE_FILE:-${WORK_ROOT}/deployed.sha}"
LOCK_DIR="${SHORTBRIDGE_DEPLOY_LOCK_DIR:-${WORK_ROOT}/lock}"

NAS_HOST="${NAS_HOST:-192.168.31.2}"
NAS_USER="${NAS_USER:-superkkj}"
NAS_PORT="${NAS_PORT:-22}"
NAS_DEPLOY_PATH="${NAS_DEPLOY_PATH:-/volume1/shortbridge}"

PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"
export PATH

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

mkdir -p "${WORK_ROOT}"

if ! mkdir "${LOCK_DIR}" 2>/dev/null; then
  log "deploy already running; skip"
  exit 0
fi
trap 'rmdir "${LOCK_DIR}" 2>/dev/null || true' EXIT

if [ ! -d "${REPO_DIR}/.git" ]; then
  log "clone ${REPO_URL} -> ${REPO_DIR}"
  rm -rf "${REPO_DIR}"
  git clone --quiet --no-tags "${REPO_URL}" "${REPO_DIR}"
fi

git -C "${REPO_DIR}" remote set-url origin "${REPO_URL}"
git -C "${REPO_DIR}" fetch --quiet --prune origin "${BRANCH}"

remote_sha="$(git -C "${REPO_DIR}" rev-parse "origin/${BRANCH}")"
deployed_sha="$(cat "${STATE_FILE}" 2>/dev/null || true)"

if [ "${remote_sha}" = "${deployed_sha}" ]; then
  log "origin/${BRANCH} unchanged (${remote_sha}); skip"
  exit 0
fi

log "deploy origin/${BRANCH} ${remote_sha}"
git -C "${REPO_DIR}" checkout -B "${BRANCH}" "origin/${BRANCH}"
git -C "${REPO_DIR}" reset --hard "origin/${BRANCH}"

gradle -p "${REPO_DIR}" bootJar --no-daemon

jar_path="$(find "${REPO_DIR}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*plain*.jar' | head -1)"
if [ -z "${jar_path}" ]; then
  log "build completed but boot jar was not found"
  exit 1
fi

ssh -p "${NAS_PORT}" "${NAS_USER}@${NAS_HOST}" \
  "mkdir -p '${NAS_DEPLOY_PATH}/logs' '${NAS_DEPLOY_PATH}/storage'"

scp -O -P "${NAS_PORT}" "${REPO_DIR}/scripts/nas/start-blue-green.sh" \
  "${NAS_USER}@${NAS_HOST}:${NAS_DEPLOY_PATH}/start.sh"
scp -O -P "${NAS_PORT}" "${REPO_DIR}/scripts/nas/stop-blue-green.sh" \
  "${NAS_USER}@${NAS_HOST}:${NAS_DEPLOY_PATH}/stop.sh"
ssh -p "${NAS_PORT}" "${NAS_USER}@${NAS_HOST}" \
  "chmod +x '${NAS_DEPLOY_PATH}/start.sh' '${NAS_DEPLOY_PATH}/stop.sh'"

scp -O -P "${NAS_PORT}" "${jar_path}" \
  "${NAS_USER}@${NAS_HOST}:${NAS_DEPLOY_PATH}/app.jar.next"

ssh -p "${NAS_PORT}" "${NAS_USER}@${NAS_HOST}" \
  "set -e; mv '${NAS_DEPLOY_PATH}/app.jar.next' '${NAS_DEPLOY_PATH}/app.jar'; '${NAS_DEPLOY_PATH}/start.sh'; sleep 5; curl -fsS http://127.0.0.1:8080/actuator/health"

printf '%s\n' "${remote_sha}" > "${STATE_FILE}"
log "deploy complete ${remote_sha}"
