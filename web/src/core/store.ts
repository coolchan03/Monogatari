import { db, subscribe, type StoreName } from './db';
import { useEffect, useState } from 'preact/hooks';
import type { Bookmark, Category, DownloadedChapter, Favourite, HistoryEntry, InstalledSource, ReadChapter, Title } from './types';
import { titleKey } from './types';

/** Re-renders a component with fresh data whenever one of the given stores changes. */
export function useStore<T>(stores: StoreName[], load: () => Promise<T>, initial: T): [T, boolean] {
	const [value, setValue] = useState<T>(initial);
	const [loading, setLoading] = useState(true);
	useEffect(() => {
		let alive = true;
		const run = () =>
			load()
				.then((v) => alive && (setValue(v), setLoading(false)))
				.catch(() => alive && setLoading(false));
		run();
		const off = subscribe(stores, run);
		return () => {
			alive = false;
			off();
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);
	return [value, loading];
}

// ---- favourites & categories ----

export const getFavourites = () => db.getAll<Favourite>('favourites');
export const getFavourite = (key: string) => db.get<Favourite>('favourites', key);
export const getCategories = async () => (await db.getAll<Category>('categories')).sort((a, b) => a.order - b.order);

export async function setFavourite(t: Title, categoryIds: number[]) {
	const existing = await getFavourite(t.key);
	const fav: Favourite = { ...existing, ...t, categoryIds, addedAt: existing?.addedAt ?? Date.now() };
	await db.put('favourites', fav);
}

export const removeFavourite = (key: string) => db.delete('favourites', key);

export async function createCategory(name: string): Promise<Category> {
	const all = await getCategories();
	const id = (all.reduce((m, c) => Math.max(m, c.id), 0) || 0) + 1;
	const cat = { id, name: name.trim() || 'Category', order: all.length };
	await db.put('categories', cat);
	return cat;
}

export async function renameCategory(id: number, name: string) {
	const c = await db.get<Category>('categories', id);
	if (c) await db.put('categories', { ...c, name });
}

export async function deleteCategory(id: number) {
	await db.delete('categories', id);
	const favs = (await getFavourites()).filter((f) => f.categoryIds.includes(id));
	await db.putMany(
		'favourites',
		favs.map((f) => ({ ...f, categoryIds: f.categoryIds.filter((c) => c !== id) })),
	);
}

// ---- history & read markers ----

export const getHistory = async () => (await db.getAll<HistoryEntry>('history')).sort((a, b) => b.updatedAt - a.updatedAt);
export const getHistoryFor = (key: string) => db.get<HistoryEntry>('history', key);
export const removeHistory = (key: string) => db.delete('history', key);
export const clearHistory = () => db.clear('history');

export async function saveProgress(t: Title, e: Pick<HistoryEntry, 'chapterUrl' | 'chapterName' | 'chapterIndex' | 'page' | 'pageCount'>) {
	await db.put<HistoryEntry>('history', { ...t, ...e, updatedAt: Date.now() });
}

export async function markRead(tKey: string, chapterUrl: string, read = true) {
	const key = `${tKey}|${chapterUrl}`;
	if (read) await db.put<ReadChapter>('read', { key, titleKey: tKey, chapterUrl });
	else await db.delete('read', key);
}

export async function getReadSet(tKey: string): Promise<Set<string>> {
	const all = await db.getAll<ReadChapter>('read');
	return new Set(all.filter((r) => r.titleKey === tKey).map((r) => r.chapterUrl));
}

// ---- bookmarks ----

export const getBookmarks = async () => (await db.getAll<Bookmark>('bookmarks')).sort((a, b) => b.createdAt - a.createdAt);

export const addBookmark = (b: Omit<Bookmark, 'key' | 'createdAt'>) =>
	db.put<Bookmark>('bookmarks', { ...b, key: `${b.titleKey}|${b.chapterUrl}|${b.page}`, createdAt: Date.now() });

export const removeBookmark = (key: string) => db.delete('bookmarks', key);

// ---- downloads ----

export const getDownloads = async () => (await db.getAll<DownloadedChapter>('downloads')).sort((a, b) => b.savedAt - a.savedAt);
export const getDownload = (tKey: string, chapterUrl: string) => db.get<DownloadedChapter>('downloads', `${tKey}|${chapterUrl}`);
export const putDownload = (d: DownloadedChapter) => db.put('downloads', d);
export const removeDownload = (key: string) => db.delete('downloads', key);

// ---- sources ----

export const getInstalled = async () => (await db.getAll<InstalledSource>('sources')).sort((a, b) => a.entry.name.localeCompare(b.entry.name));
export const getInstalledSource = (id: number) => db.get<InstalledSource>('sources', id);

export async function setSourceFlags(id: number, patch: Partial<Pick<InstalledSource, 'pinned' | 'enabled'>>) {
	const s = await db.get<InstalledSource>('sources', id);
	if (s) await db.put('sources', { ...s, ...patch });
}

export const makeTitle = (sourceId: number, url: string, title: string, cover: string | undefined, itemType: Title['itemType']): Title => ({
	key: titleKey(sourceId, url),
	sourceId,
	url,
	title,
	cover,
	itemType,
});

/** Resolve a possibly relative URL against the source's base URL, like the Android app's `absolute()`. */
export function absoluteUrl(link: string, baseUrl: string): string {
	if (/^https?:\/\//i.test(link)) return link;
	if (!baseUrl) return link;
	return baseUrl.replace(/\/+$/, '') + '/' + link.replace(/^\/+/, '');
}

/** Same 64-bit string hash the Android app uses for manga/chapter ids (jsUid), so backups line up. */
export function jsUid(sourceName: string, url: string): string {
	let h = 1125899906842597n;
	const text = sourceName + url;
	for (let i = 0; i < text.length; i++) h = BigInt.asIntN(64, 31n * h + BigInt(text.charCodeAt(i)));
	return h.toString();
}
