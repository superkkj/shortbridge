# ShortBridge NAS Current Runtime

> Last updated: 2026-05-30

## Answer First

Yes, with one condition: the active auto deploy path runs from this Mac.

When this Mac is powered on, logged in, and on the home network, `origin/master` is checked every 60 seconds. If the SHA changed, the app is built and deployed to the NAS.

The active implementation is documented in `docs/deployment/NAS_MASTER_PUSH_AUTODEPLOY.md`.

## Current Running URL

```text
http://192.168.31.2:8080
```

Health check:

```text
http://192.168.31.2:8080/actuator/health
```

Expected:

```json
{"status":"UP"}
```

## What Is Installed On NAS

- NAS host: `192.168.31.2`
- SSH user: `superkkj`
- SSH key login from this Mac: configured
- Java runtime: `/usr/local/shortbridge/java/temurin-21-jre`
- App directory: `/volume1/shortbridge`
- App JAR: `/volume1/shortbridge/app.jar`
- Runtime env file: `/volume1/shortbridge/app.env` (NAS-only secrets; do not commit)
- Public port: `8080`, served by NAS nginx
- Active app slot: blue `18080` or green `18081`
- New slot health wait timeout: up to 15 minutes on DS118
- App logs: `/volume1/shortbridge/logs/app-blue.log`, `/volume1/shortbridge/logs/app-green.log`
- Nginx logs: `/volume1/shortbridge/logs/nginx-access.log`, `/volume1/shortbridge/logs/nginx-error.log`
- Start script: `/volume1/shortbridge/start.sh`
- Stop script: `/volume1/shortbridge/stop.sh`

## Important Constraint

Synology DS118 does not support Docker/Container Manager. That means the Docker blue-green deployment files in this repo are not usable on this NAS model directly.

The current DS118 runtime is JAR-based blue-green:

```text
Mac builds JAR -> copy app.jar to NAS -> NAS starts inactive slot -> nginx switches 8080 -> old slot stops
```

## Current Dependency Shape

The app process runs on the NAS, but DB and RabbitMQ still point to this Mac's Docker containers:

- PostgreSQL: `192.168.31.18:5432`
- RabbitMQ: `192.168.31.18:5672`

This is enough for home-LAN development, but it is not a fully independent NAS production deployment.

Fully independent NAS deployment still needs:

- MariaDB `shortbridge` database/user setup on NAS, or a confirmed DB password.
- CloudAMQP/RabbitMQ URL, or another queue strategy that does not require Docker on DS118.
- OAuth callback URLs changed to the final public `BASE_URL`.

## Manual Deploy From This Mac

Use:

```bash
cd /Users/apple/Desktop/legacy/shortbridge
scripts/deploy-nas-jar.sh
```

That script:

1. Runs `gradle bootJar`.
2. Copies the JAR to `superkkj@192.168.31.2:/volume1/shortbridge/app.jar.next`.
3. Moves it to `/volume1/shortbridge/app.jar`.
4. Runs `/volume1/shortbridge/start.sh`.
5. `start.sh` performs the blue-green slot switch behind nginx.
6. Checks `/actuator/health`.

## Master Push Auto Deploy

Active local path:

```text
~/Library/LaunchAgents/com.shortbridge.nas-master-deploy.plist
```

This LaunchAgent runs `scripts/watch-master-deploy.sh` every 60 seconds through a copied runtime script at:

```text
~/.shortbridge-nas-deploy/bin/watch-master-deploy.sh
```

It deploys only when `origin/master` changes.

GitHub Actions path:

- `.github/workflows/deploy-nas-jar.yml` is still present.
- It remains optional/gated because GitHub-hosted runners cannot reach the private LAN NAS at `192.168.31.2`.

Required GitHub secrets:

- `NAS_SSH_HOST`
- `NAS_SSH_USER`
- `NAS_SSH_KEY`
- `NAS_SSH_PORT` optional, default `22`
- `NAS_DEPLOY_PATH` optional, default `/volume1/shortbridge`

GitHub Actions network requirement:

- GitHub-hosted runners must be able to SSH to the NAS, or
- a GitHub self-hosted runner must run on the home network.

Right now the active path avoids that network problem by running the deploy watcher from this Mac.

## Docker Blue-Green Status

`docs/deployment/NAS_BLUE_GREEN_CICD.md` and `scripts/deploy-nas-blue-green.sh` are kept as a future design for a Docker-capable host. They are not the active DS118 deployment path.
