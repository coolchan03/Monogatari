import sandboxJs from '../generated/sandbox.iife.js?raw';
import http from '../../../app/src/main/resources/jsext/http.js?raw';
import dom from '../../../app/src/main/resources/jsext/dom.js?raw';
import utils from '../../../app/src/main/resources/jsext/utils.js?raw';
import extractors from '../../../app/src/main/resources/jsext/extractors.js?raw';
import preferences from '../../../app/src/main/resources/jsext/preferences.js?raw';
import mprovider from '../../../app/src/main/resources/jsext/mprovider.js?raw';
import { execute, type HttpRequest } from './http';
import type { SourceEntry } from './types';

/** Evaluation order matches Mangayomi's service.dart (and the Android app). */
const SHIMS = [http, dom, utils, extractors, preferences, mprovider].join('\n');

const CALL_TIMEOUT_MS = 90_000;

export class ExtensionError extends Error {}

export function sourceInfoJson(e: SourceEntry) {
	return {
		id: e.id,
		name: e.name,
		baseUrl: e.baseUrl,
		lang: e.lang,
		apiUrl: e.apiUrl,
		iconUrl: e.iconUrl,
		dateFormat: e.dateFormat,
		dateFormatLocale: e.dateFormatLocale,
		hasCloudflare: e.hasCloudflare,
		isFullData: false,
		isNsfw: e.isNsfw,
		additionalParams: e.additionalParams,
		notes: e.notes,
		version: e.version,
		itemType: e.itemType,
	};
}

function toBase64(bytes: Uint8Array): string {
	let s = '';
	for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
	return btoa(s);
}

export interface SandboxOptions {
	entry: SourceEntry;
	code: string;
	prefs: Record<string, string>;
	onPref(key: string, value: string): void;
	onLog?(message: string): void;
}

/** One extension running in its own sandboxed iframe (opaque origin: no access to the app's storage or DOM). */
export class Sandbox {
	private frame: HTMLIFrameElement | null = null;
	private ready: Promise<Record<string, any>> | null = null;
	private nextId = 0;
	private waiting = new Map<number, { resolve: (t: string) => void; reject: (e: Error) => void; timer: number }>();
	lastProblem: string | null = null;
	declaredPreferences: Record<string, any> = {};
	lastUsed = Date.now();
	private listener = (e: MessageEvent) => this.onMessage(e);

	constructor(private opts: SandboxOptions) {}

	private start(): Promise<Record<string, any>> {
		if (this.ready) return this.ready;
		this.ready = new Promise((resolve, reject) => {
			const frame = document.createElement('iframe');
			frame.setAttribute('sandbox', 'allow-scripts');
			frame.style.display = 'none';
			frame.setAttribute('aria-hidden', 'true');
			const safe = sandboxJs.replace(/<\/script/gi, '<\\/script');
			frame.srcdoc = `<!doctype html><html><head><meta charset="utf-8"></head><body><script>${safe}</script></body></html>`;
			this.frame = frame;
			window.addEventListener('message', this.listener);
			this.bootResolve = () =>
				frame.contentWindow!.postMessage(
					{ type: 'init', info: sourceInfoJson(this.opts.entry), code: this.opts.code, prefs: this.opts.prefs, shims: SHIMS },
					'*',
				);
			this.readyResolve = resolve;
			this.readyReject = reject;
			document.body.appendChild(frame);
		});
		return this.ready;
	}

	private bootResolve: () => void = () => {};
	private readyResolve: (v: Record<string, any>) => void = () => {};
	private readyReject: (e: Error) => void = () => {};

	private async onMessage(e: MessageEvent) {
		if (!this.frame || e.source !== this.frame.contentWindow) return;
		const m = e.data;
		if (!m || typeof m !== 'object') return;
		switch (m.type) {
			case 'boot':
				this.bootResolve();
				break;
			case 'ready':
				this.declaredPreferences = m.declared ?? {};
				this.readyResolve(m.declared ?? {});
				break;
			case 'init-error':
				this.readyReject(new ExtensionError(`${this.opts.entry.name}: failed to load source: ${m.message}`));
				this.dispose();
				break;
			case 'log':
				this.opts.onLog?.(m.message);
				break;
			case 'problem':
				this.lastProblem = m.problem;
				break;
			case 'set-pref':
				this.opts.prefs[m.key] = m.value;
				this.opts.onPref(m.key, m.value);
				break;
			case 'http':
				try {
					const res = await execute(m.req as HttpRequest);
					this.frame?.contentWindow?.postMessage(
						{
							type: 'http-result',
							id: m.id,
							res: {
								statusCode: res.statusCode,
								reasonPhrase: res.reasonPhrase,
								headers: res.headers,
								body: res.body,
								bodyBase64: res.bodyBase64,
								finalUrl: res.finalUrl,
								isRedirect: res.isRedirect,
							},
						},
						'*',
					);
				} catch (err: any) {
					this.frame?.contentWindow?.postMessage({ type: 'http-error', id: m.id, message: err?.message ?? String(err) }, '*');
				}
				break;
			case 'result':
			case 'error': {
				const w = this.waiting.get(m.id);
				if (!w) break;
				this.waiting.delete(m.id);
				clearTimeout(w.timer);
				if (m.type === 'result') w.resolve(m.text);
				else w.reject(new ExtensionError(m.message));
				break;
			}
		}
	}

	/** Calls `extention.<member>(...args)` (or reads the property when args is null) and returns the JSON result. */
	async call(member: string, args: unknown[] | null): Promise<any> {
		await this.start();
		this.lastUsed = Date.now();
		this.lastProblem = null;
		const id = ++this.nextId;
		const text = await new Promise<string>((resolve, reject) => {
			const timer = window.setTimeout(() => {
				this.waiting.delete(id);
				reject(new ExtensionError(`${this.opts.entry.name}: ${member} timed out`));
			}, CALL_TIMEOUT_MS);
			this.waiting.set(id, { resolve, reject, timer });
			this.frame!.contentWindow!.postMessage({ type: 'call', id, member, args }, '*');
		}).catch((e: Error) => {
			throw new ExtensionError(`${this.opts.entry.name}: ${member} failed: ${e.message.replace(/^.*?: /, '')}`);
		});
		return text ? JSON.parse(text) : null;
	}

	setPref(key: string, value: string) {
		this.opts.prefs[key] = value;
		this.syncPrefs();
	}

	syncPrefs() {
		this.frame?.contentWindow?.postMessage({ type: 'prefs', prefs: this.opts.prefs }, '*');
	}

	dispose() {
		window.removeEventListener('message', this.listener);
		this.frame?.remove();
		this.frame = null;
		this.ready = null;
		for (const w of this.waiting.values()) {
			clearTimeout(w.timer);
			w.reject(new ExtensionError('Extension stopped'));
		}
		this.waiting.clear();
	}
}

export { toBase64 };
