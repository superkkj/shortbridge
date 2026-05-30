# ShortBridge DuckDNS Domain Setup

> Last updated: 2026-05-31

## Result

ShortBridge has a free DuckDNS hostname:

```text
https://shortbridge.duckdns.org
```

DNS last verified to the home public IP:

```text
125.243.99.46
```

Public access path:

```text
shortbridge.duckdns.org:443
-> Synology nginx
-> 127.0.0.1:8080
-> ShortBridge nginx blue-green proxy
-> active Java slot, blue 18080 or green 18081
```

Port `443` is the public entrypoint. Public `80` and `8080` were not open during setup.

## DuckDNS

DuckDNS account/domain:

```text
shortbridge.duckdns.org
```

NAS files:

```text
/volume1/shortbridge/duckdns/token
/volume1/shortbridge/duckdns/update.sh
/volume1/shortbridge/duckdns/update.log
```

The token file is NAS-only and must not be committed.

Dynamic DNS update schedule:

```text
*/5 * * * * root /volume1/shortbridge/duckdns/update.sh >/dev/null 2>&1
```

The cron entry lives in `/etc/crontab`.

## HTTPS Certificate

Let's Encrypt DNS-01 is used because public port `80` was not reachable.

ACME client and cert files:

```text
/volume1/shortbridge/acme/acme.sh
/volume1/shortbridge/certs/duckdns/fullchain.pem
/volume1/shortbridge/certs/duckdns/key.pem
/volume1/shortbridge/certs/duckdns/cert.pem
/volume1/shortbridge/certs/duckdns/ca.pem
```

Certificate renewal helper:

```text
/volume1/shortbridge/certs/renew-duckdns-cert-user.sh
```

Renewal schedule:

```text
37 3 * * * root /bin/sh -c 'su -s /bin/sh superkkj -c /volume1/shortbridge/certs/renew-duckdns-cert-user.sh >/dev/null 2>&1; synosystemctl reload nginx >/dev/null 2>&1 || true'
```

The active certificate was issued for `shortbridge.duckdns.org` by Let's Encrypt and expires on 2026-08-28.

## Synology Reverse Proxy

Synology nginx config:

```text
/usr/local/etc/nginx/sites-enabled/server.ReverseProxy.conf
```

That path is a symlink to:

```text
/usr/local/etc/nginx/sites-available/ef369ab2-6613-4469-9f0b-1051e4c01153.w3conf
```

The `shortbridge.duckdns.org` HTTPS server proxies to:

```text
http://127.0.0.1:8080
```

Important forwarded headers:

```nginx
proxy_set_header Host $http_host;
proxy_set_header X-Forwarded-Proto https;
proxy_set_header X-Forwarded-Host $http_host;
proxy_set_header X-Forwarded-Port 443;
```

## App Runtime Env

NAS env file:

```text
/volume1/shortbridge/app.env
```

Public URL values:

```text
BASE_URL=https://shortbridge.duckdns.org
SHORTBRIDGE_CONNECT_BASE_URL=https://shortbridge.duckdns.org
STORAGE_LOCAL_PUBLIC_URL=https://shortbridge.duckdns.org/files
SHORTBRIDGE_STORAGE_LOCAL_PUBLIC_BASE_URL=https://shortbridge.duckdns.org/files
```

Do not commit `/volume1/shortbridge/app.env`; it contains NAS-only runtime configuration and secrets.

## Header Preservation Fix

There are two nginx layers:

1. Synology nginx on public `443`.
2. ShortBridge nginx on NAS `8080`.

The ShortBridge nginx layer must preserve incoming forwarded headers. If it overwrites them with its own `8080` values, Spring redirects to `http://shortbridge.duckdns.org:8080/login`.

`scripts/nas/start-blue-green.sh` now maps incoming forwarded headers when present and falls back to direct `8080` values only for LAN access.

## Verification Commands

```bash
dig +short shortbridge.duckdns.org
curl -I https://shortbridge.duckdns.org/
curl -s https://shortbridge.duckdns.org/actuator/health
openssl s_client -connect shortbridge.duckdns.org:443 -servername shortbridge.duckdns.org </dev/null | openssl x509 -noout -subject -issuer -dates
```

Expected app page:

```text
https://shortbridge.duckdns.org/login
Title: 로그인 · ShortBridge
```

Playwright screenshot from verification:

```text
shortbridge-duckdns-login.png
```
