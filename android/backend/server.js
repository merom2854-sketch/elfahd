import http from 'node:http';
import crypto from 'node:crypto';

const PORT = Number(process.env.PORT || 3000);
const ADMIN_TOKEN = process.env.ADMIN_TOKEN || '';
const HOME_URL = process.env.HOME_URL || 'https://elfahd-tv.vercel.app/#/home';
const FOOTBALL_API_KEY = process.env.FOOTBALL_API_KEY || '';
const TMDB_READ_TOKEN = process.env.TMDB_READ_TOKEN || '';
const buckets = new Map();
let downloadStatsCache = { expires: 0, data: null };

const baseConfig = Object.freeze({
  appName: 'الفهد TV', packageName: 'com.alfahdtv.app.debug', homeUrl: HOME_URL,
  minimumVersionCode: 1, latestVersionCode: 22, latestVersionName: '3.1.0',
  apkUrl: 'https://github.com/merom2854-sketch/elfahd/releases/download/v3.1.0/Al-Fahd-TV-3.1.0.apk',
  maintenance: false, maintenanceMessage: '',
  features: { downloads: true, fullscreenVideo: true, pictureInPicture: true, anime: true, channels: true, secureScreens: true }
});

const ALLOWED_ORIGINS = new Set(['https://elfahd-tv.vercel.app','https://el-nemr-tv.vercel.app']);
function headers(extra={}) { return {
  'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff',
  'X-Frame-Options':'DENY','Referrer-Policy':'no-referrer','Permissions-Policy':'camera=(), microphone=(), geolocation=()',
  'Content-Security-Policy':"default-src 'none'; frame-ancestors 'none'",...extra
}; }
function send(res,status,body,extra){res.writeHead(status,headers(extra));res.end(JSON.stringify(body));}
function cors(req){const origin=String(req.headers.origin||'');return {'Access-Control-Allow-Origin':ALLOWED_ORIGINS.has(origin)?origin:'https://elfahd-tv.vercel.app','Vary':'Origin'};}
function ipOf(req){return String(req.headers['x-forwarded-for']||req.socket.remoteAddress||'unknown').split(',')[0].trim();}
function limited(req){const ip=ipOf(req),now=Date.now(),b=buckets.get(ip)||{start:now,count:0};if(now-b.start>60_000){b.start=now;b.count=0;}b.count++;buckets.set(ip,b);return b.count>90;}
function authorized(req){if(!ADMIN_TOKEN)return false;const supplied=String(req.headers.authorization||'').replace(/^Bearer\s+/i,'');const a=Buffer.from(supplied),b=Buffer.from(ADMIN_TOKEN);return a.length===b.length&&crypto.timingSafeEqual(a,b);}
async function fallbackFootball(date){const upstream=await fetch(`https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=${encodeURIComponent(date)}&s=Soccer`);if(!upstream.ok)throw new Error('Football fallback unavailable');const payload=await upstream.json();return (payload.events||[]).map(item=>({id:item.idEvent,date:item.strTimestamp||`${item.dateEvent}T${item.strTime||'00:00:00'}Z`,status:item.strStatus||'Not Started',elapsed:null,league:{name:item.strLeague||'',country:item.strCountry||'',logo:item.strLeagueBadge||''},home:{name:item.strHomeTeam||'',logo:item.strHomeTeamBadge||'',score:item.intHomeScore==null?null:Number(item.intHomeScore)},away:{name:item.strAwayTeam||'',logo:item.strAwayTeamBadge||'',score:item.intAwayScore==null?null:Number(item.intAwayScore)}}));}
async function footballMatches(date){if(FOOTBALL_API_KEY){try{const upstream=await fetch(`https://v3.football.api-sports.io/fixtures?date=${encodeURIComponent(date)}`,{headers:{'x-apisports-key':FOOTBALL_API_KEY}});const payload=await upstream.json();if(upstream.ok&&!(payload.errors&&Object.keys(payload.errors).length))return (payload.response||[]).map(item=>({id:item.fixture?.id,date:item.fixture?.date,status:item.fixture?.status?.long||'',elapsed:item.fixture?.status?.elapsed,league:{name:item.league?.name||'',country:item.league?.country||'',logo:item.league?.logo||''},home:{name:item.teams?.home?.name||'',logo:item.teams?.home?.logo||'',score:item.goals?.home},away:{name:item.teams?.away?.name||'',logo:item.teams?.away?.logo||'',score:item.goals?.away}}));}catch{}}
  return fallbackFootball(date);
}
async function downloadStats(){const now=Date.now();if(downloadStatsCache.data&&downloadStatsCache.expires>now)return downloadStatsCache.data;const upstream=await fetch('https://api.github.com/repos/merom2854-sketch/elfahd/releases?per_page=100',{headers:{Accept:'application/vnd.github+json','User-Agent':'Al-Fahd-TV'}});if(!upstream.ok)throw new Error('GitHub stats unavailable');const releases=await upstream.json();const apkAssets=releases.flatMap(release=>(release.assets||[]).filter(asset=>/\.apk$/i.test(asset.name)).map(asset=>({tag:release.tag_name,name:asset.name,downloads:Number(asset.download_count)||0})));const data={totalDownloads:apkAssets.reduce((sum,asset)=>sum+asset.downloads,0),latestDownloads:apkAssets[0]?.downloads||0,releaseCount:releases.length,latestVersion:releases[0]?.tag_name||'',updatedAt:new Date().toISOString()};downloadStatsCache={data,expires:now+10*60_000};return data;}
async function metadataActors(title,kind){if(!TMDB_READ_TOKEN)throw new Error('TMDB metadata is not configured');const type=kind==='movie'?'movie':'tv';const raw=String(title||'').trim();if(!raw)return [];const normalized=raw.replace(/\s+(?:الموسم|موسم|season)\s*[^•]+/iu,'').replace(/\s+(?:مدبلج|مترجم).*$/iu,'').trim();const candidates=[...new Set([raw,normalized].filter(Boolean))];let match=null;for(const candidate of candidates){const search=await fetch(`https://api.themoviedb.org/3/search/${type}?query=${encodeURIComponent(candidate)}&include_adult=false&language=ar`,{headers:{Authorization:`Bearer ${TMDB_READ_TOKEN}`,Accept:'application/json'}});if(!search.ok)throw new Error('TMDB search unavailable');const results=(await search.json()).results||[];if(results[0]?.id){match=results[0];break;}}if(!match?.id)return [];const credits=await fetch(`https://api.themoviedb.org/3/${type}/${match.id}/credits?language=ar`,{headers:{Authorization:`Bearer ${TMDB_READ_TOKEN}`,Accept:'application/json'}});if(!credits.ok)throw new Error('TMDB credits unavailable');return ((await credits.json()).cast||[]).slice(0,12).map(item=>({name:String(item.name||'').trim(),image:item.profile_path?`https://image.tmdb.org/t/p/w185${item.profile_path}`:''})).filter(item=>item.name);}

const server=http.createServer((req,res)=>{
  if(limited(req))return send(res,429,{status:'error',message:'Too many requests'},{'Retry-After':'60'});
  if(req.method==='GET'&&req.url==='/health')return send(res,200,{status:'ok',service:'al-fahd-tv-backend'},cors(req));
  if(req.method==='GET'&&req.url==='/v1/config')return send(res,200,{status:'success',data:baseConfig},cors(req));
  if(req.method==='GET'&&req.url==='/v1/stats/downloads')return downloadStats().then(data=>send(res,200,{status:'success',data},{...cors(req),'Cache-Control':'public, max-age=300'})).catch(()=>send(res,502,{status:'error',message:'Download statistics unavailable'},cors(req)));
  if(req.method==='GET'&&req.url.startsWith('/v1/metadata')){const requestUrl=new URL(req.url,'http://localhost');const title=requestUrl.searchParams.get('title')||'';const kind=requestUrl.searchParams.get('kind')||'movie';return metadataActors(title,kind).then(actors=>send(res,200,{status:'success',data:{actors}},{...cors(req),'Cache-Control':'public, max-age=3600'})).catch(()=>send(res,502,{status:'error',message:'Metadata unavailable'},cors(req)));}
  if(req.method==='GET'&&req.url.startsWith('/v1/football/matches')){
    const requestUrl=new URL(req.url,'http://localhost');const date=requestUrl.searchParams.get('date')||new Date().toISOString().slice(0,10);
    if(!/^\d{4}-\d{2}-\d{2}$/.test(date))return send(res,400,{status:'error',message:'Invalid date'});
    return footballMatches(date)
      .then(matches=>send(res,200,{status:'success',date,matches},cors(req)))
      .catch(()=>send(res,502,{status:'error',message:'Football provider unavailable'},cors(req)));
  }
  if(req.url==='/v1/admin/config')return send(res,authorized(req)?501:401,{status:'error',message:authorized(req)?'Persistent config storage is not enabled':'Unauthorized'});
  return send(res,404,{status:'error',message:'Not found'});
});
server.requestTimeout=10_000;server.headersTimeout=11_000;server.listen(PORT,'0.0.0.0',()=>console.log(`Al Fahd TV backend listening on ${PORT}`));
