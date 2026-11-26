import { getSettings } from './settings';

export interface HttpRequest {
	method: string;
	url: string;
	headers: Record<string, string>;
	body?: string | number[] | null;
	options?: { followRedirects?: boolean } | null;
	binary?: boolean;
}

export interface HttpResponse {
	statusCode: number;
	reasonPhrase: string;
	headers: Record<string, string>;
	body: string;
	bodyBase64?: string;
	finalUrl: string;
	isRedirect: boolean;
}

/** Present in the Windows (Electron) build only: requests go through the main process, so no CORS applies. */
export interface DesktopBridge {
	request(req: HttpRequest): Promise<HttpResponse>;
	setImageHeaders?(url: string, headers: Record<string, string>): Promise<void>;
	version?: string;
}

declare global {
	interface Window {
		monogatariDesktop?: DesktopBridge;
	}
}

export const isDesktop = () => !!window.monogatariDesktop;

export class HttpError extends Error {}

function toBase64(bytes: Uint8Array): string {
	let s = '';
	for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
	return btoa(s);
}

function decode(buf: ArrayBuffer, contentType: string | null): string {
	const charset = /charset=([^;]+)/i.exec(contentType ?? '')?.[1]?.trim().replace(/['"]/g, '') || 'utf-8';
	try {
		return new TextDecoder(charset).decode(buf);
	} catch {
		return new TextDecoder('utf-8').decode(buf);
	}
}

/**
 * Proxy protocol (see web/proxy/worker.js): GET/POST `${proxy}${encodeURIComponent(url)}`, request headers to forward
 * in `x-mgt-headers` (JSON), final URL answered in `x-mgt-final-url`.
 */
function proxied(proxy: string, url: string): string {
	return proxy.includes('{url}') ? proxy.replace('{url}', encodeURIComponent(url)) : proxy + encodeURIComponent(url);
}

async function viaFetch(req: HttpRequest, proxy: string | null): Promise<HttpResponse> {
	const headers: Record<string, string> = {};
	let target = req.url;
	if (proxy) {
		target = proxied(proxy, req.url);
		headers['x-mgt-headers'] = JSON.stringify(req.headers);
		const ct = Object.keys(req.headers).find((k) => k.toLowerCase() === 'content-type');
		if (ct) headers['content-type'] = req.headers[ct];
	} else {
		// Browsers refuse to set these; sending them would throw in some engines.
		const forbidden = /^(referer|user-agent|origin|cookie|host|accept-encoding|connection|content-length)$/i;
		for (const [k, v] of Object.entries(req.headers)) if (!forbidden.test(k)) headers[k] = v;
	}
	let body: BodyInit | undefined;
	if (req.body != null && req.method !== 'GET' && req.method !== 'HEAD') {
		body = Array.isArray(req.body) ? new Uint8Array(req.body) : req.body;
	}
	const res = await fetch(target, {
		method: req.method,
		headers,
		body,
		redirect: req.options?.followRedirects === false ? 'manual' : 'follow',
		referrerPolicy: 'no-referrer',
		credentials: 'omit',
	});
	const buf = await res.arrayBuffer();
	const out: Record<string, string> = {};
	res.headers.forEach((v, k) => (out[k.toLowerCase()] = v));
	return {
		statusCode: res.status,
		reasonPhrase: res.statusText,
		headers: out,
		body: req.binary ? '' : decode(buf, res.headers.get('content-type')),
		bodyBase64: req.binary ? toBase64(new Uint8Array(buf)) : undefined,
		finalUrl: res.headers.get('x-mgt-final-url') ?? (res.url || req.url),
		isRedirect: res.redirected,
	};
}

export async function execute(req: HttpRequest): Promise<HttpResponse> {
	if (!/^https?:\/\//i.test(req.url)) throw new HttpError('Unsupported URL: ' + req.url);
	if (window.monogatariDesktop) return window.monogatariDesktop.request(req);
	const { proxyUrl, proxyMode } = getSettings();
	const proxy = proxyUrl.trim() || null;
	if (proxy && proxyMode === 'always') return viaFetch(req, proxy);
	if (proxyMode === 'never' || !proxy) {
		try {
			return await viaFetch(req, null);
		} catch (e) {
			if (e instanceof TypeError) {
				throw new HttpError(
					'The browser blocked this request (CORS). Set a proxy under Settings → Network, or use the Windows app, which does not have this limit.',
				);
			}
			throw e;
		}
	}
	try {
		return await viaFetch(req, null);
	} catch (e) {
		if (e instanceof TypeError) return viaFetch(req, proxy);
		throw e;
	}
}

/** URL to put in an <img>/<video> src, honouring the proxy for hotlink-protected hosts. */
export function mediaUrl(url: string, headers?: Record<string, string> | null): string {
	if (!/^https?:\/\//i.test(url)) return url;
	if (window.monogatariDesktop) {
		if (headers && Object.keys(headers).length) void window.monogatariDesktop.setImageHeaders?.(url, headers);
		return url;
	}
	const { proxyUrl, proxyImages } = getSettings();
	if (proxyUrl.trim() && proxyImages) return proxied(proxyUrl.trim(), url) + (headers ? '&h=' + encodeURIComponent(JSON.stringify(headers)) : '');
	return url;
}

export async function fetchBytes(url: string, headers?: Record<string, string> | null): Promise<Blob> {
	const res = await execute({ method: 'GET', url, headers: headers ?? {}, binary: true });
	if (res.statusCode >= 400) throw new HttpError(`HTTP ${res.statusCode}`);
	const bin = atob(res.bodyBase64 ?? '');
	const bytes = new Uint8Array(bin.length);
	for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
	return new Blob([bytes], { type: res.headers['content-type'] ?? 'application/octet-stream' });
}
