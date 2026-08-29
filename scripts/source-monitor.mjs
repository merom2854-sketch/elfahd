const WORKER = 'https://akwam-stream-fetcher.meroo3292.workers.dev/';
const SOURCE = 'https://akwam.it/movies';

async function request(url, accept = 'application/json') {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20_000);
  try {
    const response = await fetch(url, { signal: controller.signal, headers: { Accept: accept, 'User-Agent': 'Al-Fahd-Source-Monitor/1.0' } });
    const body = await response.text();
    if (!response.ok) throw new Error(`HTTP ${response.status} from ${url}`);
    return accept === 'application/json' ? JSON.parse(body) : body;
  } finally { clearTimeout(timer); }
}

const normalize = value => String(value).trim().replace(/^https:\/\/(?:ak\.sv|akwam\.ss)\//i, 'https://akwam.it/');
const movies = await request(`${WORKER}?action=genre&genre=${encodeURIComponent(SOURCE)}`);
if (movies.status !== 'success' || !Array.isArray(movies.data)) throw new Error('Worker genre response is invalid');
const item = movies.data.find(value => /^https:\/\/(?:akwam\.it|akwam\.ss|ak\.sv)\/(?:movie|series)\//i.test(value?.href));
if (!item) throw new Error('Worker returned no playable content');
const page = await request(normalize(item.href), 'text/html,application/xhtml+xml');
const watch = page.match(/href=["'](https?:\/\/(?:ak\.it|ak\.sv|akwam\.it|akwam\.ss)\/watch\/[^"']+)["']/i)?.[1];
if (!watch) throw new Error(`No watch link found for ${item.title}`);
const watchPage = await request(normalize(watch), 'text/html,application/xhtml+xml');
const source = watchPage.match(/<source[^>]+src\s*=\s*["']([^"']+)["']/i)?.[1];
if (!source) throw new Error(`No video source found for ${item.title}`);
const signed = await request(`${WORKER}?action=sign&url=${encodeURIComponent(source)}`);
if (signed.status !== 'success' || !/^https:\/\//i.test(String(signed.url || ''))) throw new Error('Worker signing endpoint failed');
console.log(`Source monitor OK: ${item.title} → signed HTTPS playback URL`);
