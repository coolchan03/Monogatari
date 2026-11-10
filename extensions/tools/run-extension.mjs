// Minimal host for Mangayomi-style JS extensions, used to check an extension's logic against a
// live site from a plain Node process. It is NOT the app's runtime (QuickJS + Kotlin host); it only
// provides what simple JSON-API extensions need: MProvider, Client (GET/POST via fetch) and
// SharedPreferences. Extensions needing Document/Jsoup helpers cannot be checked with it.
//
// Usage: node run-extension.mjs <extension.js> [searchQuery]
import fs from 'node:fs';
import vm from 'node:vm';

const file = process.argv[2];
const query = process.argv[3] ?? 'dragon';
if (!file) { console.error('usage: node run-extension.mjs <extension.js> [query]'); process.exit(2); }

const UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36';
let requests = 0;
class Client {
  async request(method, url, headers, body) {
    requests++;
    await new Promise(r => setTimeout(r, 400)); // keep it polite
    const res = await fetch(url, { method, headers: { 'User-Agent': UA, ...(headers || {}) }, body, redirect: 'follow' });
    return { statusCode: res.status, body: await res.text(), headers: Object.fromEntries(res.headers) };
  }
  get(url, headers) { return this.request('GET', url, headers); }
  post(url, headers, body) { return this.request('POST', url, headers, typeof body === 'string' ? body : JSON.stringify(body)); }
}
class MProvider {}
class SharedPreferences { get() { return null; } set() {} }

const ctx = vm.createContext({ Client, MProvider, SharedPreferences, console, URL, encodeURIComponent, JSON, Date, Math, Promise, Array, Object, Set, Map, String, Number, isNaN, parseInt, parseFloat, Error });
const src = fs.readFileSync(file, 'utf8');
vm.runInContext(src + '\n;globalThis.__Ext = DefaultExtension; globalThis.__Sources = mangayomiSources;', ctx);
const ext = new ctx.__Ext();
ext.source = ctx.__Sources[0];

const fails = [];
const check = (name, cond, extra = '') => { console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}${extra ? '  ' + extra : ''}`); if (!cond) fails.push(name); };

const pop = await ext.getPopular(1);
check('getPopular returns titles', pop.list.length > 0, `${pop.list.length} items, hasNextPage=${pop.hasNextPage}`);
check('popular items have name/link/absolute cover', pop.list.every(i => i.name && i.link.startsWith('/') && /^https:\/\//.test(i.imageUrl)));

const latest = await ext.getLatestUpdates(1);
check('getLatestUpdates returns titles', latest.list.length > 0, `${latest.list.length} items`);

const found = await ext.search(query, 1, []);
check(`search("${query}") returns titles`, found.list.length > 0, `${found.list.length} items, hasNextPage=${found.hasNextPage}`);
const empty = await ext.search('zzzzqqqqxxxx9', 1, []);
check('search with no hits is an empty list, not an error', Array.isArray(empty.list) && empty.list.length === 0);

// Use the first title that actually has chapters (some sites list unreadable entries).
let detail = null;
for (const cand of [...found.list, ...pop.list].slice(0, 6)) {
  const d = await ext.getDetail(cand.link);
  if (d.chapters.length > 0) { detail = d; break; }
}
if (!detail) { check('found a title with chapters', false); process.exit(1); }
check('getDetail has title/cover/chapters', detail.name && detail.imageUrl && detail.chapters.length > 0, `"${detail.name}" ${detail.chapters.length} chapters, status=${detail.status}`);
const urls = detail.chapters.map(c => c.url);
check('chapter urls are unique', new Set(urls).size === urls.length);
check('chapter dates are numeric strings', detail.chapters.every(c => /^\d+$/.test(c.dateUpload)));

const pages = await ext.getPageList(detail.chapters[0].url);
check('getPageList returns pages', pages.length > 0, `${pages.length} pages`);
check('page urls are absolute and carry a Referer', pages.every(p => /^https:\/\//.test(p.url) && p.headers && p.headers.Referer));
const img = await fetch(pages[0].url, { headers: { 'User-Agent': UA, ...pages[0].headers } });
const buf = await img.arrayBuffer();
check('first page image downloads', img.ok && buf.byteLength > 1000 && /^image\//.test(img.headers.get('content-type') || ''), `${img.status} ${img.headers.get('content-type')} ${buf.byteLength}B`);

let threw = false;
try { await ext.getDetail('/this-title-does-not-exist-zz'); } catch (e) { threw = true; }
check('unknown title throws a clear error', threw);

console.log(`\n${requests} API requests, ${fails.length} failure(s)`);
process.exit(fails.length ? 1 : 0);
