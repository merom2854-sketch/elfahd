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
    // Watch pages consistently carry a playable source; download pages may only
    // contain quality controls after recent source-side markup changes.
    const landing = links.find(link => /\/watch\//i.test(link)) || links.find(link => /\/download\//i.test(link)) || '';
    const target = landing || raw;
    const sourcePage = target === raw ? html : await (await fetch(target.replace(/^https:\/\/akwam\.ss\//i, 'https://akwam.it/'), { headers, redirect: 'follow' })).text();
    const media = firstMatch(sourcePage, /<source[^>]+src=["'](https?:\/\/[^"']+)["']/i) || firstMatch(sourcePage, /(?:src|file)\s*[:=]\s*["'](https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)["']/i);
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
