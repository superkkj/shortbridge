#!/usr/bin/env bash
set -euo pipefail

INTERVAL_SECONDS="${SHORTBRIDGE_DEPLOY_INTERVAL_SECONDS:-60}"
ONCE_SCRIPT="${SHORTBRIDGE_DEPLOY_ONCE_SCRIPT:-${HOME}/.shortbridge-nas-deploy/bin/watch-master-deploy.sh}"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

while true; do
  if "${ONCE_SCRIPT}"; then
    :
  else
    status="$?"
    log "deploy check failed with exit ${status}"
  fi
  sleep "${INTERVAL_SECONDS}"
done
