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
const PRIVATE_IP = /^(127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.|0\.|\[?(::1?\]?$|::ffff:|fc|fd|fe[89ab]))/i;
const MAX_REDIRECTS = 5;
const MAX_BODY_BYTES = 8 * 1024 * 1024; // request bodies larger than this are refused
const DROP_UPSTREAM = /^(host|content-length|connection|cf-|x-forwarded-|x-real-ip)/i;

export function targetAllowed(url) {
	return /^https?:$/.test(url.protocol) && !BLOCKED_HOST.test(url.hostname) && !PRIVATE_IP.test(url.hostname);
}

export default {
	async fetch(request, env) {
		const origin = request.headers.get('origin') || '';
		const allowed = env && env.ALLOWED_ORIGIN ? String(env.ALLOWED_ORIGIN).split(',').map((s) => s.trim()) : null;
		// With an allow-list configured a request must name an allowed origin; a missing Origin header (curl, scripts) is refused.
		if (allowed && !allowed.includes(origin)) return new Response('Origin not allowed', { status: 403 });
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
		if (!targetAllowed(upstream)) {
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
		if (hasBody && Number(request.headers.get('content-length') || 0) > MAX_BODY_BYTES) {
			return new Response('Request body too large', { status: 413, headers: cors });
		}
		// Follow redirects by hand so every hop is checked against the same rules as the first target.
		let res;
		let current = upstream;
		let method = request.method;
		let body = hasBody ? request.body : undefined;
		try {
			for (let hop = 0; ; hop++) {
				res = await fetch(current.toString(), { method, headers, body, redirect: 'manual' });
				const location = res.headers.get('location');
				if (res.status < 300 || res.status >= 400 || !location) break;
				if (hop >= MAX_REDIRECTS) return new Response('Too many redirects', { status: 508, headers: cors });
				let next;
				try {
					next = new URL(location, current);
				} catch {
					return new Response('Bad redirect', { status: 502, headers: cors });
				}
				if (!targetAllowed(next)) return new Response('Redirect target not allowed', { status: 403, headers: cors });
				if (next.origin !== current.origin) {
					// Never carry credentials to another origin.
					for (const k of ['cookie', 'authorization']) headers.delete(k);
				}
				if (res.status === 303 || ((res.status === 301 || res.status === 302) && method === 'POST')) {
					method = 'GET';
					body = undefined;
					headers.delete('content-type');
				} else if (body) {
					return new Response('Cannot replay request body across a redirect', { status: 502, headers: cors });
				}
				current = next;
			}
		} catch (e) {
			return new Response('Upstream request failed: ' + (e && e.message), { status: 502, headers: cors });
		}
		const out = new Headers(res.headers);
		out.delete('set-cookie'); // never hand upstream cookies to a web page
		out.delete('content-security-policy');
		for (const [k, v] of Object.entries(cors)) out.set(k, v);
		out.set('x-mgt-final-url', current.toString());
		return new Response(res.body, { status: res.status, statusText: res.statusText, headers: out });
	},
};
