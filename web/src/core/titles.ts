import { getExtension } from './extensions';
import { absoluteUrl, getFavourite, getHistoryFor, getInstalledSource, jsUid } from './store';
import type { InstalledSource, ItemType, JsChapter, JsManga } from './types';

export interface Chapter extends JsChapter {
	index: number; // reading order, oldest first
	uid: string;
}

export interface Details {
	sourceId: number;
	itemType: ItemType;
	url: string;
	publicUrl: string;
	title: string;
	cover?: string;
	description?: string;
	authors: string[];
	genres: string[];
	status: string;
	chapters: Chapter[];
}

export const STATUS = ['Ongoing', 'Completed', 'On hiatus', 'Canceled', 'Publishing finished'];

/**
 * Mangayomi lists chapters newest-first by convention; reading order is oldest-first. A source that already returns
 * them oldest-first is detected from the upload dates (same rule as the Android app).
 */
export function orderChapters(list: JsChapter[], sourceId: number): Chapter[] {
	const first = list[0]?.dateUpload ?? 0;
	const last = list[list.length - 1]?.dateUpload ?? 0;
	const ordered = first && last && first < last ? list : [...list].reverse();
	return ordered.map((c, index) => ({ ...c, index, uid: jsUid(`js:${sourceId}`, c.url) }));
}

const cache = new Map<string, { at: number; details: Details }>();

/**
 * Mangayomi's getDetail() often leaves out the name and cover (they come from the list entry), so list cards
 * remember them and details fall back to them, like the Android app does with the Manga it was opened from.
 */
const known = new Map<string, { title?: string; cover?: string }>();
export function rememberTitle(sourceId: number, url: string, title?: string, cover?: string) {
	known.set(`${sourceId}:${url}`, { title, cover });
	if (known.size > 2000) known.delete(known.keys().next().value as string);
}

export function toDetails(src: InstalledSource, url: string, m: JsManga, fallback?: { title?: string; cover?: string }): Details {
	return {
		sourceId: src.id,
		itemType: src.entry.itemType,
		url,
		publicUrl: absoluteUrl(url, src.entry.baseUrl),
		title: m.name?.trim() || fallback?.title || url,
		cover: m.imageUrl || fallback?.cover,
		description: m.description?.trim() || undefined,
		authors: [m.author, m.artist].filter((x): x is string => !!x && !!x.trim()).filter((x, i, a) => a.indexOf(x) === i),
		genres: m.genre.map((g) => g.trim()).filter(Boolean),
		status: STATUS[m.status] ?? '',
		chapters: orderChapters(m.chapters, src.id),
	};
}

export async function loadDetails(sourceId: number, url: string, opts: { force?: boolean; fallback?: { title?: string; cover?: string } } = {}): Promise<Details> {
	const key = `${sourceId}:${url}`;
	const hit = cache.get(key);
	if (hit && !opts.force && Date.now() - hit.at < 10 * 60_000) return hit.details;
	const src = await getInstalledSource(sourceId);
	if (!src) throw new Error('This source is not installed. Install it from Explore → Source repositories.');
	if (!opts.fallback) {
		const fav = await getFavourite(key);
		const hist = fav ? undefined : await getHistoryFor(key);
		opts = { ...opts, fallback: known.get(key) ?? fav ?? hist };
	}
	const ext = await getExtension(sourceId);
	const m = await ext.getDetail(url);
	const details = toDetails(src, url, m, opts.fallback);
	cache.set(key, { at: Date.now(), details });
	return details;
}

export const cachedDetails = (sourceId: number, url: string) => cache.get(`${sourceId}:${url}`)?.details;
