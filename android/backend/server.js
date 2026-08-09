import http from 'node:http';
import crypto from 'node:crypto';

const PORT = Number(process.env.PORT || 3000);
const ADMIN_TOKEN = process.env.ADMIN_TOKEN || '';
const HOME_URL = process.env.HOME_URL || 'https://el-nemr-tv.vercel.app/#/home';
const FOOTBALL_API_KEY = process.env.FOOTBALL_API_KEY || '';
const buckets = new Map();

const baseConfig = Object.freeze({
  appName: 'الفهد TV', packageName: 'com.alfahdtv.app', homeUrl: HOME_URL,
  minimumVersionCode: 1, latestVersionCode: 1, latestVersionName: '1.0.0',
  maintenance: false, maintenanceMessage: '',
  features: { downloads: true, fullscreenVideo: true, secureScreens: true }
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

const server=http.createServer((req,res)=>{
  if(limited(req))return send(res,429,{status:'error',message:'Too many requests'},{'Retry-After':'60'});
  if(req.method==='GET'&&req.url==='/health')return send(res,200,{status:'ok',service:'al-fahd-tv-backend'},cors(req));
  if(req.method==='GET'&&req.url==='/v1/config')return send(res,200,{status:'success',data:baseConfig},cors(req));
  if(req.method==='GET'&&req.url.startsWith('/v1/football/matches')){
    if(!FOOTBALL_API_KEY)return send(res,503,{status:'error',message:'Football service is not configured'});
    const requestUrl=new URL(req.url,'http://localhost');const date=requestUrl.searchParams.get('date')||new Date().toISOString().slice(0,10);
    if(!/^\d{4}-\d{2}-\d{2}$/.test(date))return send(res,400,{status:'error',message:'Invalid date'});
    return fetch(`https://v3.football.api-sports.io/fixtures?date=${encodeURIComponent(date)}`,{headers:{'x-apisports-key':FOOTBALL_API_KEY}})
      .then(async upstream=>{const payload=await upstream.json();if(!upstream.ok||payload.errors&&Object.keys(payload.errors).length)throw new Error('Football provider unavailable');const matches=(payload.response||[]).map(item=>({id:item.fixture?.id,date:item.fixture?.date,status:item.fixture?.status?.long||'',elapsed:item.fixture?.status?.elapsed,league:{name:item.league?.name||'',country:item.league?.country||'',logo:item.league?.logo||''},home:{name:item.teams?.home?.name||'',logo:item.teams?.home?.logo||'',score:item.goals?.home},away:{name:item.teams?.away?.name||'',logo:item.teams?.away?.logo||'',score:item.goals?.away}}));send(res,200,{status:'success',date,matches},cors(req));})
      .catch(()=>send(res,502,{status:'error',message:'Football provider unavailable'},cors(req)));
  }
  if(req.url==='/v1/admin/config')return send(res,authorized(req)?501:401,{status:'error',message:authorized(req)?'Persistent config storage is not enabled':'Unauthorized'});
  return send(res,404,{status:'error',message:'Not found'});
});
server.requestTimeout=10_000;server.headersTimeout=11_000;server.listen(PORT,'0.0.0.0',()=>console.log(`Al Fahd TV backend listening on ${PORT}`));
