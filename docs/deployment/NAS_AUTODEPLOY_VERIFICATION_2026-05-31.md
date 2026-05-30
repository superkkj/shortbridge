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

## Current Domain Direction

For a free public hostname, use this order:

1. Existing Synology DDNS if available, for example `dunblack.synology.me`.
2. DuckDNS if a simple free DDNS subdomain is enough.
3. FreeDNS afraid.org if DuckDNS naming is not available.
4. No-IP only if a single free hostname is enough and account maintenance is acceptable.
5. EU.org only if waiting/manual approval is acceptable.

For real OAuth callbacks and user-facing access, HTTPS is still required. Free DDNS gives a hostname, but TLS/reverse proxy setup is a separate step.

Reference links checked on 2026-05-31:

- Synology DDNS: `https://kb.synology.com/en-global/DSM/help/DSM/AdminCenter/connection_ddns`
- DuckDNS: `https://www.duckdns.org/`
- FreeDNS: `https://freedns.afraid.org/`
- No-IP Free Dynamic DNS: `https://www.noip.com/free`
- EU.org: `https://nic.eu.org/`

Recommendation for ShortBridge:

```text
Use Synology DDNS first if `dunblack.synology.me` is controllable.
Then configure HTTPS on Synology reverse proxy or another front proxy.
Only switch to DuckDNS/FreeDNS if the Synology hostname is not usable for OAuth callbacks.
```
