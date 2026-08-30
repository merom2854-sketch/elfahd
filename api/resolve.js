const allowedHosts = new Set(['akwam.it', 'akwam.ss', 'ak.sv']);

function allowed(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return [...allowedHosts].some(domain => host === domain || host.endsWith(`.${domain}`));
  } catch { return false; }
}

function firstMatch(html, expression) {
  return expression.exec(html)?.[1]?.replace(/&amp;/g, '&').trim() || '';
}

function plain(value) {
  return String(value || '').replace(/<[^>]+>/g, ' ').replace(/&(?:amp|quot);/g, ' ').replace(/\s+/g, ' ').trim();
}

module.exports = async (request, response) => {
  const raw = String(request.query.url || '').trim();
  if (!allowed(raw)) return response.status(400).json({ status: 'error', message: 'Invalid source URL' });

  try {
    const headers = { 'User-Agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36', Accept: 'text/html,application/xhtml+xml' };
    const page = await fetch(raw.replace(/^https:\/\/(?:akwam\.ss|ak\.sv)\//i, 'https://akwam.it/'), { headers, redirect: 'follow' });
    if (!page.ok) throw new Error(`Source HTTP ${page.status}`);
    const html = await page.text();

    const links = [...html.matchAll(/href=["'](https?:\/\/(?:[\w-]+\.)?(?:akwam\.it|akwam\.ss|ak\.sv)\/(?:download|watch)\/[^"']+)["']/gi)].map(match => match[1]);
    // A source item can publish several qualities; some listed watch links may
    // be expired. Probe the small candidate set concurrently and use the first
    // page that actually contains a media source.
    const candidates = [...new Set([...links.filter(link => /\/watch\//i.test(link)), ...links.filter(link => /\/download\//i.test(link))])].slice(0, 12);
    const resolved = await Promise.all(candidates.map(async target => {
      try {
        const item = await fetch(target.replace(/^https:\/\/akwam\.ss\//i, 'https://akwam.it/'), { headers, redirect: 'follow' });
        if (!item.ok) return '';
        const sourcePage = await item.text();
        return firstMatch(sourcePage, /<source[^>]+src=["'](https?:\/\/[^"']+)["']/i) || firstMatch(sourcePage, /(?:src|file)\s*[:=]\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)["']/i);
      } catch { return ''; }
    }));
    const media = resolved.find(value => /^https:\/\//i.test(value)) || '';
    if (!/^https:\/\//i.test(media)) throw new Error('No playable media found');

    const description = firstMatch(html, /<meta[^>]+name=["']description["'][^>]+content=["']([^"']+)["']/i);
    const castArea = html.split(/طاقم العمل والبطولة[^<]*/i)[1] || '';
    const actors = [...castArea.matchAll(/<b[^>]*>([^<:]{2,80}):<\/b>/gi)].map(match => plain(match[1])).filter(name => name && !/طاقم|الممثلين/i.test(name)).filter((name, index, list) => list.indexOf(name) === index).slice(0, 12);
    response.setHeader('Cache-Control', 'no-store');
    return response.status(200).json({ status: 'success', media_src: media, download_src: media, description: plain(description), actors });
  } catch (error) {
    return response.status(502).json({ status: 'error', message: error.message || 'Resolve failed' });
  }
};
