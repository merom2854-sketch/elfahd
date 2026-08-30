# Al Fahd Live Gateway

This is the production-oriented Cloudflare Worker layer for live streams that
Al Fahd TV is authorized to distribute. It has no default upstream and cannot
operate as an open proxy.

## What it protects

- Only `GET` and `HEAD` are proxied.
- The primary upstream and every additional media CDN are explicit HTTPS
  allow-list entries. Redirects to any other host are blocked.
- Requests require a short-lived, HMAC-signed stream ticket.
- A Durable Object enforces a per-viewer/IP request budget before contacting
  the upstream.
- Cookies, visitor authorization, client IP forwarding, and arbitrary
  referrers are never passed to the source.
- HLS manifests are rewritten, including nested playlists, segments, keys,
  maps, and media URIs. The signed ticket is propagated only to approved
  proxied URLs.

This stops casual scraping and public hotlinking. It does **not** make a secret
embedded in an Android APK secure; the app must receive short-lived tickets
from a server you operate.

## Deployment values

Set these values in Cloudflare (they are intentionally absent from git):

```powershell
npx wrangler secret put UPSTREAM_ORIGIN
npx wrangler secret put AUTH_SECRET
npx wrangler secret put ALLOWED_MEDIA_ORIGINS
npx wrangler secret put FRONTEND_ORIGINS
npx wrangler secret put UPSTREAM_REFERER
npx wrangler secret put RATE_LIMIT_PER_MINUTE
```

Suggested values:

| Setting | Example / purpose |
| --- | --- |
| `UPSTREAM_ORIGIN` | `https://streams.example.com` — required, exact source origin only. |
| `AUTH_SECRET` | A unique random 32+ character secret shared only with the ticket issuer. |
| `ALLOWED_MEDIA_ORIGINS` | Comma-separated exact CDN origins, e.g. `https://cdn-a.example.com,https://cdn-b.example.com`. |
| `FRONTEND_ORIGINS` | Web origins allowed to call the Worker; do not use `*`. |
| `UPSTREAM_REFERER` | Optional HTTPS referrer required by a source you operate. |
| `RATE_LIMIT_PER_MINUTE` | Start at `360`, then tune using real player telemetry. |

Use variables (not secrets) only for values you are comfortable showing to
Cloudflare project members. `AUTH_SECRET` must always be a Worker secret.

Deploy after authentication:

```powershell
cd live-proxy-worker
npm install
npx wrangler login
npx wrangler deploy
```

## Stream ticket contract

The Worker accepts a token in `Authorization: Bearer <ticket>` for an initial
request or `?__af_ticket=<ticket>` for HLS child URLs. A ticket is:

```
base64url(JSON payload) + "." + base64url(HMAC-SHA256(AUTH_SECRET, payloadPart))
```

Example payload (never hard-code this in the app):

```json
{
  "sub": "opaque-viewer-id",
  "exp": 1893456300,
  "scope": "*",
  "aud": "live.example.com"
}
```

`exp` should be 2–5 minutes ahead. The issuing endpoint belongs on the Railway
backend after a real user/device verification step. For Android, use Play
Integrity (or a user session) before issuing it; a static key inside the APK is
extractable and is not authentication.

## Cloudflare controls to enable

1. Bind a custom hostname, such as `live.elfahd.tv`, and disable the
   `workers.dev` route after verification.
2. Add a WAF rate-limit rule as a second outer layer for abusive IPs.
3. Restrict dashboard access, enable MFA, and keep `AUTH_SECRET` different
   from `ADMIN_TOKEN` and all other project secrets.
4. Add an uptime check to `https://live.elfahd.tv/_af/health` and alert on 5xx.
5. Use Cloudflare Analytics Engine/Logpush if you need reporting. Do not log
   playback URLs or tickets.

## Test before connecting the app

1. Run `npm run check`.
2. Deploy to a non-public staging hostname.
3. Issue a test ticket from the backend and test an HLS master playlist,
   a rendition playlist, segments, seek/range requests, and stream expiry.
4. Verify that an unsigned request returns `401`, an unapproved redirect
   returns `502`, and a burst returns `429`.
5. Only then point the Android live-channel configuration at the custom host.
