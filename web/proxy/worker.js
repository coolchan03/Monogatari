/**
 * Monogatari CORS relay for the browser (PWA) version.
 *
 * Browsers only let a web page read responses from other websites when those sites opt in (CORS), and almost no manga
 * site does. This tiny Cloudflare Worker fetches the page for you and adds the CORS headers. The Windows app does not
 * need it.
 *
 * Deploy (free): Cloudflare dashboard -> Workers & Pages -> Create -> Worker -> paste this file -> Deploy.
 * Then in Monogatari: Settings -> Network -> Proxy URL = https://<your-worker>.workers.dev/?url=
 *
 * Strongly recommended: set an ALLOWED_ORIGIN variable on the Worker (Settings -> Variables), for example
 *   https://coolchan03.github.io
 * so that only your own copy of the app can use your relay. Without it, anyone who finds the URL can use it.
 *
 * Protocol:  <worker>/?url=<encoded target>[&h=<encoded JSON headers>]
 *            request header  x-mgt-headers: JSON object of headers to send upstream (Referer, Origin, User-Agent, Cookie, ...)
 *            response header x-mgt-final-url: the URL after redirects
 */
const BLOCKED_HOST = /^(localhost|.*\.local|.*\.internal|metadata\.google\.internal)$/i;
const PRIVATE_IP = /^(127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|0\.|\[?(::1|fc|fd|fe80))/i;
const DROP_UPSTREAM = /^(host|content-length|connection|cf-|x-forwarded-|x-real-ip)/i;

export default {
	async fetch(request, env) {
		const origin = request.headers.get('origin') || '';
		const allowed = env && env.ALLOWED_ORIGIN ? String(env.ALLOWED_ORIGIN).split(',').map((s) => s.trim()) : null;
		if (allowed && origin && !allowed.includes(origin)) return new Response('Origin not allowed', { status: 403 });
		const cors = {
			'access-control-allow-origin': allowed ? origin : '*',
			'access-control-allow-methods': 'GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS',
			'access-control-allow-headers': 'x-mgt-headers, content-type',
			'access-control-expose-headers': 'x-mgt-final-url, content-type, content-length',
			'access-control-max-age': '86400',
			vary: 'origin',
		};
		if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });

		const reqUrl = new URL(request.url);
		const target = reqUrl.searchParams.get('url');
		if (!target) return new Response('Monogatari relay: pass ?url=<encoded address>', { status: 400, headers: cors });
		let upstream;
		try {
			upstream = new URL(target);
		} catch {
			return new Response('Bad url', { status: 400, headers: cors });
		}
		if (!/^https?:$/.test(upstream.protocol) || BLOCKED_HOST.test(upstream.hostname) || PRIVATE_IP.test(upstream.hostname)) {
			return new Response('Target not allowed', { status: 403, headers: cors });
		}

		const headers = new Headers();
		const raw = request.headers.get('x-mgt-headers') || reqUrl.searchParams.get('h');
		if (raw) {
			try {
				for (const [k, v] of Object.entries(JSON.parse(raw))) if (!DROP_UPSTREAM.test(k)) headers.set(k, String(v));
			} catch {
				/* ignore malformed header JSON */
			}
		}
		const ct = request.headers.get('content-type');
		if (ct && !headers.has('content-type')) headers.set('content-type', ct);
		if (!headers.has('user-agent')) headers.set('user-agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Safari/537.36');

		const hasBody = !['GET', 'HEAD'].includes(request.method);
		let res;
		try {
			res = await fetch(upstream.toString(), { method: request.method, headers, body: hasBody ? request.body : undefined, redirect: 'follow' });
		} catch (e) {
			return new Response('Upstream request failed: ' + (e && e.message), { status: 502, headers: cors });
		}
		const out = new Headers(res.headers);
		out.delete('set-cookie'); // never hand upstream cookies to a web page
		out.delete('content-security-policy');
		for (const [k, v] of Object.entries(cors)) out.set(k, v);
		out.set('x-mgt-final-url', res.url || upstream.toString());
		return new Response(res.body, { status: res.status, statusText: res.statusText, headers: out });
	},
};
