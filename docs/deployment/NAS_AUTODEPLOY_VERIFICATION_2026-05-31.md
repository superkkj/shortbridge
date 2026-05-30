# NAS Auto Deploy Verification - 2026-05-31

## Result

Verified.

`master` push now triggers the local LaunchAgent watcher, builds the app from `origin/master`, uploads it to the NAS, performs the JAR blue-green switch, and serves the app through NAS nginx on port `8080`.

## Verified Push

Verification commit:

```text
2771bb7b3b37b29c3c92886ea74871cd841c665f
```

Commit message:

```text
Verify NAS auto deploy
```

Observed flow:

1. `git push origin master`
2. LaunchAgent detected `origin/master`
3. `gradle bootJar --no-daemon` completed
4. NAS started inactive slot `green` on `127.0.0.1:18081`
5. nginx switched public `8080` to `green`
6. `~/.shortbridge-nas-deploy/deployed.sha` became `2771bb7b3b37b29c3c92886ea74871cd841c665f`
7. Public health stayed `UP`

Final state after verification:

```text
active slot: green
upstream: 127.0.0.1:18081
health: {"status":"UP"}
```

## Browser Verification

Playwright/Chrome opened:

```text
http://192.168.31.2:8080/login
```

Observed page:

```text
Title: 로그인 · ShortBridge
Heading: ShortBridge
Primary action: Google 로 시작하기
Supported platforms: YouTube, Instagram, TikTok
```

Screenshot was captured during verification:

```text
shortbridge-nas-login-after-autodeploy.png
```

## Redirect Bug Found And Fixed

Initial browser check failed with Chrome privacy/security error.

Root cause:

```text
GET / -> 302 Location: http://192.168.31.2/login
```

The app was behind nginx on `:8080`, but nginx forwarded:

```nginx
proxy_set_header Host $host;
```

`$host` strips the port, so Spring generated a redirect without `:8080`. The browser followed `http://192.168.31.2/login`, which hits the NAS web server instead of the ShortBridge nginx proxy.

Fix:

```nginx
proxy_set_header Host $http_host;
proxy_set_header X-Forwarded-Host $http_host;
proxy_set_header X-Forwarded-Port $server_port;
```

After the fix:

```text
GET / -> 302 Location: http://192.168.31.2:8080/login
GET /login -> 200 OK
```

## Runtime Notes

- DS118 is slow with two JVMs during blue-green deployment.
- Observed slot startup time was about 5-6 minutes.
- `start-blue-green.sh` waits up to 15 minutes before declaring a new slot unhealthy.
- During startup, old slot remains active until the new slot passes `/actuator/health`.
- NAS-only runtime secrets are in `/volume1/shortbridge/app.env`; do not commit them.

## Current Domain

ShortBridge now uses DuckDNS:

```text
https://shortbridge.duckdns.org
```

DuckDNS and HTTPS setup details are documented in:

```text
docs/deployment/DUCKDNS_DOMAIN_SETUP.md
```

The public path is:

```text
shortbridge.duckdns.org:443
-> Synology nginx
-> 127.0.0.1:8080
-> ShortBridge nginx blue-green proxy
-> active Java slot
```

## Public HTTPS Header Fix

When Synology nginx was added in front of ShortBridge nginx, `/` initially redirected to:

```text
http://shortbridge.duckdns.org:8080/login
```

Root cause:

```text
Synology nginx correctly forwarded https/443, but the ShortBridge nginx layer overwrote forwarded proto/port with http/8080.
```

Fix:

```text
scripts/nas/start-blue-green.sh now preserves incoming X-Forwarded-Host, X-Forwarded-Proto, and X-Forwarded-Port when present.
Direct LAN access still falls back to the 8080 values.
```
