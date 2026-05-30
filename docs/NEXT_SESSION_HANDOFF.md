# ShortBridge Next Session Handoff

> Last updated: 2026-05-31

## Read First

1. `docs/NEXT_SESSION_HANDOFF.md`
2. `docs/deployment/NAS_CURRENT_RUNTIME.md`
3. `docs/deployment/NAS_MASTER_PUSH_AUTODEPLOY.md`
4. `docs/deployment/NAS_AUTODEPLOY_VERIFICATION_2026-05-31.md`
5. `docs/deployment/NAS_BLUE_GREEN_CICD.md`
6. `docs/integrations/META_TEST_ACCOUNT_WORKLOG.md` if present locally
7. `docs/integrations/INSTAGRAM_REELS_SETUP.md` if present locally

## Instagram / Meta Current State

- Instagram test profile is `blood_nail2026`.
- The Instagram profile shows Professional dashboard, so the account is already professional.
- Facebook login for Business reached the Instagram confirmation step.
- Safari was last stopped at Instagram password confirmation for `blood_nail2026`; password must be entered manually by the user.
- Meta app ID in use: `780362769998612`.
- Current local tunnel during the 2026-05-30 session was `https://lbs-decisions-simultaneously-jenny.trycloudflare.com`.
- Registered callback during that session: `https://lbs-decisions-simultaneously-jenny.trycloudflare.com/connect/instagram/callback`.
- YouTube is connected.
- TikTok can publish only under current sandbox/privacy constraints; user accepts self-only behavior for now.

## Instagram Code State

- `InstagramConnector` is implemented.
- It sends Business Login onboarding params.
- It checks both `instagram_business_account` and `connected_instagram_account`.
- It logs a masked diagnostic body when `/me/accounts` has no connected Instagram account.
- `SocialAccountConnectController` redirects back to the app base URL after callback/error, avoiding tunnel-domain login confusion.
- `application-nas.yml` includes Instagram redirect URI, API base URL, and scopes.

## Instagram Next Step

Start the app with the same Instagram env vars, open Safari, then run:

```text
http://localhost:8080/dev/login
http://localhost:8080/connect/instagram
```

Proceed through Facebook Business Login. The user must manually enter Instagram password/2FA/CAPTCHA if asked.

If callback fails with:

```text
No Instagram professional account is connected to an accessible Facebook Page
```

then inspect the app log line:

```text
instagram page lookup returned no connected IG account: body=...
```

That log decides whether the problem is page access, missing IG linkage, or a connector field mismatch.

## NAS CI/CD State

- NAS blue-green deployment files were added on 2026-05-30.
- Local network scan found the Synology NAS at `192.168.31.2`.
- SSH key login for `superkkj@192.168.31.2` is configured from this Mac.
- DS118 has no Docker/Container Manager and no Java package by default.
- Temurin JRE 21 ARM64 was installed at `/usr/local/shortbridge/java/temurin-21-jre`.
- App jar was copied to `/volume1/shortbridge/app.jar`.
- App is currently running on the NAS at `http://192.168.31.2:8080`.
- Port `8080` is served by NAS nginx, which proxies to the active Java slot.
- Current Java slots:
  - blue: `127.0.0.1:18080`
  - green: `127.0.0.1:18081`
  - active slot file: `/volume1/shortbridge/active_slot`
- Health check passed: `http://192.168.31.2:8080/actuator/health` returned `{"status":"UP"}`.
- Blue-green scripts:
  - `/volume1/shortbridge/start.sh`
  - `/volume1/shortbridge/stop.sh`
- Current runtime is a bridge setup: NAS runs the Java app, but DB/RabbitMQ still point to Mac Docker at `192.168.31.18`.
- Fully independent NAS deployment still needs MariaDB root password or DB setup, plus CloudAMQP/RabbitMQ replacement.
- `master` push auto deploy is active through this Mac's LaunchAgent:
  - `~/Library/LaunchAgents/com.shortbridge.nas-master-deploy.plist`
  - repo source script: `scripts/watch-master-deploy.sh`
  - repo loop script: `scripts/watch-master-deploy-loop.sh`
  - runtime work dir: `~/.shortbridge-nas-deploy`
  - log: `~/Library/Logs/shortbridge-nas-deploy.log`
- The watcher checks `origin/master` every 60 seconds, builds in an isolated clone, uploads `app.jar`, and runs the NAS blue-green `start.sh`.
- GitHub Actions JAR deploy workflow is present at `.github/workflows/deploy-nas-jar.yml`, but the active path is the local LaunchAgent because GitHub-hosted runners cannot reach the private LAN NAS.
- Auto deploy was verified with commit `2771bb7b3b37b29c3c92886ea74871cd841c665f`.
- Browser `/login` was verified with Playwright/Chrome. Page title was `로그인 · ShortBridge`.
- nginx redirect bug was fixed by preserving `$http_host` and `X-Forwarded-Port`; without this, `/` redirected to `http://192.168.31.2/login` and missed port `8080`.
- Free domain direction: prefer existing Synology DDNS first, then DuckDNS/FreeDNS. See `docs/deployment/NAS_AUTODEPLOY_VERIFICATION_2026-05-31.md`.
