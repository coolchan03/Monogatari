// End-to-end smoke test: installs the real wordrain69 Mangayomi extension from the Android test fixtures and walks
// through browse -> details -> chapter reading in a headless browser.
// Run: npm run build && node e2e/smoke.mjs   (needs the preinstalled Playwright: NODE_PATH=$(npm root -g))
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const { chromium } = createRequire(import.meta.url)('playwright');
const here = dirname(fileURLToPath(import.meta.url));
const fixtures = join(here, '../../app/src/test/resources/jsext');
const read = (p) => readFileSync(join(fixtures, p), 'utf8');
const cors = { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-expose-headers': '*' };
const APP = 'http://localhost:4173/';

const server = spawn(process.execPath, [join(here, '../node_modules/vite/bin/vite.js'), 'preview', '--port', '4173', '--strictPort'], { cwd: join(here, '..'), stdio: 'pipe' });
await new Promise((resolve) => server.stdout.on('data', (d) => String(d).includes('4173') && resolve()));

const pages = {
	'https://wordrain69.com/manga-genre/novel/page/1/?m_orderby=trending': 'wordrain69/list.html',
	'https://wordrain69.com/?s=rain': 'wordrain69/list.html',
	'https://wordrain69.com/manga/novel-one/': 'wordrain69/detail.html',
	'https://wordrain69.com/manga/novel-one/ajax/chapters/': 'wordrain69/chapters.html',
	'https://wordrain69.com/manga/novel-one/chapter-1/': 'wordrain69/chapter.html',
};
const index = JSON.stringify([
	{ id: 1, name: 'Wordrain69', baseUrl: 'https://wordrain69.com', lang: 'en', version: '0.0.4', itemType: 2, sourceCodeLanguage: 1, sourceCodeUrl: 'https://example.test/wordrain69.js' },
]);

let failed = 0;
const check = (name, ok, extra = '') => {
	console.log(`${ok ? 'PASS' : 'FAIL'}  ${name} ${ok ? '' : extra}`);
	if (!ok) failed++;
};

const browser = await chromium.launch();
try {
	const page = await browser.newPage({ viewport: { width: 1100, height: 800 } });
	const errors = [];
	page.on('pageerror', (e) => errors.push(e.message));
	await page.route(/^https:\/\/(example\.test|wordrain69\.com)\//, async (route) => {
		const u = route.request().url();
		const h = { ...cors };
		if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers: h });
		if (u === 'https://example.test/index.json') return route.fulfill({ status: 200, headers: { ...h, 'content-type': 'application/json' }, body: index });
		if (u === 'https://example.test/wordrain69.js') return route.fulfill({ status: 200, headers: { ...h, 'content-type': 'text/javascript' }, body: read('wordrain69.js') });
		const f = pages[u];
		if (f) return route.fulfill({ status: 200, headers: { ...h, 'content-type': 'text/html; charset=utf-8' }, body: read(f) });
		return route.fulfill({ status: 404, headers: h, body: '' });
	});

	await page.goto(APP + '#/repos');
	await page.fill('input[aria-label="Repository URL"]', 'https://example.test/index.json');
	await page.click('button:has-text("Add")');
	await page.waitForSelector('text=1 sources');
	check('repository index parsed', true);
	await page.click('button[role=tab]:has-text("Novels")');
	await page.waitForSelector('text=Wordrain69');
	await page.click('button:has-text("Install")');
	await page.waitForSelector('button:has-text("Uninstall")');
	check('extension installed', true);

	await page.goto(APP + '#/explore');
	await page.click('button[role=tab]:has-text("Novels")');
	await page.click('a:has-text("Wordrain69")');
	await page.waitForSelector('.card', { timeout: 20000 });
	const titles = await page.$$eval('.card-title', (els) => els.map((e) => e.textContent));
	check('popular list parsed through the sandbox', JSON.stringify(titles) === JSON.stringify(['Novel One', 'Novel Two']), JSON.stringify(titles));

	await page.click('.card >> nth=0');
	await page.waitForSelector('.chapter', { timeout: 20000 });
	const chapters = await page.$$eval('.chapter-name', (els) => els.map((e) => e.textContent));
	check('details + chapter ajax POST', chapters.length === 2 && chapters.includes('Chapter 1'), JSON.stringify(chapters));
	const info = await page.textContent('.details-info');
	check('author shown', info.includes('Jane Doe'), info);

	await page.click('button:has-text("Add to library")');
	await page.click('a:has-text("Chapter 1")');
	await page.waitForSelector('.novel-text', { timeout: 20000 });
	const text = await page.textContent('.novel-text');
	check('novel chapter rendered', text.includes('Hello') && text.includes('world'), text);

	await page.goto(APP + '#/');
	await page.waitForSelector('.card');
	check('library lists the favourite', (await page.$$('.card')).length === 1);
	await page.goto(APP + '#/history');
	await page.waitForSelector('.item', { timeout: 5000 });
	const hist = await page.textContent('main');
	check('history recorded', hist.includes('Novel One'), hist);

	await page.goto(APP + '#/settings');
	const [dl] = await Promise.all([page.waitForEvent('download'), page.click('button:has-text("Export backup")')]);
	const backup = JSON.parse(readFileSync(await dl.path(), 'utf8'));
	check('backup export contains library + source', backup.favourites.length === 1 && backup.sources.length === 1 && backup.history.length >= 1);

	check('no uncaught page errors', errors.length === 0, errors.join('; '));
} finally {
	await browser.close();
	server.kill();
}
process.exit(failed ? 1 : 0);
