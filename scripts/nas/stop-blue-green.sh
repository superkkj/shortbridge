#!/usr/bin/env sh
set -eu

APP="${SHORTBRIDGE_APP_DIR:-/volume1/shortbridge}"
NGINX="${SHORTBRIDGE_NGINX:-/usr/bin/nginx}"
NGINX_CONF="$APP/proxy/nginx.conf"

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

if [ -f "$NGINX_CONF" ]; then
  "$NGINX" -e "$APP/logs/nginx-error.log" -c "$NGINX_CONF" -s quit 2>/dev/null || true
fi

stop_pid_file "$APP/app-blue.pid"
stop_pid_file "$APP/app-green.pid"
stop_pid_file "$APP/app.pid"
rm -f "$APP/active_slot"

echo "stopped shortbridge"
