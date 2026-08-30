/**
 * Al Fahd Live Gateway
 *
 * A fixed-origin, authenticated Cloudflare Worker proxy for streams that
 * Al Fahd TV is authorized to distribute. It is deliberately not an open
 * proxy: every upstream and media CDN origin is allow-listed at deployment.
 */

const HOP_BY_HOP_HEADERS = [
  "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
  "te", "trailer", "transfer-encoding", "upgrade", "host",
];

const URL_ATTRIBUTES = [
  ["a[href]", "href"], ["area[href]", "href"], ["link[href]", "href"],
  ["script[src]", "src"], ["img[src]", "src"], ["iframe[src]", "src"],
  ["source[src]", "src"], ["video[src]", "src"], ["audio[src]", "src"],
  ["track[src]", "src"], ["embed[src]", "src"], ["object[data]", "data"],
];

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const TICKET_PARAMETER = "__af_ticket";
const TARGET_PARAMETER = "__af_target";
const MEDIA_PATH = "/_af/media";
const HEALTH_PATH = "/_af/health";

function textResponse(status, message, extraHeaders = {}) {
  return new Response(message, {
    status,
    headers: {
      "Content-Type": "text/plain; charset=UTF-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      ...extraHeaders,
    },
  });
}

function jsonResponse(status, body, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=UTF-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      ...extraHeaders,
    },
  });
}

function isSpecialUrl(value) {
  return !value || value.startsWith("#") || /^(?:data|javascript|mailto|tel|blob):/i.test(value);
}

function toBase64Url(bytes) {
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function fromBase64Url(value) {
  if (!/^[A-Za-z0-9_-]+$/.test(value || "")) throw new Error("Invalid base64url value");
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (value.length % 4)) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function encodeText(value) {
  return toBase64Url(encoder.encode(value));
}

function decodeText(value) {
  return decoder.decode(fromBase64Url(value));
}

function parseOrigin(value, name) {
  const url = new URL(value);
  if (url.protocol !== "https:" || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    throw new Error(`${name} must be a credential-free HTTPS origin without a path.`);
  }
  return url;
}

function parseOriginList(value, name) {
  if (!value) return new Set();
  return new Set(value.split(",").map((item) => item.trim()).filter(Boolean).map((item) => parseOrigin(item, name).origin));
}

function readNumber(value, fallback, minimum, maximum) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(minimum, Math.min(maximum, Math.floor(number)));
}

function readConfig(env) {
  if (!env.UPSTREAM_ORIGIN) throw new Error("UPSTREAM_ORIGIN is not configured.");
  if (!env.AUTH_SECRET || String(env.AUTH_SECRET).length < 32) throw new Error("AUTH_SECRET must contain at least 32 characters.");
  if (!env.RATE_LIMITER) throw new Error("RATE_LIMITER Durable Object binding is not configured.");

  const upstream = parseOrigin(env.UPSTREAM_ORIGIN, "UPSTREAM_ORIGIN");
  const allowedOrigins = parseOriginList(env.ALLOWED_MEDIA_ORIGINS || "", "ALLOWED_MEDIA_ORIGINS");
  allowedOrigins.add(upstream.origin);
  const frontendOrigins = parseOriginList(env.FRONTEND_ORIGINS || "", "FRONTEND_ORIGINS");
  let upstreamReferer = "";
  if (env.UPSTREAM_REFERER) {
    const referer = new URL(env.UPSTREAM_REFERER);
    if (referer.protocol !== "https:") throw new Error("UPSTREAM_REFERER must use HTTPS.");
    upstreamReferer = referer.toString();
  }

  return {
    upstream,
    allowedOrigins,
    frontendOrigins,
    upstreamReferer,
    rateLimit: readNumber(env.RATE_LIMIT_PER_MINUTE, 360, 30, 2000),
    rateWindowSeconds: 60,
  };
}

function allowedTarget(target, config) {
  return target.protocol === "https:" && !target.username && !target.password && config.allowedOrigins.has(target.origin);
}

function providedToken(request) {
  const queryToken = new URL(request.url).searchParams.get(TICKET_PARAMETER);
  if (queryToken) return queryToken;
  const authorization = request.headers.get("Authorization") || "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  return match ? match[1].trim() : "";
}

async function importHmacKey(secret) {
  return crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
}

async function authenticate(request, env) {
  const token = providedToken(request);
  if (!token) return { error: textResponse(401, "A stream ticket is required.") };
  const parts = token.split(".");
  if (parts.length !== 2) return { error: textResponse(401, "Invalid stream ticket.") };

  try {
    const key = await importHmacKey(env.AUTH_SECRET);
    const valid = await crypto.subtle.verify("HMAC", key, fromBase64Url(parts[1]), encoder.encode(parts[0]));
    if (!valid) return { error: textResponse(401, "Invalid stream ticket.") };

    const payload = JSON.parse(decodeText(parts[0]));
    const now = Math.floor(Date.now() / 1000);
    if (!Number.isInteger(payload.exp) || payload.exp <= now || (payload.nbf && payload.nbf > now)) {
      return { error: textResponse(401, "Expired stream ticket.") };
    }
    const publicUrl = new URL(request.url);
    if (payload.aud && payload.aud !== publicUrl.host) return { error: textResponse(403, "Stream ticket audience does not match.") };

    const scope = typeof payload.scope === "string" ? payload.scope : "*";
    if (scope !== "*" && (!scope.startsWith("/") || !publicUrl.pathname.startsWith(scope))) {
      return { error: textResponse(403, "Stream ticket is not valid for this path.") };
    }
    return { token, subject: String(payload.sub || "anonymous").slice(0, 160) };
  } catch {
    return { error: textResponse(401, "Invalid stream ticket.") };
  }
}

async function rateLimit(request, env, config, subject) {
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const fingerprint = await crypto.subtle.digest("SHA-256", encoder.encode(`${subject}:${ip}`));
  const id = env.RATE_LIMITER.idFromName(toBase64Url(new Uint8Array(fingerprint)));
  const response = await env.RATE_LIMITER.get(id).fetch("https://rate-limit/check", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ limit: config.rateLimit, windowSeconds: config.rateWindowSeconds }),
  });
  const value = await response.json();
  return value;
}

function removeInternalQuery(url) {
  const target = new URL(url);
  target.searchParams.delete(TICKET_PARAMETER);
  target.searchParams.delete(TARGET_PARAMETER);
  return target;
}

function resolveTarget(request, config) {
  const publicUrl = new URL(request.url);
  if (publicUrl.pathname === MEDIA_PATH) {
    const encoded = publicUrl.searchParams.get(TARGET_PARAMETER);
    if (!encoded) throw new Error("Missing media target.");
    const target = new URL(decodeText(encoded));
    if (!allowedTarget(target, config)) throw new Error("Media origin is not allow-listed.");
    return target;
  }

  const target = new URL(`${publicUrl.pathname}${removeInternalQuery(publicUrl).search}`, config.upstream);
  if (!allowedTarget(target, config) || target.origin !== config.upstream.origin) throw new Error("Invalid upstream target.");
  return target;
}

function proxyUrl(target, publicUrl, token, config) {
  if (!allowedTarget(target, config)) return null;
  const proxied = new URL(publicUrl.origin);
  if (target.origin === config.upstream.origin) {
    proxied.pathname = target.pathname;
    proxied.search = target.search;
  } else {
    proxied.pathname = MEDIA_PATH;
    proxied.searchParams.set(TARGET_PARAMETER, encodeText(target.toString()));
  }
  proxied.searchParams.set(TICKET_PARAMETER, token);
  return proxied.toString();
}

function rewriteUrl(value, baseUrl, publicUrl, token, config) {
  if (isSpecialUrl(value)) return value;
  try {
    const target = new URL(value, baseUrl);
    return proxyUrl(target, publicUrl, token, config) || value;
  } catch {
    return value;
  }
}

class AttributeRewriter {
  constructor(attribute, rewrite) { this.attribute = attribute; this.rewrite = rewrite; }
  element(element) {
    const value = element.getAttribute(this.attribute);
    if (value) element.setAttribute(this.attribute, this.rewrite(value));
  }
}

class SrcsetRewriter {
  constructor(rewrite) { this.rewrite = rewrite; }
  element(element) {
    const srcset = element.getAttribute("srcset");
    if (!srcset || /^\s*data:/i.test(srcset)) return;
    const rewritten = srcset.split(",").map((candidate) => {
      const parts = candidate.trim().split(/\s+/);
      if (!parts[0]) return candidate;
      parts[0] = this.rewrite(parts[0]);
      return parts.join(" ");
    }).join(", ");
    element.setAttribute("srcset", rewritten);
  }
}

function rewriteHtml(response, headers, target, publicUrl, token, config) {
  const rewrite = (value) => rewriteUrl(value, target, publicUrl, token, config);
  let rewriter = new HTMLRewriter();
  for (const [selector, attribute] of URL_ATTRIBUTES) rewriter = rewriter.on(selector, new AttributeRewriter(attribute, rewrite));
  rewriter = rewriter.on("img[srcset]", new SrcsetRewriter(rewrite)).on("source[srcset]", new SrcsetRewriter(rewrite));
  headers.delete("content-length");
  return rewriter.transform(new Response(response.body, { status: response.status, statusText: response.statusText, headers }));
}

function rewritePlaylist(text, target, publicUrl, token, config) {
  const rewrite = (value) => rewriteUrl(value, target, publicUrl, token, config);
  return text.split(/(\r?\n)/).map((line) => {
    if (!line || /^\r?\n$/.test(line)) return line;
    if (!line.startsWith("#")) return rewrite(line.trim());
    return line.replace(/URI=(?:(["'])(.*?)\1|([^,\s]+))/gi, (match, quote, quoted, bare) => {
      const original = quoted ?? bare;
      const value = rewrite(original);
      return `URI=${quote || ""}${value}${quote || ""}`;
    });
  }).join("");
}

function isHtml(contentType) { return /\btext\/html\b/i.test(contentType); }
function isPlaylist(contentType, target) { return /(?:application|audio)\/(?:vnd\.apple\.mpegurl|x-mpegurl)/i.test(contentType) || /\.m3u8$/i.test(target.pathname); }

function applyCors(headers, request, config) {
  const origin = request.headers.get("Origin");
  headers.delete("access-control-allow-origin");
  headers.delete("access-control-allow-credentials");
  if (origin && config.frontendOrigins.has(origin)) {
    headers.set("Access-Control-Allow-Origin", origin);
    headers.set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
    headers.set("Access-Control-Allow-Headers", "Authorization, Range, Content-Type");
    headers.set("Access-Control-Expose-Headers", "Accept-Ranges, Content-Range, Content-Length");
    headers.set("Vary", "Origin");
  }
}

function responseHeaders(upstreamResponse, request, config, rewrittenLocation) {
  const headers = new Headers(upstreamResponse.headers);
  for (const name of HOP_BY_HOP_HEADERS) headers.delete(name);
  headers.delete("set-cookie");
  headers.delete("server");
  headers.delete("via");
  if (rewrittenLocation) headers.set("location", rewrittenLocation);
  headers.set("X-Content-Type-Options", "nosniff");
  headers.set("Referrer-Policy", "no-referrer");
  applyCors(headers, request, config);
  return headers;
}

function upstreamRequest(request, target, config) {
  const headers = new Headers(request.headers);
  for (const name of HOP_BY_HOP_HEADERS) headers.delete(name);
  for (const name of ["cookie", "authorization", "origin", "referer", "cf-connecting-ip", "x-forwarded-for"]) headers.delete(name);
  if (config.upstreamReferer) headers.set("referer", config.upstreamReferer);
  return new Request(target, { method: request.method, headers, redirect: "manual" });
}

function optionsResponse(request, config) {
  const origin = request.headers.get("Origin");
  if (!origin || !config.frontendOrigins.has(origin)) return textResponse(403, "Origin is not allowed.");
  return new Response(null, { status: 204, headers: {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Range, Content-Type",
    "Access-Control-Max-Age": "600",
    "Vary": "Origin",
  }});
}

export class RateLimiter {
  constructor(state) { this.state = state; }

  async fetch(request) {
    if (request.method !== "POST") return textResponse(405, "Method not allowed.");
    const input = await request.json();
    const limit = readNumber(input.limit, 360, 30, 2000);
    const windowSeconds = readNumber(input.windowSeconds, 60, 10, 3600);
    const now = Date.now();
    const current = await this.state.storage.get("bucket") || { startedAt: now, count: 0 };
    if (now - current.startedAt >= windowSeconds * 1000) {
      current.startedAt = now;
      current.count = 0;
    }
    current.count += 1;
    await this.state.storage.put("bucket", current);
    const retryAfter = Math.max(1, Math.ceil((current.startedAt + windowSeconds * 1000 - now) / 1000));
    return jsonResponse(200, { allowed: current.count <= limit, remaining: Math.max(0, limit - current.count), retryAfter });
  }
}

export default {
  async fetch(request, env) {
    let config;
    try { config = readConfig(env); } catch (error) { return textResponse(503, error.message || "Gateway is not configured."); }

    const publicUrl = new URL(request.url);
    if (request.method === "OPTIONS") return optionsResponse(request, config);
    if (publicUrl.pathname === HEALTH_PATH) return jsonResponse(200, { status: "ok", service: "al-fahd-live-gateway" });
    if (request.method !== "GET" && request.method !== "HEAD") return textResponse(405, "Only GET and HEAD are supported.", { Allow: "GET, HEAD, OPTIONS" });

    const auth = await authenticate(request, env);
    if (auth.error) return auth.error;

    let limit;
    try { limit = await rateLimit(request, env, config, auth.subject); } catch { return textResponse(503, "Rate limiter is unavailable."); }
    if (!limit.allowed) return textResponse(429, "Too many stream requests.", { "Retry-After": String(limit.retryAfter) });

    let target;
    try { target = resolveTarget(request, config); } catch (error) { return textResponse(400, error.message || "Invalid target."); }

    try {
      const upstreamResponse = await fetch(upstreamRequest(request, target, config));
      const location = upstreamResponse.headers.get("location");
      let rewrittenLocation = "";
      if (location) {
        const redirectTarget = new URL(location, target);
        rewrittenLocation = proxyUrl(redirectTarget, publicUrl, auth.token, config);
        if (!rewrittenLocation) return textResponse(502, "Upstream redirected to a blocked origin.");
      }
      const headers = responseHeaders(upstreamResponse, request, config, rewrittenLocation);
      const contentType = upstreamResponse.headers.get("content-type") || "";

      if (request.method === "HEAD" || !upstreamResponse.body) {
        return new Response(null, { status: upstreamResponse.status, statusText: upstreamResponse.statusText, headers });
      }
      if (isHtml(contentType)) return rewriteHtml(upstreamResponse, headers, target, publicUrl, auth.token, config);
      if (isPlaylist(contentType, target)) {
        const playlist = rewritePlaylist(await upstreamResponse.text(), target, publicUrl, auth.token, config);
        headers.delete("content-length");
        headers.set("Content-Type", "application/vnd.apple.mpegurl; charset=UTF-8");
        headers.set("Cache-Control", "private, no-store");
        return new Response(playlist, { status: upstreamResponse.status, statusText: upstreamResponse.statusText, headers });
      }
      return new Response(upstreamResponse.body, { status: upstreamResponse.status, statusText: upstreamResponse.statusText, headers });
    } catch (error) {
      console.error("Live gateway upstream failure", { message: error instanceof Error ? error.message : String(error), path: publicUrl.pathname });
      return textResponse(502, "The upstream stream could not be reached.");
    }
  },
};
