#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    fail "docker compose or docker-compose is required"
  fi
}

fetch() {
  if command -v curl >/dev/null 2>&1; then
    curl -fsS "$1"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O- "$1"
  else
    return 1
  fi
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd "${SCRIPT_DIR}/.." && pwd)}"

PROJECT="${PROJECT:-shortbridge-nas}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-shortbridge-nas}"
COMPOSE_FILE="${COMPOSE_FILE:-docker/nas/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-.env.nas}"
NETWORK="${SHORTBRIDGE_NETWORK:-shortbridge-nas-net}"
TRAEFIK_NAME="${TRAEFIK_NAME:-shortbridge-traefik}"
TRAEFIK_API_PORT="${TRAEFIK_API_PORT:-8081}"

TAG="${1:-${TAG:-latest}}"
REGISTRY="${REGISTRY:-ghcr.io}"
IMAGE_NAME="${IMAGE_NAME:-superkkj/shortbridge}"
IMAGE="${REGISTRY}/${IMAGE_NAME}:${TAG}"

APP_PORT="${APP_PORT:-8080}"
HEALTHCHECK_URL="${HEALTHCHECK_URL:-http://localhost:${APP_PORT}/actuator/health}"
MAX_WAIT="${MAX_WAIT:-180}"
CHECK_INTERVAL="${CHECK_INTERVAL:-5}"
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"
STORAGE_DIR="${STORAGE_DIR:-${APP_DIR}/storage}"
LOG_DIR="${LOG_DIR:-${APP_DIR}/logs}"
PRUNE_IMAGES="${PRUNE_IMAGES:-true}"

cd "${APP_DIR}"

[ -f "${ENV_FILE}" ] || fail "${ENV_FILE} is missing. Copy docker/nas/.env.nas.example to ${ENV_FILE} on the NAS and fill secrets."
[ -f "${COMPOSE_FILE}" ] || fail "${COMPOSE_FILE} is missing"

set -a
# shellcheck disable=SC1090
. "${ENV_FILE}"
set +a

NETWORK="${SHORTBRIDGE_NETWORK:-${NETWORK}}"
TRAEFIK_API_PORT="${TRAEFIK_API_PORT:-${TRAEFIK_API_PORT}}"
APP_PORT="${APP_PORT:-${APP_PORT}}"
HEALTHCHECK_URL="${HEALTHCHECK_URL:-http://localhost:${APP_PORT}/actuator/health}"

mkdir -p "${STORAGE_DIR}" "${LOG_DIR}"

if [ -n "${GHCR_TOKEN:-}" ]; then
  log "Logging in to ${REGISTRY}"
  printf '%s' "${GHCR_TOKEN}" | docker login "${REGISTRY}" -u "${GHCR_USER:-token}" --password-stdin >/dev/null
fi

log "Ensuring Traefik and RabbitMQ are running"
compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" up -d traefik rabbitmq

if ! docker network inspect "${NETWORK}" >/dev/null 2>&1; then
  fail "Docker network ${NETWORK} was not created"
fi

CURRENT_NAME="$(docker ps --format '{{.Names}}' | grep -E "^${PROJECT}-(blue|green)$" | head -1 || true)"
CURRENT_CONTAINER=""
if [ -n "${CURRENT_NAME}" ]; then
  CURRENT_CONTAINER="$(docker ps -q -f "name=^${CURRENT_NAME}$")"
fi

if [[ "${CURRENT_NAME}" == *"-blue" ]]; then
  NEW_NAME="${PROJECT}-green"
else
  NEW_NAME="${PROJECT}-blue"
fi
COLOR="${NEW_NAME##*-}"
PRIORITY="$(date +%s)"

log "Deploying ${IMAGE}"
log "Current=${CURRENT_NAME:-none} New=${NEW_NAME}"

docker pull "${IMAGE}"
docker stop "${NEW_NAME}" 2>/dev/null || true
docker rm "${NEW_NAME}" 2>/dev/null || true

ADD_HOST_ARGS=()
if [ "${ADD_HOST_GATEWAY:-false}" = "true" ]; then
  ADD_HOST_ARGS=(--add-host=host.docker.internal:host-gateway)
fi

docker run -d \
  --name "${NEW_NAME}" \
  --network "${NETWORK}" \
  --restart unless-stopped \
  --env-file "${ENV_FILE}" \
  "${ADD_HOST_ARGS[@]}" \
  -e SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-nas}" \
  -e PORT="${APP_PORT}" \
  -e STORAGE_LOCAL_BASE="/app/storage" \
  -e JAVA_OPTS="${JAVA_OPTS:--Xms128m -Xmx512m -XX:+UseG1GC}" \
  -v "${STORAGE_DIR}:/app/storage" \
  -v "${LOG_DIR}:/app/logs" \
  --label "com.docker.compose.project=${COMPOSE_PROJECT}" \
  --label "traefik.enable=true" \
  --label "traefik.http.routers.${NEW_NAME}.rule=PathPrefix(\`/\`)" \
  --label "traefik.http.routers.${NEW_NAME}.entrypoints=web" \
  --label "traefik.http.routers.${NEW_NAME}.service=${NEW_NAME}" \
  --label "traefik.http.routers.${NEW_NAME}.middlewares=shortbridge-retry@docker" \
  --label "traefik.http.routers.${NEW_NAME}.priority=${PRIORITY}" \
  --label "traefik.http.services.${NEW_NAME}.loadbalancer.server.port=${APP_PORT}" \
  --label "traefik.http.services.${NEW_NAME}.loadbalancer.healthcheck.path=/actuator/health" \
  --label "traefik.http.services.${NEW_NAME}.loadbalancer.healthcheck.interval=5s" \
  --label "traefik.http.middlewares.shortbridge-retry.retry.attempts=3" \
  --label "traefik.http.middlewares.shortbridge-retry.retry.initialinterval=100ms" \
  "${IMAGE}"

log "Waiting for health check"
WAITED=0
while [ "${WAITED}" -lt "${MAX_WAIT}" ]; do
  if docker exec "${NEW_NAME}" wget -q --spider "${HEALTHCHECK_URL}" >/dev/null 2>&1; then
    log "Healthy after ${WAITED}s"
    break
  fi
  sleep "${CHECK_INTERVAL}"
  WAITED=$((WAITED + CHECK_INTERVAL))
done

if [ "${WAITED}" -ge "${MAX_WAIT}" ]; then
  docker logs --tail 120 "${NEW_NAME}" 2>&1 || true
  docker stop "${NEW_NAME}" 2>/dev/null || true
  docker rm "${NEW_NAME}" 2>/dev/null || true
  fail "Health check timeout. Rollback complete."
fi

NEW_IP="$(docker inspect --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "${NEW_NAME}")"
if [ -n "${NEW_IP}" ]; then
  log "Waiting for Traefik docker provider"
  TRAEFIK_WAIT=0
  while [ "${TRAEFIK_WAIT}" -lt 60 ]; do
    if fetch "http://localhost:${TRAEFIK_API_PORT}/api/http/services/${NEW_NAME}@docker" 2>/dev/null | grep -q "${NEW_IP}"; then
      break
    fi
    sleep 3
    TRAEFIK_WAIT=$((TRAEFIK_WAIT + 3))
  done
fi

sleep 3

if [ -n "${CURRENT_CONTAINER}" ]; then
  log "Stopping old container ${CURRENT_NAME}"
  docker stop --time="${STOP_TIMEOUT}" "${CURRENT_NAME}" 2>/dev/null || true
  docker rm "${CURRENT_NAME}" 2>/dev/null || true
fi

LEGACY_CN="$(docker ps -aq -f "name=^${PROJECT}$")"
if [ -n "${LEGACY_CN}" ]; then
  log "Removing legacy exact-name container ${PROJECT}"
  docker stop "${LEGACY_CN}" 2>/dev/null || true
  docker rm "${LEGACY_CN}" 2>/dev/null || true
fi

if [ "${PRUNE_IMAGES}" = "true" ]; then
  docker image prune -f >/dev/null || true
fi

log "Blue-green deployment complete"
docker ps --filter "name=^${PROJECT}-(blue|green)$" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
