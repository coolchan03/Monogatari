/**
 * Extension sandbox runtime. Runs inside a sandboxed, opaque-origin iframe and executes one unmodified
 * Mangayomi JS extension. The JS half of Mangayomi's runtime (the shims in app/src/main/resources/jsext) is shared
 * verbatim with the Android app; this file is the browser implementation of the native half (`sendMessage`).
 *
 * Protocol (postMessage with the parent):
 *   parent -> {type:'init', info, code, prefs, shims}      child -> {type:'ready'} | {type:'init-error', message}
 *   parent -> {type:'call', id, member, args}               child -> {type:'result', id, text} | {type:'error', id, message}
 *   child  -> {type:'http', id, req}                         parent -> {type:'http-result', id, res} | {type:'http-error', id, message}
 *   child  -> {type:'log'|'set-pref', ...}
 */
import { DomBridge } from './dom';
import * as C from './crypto';
import { unpack, unpackAndCombine } from './unpacker';
import { parseDates } from './dates';
import { parseEpub, type EpubBook } from './epub';

const HTTP_METHODS: Record<string, string> = {
	http_head: 'HEAD',
	http_get: 'GET',
	http_post: 'POST',
	http_put: 'PUT',
	http_delete: 'DELETE',
	http_patch: 'PATCH',
};
const ASYNC_NAMES = new Set([...Object.keys(HTTP_METHODS), 'evaluateJavascriptViaWebview', 'parseEpub', 'parseEpubChapter']);

const dom = new DomBridge();
let prefs: Record<string, string> = {};
let declared: Record<string, any> = {};
let seq = 0;
const pending = new Map<number, { resolve: (v: any) => void; reject: (e: Error) => void }>();
let lastProblem: string | null = null;

function post(msg: unknown) {
	window.parent.postMessage(msg, '*');
}

function request<T>(msg: Record<string, unknown>): Promise<T> {
	return new Promise((resolve, reject) => {
		const id = ++seq;
		pending.set(id, { resolve, reject });
		post({ ...msg, id });
	});
}

const str = (v: unknown): string => (typeof v === 'string' ? v : v == null ? '' : String(v));

function formEncode(obj: Record<string, unknown>): string {
	return Object.entries(obj)
		.map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(str(v)))
		.join('&');
}

async function http(method: string, a: any[]): Promise<string> {
	const options = a[1] ?? null;
	const url = a[2];
	if (!url) throw new Error('http: missing url');
	const headers: Record<string, string> = { ...(a[3] ?? {}) };
	const body = a[4];
	let payload: string | number[] | null = null;
	if (method !== 'GET' && method !== 'HEAD' && body != null) {
		const ctKey = Object.keys(headers).find((k) => k.toLowerCase() === 'content-type');
		const isJson = !!ctKey && headers[ctKey].toLowerCase().includes('application/json');
		if (typeof body === 'string') payload = body;
		else if (isJson) payload = JSON.stringify(body);
		else if (Array.isArray(body)) payload = body.map((n) => Number(n) & 0xff);
		else if (typeof body === 'object') {
			if (!ctKey) headers['content-type'] = 'application/x-www-form-urlencoded; charset=utf-8';
			payload = formEncode(body);
		} else payload = String(body);
	}
	let res: any;
	try {
		res = await request({ type: 'http', req: { method, url, headers, body: payload, options } });
	} catch (e: any) {
		lastProblem = 'network=' + str(e?.message).replace(/\n/g, ' ').slice(0, 80);
		throw e;
	}
	if (res.statusCode >= 400) {
		lastProblem = `http_status=${res.statusCode}` + (/challenge/i.test(res.headers?.['cf-mitigated'] ?? '') ? ' cf-mitigated' : '');
	}
	post({ type: 'problem', problem: lastProblem });
	return JSON.stringify({
		body: res.body,
		headers: res.headers,
		isRedirect: !!res.isRedirect,
		persistentConnection: true,
		reasonPhrase: res.reasonPhrase,
		statusCode: res.statusCode,
		request: {
			url: res.finalUrl || url,
			method,
			headers,
			followRedirects: true,
			maxRedirects: 5,
			persistentConnection: true,
		},
	});
}

const epubCache = new Map<string, EpubBook>();
async function epub(name: string, a: any[]): Promise<string> {
	const url = str(a[1]);
	if (!url) throw new Error('EPUB: missing URL');
	let book = epubCache.get(url);
	if (!book) {
		const res: any = await request({ type: 'http', req: { method: 'GET', url, headers: a[2] ?? {}, body: null, options: null, binary: true } });
		if (res.statusCode < 200 || res.statusCode > 299) throw new Error(`EPUB download failed: HTTP ${res.statusCode}`);
		if (!res.bodyBase64) throw new Error('EPUB download returned an empty file');
		book = parseEpub(C.b64decode(res.bodyBase64));
		epubCache.set(url, book);
	}
	if (name === 'parseEpub') return JSON.stringify({ title: book.title, ...(book.author ? { author: book.author } : {}), chapters: book.chapters.map((c) => c.title) });
	const wanted = str(a[3]);
	return book.chapters.find((c) => c.title === wanted)?.content ?? '';
}

function preferenceValue(key: string): unknown {
	const d = declared[key];
	if (!d) throw new Error(`Source preference '${key}' is not declared`);
	const stored = prefs[key];
	if (d.listPreference) {
		const values: unknown[] = d.listPreference.entryValues ?? [];
		const index = Number(d.listPreference.valueIndex ?? 0);
		return stored ?? values[index] ?? null;
	}
	for (const kind of ['switchPreferenceCompat', 'checkBoxPreference']) {
		if (d[kind]) return stored != null ? stored === 'true' : !!d[kind].value;
	}
	if (d.editTextPreference) return stored ?? str(d.editTextPreference.value);
	if (d.multiSelectListPreference) {
		let values: unknown = d.multiSelectListPreference.values;
		if (stored != null) {
			try {
				values = JSON.parse(stored);
			} catch {
				/* keep declared default */
			}
		}
		return Array.isArray(values) ? values.map(str) : [];
	}
	return null;
}

function hostSync(name: string, a: any[]): unknown {
	const d = dom.handle(name, a);
	if (d !== undefined) return d;
	switch (name) {
		case 'log':
			post({ type: 'log', message: str(a[0]) });
			return null;
		case 'cryptoHandler':
			return C.cryptoHandler(str(a[0]), str(a[1]), str(a[2]), !!a[3]);
		case 'encryptAESCryptoJS':
			return C.encryptAesCryptoJs(str(a[0]), str(a[1]));
		case 'decryptAESCryptoJS':
			return C.decryptAesCryptoJs(str(a[0]), str(a[1]));
		case 'decryptAESGCM':
			return C.decryptAesGcm(str(a[0]), str(a[1]), str(a[2]), str(a[3]));
		case 'deobfuscateJsPassword':
			return C.deobfuscateJsPassword(str(a[0]));
		case 'unpackJs':
			return unpack(str(a[0])) ?? '';
		case 'unpackJsAndCombine':
			return unpackAndCombine(str(a[0])) ?? '';
		case 'parseDates':
			return JSON.stringify(parseDates(Array.isArray(a[0]) ? a[0] : [], str(a[1]), str(a[2])));
		case 'get':
			return preferenceValue(str(a[0]));
		case 'getString':
			return prefs[str(a[0])] ?? str(a[1]);
		case 'setString':
			prefs[str(a[0])] = str(a[1]);
			post({ type: 'set-pref', key: str(a[0]), value: str(a[1]) });
			return '';
		default:
			if (name.endsWith('Extractor')) throw new Error(`Video extractor not supported in the web build yet: ${name}`);
			throw new Error('Unknown host call: ' + name);
	}
}

async function hostAsync(name: string, a: any[]): Promise<unknown> {
	if (HTTP_METHODS[name]) return http(HTTP_METHODS[name], a);
	if (name === 'parseEpub' || name === 'parseEpubChapter') return epub(name, a);
	if (name.endsWith('Extractor')) throw new Error(`Video extractor not supported in the web build yet: ${name}`);
	throw new Error(`Host call '${name}' is not supported yet`);
}

(window as any).sendMessage = (name: string, argsJson: string) => {
	const a = JSON.parse(argsJson);
	return ASYNC_NAMES.has(name) || name.endsWith('Extractor') ? hostAsync(name, a) : hostSync(name, a);
};
// A bare context has no console that reaches the host; utils.js overrides log/warn/error.
(console as any).info = (console as any).debug = (m: unknown) => post({ type: 'log', message: str(m) });

function runScript(code: string) {
	let error: string | null = null;
	const onError = (e: ErrorEvent) => {
		error = e.message || 'script error';
		e.preventDefault();
	};
	window.addEventListener('error', onError);
	const s = document.createElement('script');
	s.textContent = code;
	document.head.appendChild(s);
	window.removeEventListener('error', onError);
	if (error) throw new Error(error);
}

async function init(msg: any) {
	prefs = { ...(msg.prefs ?? {}) };
	try {
		(window as any).__SOURCE__ = msg.info;
		runScript(msg.shims.replace('__SOURCE_JSON__', () => JSON.stringify(msg.info)));
		runScript(msg.code + '\nvar extention = new DefaultExtension();');
		try {
			const text: string = await (window as any).jsonStringify(() => (window as any).extention.getSourcePreferences());
			const list = JSON.parse(text || '[]');
			declared = Array.isArray(list) ? Object.fromEntries(list.filter((p: any) => p?.key).map((p: any) => [p.key, p])) : {};
		} catch {
			declared = {}; // most sources do not declare preferences, and getSourcePreferences throws by default
		}
		post({ type: 'ready', declared });
	} catch (e: any) {
		post({ type: 'init-error', message: e?.message ?? String(e) });
	}
}

async function call(msg: any) {
	dom.reset();
	lastProblem = null;
	try {
		const ext = (window as any).extention;
		const text: string | undefined = await (window as any).jsonStringify(() =>
			msg.args ? ext[msg.member](...msg.args) : ext[msg.member],
		);
		post({ type: 'result', id: msg.id, text: text ?? '' });
	} catch (e: any) {
		post({ type: 'error', id: msg.id, message: e?.message ?? String(e) });
	}
}

window.addEventListener('message', (e) => {
	if (e.source !== window.parent) return;
	const m = e.data;
	if (!m || typeof m !== 'object') return;
	switch (m.type) {
		case 'init':
			void init(m);
			break;
		case 'call':
			void call(m);
			break;
		case 'prefs':
			prefs = { ...m.prefs };
			break;
		case 'http-result':
		case 'http-error': {
			const p = pending.get(m.id);
			if (!p) return;
			pending.delete(m.id);
			if (m.type === 'http-result') p.resolve(m.res);
			else p.reject(new Error(m.message));
			break;
		}
	}
});
post({ type: 'boot' });
