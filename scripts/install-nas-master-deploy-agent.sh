#!/usr/bin/env bash
set -euo pipefail

LABEL="com.shortbridge.nas-master-deploy"
SOURCE_SCRIPT_PATH="/Users/apple/Desktop/legacy/shortbridge/scripts/watch-master-deploy.sh"
SOURCE_LOOP_SCRIPT_PATH="/Users/apple/Desktop/legacy/shortbridge/scripts/watch-master-deploy-loop.sh"
PLIST_PATH="${HOME}/Library/LaunchAgents/${LABEL}.plist"
LOG_PATH="${HOME}/Library/Logs/shortbridge-nas-deploy.log"
ERR_PATH="${HOME}/Library/Logs/shortbridge-nas-deploy.err.log"
STATE_DIR="${HOME}/.shortbridge-nas-deploy"
STATE_FILE="${STATE_DIR}/deployed.sha"
BIN_DIR="${STATE_DIR}/bin"
SCRIPT_PATH="${BIN_DIR}/watch-master-deploy.sh"
LOOP_SCRIPT_PATH="${BIN_DIR}/watch-master-deploy-loop.sh"
REPO_URL="${SHORTBRIDGE_REPO_URL:-git@github.com:superkkj/shortbridge.git}"
BRANCH="${SHORTBRIDGE_DEPLOY_BRANCH:-master}"
UID_VALUE="$(id -u)"

mkdir -p "${HOME}/Library/LaunchAgents" "${HOME}/Library/Logs" "${STATE_DIR}" "${BIN_DIR}"
cp "${SOURCE_SCRIPT_PATH}" "${SCRIPT_PATH}"
cp "${SOURCE_LOOP_SCRIPT_PATH}" "${LOOP_SCRIPT_PATH}"
chmod +x "${SCRIPT_PATH}" "${LOOP_SCRIPT_PATH}"

if [ ! -f "${STATE_FILE}" ]; then
  git ls-remote "${REPO_URL}" "refs/heads/${BRANCH}" | awk '{print $1}' > "${STATE_FILE}"
fi

cat > "${PLIST_PATH}" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>${LOOP_SCRIPT_PATH}</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>HOME</key>
    <string>${HOME}</string>
    <key>PATH</key>
    <string>/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>
  </dict>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>${LOG_PATH}</string>
  <key>StandardErrorPath</key>
  <string>${ERR_PATH}</string>
</dict>
</plist>
PLIST

plutil -lint "${PLIST_PATH}"
launchctl bootout "gui/${UID_VALUE}" "${PLIST_PATH}" 2>/dev/null || true
launchctl bootstrap "gui/${UID_VALUE}" "${PLIST_PATH}"
launchctl enable "gui/${UID_VALUE}/${LABEL}"
launchctl kickstart -k "gui/${UID_VALUE}/${LABEL}"

echo "installed ${LABEL}"
echo "plist: ${PLIST_PATH}"
echo "logs: ${LOG_PATH}"
