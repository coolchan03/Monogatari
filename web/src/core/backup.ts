import { unzipSync, strFromU8, zipSync, strToU8 } from 'fflate';
import { db, STORES } from './db';
import { DEFAULT_SETTINGS, getSettings, replaceSettings, type Settings } from './settings';
import { refreshRepo, type RepoRecord } from './repos';
import type { Bookmark, Category, Favourite, HistoryEntry, InstalledSource, ReadChapter } from './types';
import { titleKey } from './types';

export const BACKUP_FORMAT = 'monogatari-web-backup';

export interface ImportReport {
	kind: 'web' | 'android';
	categories: number;
	favourites: number;
	history: number;
	bookmarks: number;
	skipped: number;
	missingSources: { id: number; repoUrl: string; version: string }[];
	note?: string;
}

/** Everything except downloaded pages (those are large; they are device-local). */
export async function exportBackup(includeSourceCode = true): Promise<Blob> {
	const [favourites, categories, history, read, bookmarks, repos, sources, prefs] = await Promise.all([
		db.getAll<Favourite>('favourites'),
		db.getAll<Category>('categories'),
		db.getAll<HistoryEntry>('history'),
		db.getAll<ReadChapter>('read'),
		db.getAll<Bookmark>('bookmarks'),
		db.getAll<RepoRecord>('repos'),
		db.getAll<InstalledSource>('sources'),
		db.entriesWithPrefix<string>('prefs', ''),
	]);
	const data = {
		format: BACKUP_FORMAT,
		version: 1,
		exportedAt: new Date().toISOString(),
		settings: getSettings(),
		favourites,
		categories,
		history,
		read,
		bookmarks,
		repos: repos.map((r) => ({ url: r.url, addedAt: r.addedAt })),
		sources: sources.map((s) => ({ ...s, code: includeSourceCode ? s.code : '' })),
		prefs,
	};
	return new Blob([JSON.stringify(data)], { type: 'application/json' });
}

async function restoreWeb(data: any): Promise<ImportReport> {
	const rep: ImportReport = { kind: 'web', categories: 0, favourites: 0, history: 0, bookmarks: 0, skipped: 0, missingSources: [] };
	if (data.settings) replaceSettings({ ...DEFAULT_SETTINGS, ...(data.settings as Partial<Settings>) });
	const put = async (store: (typeof STORES)[number], list: unknown, counter?: keyof ImportReport) => {
		if (!Array.isArray(list)) return;
		await db.putMany(store, list);
		if (counter) (rep[counter] as number) = list.length;
	};
	await put('categories', data.categories, 'categories');
	await put('favourites', data.favourites, 'favourites');
	await put('history', data.history, 'history');
	await put('read', data.read);
	await put('bookmarks', data.bookmarks, 'bookmarks');
	if (Array.isArray(data.repos)) await db.putMany('repos', data.repos);
	if (Array.isArray(data.sources)) {
		const withCode = data.sources.filter((s: InstalledSource) => s.code);
		await db.putMany('sources', withCode);
		for (const s of data.sources as InstalledSource[]) if (!s.code) rep.missingSources.push({ id: s.id, repoUrl: s.entry.repoUrl, version: s.entry.version });
	}
	if (Array.isArray(data.prefs)) for (const [k, v] of data.prefs as [string, string][]) await db.put('prefs', v, k);
	return rep;
}

const isZip = (b: Uint8Array) => b.length > 4 && b[0] === 0x50 && b[1] === 0x4b;

/** Imports a Monogatari/Kotatsu Android backup (zip of JSON sections). Only Mangayomi JS sources ("js:<id>") carry over. */
async function restoreAndroid(bytes: Uint8Array): Promise<ImportReport> {
	const files = unzipSync(bytes);
	const section = (name: string): any => {
		const f = files[name];
		if (!f) return null;
		try {
			return JSON.parse(strFromU8(f));
		} catch {
			return null;
		}
	};
	const rep: ImportReport = { kind: 'android', categories: 0, favourites: 0, history: 0, bookmarks: 0, skipped: 0, missingSources: [] };
	const jsId = (source: string): number | null => {
		const m = /^js:(\d+)$/.exec(source);
		return m ? Number(m[1]) : null;
	};
	const catList = section('categories');
	if (Array.isArray(catList)) {
		const cats: Category[] = catList
			.filter((c: any) => !c.deleted_at)
			.map((c: any, i: number) => ({ id: Number(c.category_id), name: String(c.title), order: Number(c.sort_key ?? i) }));
		await db.putMany('categories', cats);
		rep.categories = cats.length;
	}
	const favList = section('favourites');
	if (Array.isArray(favList)) {
		const byKey = new Map<string, Favourite>();
		for (const f of favList) {
			if (f.deleted_at) continue;
			const sid = jsId(String(f.manga?.source ?? ''));
			if (sid == null) {
				rep.skipped++;
				continue;
			}
			const key = titleKey(sid, f.manga.url);
			const ex = byKey.get(key);
			if (ex) ex.categoryIds.push(Number(f.category_id));
			else
				byKey.set(key, {
					key,
					sourceId: sid,
					url: f.manga.url,
					title: f.manga.title,
					cover: f.manga.cover_url || undefined,
					itemType: 0,
					categoryIds: [Number(f.category_id)],
					addedAt: Number(f.created_at ?? Date.now()),
				});
		}
		await db.putMany('favourites', [...byKey.values()]);
		rep.favourites = byKey.size;
	}
	const histList = section('history');
	if (Array.isArray(histList)) {
		const out: HistoryEntry[] = [];
		for (const h of histList) {
			if (h.deleted_at) continue;
			const sid = jsId(String(h.manga?.source ?? ''));
			if (sid == null) {
				rep.skipped++;
				continue;
			}
			out.push({
				key: titleKey(sid, h.manga.url),
				sourceId: sid,
				url: h.manga.url,
				title: h.manga.title,
				cover: h.manga.cover_url || undefined,
				itemType: 0,
				chapterUrl: '',
				chapterName: '',
				chapterIndex: -1,
				chapterUid: String(h.chapter_id),
				page: Number(h.page ?? 0),
				pageCount: 0,
				updatedAt: Number(h.updated_at ?? Date.now()),
			});
		}
		await db.putMany('history', out);
		rep.history = out.length;
	}
	const bmList = section('bookmarks');
	if (Array.isArray(bmList)) {
		const out: Bookmark[] = [];
		for (const entry of bmList) {
			const sid = jsId(String(entry.manga?.source ?? ''));
			if (sid == null) {
				rep.skipped += entry.bookmarks?.length ?? 1;
				continue;
			}
			for (const b of entry.bookmarks ?? []) {
				out.push({
					key: `${titleKey(sid, entry.manga.url)}|chapter:${b.chapter_id}|${b.page}`,
					titleKey: titleKey(sid, entry.manga.url),
					title: entry.manga.title,
					cover: entry.manga.cover_url || undefined,
					sourceId: sid,
					titleUrl: entry.manga.url,
					chapterUrl: '',
					chapterName: `chapter ${b.chapter_id}`,
					page: Number(b.page ?? 0),
					createdAt: Number(b.created_at ?? Date.now()),
				});
			}
		}
		await db.putMany('bookmarks', out);
		rep.bookmarks = out.length;
	}
	const js = section('js_sources');
	if (Array.isArray(js)) {
		const installed = new Set((await db.getAll<InstalledSource>('sources')).map((s) => s.id));
		const repoUrls = new Set<string>();
		for (const s of js) {
			if (!installed.has(Number(s.id))) rep.missingSources.push({ id: Number(s.id), repoUrl: String(s.repo_url), version: String(s.version) });
			if (typeof s.repo_url === 'string' && /^https:\/\//.test(s.repo_url)) repoUrls.add(s.repo_url);
		}
		for (const url of repoUrls) await refreshRepo(url);
	}
	rep.note = 'Sources that use built-in Kotlin parsers (not Mangayomi JS) cannot run in the web/Windows app and were skipped.';
	return rep;
}

export async function importBackup(file: File): Promise<ImportReport> {
	const bytes = new Uint8Array(await file.arrayBuffer());
	if (isZip(bytes)) return restoreAndroid(bytes);
	let data: any;
	try {
		data = JSON.parse(strFromU8(bytes));
	} catch {
		throw new Error('Not a Monogatari backup');
	}
	if (data?.format !== BACKUP_FORMAT) throw new Error('Not a Monogatari backup');
	return restoreWeb(data);
}

export async function wipeAll() {
	for (const s of STORES) if (s !== 'kv') await db.clear(s);
	localStorage.clear();
}

/** Zip of one chapter's page images, for "Save to disk". */
export async function zipPages(pages: Blob[]): Promise<Blob> {
	const files: Record<string, Uint8Array> = {};
	for (let i = 0; i < pages.length; i++) {
		const ext = /png/.test(pages[i].type) ? 'png' : /webp/.test(pages[i].type) ? 'webp' : /gif/.test(pages[i].type) ? 'gif' : 'jpg';
		files[String(i + 1).padStart(4, '0') + '.' + ext] = new Uint8Array(await pages[i].arrayBuffer());
	}
	void strToU8;
	return new Blob([zipSync(files, { level: 0 }) as BlobPart], { type: 'application/vnd.comicbook+zip' });
}
