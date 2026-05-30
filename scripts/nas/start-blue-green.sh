#!/usr/bin/env sh
set -eu

APP="${SHORTBRIDGE_APP_DIR:-/volume1/shortbridge}"
JAVA="${SHORTBRIDGE_JAVA:-/usr/local/shortbridge/java/temurin-21-jre/bin/java}"
JAVA_OPTS="${JAVA_OPTS:--Xms32m -Xmx128m -XX:+UseG1GC}"
NGINX="${SHORTBRIDGE_NGINX:-/usr/bin/nginx}"
PUBLIC_PORT="${PUBLIC_PORT:-8080}"
BLUE_PORT="${BLUE_PORT:-18080}"
GREEN_PORT="${GREEN_PORT:-18081}"
PROXY_DIR="$APP/proxy"
NGINX_CONF="$PROXY_DIR/nginx.conf"
UPSTREAM_CONF="$PROXY_DIR/active_upstream.conf"
ACTIVE_SLOT_FILE="$APP/active_slot"

mkdir -p "$APP/logs" "$APP/storage" "$PROXY_DIR/client_body_temp" "$PROXY_DIR/proxy_temp" \
  "$PROXY_DIR/fastcgi_temp" "$PROXY_DIR/uwsgi_temp" "$PROXY_DIR/scgi_temp"

if [ -f "$APP/app.env" ]; then
  # shellcheck disable=SC1091
  . "$APP/app.env"
fi

slot_port() {
  case "$1" in
    blue) printf '%s\n' "$BLUE_PORT" ;;
    green) printf '%s\n' "$GREEN_PORT" ;;
    *) return 1 ;;
  esac
}

pid_file() {
  printf '%s/app-%s.pid\n' "$APP" "$1"
}

stop_pid_file() {
  file="$1"
  if [ ! -f "$file" ]; then
    return 0
  fi

  pid="$(cat "$file" 2>/dev/null || true)"
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null || true
    i=0
    while kill -0 "$pid" 2>/dev/null; do
      i=$((i + 1))
      if [ "$i" -ge 20 ]; then
        kill -9 "$pid" 2>/dev/null || true
        break
      fi
      sleep 1
    done
  fi
  rm -f "$file"
}

stop_slot() {
  stop_pid_file "$(pid_file "$1")"
}

start_slot() {
  slot="$1"
  port="$(slot_port "$slot")"
  log_file="$APP/logs/app-$slot.log"
  base_url="${BASE_URL:-http://192.168.31.2:${PUBLIC_PORT}}"
  storage_public_url="${STORAGE_LOCAL_PUBLIC_URL:-${SHORTBRIDGE_STORAGE_LOCAL_PUBLIC_BASE_URL:-${base_url}/files}}"

  stop_slot "$slot"

  nohup env \
    SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}" \
    PORT="$port" \
    SERVER_PORT="$port" \
    SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://192.168.31.18:5432/shortbridge?stringtype=unspecified}" \
    SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-shortbridge}" \
    SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-shortbridge}" \
    SPRING_RABBITMQ_HOST="${SPRING_RABBITMQ_HOST:-192.168.31.18}" \
    SPRING_RABBITMQ_PORT="${SPRING_RABBITMQ_PORT:-5672}" \
    SPRING_RABBITMQ_USERNAME="${SPRING_RABBITMQ_USERNAME:-shortbridge}" \
    SPRING_RABBITMQ_PASSWORD="${SPRING_RABBITMQ_PASSWORD:-shortbridge}" \
    BASE_URL="$base_url" \
    SHORTBRIDGE_CONNECT_BASE_URL="${SHORTBRIDGE_CONNECT_BASE_URL:-$base_url}" \
    SHORTBRIDGE_STORAGE_LOCAL_BASE_DIR="${SHORTBRIDGE_STORAGE_LOCAL_BASE_DIR:-$APP/storage}" \
    STORAGE_LOCAL_PUBLIC_URL="$storage_public_url" \
    SHORTBRIDGE_STORAGE_LOCAL_PUBLIC_BASE_URL="$storage_public_url" \
    "$JAVA" $JAVA_OPTS -jar "$APP/app.jar" \
    > "$log_file" 2>&1 &

  echo $! > "$(pid_file "$slot")"
  echo "started $slot $(cat "$(pid_file "$slot")") on port $port"
}

wait_health() {
  port="$1"
  i=0
  while [ "$i" -lt 450 ]; do
    if curl -fsS "http://127.0.0.1:$port/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  return 1
}

write_nginx_conf() {
  cat > "$NGINX_CONF" <<EOF
worker_processes 1;
pid $PROXY_DIR/nginx.pid;
error_log $APP/logs/nginx-error.log warn;

events {
  worker_connections 256;
}

http {
  include $UPSTREAM_CONF;
  access_log $APP/logs/nginx-access.log;
  client_max_body_size 600m;
  client_body_temp_path $PROXY_DIR/client_body_temp;
  proxy_temp_path $PROXY_DIR/proxy_temp;
  fastcgi_temp_path $PROXY_DIR/fastcgi_temp;
  uwsgi_temp_path $PROXY_DIR/uwsgi_temp;
  scgi_temp_path $PROXY_DIR/scgi_temp;

  map \$http_x_forwarded_host \$shortbridge_forwarded_host {
    default \$http_x_forwarded_host;
    "" \$http_host;
  }

  map \$http_x_forwarded_proto \$shortbridge_forwarded_proto {
    default \$http_x_forwarded_proto;
    "" \$scheme;
  }

  map \$http_x_forwarded_port \$shortbridge_forwarded_port {
    default \$http_x_forwarded_port;
    "" \$server_port;
  }

  server {
    listen $PUBLIC_PORT;

    location / {
      proxy_pass http://shortbridge_backend;
      proxy_http_version 1.1;
      proxy_set_header Host \$shortbridge_forwarded_host;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Host \$shortbridge_forwarded_host;
      proxy_set_header X-Forwarded-Proto \$shortbridge_forwarded_proto;
      proxy_set_header X-Forwarded-Port \$shortbridge_forwarded_port;
      proxy_connect_timeout 60s;
      proxy_send_timeout 600s;
      proxy_read_timeout 600s;
      proxy_request_buffering off;
    }
  }
}
EOF
}

write_upstream() {
  port="$1"
  tmp="$UPSTREAM_CONF.tmp"
  cat > "$tmp" <<EOF
upstream shortbridge_backend {
  server 127.0.0.1:$port;
  keepalive 16;
}
EOF
  mv "$tmp" "$UPSTREAM_CONF"
}

nginx_running() {
  if [ ! -f "$PROXY_DIR/nginx.pid" ]; then
    return 1
  fi
  pid="$(cat "$PROXY_DIR/nginx.pid" 2>/dev/null || true)"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

stop_legacy_app() {
  stop_pid_file "$APP/app.pid"
}

start_or_reload_nginx() {
  write_nginx_conf
  "$NGINX" -e "$APP/logs/nginx-error.log" -t -c "$NGINX_CONF"
  if nginx_running; then
    "$NGINX" -e "$APP/logs/nginx-error.log" -c "$NGINX_CONF" -s reload
  else
    stop_legacy_app
    "$NGINX" -e "$APP/logs/nginx-error.log" -c "$NGINX_CONF"
  fi
}

current_slot="$(cat "$ACTIVE_SLOT_FILE" 2>/dev/null || true)"
case "$current_slot" in
  blue)
    new_slot="green"
    old_slot="blue"
    ;;
  green)
    new_slot="blue"
    old_slot="green"
    ;;
  *)
    new_slot="blue"
    old_slot=""
    ;;
esac

new_port="$(slot_port "$new_slot")"
start_slot "$new_slot"

if ! wait_health "$new_port"; then
  echo "new $new_slot slot did not become healthy" >&2
  tail -80 "$APP/logs/app-$new_slot.log" >&2 || true
  stop_slot "$new_slot"
  exit 1
fi

write_upstream "$new_port"
start_or_reload_nginx

if ! wait_health "$PUBLIC_PORT"; then
  echo "public health check failed after switching nginx" >&2
  exit 1
fi

echo "$new_slot" > "$ACTIVE_SLOT_FILE"
if [ -n "$old_slot" ]; then
  stop_slot "$old_slot"
fi

echo "active slot: $new_slot on port $new_port"
