#!/usr/bin/env bash
set -euo pipefail

NAS_HOST="${NAS_HOST:-192.168.31.2}"
NAS_USER="${NAS_USER:-superkkj}"
NAS_PORT="${NAS_PORT:-22}"
NAS_DEPLOY_PATH="${NAS_DEPLOY_PATH:-/volume1/shortbridge}"

cd "$(dirname "$0")/.."

gradle bootJar

JAR="$(ls build/libs/*.jar | head -1)"
ssh -p "${NAS_PORT}" "${NAS_USER}@${NAS_HOST}" "mkdir -p '${NAS_DEPLOY_PATH}/logs' '${NAS_DEPLOY_PATH}/storage'"
scp -O -P "${NAS_PORT}" "${JAR}" "${NAS_USER}@${NAS_HOST}:${NAS_DEPLOY_PATH}/app.jar.next"
ssh -p "${NAS_PORT}" "${NAS_USER}@${NAS_HOST}" \
  "set -e; mv '${NAS_DEPLOY_PATH}/app.jar.next' '${NAS_DEPLOY_PATH}/app.jar'; '${NAS_DEPLOY_PATH}/start.sh'; sleep 5; curl -fsS http://127.0.0.1:8080/actuator/health"
