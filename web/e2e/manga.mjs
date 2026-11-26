// Manga reader smoke test with a tiny synthetic Mangayomi extension (HTML scraping + image pages).
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const { chromium } = createRequire(import.meta.url)('playwright');
const here = dirname(fileURLToPath(import.meta.url));
const cors = { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' };
const APP = 'http://localhost:4175/';
const server = spawn(process.execPath, [join(here, '../node_modules/vite/bin/vite.js'), 'preview', '--port', '4175', '--strictPort'], { cwd: join(here, '..'), stdio: 'pipe' });
await new Promise((resolve) => server.stdout.on('data', (d) => String(d).includes('4175') && resolve()));

const ext = `
class DefaultExtension extends MProvider {
  async getPopular(page) {
    const res = await new Client().get("https://manga.test/list");
    const doc = new Document(res.body);
    const list = doc.select("div.entry:containsOwn(Demo)").map((e) => ({
      name: e.selectFirst("a").text, link: e.selectFirst("a").attr("href"), imageUrl: e.selectFirst("img").getSrc,
    }));
    return { list, hasNextPage: false };
  }
  async getLatestUpdates(page) { return this.getPopular(page); }
  async search(q, page, f) { return this.getPopular(page); }
  async getDetail(url) {
    const res = await new Client().get("https://manga.test" + url);
    const doc = new Document(res.body);
    const chapters = doc.select("li.ch a").map((a) => ({ name: a.text, url: a.attr("href"), dateUpload: String(Date.parse("2024-01-01")) }));
    return { name: doc.selectFirst("h1").text, imageUrl: "https://manga.test/c.png", description: "d", genre: ["Action"], status: 0, chapters };
  }
  async getPageList(url) {
    return [1, 2, 3].map((n) => "https://manga.test/p" + n + ".png");
  }
}`;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
const html = {
	'/list': '<div class="entry">Demo <a href="/m/1">Demo Manga</a><img src="https://manga.test/c.png"></div><div class="entry">Skip <a href="/m/2">Other</a></div>',
	'/m/1': '<h1>Demo Manga</h1><ul><li class="ch"><a href="/m/1/c2">Chapter 2</a></li><li class="ch"><a href="/m/1/c1">Chapter 1</a></li></ul>',
};
const index = JSON.stringify([{ id: 7, name: 'MangaTest', baseUrl: 'https://manga.test', lang: 'en', version: '1.0.0', itemType: 0, sourceCodeLanguage: 1, sourceCodeUrl: 'https://repo.test/m.js' }]);

let failed = 0;
const check = (n, ok, extra = '') => {
	console.log(`${ok ? 'PASS' : 'FAIL'}  ${n} ${ok ? '' : extra}`);
	if (!ok) failed++;
};
const browser = await chromium.launch();
try {
	const page = await browser.newPage({ viewport: { width: 900, height: 900 } });
	const errors = [];
	page.on('pageerror', (e) => errors.push(e.message));
	await page.route(/^https:\/\/(repo|manga)\.test\//, (route) => {
		const u = new URL(route.request().url());
		if (u.hostname === 'repo.test') return route.fulfill({ status: 200, headers: { ...cors, 'content-type': u.pathname.endsWith('.js') ? 'text/javascript' : 'application/json' }, body: u.pathname.endsWith('.js') ? ext : index });
		if (u.pathname.endsWith('.png')) return route.fulfill({ status: 200, headers: { ...cors, 'content-type': 'image/png' }, body: png });
		const body = html[u.pathname];
		return body ? route.fulfill({ status: 200, headers: { ...cors, 'content-type': 'text/html' }, body }) : route.fulfill({ status: 404, headers: cors, body: '' });
	});
	await page.goto(APP + '#/repos');
	await page.fill('input[aria-label="Repository URL"]', 'https://repo.test/index.json');
	await page.click('button:has-text("Add")');
	await page.click('button:has-text("Install")');
	await page.waitForSelector('button:has-text("Uninstall")');
	await page.goto(APP + '#/source/7');
	await page.waitForSelector('.card');
	const titles = await page.$$eval('.card-title', (e) => e.map((x) => x.textContent));
	check('jsoup :containsOwn filtering works in a real extension', titles.length === 1 && titles[0] === 'Demo Manga', JSON.stringify(titles));
	await page.click('.card');
	await page.waitForSelector('.chapter');
	const order = await page.$$eval('.chapter-name', (e) => e.map((x) => x.textContent));
	check('chapters shown newest first', order[0] === 'Chapter 2', JSON.stringify(order));
	await page.click('a:has-text("Start")');
	await page.waitForSelector('.pager img', { state: 'attached', timeout: 15000 });
	check('reader shows page 1 of 3', (await page.textContent('.reader-bar.bottom')).includes('1 / 3'));
	await page.keyboard.press('ArrowLeft'); // RTL default: left = next
	await page.waitForFunction(() => document.querySelector('.reader-bar.bottom')?.textContent?.includes('2 / 3'));
	check('RTL paging', true);
	await page.keyboard.press('ArrowRight');
	await page.waitForFunction(() => document.querySelector('.reader-bar.bottom')?.textContent?.includes('1 / 3'));
	check('paging back', true);
	// webtoon mode
	await page.click('button[aria-label="Reader settings"]');
	await page.selectOption('.panel select >> nth=0', 'webtoon');
	await page.waitForSelector('.webtoon img', { state: 'attached' });
	check('webtoon renders all pages', (await page.$$('.webtoon img')).length === 3);
	check('no uncaught page errors', errors.length === 0, errors.join('; '));
} finally {
	await browser.close();
	server.kill();
}
process.exit(failed ? 1 : 0);
