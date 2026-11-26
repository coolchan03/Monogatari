// Launches the real Electron shell against a local test server (run under xvfb on Linux:
//   NODE_PATH=$(npm root -g) xvfb-run -a node e2e/electron.mjs)
import { createRequire } from 'node:module';
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const { _electron } = createRequire(import.meta.url)('playwright');
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
let seen = null;
const srv = http.createServer((req, res) => {
	seen = { ...req.headers };
	res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' }); // deliberately no CORS headers
	res.end('<p>héllo</p>');
});
await new Promise((r) => srv.listen(0, '127.0.0.1', r));
const port = srv.address().port;

let failed = 0;
const check = (n, ok, extra = '') => {
	console.log(`${ok ? 'PASS' : 'FAIL'}  ${n} ${ok ? '' : extra}`);
	if (!ok) failed++;
};
const app = await _electron.launch({ executablePath: join(root, 'node_modules/electron/dist/electron'), args: ['--no-sandbox', root], cwd: root });
try {
	const win = await app.firstWindow();
	await win.waitForSelector('.nav', { timeout: 20000 });
	check('app UI loads from app://monogatari', win.url().startsWith('app://monogatari'), win.url());
	check('desktop bridge exposed', await win.evaluate(() => !!window.monogatariDesktop));
	const res = await win.evaluate((p) => window.monogatariDesktop.request({ method: 'GET', url: `http://127.0.0.1:${p}/x`, headers: { Referer: 'https://site.test/', 'X-Test': '1' } }), port);
	check('cross-origin request without CORS succeeds', res.statusCode === 200 && res.body === '<p>héllo</p>', JSON.stringify(res));
	check('custom Referer header is sent', seen?.referer === 'https://site.test/', JSON.stringify(seen));
	check('user agent has no Electron token', !/Electron/.test(seen?.['user-agent'] ?? ''), seen?.['user-agent']);
	const bin = await win.evaluate((p) => window.monogatariDesktop.request({ method: 'GET', url: `http://127.0.0.1:${p}/x`, headers: {}, binary: true }), port);
	check('binary responses come back as base64', Buffer.from(bin.bodyBase64, 'base64').toString() === '<p>héllo</p>');
	// image requests carry the headers an extension asked for (Referer works for renderer requests)
	seen = null;
	await win.evaluate(
		(p) =>
			new Promise((resolve) => {
				const url = `http://127.0.0.1:${p}/img.png`;
				window.monogatariDesktop.setImageHeaders(url, { Referer: 'https://img-site.test/', 'X-Img': 'yes' }).then(() => {
					const im = new Image();
					im.onload = im.onerror = () => resolve();
					im.src = url;
				});
			}),
		port,
	);
	check('image request gets extension Referer + headers', seen?.referer === 'https://img-site.test/' && seen?.['x-img'] === 'yes', JSON.stringify(seen));
	// the sandboxed extension iframe still works inside Electron
	const ok = await win.evaluate(
		() =>
			new Promise((resolve) => {
				const f = document.createElement('iframe');
				f.setAttribute('sandbox', 'allow-scripts');
				f.srcdoc = '<script>parent.postMessage("pong","*")<\/script>';
				addEventListener('message', (e) => e.data === 'pong' && resolve(true));
				document.body.appendChild(f);
				setTimeout(() => resolve(false), 5000);
			}),
	);
	check('sandboxed iframe runs scripts', ok);
} finally {
	await app.close();
	srv.close();
}
process.exit(failed ? 1 : 0);
