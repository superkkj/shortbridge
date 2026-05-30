# ShortBridge Docker Blue-Green CI/CD

> Last updated: 2026-05-31

## Current Status

This Docker path is not the active Synology DS118 deployment path.

DS118 does not support Docker/Container Manager, so Docker blue-green cannot run directly on the current NAS. The active runtime is JAR blue-green behind NAS nginx and is documented in `docs/deployment/NAS_CURRENT_RUNTIME.md` and `docs/deployment/NAS_MASTER_PUSH_AUTODEPLOY.md`.

## What Was Added

- GitHub Actions workflow: `.github/workflows/deploy-nas.yml`
- NAS runtime compose: `docker/nas/docker-compose.yml`
- NAS env template: `docker/nas/.env.nas.example`
- Blue-green deploy script: `scripts/deploy-nas-blue-green.sh`
- Dockerfile now respects `SPRING_PROFILES_ACTIVE`; NAS can run `nas` profile.

## Current NAS Facts

- Local LAN is `192.168.31.0/24`.
- Synology MAC OUI was found at `192.168.31.2`.
- Open ports on `192.168.31.2`: `22`, `80`, `443`, `5000`, `5001`.
- Public DuckDNS domain is `shortbridge.duckdns.org`.
- SSH key login for `superkkj@192.168.31.2` is configured from this Mac.
- DS118 does not have Docker/Container Manager available, so Docker blue-green cannot run directly on this NAS model.
- Active live runtime is JAR blue-green:
  - JRE: `/usr/local/shortbridge/java/temurin-21-jre`
  - App: `/volume1/shortbridge/app.jar`
  - Start: `/volume1/shortbridge/start.sh`
  - Stop: `/volume1/shortbridge/stop.sh`
  - Public URL: `https://shortbridge.duckdns.org`
  - LAN URL: `http://192.168.31.2:8080`
  - Synology nginx public port: `443`
  - ShortBridge nginx LAN port: `8080`
  - Java slots: `18080` and `18081`
  - DB/RabbitMQ currently point back to Mac Docker at `192.168.31.18`.

## Planned Runtime Shape For Docker-Capable Host

- Traefik listens on NAS host port `8080`.
- ShortBridge app containers are named `shortbridge-nas-blue` and `shortbridge-nas-green`.
- New container starts with higher Traefik router priority.
- Health check uses `/actuator/health`.
- Old container is stopped only after the new one is healthy.
- RabbitMQ runs as `shortbridge-rabbitmq`.
- MariaDB is expected to run on Synology or another host and is reached via `NAS_DB_HOST`.

## NAS Bootstrap

On the NAS:

```bash
mkdir -p /volume1/docker/shortbridge-nas
cd /volume1/docker/shortbridge-nas
```

Copy these files into that directory:

- `scripts/deploy-nas-blue-green.sh`
- `docker/nas/docker-compose.yml`
- `docker/nas/.env.nas.example`

Then:

```bash
cp docker/nas/.env.nas.example .env.nas
vi .env.nas
chmod +x scripts/deploy-nas-blue-green.sh
```

Fill `.env.nas` with real values. Do not commit this file.

Minimum required secrets:

- `NAS_DB_PASSWORD`
- `RABBITMQ_DEFAULT_PASS`
- `RABBITMQ_URL`
- `TOKEN_KEY_V1`
- OAuth client IDs/secrets for enabled platforms

## GitHub Actions Secrets

Set these in GitHub repository secrets:

- `NAS_SSH_HOST`: use `192.168.31.2` for a self-hosted runner on the home LAN, or `shortbridge.duckdns.org` if SSH is intentionally port-forwarded.
- `NAS_SSH_USER`
- `NAS_SSH_KEY`
- `NAS_SSH_PORT`: optional, default `22`
- `NAS_DEPLOY_PATH`: optional, default `/volume1/docker/shortbridge-nas`

Optional repository variable:

- `NAS_DOCKER_PLATFORMS`: default `linux/amd64,linux/arm64`

If using GitHub-hosted runners, the NAS must be reachable from the internet. If SSH is only reachable on the home LAN, use a GitHub self-hosted runner on the NAS or on a home-network machine.

## Manual Deploy

After `.env.nas` is ready:

```bash
cd /volume1/docker/shortbridge-nas
REGISTRY=ghcr.io IMAGE_NAME=superkkj/shortbridge TAG=latest ./scripts/deploy-nas-blue-green.sh
```

If GHCR package is private:

```bash
export GHCR_USER=<github-user>
export GHCR_TOKEN=<github-token-with-package-read>
REGISTRY=ghcr.io IMAGE_NAME=superkkj/shortbridge TAG=latest ./scripts/deploy-nas-blue-green.sh
```

## OAuth Redirects For NAS

Register these URLs in external developer consoles when NAS is the public runtime:

- Google: `https://shortbridge.duckdns.org/login/oauth2/code/google`
- YouTube: `https://shortbridge.duckdns.org/connect/youtube/callback`
- TikTok: `https://shortbridge.duckdns.org/connect/tiktok/callback`
- Instagram: `https://shortbridge.duckdns.org/connect/instagram/callback`

`BASE_URL` should remain `https://shortbridge.duckdns.org` for the current NAS runtime.
