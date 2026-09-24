# Al Fahd Cima emergency catalogue

This Cloudflare Worker exposes a small JSON catalogue API compatible with the
Al Fahd catalogue worker:

- `?action=health`
- `?action=genre&genre=movies|series|anime`
- `?action=search&q=<title>`
- `?action=series&series=<CimaLight watch URL>`

It has a fixed allow-list for `https://e.cimalight.co`. It does not accept an
arbitrary target URL, proxy media, bypass a challenge page, or forward visitor
cookies. Successful responses are cached at the edge for a short period so a
brief upstream failure can return the last healthy catalogue response.

The source's current playback page hands viewing off to a separate host. The
worker therefore returns catalogue and detail data only. A direct, authorised
media API or a source-approved playback endpoint is required before the native
player should use it as an automatic viewing fallback.
