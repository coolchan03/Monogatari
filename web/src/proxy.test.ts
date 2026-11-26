import { afterEach, describe, expect, it, vi } from 'vitest';
// @ts-expect-error plain JS Cloudflare Worker module
import worker, { targetAllowed } from '../proxy/worker.js';

const relay = (target: string, init: RequestInit = {}, env: Record<string, string> = {}) =>
	worker.fetch(new Request('https://relay.example/?url=' + encodeURIComponent(target), init), env) as Promise<Response>;

afterEach(() => vi.unstubAllGlobals());

describe('relay target rules', () => {
	it.each(['http://localhost/', 'http://127.0.0.1/', 'http://10.1.2.3/', 'http://192.168.0.5/', 'http://169.254.169.254/', 'http://172.16.0.1/', 'http://100.64.0.1/', 'http://[::1]/', 'http://[::ffff:7f00:1]/', 'http://[fe80::1]/', 'http://foo.internal/', 'ftp://example.com/'])(
		'blocks %s',
		(u) => expect(targetAllowed(new URL(u))).toBe(false),
	);
	it.each(['https://example.com/', 'http://8.8.8.8/', 'https://172.32.0.1/', 'https://100.128.0.1/'])('allows %s', (u) => expect(targetAllowed(new URL(u))).toBe(true));
});

describe('relay origin policy', () => {
	it('refuses requests without an Origin header when ALLOWED_ORIGIN is set', async () => {
		const f = vi.fn();
		vi.stubGlobal('fetch', f);
		const res = await relay('https://example.com/', {}, { ALLOWED_ORIGIN: 'https://me.github.io' });
		expect(res.status).toBe(403);
		expect(f).not.toHaveBeenCalled();
	});
	it('refuses other origins and serves the allowed one', async () => {
		vi.stubGlobal('fetch', vi.fn(async () => new Response('ok')));
		const env = { ALLOWED_ORIGIN: 'https://me.github.io' };
		expect((await relay('https://example.com/', { headers: { origin: 'https://evil.example' } }, env)).status).toBe(403);
		const ok = await relay('https://example.com/', { headers: { origin: 'https://me.github.io' } }, env);
		expect(ok.status).toBe(200);
		expect(ok.headers.get('access-control-allow-origin')).toBe('https://me.github.io');
	});
});

describe('relay redirects', () => {
	it('re-validates each redirect hop', async () => {
		const f = vi.fn(async () => new Response(null, { status: 302, headers: { location: 'http://169.254.169.254/latest' } }));
		vi.stubGlobal('fetch', f);
		const res = await relay('https://example.com/');
		expect(res.status).toBe(403);
		expect(f).toHaveBeenCalledTimes(1);
	});
	it('drops cookies when a redirect leaves the origin and reports the final URL', async () => {
		const seen: Array<string | null> = [];
		const f = vi.fn(async (url: string, init: RequestInit) => {
			seen.push(new Headers(init.headers).get('cookie'));
			return url.startsWith('https://a.example')
				? new Response(null, { status: 301, headers: { location: 'https://b.example/x' } })
				: new Response('done');
		});
		vi.stubGlobal('fetch', f);
		const res = await relay('https://a.example/', { headers: { 'x-mgt-headers': JSON.stringify({ Cookie: 'sid=1' }) } });
		expect(seen).toEqual(['sid=1', null]);
		expect(res.headers.get('x-mgt-final-url')).toBe('https://b.example/x');
	});
	it('stops redirect loops', async () => {
		vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 302, headers: { location: 'https://loop.example/' } })));
		expect((await relay('https://loop.example/')).status).toBe(508);
	});
});
