import { useEffect, useState } from 'preact/hooks';
import { getExtension } from './extensions';
import { fetchBytes } from './http';
import { getDownload, putDownload } from './store';
import type { Title } from './types';
import type { Chapter } from './titles';

const progress = new Map<string, number>();
const subs = new Set<() => void>();
const emit = () => subs.forEach((f) => f());

export function useDownloadProgress(): Map<string, number> {
	const [, tick] = useState(0);
	useEffect(() => {
		const f = () => tick((n) => n + 1);
		subs.add(f);
		return () => void subs.delete(f);
	}, []);
	return progress;
}

export const isDownloaded = async (tKey: string, chapterUrl: string) => !!(await getDownload(tKey, chapterUrl));

/** Saves a chapter for offline reading: image pages as blobs for manga, sanitised HTML for novels. */
export async function downloadChapter(t: Title, ch: Chapter): Promise<void> {
	const key = `${t.key}|${ch.url}`;
	if (progress.has(key) || (await getDownload(t.key, ch.url))) return;
	progress.set(key, 0);
	emit();
	try {
		const ext = await getExtension(t.sourceId);
		if (t.itemType === 2) {
			const html = await ext.getHtmlContent(ch.name, ch.url);
			await putDownload({ key, titleKey: t.key, chapterUrl: ch.url, chapterName: ch.name, itemType: 2, html, savedAt: Date.now() });
		} else if (t.itemType === 0) {
			const pages = await ext.getPageList(ch.url);
			const blobs: Blob[] = [];
			for (let i = 0; i < pages.length; i++) {
				blobs.push(await fetchBytes(pages[i].url, pages[i].headers));
				progress.set(key, (i + 1) / pages.length);
				emit();
			}
			await putDownload({ key, titleKey: t.key, chapterUrl: ch.url, chapterName: ch.name, itemType: 0, pages: blobs, savedAt: Date.now() });
		} else throw new Error('Video download is not supported');
	} finally {
		progress.delete(key);
		emit();
	}
}
