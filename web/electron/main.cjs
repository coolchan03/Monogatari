'use strict';
/**
 * Monogatari for Windows: the same web app as the PWA, hosted in Electron.
 *
 * What the desktop shell adds over the browser:
 *  - network requests are made by the main process (Chromium's network stack), so there is no CORS and extensions can set
 *    Referer / User-Agent / Cookie headers;
 *  - Cloudflare "checking your browser" pages open in a real window, the cookie is kept and the request is retried;
 *  - image/video requests can carry the headers an extension asked for;
 *  - a stable app:// origin, so the library (IndexedDB) survives updates.
 */
const { app, BrowserWindow, ipcMain, net, protocol, session, shell, Menu } = require('electron');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const DIST = path.join(__dirname, '..', 'dist');
const ORIGIN = 'app://monogatari';

protocol.registerSchemesAsPrivileged([
	{ scheme: 'app', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } },
]);

if (!app.requestSingleInstanceLock()) {
	app.quit();
}

/** @type {BrowserWindow | null} */
let mainWindow = null;
/** url -> headers an extension wants on a media request (cleared when the map grows too large). */
const mediaHeaders = new Map();
const solved = new Map(); // host -> timestamp of last solved challenge

const CLEAN_UA = app.userAgentFallback.replace(/\s(Electron|monogatari-web|Monogatari)\/\S+/gi, '');
// Cloudflare binds clearance cookies to the user agent, so every request (pages, images, the challenge window) shares one
// browser identity without the Electron tokens.
app.userAgentFallback = CLEAN_UA;
const chromeUserAgent = () => CLEAN_UA;



function createWindow() {
	mainWindow = new BrowserWindow({
		width: 1280,
		height: 840,
		minWidth: 380,
		minHeight: 560,
		backgroundColor: '#101418',
		autoHideMenuBar: true,
		title: 'Monogatari',
		icon: path.join(__dirname, '..', 'build', 'icon.png'),
		webPreferences: {
			preload: path.join(__dirname, 'preload.cjs'),
			contextIsolation: true,
			nodeIntegration: false,
			sandbox: true,
			spellcheck: false,
		},
	});
	mainWindow.loadURL(ORIGIN + '/index.html');
	mainWindow.webContents.setWindowOpenHandler(({ url }) => {
		if (/^https?:\/\//i.test(url)) void shell.openExternal(url);
		return { action: 'deny' };
	});
	mainWindow.webContents.on('will-navigate', (e, url) => {
		if (!url.startsWith(ORIGIN)) {
			e.preventDefault();
			if (/^https?:\/\//i.test(url)) void shell.openExternal(url);
		}
	});
	mainWindow.on('closed', () => (mainWindow = null));
}

app.on('second-instance', () => {
	if (mainWindow) {
		if (mainWindow.isMinimized()) mainWindow.restore();
		mainWindow.focus();
	}
});

// ---------------------------------------------------------------- network

const FORBIDDEN = /^(host|content-length|connection|transfer-encoding|accept-encoding)$/i;

function decode(buffer, contentType) {
	const charset = /charset=([^;]+)/i.exec(contentType || '')?.[1]?.trim().replace(/["']/g, '') || 'utf-8';
	try {
		return new TextDecoder(charset).decode(buffer);
	} catch {
		return new TextDecoder('utf-8').decode(buffer);
	}
}

/**
 * Chromium's network stack will not let a main-process request choose its own Referer, and many extensions need one. Those
 * requests go through Node's http(s) instead, with the Electron session's cookie jar kept in sync by hand.
 */
function nodeRequest(req, depth = 0) {
	const zlib = require('node:zlib');
	return new Promise((resolve, reject) => {
		const u = new URL(req.url);
		const lib = u.protocol === 'https:' ? require('node:https') : require('node:http');
		const method = req.method;
		session.defaultSession.cookies
			.get({ url: req.url })
			.catch(() => [])
			.then((jar) => {
				const headers = Object.assign({ 'user-agent': chromeUserAgent(), accept: '*/*', 'accept-encoding': 'gzip, deflate, br' }, lowerKeys(req.headers || {}));
				if (!headers.cookie && jar.length) headers.cookie = jar.map((c) => c.name + '=' + c.value).join('; ');
				let payload = null;
				if (req.body != null && method !== 'GET' && method !== 'HEAD') {
					payload = Array.isArray(req.body) ? Buffer.from(req.body) : Buffer.from(String(req.body), 'utf8');
					headers['content-length'] = String(payload.length);
				}
				const r = lib.request(u, { method, headers, timeout: 60_000 }, (res) => {
					storeCookies(req.url, res.headers['set-cookie']);
					const status = res.statusCode || 0;
					const location = res.headers.location;
					const manual = req.options && req.options.followRedirects === false;
					if (location && status >= 300 && status < 400 && !manual && depth < 5) {
						res.resume();
						const next = Object.assign({}, req, { url: new URL(location, req.url).toString() });
						if (status === 303 || ((status === 301 || status === 302) && method === 'POST')) {
							next.method = 'GET';
							next.body = null;
						}
						nodeRequest(next, depth + 1).then((x) => resolve(Object.assign(x, { isRedirect: true })), reject);
						return;
					}
					const enc = String(res.headers['content-encoding'] || '').toLowerCase();
					let stream = res;
					if (enc === 'gzip') stream = res.pipe(zlib.createGunzip());
					else if (enc === 'deflate') stream = res.pipe(zlib.createInflate());
					else if (enc === 'br') stream = res.pipe(zlib.createBrotliDecompress());
					const chunks = [];
					stream.on('data', (c) => chunks.push(c));
					stream.on('error', reject);
					stream.on('end', () => {
						const buf = Buffer.concat(chunks);
						const out = {};
						for (const [k, v] of Object.entries(res.headers)) out[k.toLowerCase()] = Array.isArray(v) ? v.join(', ') : String(v);
						resolve({
							statusCode: status,
							reasonPhrase: res.statusMessage || '',
							headers: out,
							body: req.binary ? '' : decode(buf, out['content-type']),
							bodyBase64: req.binary ? buf.toString('base64') : undefined,
							finalUrl: req.url,
							isRedirect: false,
						});
					});
				});
				r.on('timeout', () => r.destroy(new Error('Request timed out')));
				r.on('error', reject);
				if (payload) r.write(payload);
				r.end();
			});
	});
}

function lowerKeys(o) {
	const out = {};
	for (const [k, v] of Object.entries(o)) if (!FORBIDDEN.test(k)) out[k.toLowerCase()] = String(v);
	return out;
}

function storeCookies(url, setCookie) {
	for (const line of setCookie || []) {
		const [pair, ...attrs] = line.split(';');
		const eq = pair.indexOf('=');
		if (eq < 1) continue;
		const cookie = { url, name: pair.slice(0, eq).trim(), value: pair.slice(eq + 1).trim() };
		for (const a of attrs) {
			const [k, v = ''] = a.split('=');
			const key = k.trim().toLowerCase();
			if (key === 'path') cookie.path = v.trim();
			else if (key === 'domain') cookie.domain = v.trim();
			else if (key === 'secure') cookie.secure = true;
			else if (key === 'httponly') cookie.httpOnly = true;
			else if (key === 'max-age' && !Number.isNaN(Number(v))) cookie.expirationDate = Date.now() / 1000 + Number(v);
			else if (key === 'expires' && cookie.expirationDate == null) {
				const t = Date.parse(v);
				if (!Number.isNaN(t)) cookie.expirationDate = t / 1000;
			}
		}
		session.defaultSession.cookies.set(cookie).catch(() => {});
	}
}

const wantsReferer = (req) => Object.keys(req.headers || {}).some((k) => k.toLowerCase() === 'referer');
const rawRequest = (req) => (wantsReferer(req) ? nodeRequest(req) : chromiumRequest(req));

function chromiumRequest(req) {
	return new Promise((resolve, reject) => {
		let finalUrl = req.url;
		let redirected = false;
		const manual = req.options && req.options.followRedirects === false;
		const r = net.request({ method: req.method, url: req.url, session: session.defaultSession, useSessionCookies: true, redirect: manual ? 'manual' : 'follow' });
		const headers = Object.assign({ 'User-Agent': chromeUserAgent(), Accept: '*/*' }, req.headers || {});
		for (const [k, v] of Object.entries(headers)) {
			if (FORBIDDEN.test(k)) continue;
			try {
				r.setHeader(k, String(v));
			} catch {
				/* a header Chromium refuses */
			}
		}
		const timer = setTimeout(() => {
			r.abort();
			reject(new Error('Request timed out'));
		}, 60_000);
		r.on('redirect', (_status, _method, url) => {
			finalUrl = url;
			redirected = true;
		});
		r.on('response', (res) => {
			const chunks = [];
			res.on('data', (c) => chunks.push(c));
			res.on('error', (e) => (clearTimeout(timer), reject(e)));
			res.on('end', () => {
				clearTimeout(timer);
				const buf = Buffer.concat(chunks);
				const out = {};
				for (const [k, v] of Object.entries(res.headers)) out[k.toLowerCase()] = Array.isArray(v) ? v.join(', ') : String(v);
				resolve({
					statusCode: res.statusCode,
					reasonPhrase: res.statusMessage || '',
					headers: out,
					body: req.binary ? '' : decode(buf, out['content-type']),
					bodyBase64: req.binary ? buf.toString('base64') : undefined,
					finalUrl,
					isRedirect: redirected,
				});
			});
		});
		r.on('error', (e) => (clearTimeout(timer), reject(e)));
		if (req.body != null && req.method !== 'GET' && req.method !== 'HEAD') {
			r.write(Array.isArray(req.body) ? Buffer.from(req.body) : Buffer.from(String(req.body), 'utf8'));
		}
		r.end();
	});
}

function looksLikeChallenge(res) {
	if (res.statusCode !== 403 && res.statusCode !== 503) return false;
	if (/challenge/i.test(res.headers['cf-mitigated'] || '')) return true;
	return /cloudflare/i.test(res.headers.server || '') && /just a moment|cf-chl|attention required/i.test(res.body || '');
}

/** Opens the site in a normal window so the person can pass the check; resolves when cf_clearance appears or the window closes. */
function solveChallenge(url) {
	return new Promise((resolve) => {
		const host = new URL(url).hostname;
		const win = new BrowserWindow({
			width: 900,
			height: 720,
			parent: mainWindow || undefined,
			title: 'Verify you are human — close this window when the page loads',
			autoHideMenuBar: true,
			webPreferences: { session: session.defaultSession, sandbox: true, contextIsolation: true },
		});
		win.webContents.setUserAgent(chromeUserAgent());
		let done = false;
		const finish = () => {
			if (done) return;
			done = true;
			clearInterval(poll);
			if (!win.isDestroyed()) win.close();
			solved.set(host, Date.now());
			resolve();
		};
		const poll = setInterval(async () => {
			try {
				const cookies = await session.defaultSession.cookies.get({ name: 'cf_clearance' });
				if (cookies.some((c) => host.endsWith(c.domain.replace(/^\./, '')))) setTimeout(finish, 800);
			} catch {
				/* keep waiting */
			}
		}, 1000);
		win.on('closed', finish);
		setTimeout(finish, 180_000);
		void win.loadURL(url).catch(() => {});
	});
}

ipcMain.handle('http:request', async (_e, req) => {
	if (!req || !/^https?:\/\//i.test(req.url)) throw new Error('Unsupported URL');
	let res = await rawRequest(req);
	if (looksLikeChallenge(res)) {
		const host = new URL(req.url).hostname;
		if (!solved.has(host) || Date.now() - solved.get(host) > 60_000) {
			await solveChallenge(new URL(req.url).origin + '/');
			res = await rawRequest(req);
		}
	}
	return res;
});

ipcMain.handle('media:headers', (_e, url, headers) => {
	if (typeof url !== 'string' || !/^https?:\/\//i.test(url) || !headers || typeof headers !== 'object') return;
	if (mediaHeaders.size > 5000) mediaHeaders.clear();
	mediaHeaders.set(url, headers);
});

ipcMain.handle('app:info', () => ({ version: app.getVersion(), platform: process.platform }));

// ---------------------------------------------------------------- startup

app.whenReady().then(() => {
	Menu.setApplicationMenu(null);

	// Serve the built web app from a stable origin.
	protocol.handle('app', (request) => {
		const url = new URL(request.url);
		let rel = decodeURIComponent(url.pathname);
		if (rel === '/' || rel === '') rel = '/index.html';
		const file = path.normalize(path.join(DIST, rel));
		if (!file.startsWith(DIST)) return new Response('Forbidden', { status: 403 });
		return net.fetch(pathToFileURL(file).toString());
	});

	// Apply extension-requested headers to image/video loads, and a sensible Referer for hotlink-protected hosts.
	session.defaultSession.webRequest.onBeforeSendHeaders({ urls: ['http://*/*', 'https://*/*'] }, (details, callback) => {
		const extra = mediaHeaders.get(details.url);
		const h = details.requestHeaders;
		if (extra) {
			for (const [k, v] of Object.entries(extra)) h[k] = v;
		} else if (!h.Referer && (details.resourceType === 'image' || details.resourceType === 'media')) {
			try {
				h.Referer = new URL(details.url).origin + '/';
			} catch {
				/* ignore */
			}
		}
		callback({ requestHeaders: h });
	});

	// Nothing in the app needs device permissions.
	session.defaultSession.setPermissionRequestHandler((_wc, _permission, cb) => cb(false));

	createWindow();
	app.on('activate', () => BrowserWindow.getAllWindows().length === 0 && createWindow());
});

app.on('window-all-closed', () => {
	if (process.platform !== 'darwin') app.quit();
});
