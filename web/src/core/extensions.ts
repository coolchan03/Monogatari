import { db, subscribe } from './db';
import { Sandbox } from './sandbox-host';
import type { InstalledSource, JsChapter, JsManga, JsPage, JsPages, JsTrack, JsVideo } from './types';

const str = (v: unknown): string | null => (typeof v === 'string' ? v : typeof v === 'number' ? String(v) : null);

function flatStrings(v: unknown): string[] {
	if (!Array.isArray(v)) return [];
	return v.flatMap((e) => (Array.isArray(e) ? flatStrings(e) : typeof e === 'string' ? [e] : []));
}

function toChapter(o: any): JsChapter {
	const d = o?.dateUpload;
	return {
		name: str(o?.name) ?? '',
		url: str(o?.url) ?? '',
		dateUpload: d == null || d === '' ? null : Number(d) || null,
		scanlator: str(o?.scanlator),
	};
}

function toManga(o: any): JsManga {
	const ch = Array.isArray(o?.chapters) ? o.chapters : Array.isArray(o?.episodes) ? o.episodes : [];
	return {
		name: str(o?.name),
		link: str(o?.link),
		imageUrl: str(o?.imageUrl),
		description: str(o?.description),
		author: str(o?.author),
		artist: str(o?.artist),
		status: o?.status == null ? 5 : Number(o.status),
		genre: flatStrings(o?.genre),
		chapters: ch.filter((c: any) => c && typeof c === 'object').map(toChapter),
	};
}

function toPages(o: any): JsPages {
	if (!o || typeof o !== 'object') throw new Error('The source did not return a list');
	return { list: (Array.isArray(o.list) ? o.list : []).filter((x: any) => x && typeof x === 'object').map(toManga), hasNextPage: o.hasNextPage === true };
}

function toTracks(v: unknown): JsTrack[] {
	return (Array.isArray(v) ? v : []).filter((e) => e && typeof e === 'object').map((e: any) => ({ file: str(e.file)?.trim(), label: str(e.label)?.trim() }));
}

function toVideo(o: any): JsVideo | null {
	const url = str(o?.url)?.trim();
	if (!url) return null;
	return {
		url,
		quality: str(o.quality)?.trim() ?? '',
		originalUrl: str(o.originalUrl)?.trim() || url,
		headers: o.headers && typeof o.headers === 'object' ? o.headers : null,
		subtitles: toTracks(o.subtitles),
		audios: toTracks(o.audios),
	};
}

/** Typed facade over one running extension (the Mangayomi extension contract). */
export class Extension {
	constructor(
		public readonly source: InstalledSource,
		private sandbox: Sandbox,
	) {}

	get lastNetworkProblem() {
		return this.sandbox.lastProblem;
	}

	get declaredPreferences() {
		return this.sandbox.declaredPreferences;
	}

	async getPopular(page: number) {
		return toPages(await this.sandbox.call('getPopular', [page]));
	}

	async getLatestUpdates(page: number) {
		return toPages(await this.sandbox.call('getLatestUpdates', [page]));
	}

	async search(query: string, page: number, filters: unknown[] = []) {
		return toPages(await this.sandbox.call('search', [query, page, filters]));
	}

	async getDetail(url: string) {
		const o = await this.sandbox.call('getDetail', [url]);
		if (!o || typeof o !== 'object') throw new Error('The source did not return details');
		return toManga(o);
	}

	async getPageList(url: string): Promise<JsPage[]> {
		const arr = await this.sandbox.call('getPageList', [url]);
		const seen = new Set<string>();
		const out: JsPage[] = [];
		for (const e of Array.isArray(arr) ? arr : []) {
			const u = (typeof e === 'string' ? e : str(e?.url))?.trim();
			if (!u || seen.has(u)) continue;
			seen.add(u);
			out.push({ url: u, headers: e && typeof e === 'object' && e.headers && typeof e.headers === 'object' ? e.headers : null });
		}
		return out;
	}

	async getVideoList(url: string): Promise<JsVideo[]> {
		const arr = await this.sandbox.call('getVideoList', [url]);
		const seen = new Set<string>();
		return (Array.isArray(arr) ? arr : [])
			.map(toVideo)
			.filter((v): v is JsVideo => !!v)
			.filter((v) => {
				const k = v.url + '|' + v.originalUrl;
				if (seen.has(k)) return false;
				seen.add(k);
				return true;
			});
	}

	async getHtmlContent(name: string, url: string): Promise<string> {
		return str(await this.sandbox.call('getHtmlContent', [name, url])) ?? '';
	}

	async cleanHtmlContent(html: string): Promise<string> {
		return str(await this.sandbox.call('cleanHtmlContent', [html])) ?? '';
	}

	async getFilterList(): Promise<any[]> {
		const r = await this.sandbox.call('getFilterList', []);
		return Array.isArray(r) ? r : [];
	}

	async supportsLatest(): Promise<boolean> {
		try {
			return (await this.sandbox.call('supportsLatest', null)) !== false;
		} catch {
			return true;
		}
	}

	async getHeaders(): Promise<Record<string, string>> {
		try {
			const r = await this.sandbox.call('getHeaders', [this.source.entry.baseUrl]);
			return r && typeof r === 'object' ? r : {};
		} catch {
			return {};
		}
	}

	async getSourcePreferences(): Promise<any[]> {
		try {
			const r = await this.sandbox.call('getSourcePreferences', []);
			return Array.isArray(r) ? r : [];
		} catch {
			return [];
		}
	}

	/** Persists a source preference and pushes it to the running extension. */
	setPreference(key: string, value: string) {
		void db.put('prefs', value, `${this.source.id}:${key}`);
		this.sandbox.setPref(key, value);
	}
}

const MAX_LIVE = 4;
const live = new Map<number, { ext: Extension; sandbox: Sandbox }>();

async function prefsFor(id: number): Promise<Record<string, string>> {
	const prefix = `${id}:`;
	const entries = await db.entriesWithPrefix<string>('prefs', prefix);
	return Object.fromEntries(entries.map(([k, v]) => [k.slice(prefix.length), String(v)]));
}

export async function getExtension(sourceId: number): Promise<Extension> {
	const existing = live.get(sourceId);
	if (existing) return existing.ext;
	const src = await db.get<InstalledSource>('sources', sourceId);
	if (!src) throw new Error('This source is not installed');
	const prefs = await prefsFor(sourceId);
	const sandbox = new Sandbox({
		entry: src.entry,
		code: src.code,
		prefs,
		onPref: (key, value) => void db.put('prefs', value, `${sourceId}:${key}`),
		onLog: (m) => console.debug(`[${src.entry.name}]`, m),
	});
	const ext = new Extension(src, sandbox);
	live.set(sourceId, { ext, sandbox });
	// Keep a handful of sandboxes alive; stop the least recently used ones.
	if (live.size > MAX_LIVE) {
		const oldest = [...live.entries()].sort((a, b) => a[1].sandbox.lastUsed - b[1].sandbox.lastUsed)[0];
		if (oldest && oldest[0] !== sourceId) {
			oldest[1].sandbox.dispose();
			live.delete(oldest[0]);
		}
	}
	return ext;
}

export function disposeExtension(sourceId: number) {
	live.get(sourceId)?.sandbox.dispose();
	live.delete(sourceId);
}

subscribe(['sources'], () => {
	// An updated or removed source must not keep running stale code.
	void db.getAll<InstalledSource>('sources').then((all) => {
		const ids = new Map(all.map((s) => [s.id, s.installedAt]));
		for (const [id, { ext }] of live) {
			if (ids.get(id) !== ext.source.installedAt) disposeExtension(id);
		}
	});
});
