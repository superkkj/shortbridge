# ShortBridge NAS Master Push Auto Deploy

> Last updated: 2026-05-30

## Status

Active.

When this Mac is powered on, logged in, and on the home network, a push to GitHub `origin/master` is picked up within about 60 seconds and deployed to the NAS.

This is not using GitHub-hosted Actions for the active path because the NAS is only reachable on the private LAN as `192.168.31.2`. GitHub-hosted runners cannot SSH into that address.

## Trigger

macOS LaunchAgent:

```text
~/Library/LaunchAgents/com.shortbridge.nas-master-deploy.plist
```

It runs this long-lived loop:

```text
~/.shortbridge-nas-deploy/bin/watch-master-deploy-loop.sh
```

The loop calls this once-per-check script every 60 seconds:

```text
~/.shortbridge-nas-deploy/bin/watch-master-deploy.sh
```

The source scripts in the repo are:

```text
scripts/watch-master-deploy.sh
scripts/watch-master-deploy-loop.sh
```

## Deploy Flow

1. Fetch `origin/master` into an isolated worktree:

```text
~/.shortbridge-nas-deploy/repo
```

2. Compare `origin/master` SHA with:

```text
~/.shortbridge-nas-deploy/deployed.sha
```

3. If the SHA changed, build:

```bash
gradle -p ~/.shortbridge-nas-deploy/repo bootJar --no-daemon
```

4. Upload the new JAR:

```text
superkkj@192.168.31.2:/volume1/shortbridge/app.jar.next
```

5. Move it into place and run:

```text
/volume1/shortbridge/start.sh
```

6. Record the deployed SHA only after NAS health check passes.

## NAS Runtime

Public app URL:

```text
http://192.168.31.2:8080
```

Port `8080` is handled by NAS nginx. The Java app runs behind it on one active slot:

- blue: `127.0.0.1:18080`
- green: `127.0.0.1:18081`

Current active slot is stored at:

```text
/volume1/shortbridge/active_slot
```

The upstream nginx target is stored at:

```text
/volume1/shortbridge/proxy/active_upstream.conf
```

## Blue-Green Behavior

`/volume1/shortbridge/start.sh` does this:

1. Reads the current active slot.
2. Starts the inactive slot with the new `app.jar`.
3. Waits for `/actuator/health` on the inactive slot.
4. Reloads nginx to point `8080` at the new slot.
5. Confirms public `/actuator/health`.
6. Stops the previous slot.

If the new slot does not become healthy, nginx is not switched and the old slot keeps serving traffic.

## Operational Files

NAS:

- `/volume1/shortbridge/app.jar`
- `/volume1/shortbridge/start.sh`
- `/volume1/shortbridge/stop.sh`
- `/volume1/shortbridge/logs/app-blue.log`
- `/volume1/shortbridge/logs/app-green.log`
- `/volume1/shortbridge/logs/nginx-error.log`
- `/volume1/shortbridge/logs/nginx-access.log`

Mac:

- `~/Library/Logs/shortbridge-nas-deploy.log`
- `~/Library/Logs/shortbridge-nas-deploy.err.log`
- `~/.shortbridge-nas-deploy/deployed.sha`
- `~/.shortbridge-nas-deploy/repo`

## Check Commands

Agent:

```bash
launchctl print gui/$(id -u)/com.shortbridge.nas-master-deploy
tail -f ~/Library/Logs/shortbridge-nas-deploy.log
```

NAS:

```bash
ssh superkkj@192.168.31.2 'cat /volume1/shortbridge/active_slot; curl -s http://127.0.0.1:8080/actuator/health'
curl -s http://192.168.31.2:8080/actuator/health
```

Manual deploy from this Mac:

```bash
cd /Users/apple/Desktop/legacy/shortbridge
scripts/deploy-nas-jar.sh
```

## Constraints

- The Mac must stay on and logged in for the LaunchAgent to run.
- The Mac must be able to SSH to the NAS.
- DB and RabbitMQ still point to this Mac's Docker containers at `192.168.31.18`.
- DS118 has limited memory, so starting a new slot can take around 2 minutes.
- GitHub Actions workflows remain in the repo, but the active automatic deployment path is the local LaunchAgent.
