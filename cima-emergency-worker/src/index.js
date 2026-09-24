const ORIGIN = "https://e.cimalight.co";
const CACHE_SECONDS = 10 * 60;
const DEFAULT_HEADERS = {
  Accept: "text/html,application/xhtml+xml",
  "User-Agent": "AlFahdTV-EmergencyCatalog/1.0 (+https://elfahd-tv.vercel.app)",
};

const FEEDS = {
  movies: "/movies.php",
  series: "/all-series.php",
  anime: "/category.php?cat=anime2",
};

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Access-Control-Max-Age": "86400",
};

function json(body, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "public, max-age=60",
      "X-Content-Type-Options": "nosniff",
      ...cors,
      ...extraHeaders,
    },
  });
}

function clean(value = "") {
  return String(value)
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;/gi, '"')
    .replace(/&#0*39;/gi, "'")
    .replace(/\s+/g, " ")
    .trim();
}

function absolute(value, base = ORIGIN) {
  try {
    const url = new URL(value, base);
    return url.protocol === "https:" ? url.toString() : "";
  } catch {
    return "";
  }
}

function allowedCimaUrl(value) {
  try {
    const url = new URL(value);
    if (url.origin !== ORIGIN || url.protocol !== "https:" || url.username || url.password) return null;
    if (!/\/(?:main31|movies\.php|all-series\.php|episodes\.php|category\.php|watch\.php|downloads\.php|search\.php)$/i.test(url.pathname)) return null;
    return url;
  } catch {
    return null;
  }
}

function titleFromBlock(block) {
  const title = block.match(/<h3[^>]*>\s*<a[^>]*>([\s\S]*?)<\/a>/i)?.[1]
    || block.match(/<a[^>]+title=["']([^"']+)["']/i)?.[1]
    || "";
  return clean(title);
}

function imageFromBlock(block) {
  const source = block.match(/<(?:img)[^>]+(?:data-src|src)=["']([^"']+)["']/i)?.[1] || "";
  return absolute(source);
}

function watchUrlFromBlock(block) {
  const source = block.match(/href=["']([^"']*\/watch\.php\?vid=[a-z0-9]+[^"']*)["']/i)?.[1] || "";
  const url = absolute(source);
  return allowedCimaUrl(url)?.toString() || "";
}

function extractCards(html, limit = 40) {
  const cards = [];
  const seen = new Set();
  const blocks = html.match(/<li\b[^>]*>[\s\S]*?<\/li>/gi) || [];
  for (const block of blocks) {
    const href = watchUrlFromBlock(block);
    const title = titleFromBlock(block);
    if (!href || !title || seen.has(href)) continue;
    seen.add(href);
    cards.push({ title, href, img: imageFromBlock(block) });
    if (cards.length >= limit) break;
  }
  return cards;
}

function extractEpisodes(html, limit = 250) {
  const episodes = [];
  const seen = new Set();
  const anchors = html.match(/<a\b[^>]+href=["'][^"']*\/watch\.php\?vid=[a-z0-9]+[^"']*["'][^>]*>[\s\S]*?<\/a>/gi) || [];
  for (const anchor of anchors) {
    const href = watchUrlFromBlock(anchor);
    if (!href || seen.has(href)) continue;
    const title = titleFromBlock(anchor) || clean(anchor);
    if (!/الحلقة|episode/i.test(title)) continue;
    seen.add(href);
    const number = title.match(/(?:الحلقة|episode)\s*(\d+)/i)?.[1] || String(episodes.length + 1);
    episodes.push({ num: number, link: href, title });
    if (episodes.length >= limit) break;
  }
  return episodes;
}

function descriptionFromHtml(html) {
  const description = html.match(/<div[^>]+class=["'][^"']*pm-video-description[^"']*["'][^>]*>([\s\S]*?)<\/div>/i)?.[1]
    || html.match(/<meta[^>]+name=["']description["'][^>]+content=["']([^"']*)["']/i)?.[1]
    || "";
  return clean(description);
}

function titleFromHtml(html) {
  const dataTitle = html.match(/title\s*:\s*['"]([^'"]+)['"]/i)?.[1];
  const heading = html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i)?.[1];
  return clean(dataTitle || heading || "");
}

function imageFromHtml(html) {
  const dataImage = html.match(/(?:thumb_url|preview_image_url)\s*:\s*['"]([^'"]+)['"]/i)?.[1];
  const image = dataImage || html.match(/<meta[^>]+itemprop=["']image["'][^>]+content=["']([^"']+)["']/i)?.[1] || "";
  return absolute(image);
}

function feedFor(value) {
  const cleanValue = String(value || "movies").trim().toLowerCase();
  if (cleanValue in FEEDS) return FEEDS[cleanValue];
  if (cleanValue.includes("anime")) return FEEDS.anime;
  if (cleanValue.includes("series")) return FEEDS.series;
  if (cleanValue.includes("movie")) return FEEDS.movies;
  const passed = allowedCimaUrl(value);
  if (passed && ["/movies.php", "/all-series.php", "/episodes.php", "/category.php"].includes(passed.pathname)) {
    return `${passed.pathname}${passed.search}`;
  }
  return FEEDS.movies;
}

function cacheKey(url) {
  const key = new URL(url);
  key.searchParams.sort();
  return new Request(key.toString(), { method: "GET" });
}

async function fetchHtml(path) {
  const target = new URL(path, ORIGIN);
  const response = await fetch(target, { headers: DEFAULT_HEADERS, redirect: "follow" });
  const body = await response.text();
  if (!response.ok) throw new Error(`upstream_http_${response.status}`);
  // A challenge must be solved/whitelisted by the source owner. This worker
  // reports it rather than attempting to defeat the source's protection.
  if (/just a moment|__cf_chl|challenge-platform/i.test(body)) throw new Error("upstream_challenge");
  return body;
}

async function cachedJson(request, build) {
  const cache = caches.default;
  const key = cacheKey(request.url);
  const current = await cache.match(key);
  try {
    const payload = await build();
    const response = json({ ...payload, cache: "fresh" }, 200, { "Cache-Control": `public, max-age=${CACHE_SECONDS}` });
    await cache.put(key, response.clone());
    return response;
  } catch (error) {
    if (current) {
      const cached = await current.json();
      return json({ ...cached, cache: "stale", staleReason: error instanceof Error ? error.message : "upstream_unavailable" });
    }
    const reason = error instanceof Error ? error.message : "upstream_unavailable";
    return json({ status: "error", source: "cimalight-emergency", message: "تعذر الوصول إلى مصدر الطوارئ", reason }, 503);
  }
}

async function catalogueAction(request, feed) {
  return cachedJson(request, async () => {
    const html = await fetchHtml(feed);
    return {
      status: "success",
      source: "cimalight-emergency",
      upstream: ORIGIN,
      data: extractCards(html),
    };
  });
}

async function searchAction(request, query) {
  const normalized = String(query || "").trim();
  if (normalized.length < 2) return json({ status: "success", source: "cimalight-emergency", data: [] });
  return cachedJson(request, async () => {
    // The source may protect its dedicated search page. A bounded local search
    // over the authorised public feed keeps emergency search useful without
    // attempting to evade that protection.
    const pages = await Promise.all([fetchHtml(FEEDS.movies), fetchHtml(FEEDS.series)]);
    const data = pages.flatMap((html) => extractCards(html, 60))
      .filter((item) => item.title.toLocaleLowerCase().includes(normalized.toLocaleLowerCase()))
      .filter((item, index, values) => values.findIndex((value) => value.href === item.href) === index)
      .slice(0, 30);
    return { status: "success", source: "cimalight-emergency", data };
  });
}

async function detailAction(request, rawSeries) {
  const series = allowedCimaUrl(rawSeries);
  if (!series || series.pathname !== "/watch.php") {
    return json({ status: "error", message: "رابط العمل غير صالح لمصدر الطوارئ" }, 400);
  }
  return cachedJson(request, async () => {
    const html = await fetchHtml(`${series.pathname}${series.search}`);
    const title = titleFromHtml(html);
    const episodes = extractEpisodes(html);
    return {
      status: "success",
      source: "cimalight-emergency",
      movie_title: title,
      description: descriptionFromHtml(html),
      image: imageFromHtml(html),
      // CimaLight's current player delegates playback to a separate host. Do
      // not pretend this HTML page is a direct media stream; the Android app
      // can use source_page only after an authorised playback endpoint exists.
      media_src: "",
      source_page: series.toString(),
      episodes,
      actors: [],
    };
  });
}

export default {
  async fetch(request) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (request.method !== "GET") return json({ status: "error", message: "GET فقط" }, 405);

    const url = new URL(request.url);
    const action = (url.searchParams.get("action") || "health").toLowerCase();
    if (action === "health") {
      return cachedJson(request, async () => {
        const html = await fetchHtml(FEEDS.movies);
        return { status: "success", service: "elfahd-cima-emergency", cards: extractCards(html, 3).length, checkedAt: new Date().toISOString() };
      });
    }
    if (action === "genre") return catalogueAction(request, feedFor(url.searchParams.get("genre")));
    if (action === "search") return searchAction(request, url.searchParams.get("q"));
    if (action === "series") return detailAction(request, url.searchParams.get("series"));
    return json({ status: "error", message: "إجراء غير معروف" }, 400);
  },
};
