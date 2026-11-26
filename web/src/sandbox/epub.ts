import { unzipSync, strFromU8 } from 'fflate';

export interface EpubChapter {
	title: string;
	content: string;
}
export interface EpubBook {
	title: string;
	author?: string;
	chapters: EpubChapter[];
}

function dirname(p: string) {
	const i = p.lastIndexOf('/');
	return i < 0 ? '' : p.slice(0, i + 1);
}

function resolvePath(base: string, href: string): string {
	const parts = (base + decodeURIComponent(href.split('#')[0])).split('/');
	const out: string[] = [];
	for (const part of parts) {
		if (part === '..') out.pop();
		else if (part && part !== '.') out.push(part);
	}
	return out.join('/');
}

/** Minimal EPUB 2/3 reader: spine order, titles from the nav document / NCX, XHTML body per chapter. */
export function parseEpub(bytes: Uint8Array): EpubBook {
	const files = unzipSync(bytes);
	const text = (path: string) => {
		const f = files[path];
		if (!f) throw new Error('Invalid EPUB: missing ' + path);
		return strFromU8(f);
	};
	const xml = (s: string) => new DOMParser().parseFromString(s, 'application/xml');
	const container = xml(text('META-INF/container.xml'));
	const opfPath = container.querySelector('rootfile')?.getAttribute('full-path');
	if (!opfPath) throw new Error('Invalid EPUB: no package document');
	const opf = xml(text(opfPath));
	const base = dirname(opfPath);
	const manifest = new Map<string, { href: string; props: string; type: string }>();
	opf.querySelectorAll('manifest > item').forEach((i) =>
		manifest.set(i.getAttribute('id') ?? '', {
			href: i.getAttribute('href') ?? '',
			props: i.getAttribute('properties') ?? '',
			type: i.getAttribute('media-type') ?? '',
		}),
	);
	const titles = new Map<string, string>();
	const nav = [...manifest.values()].find((m) => m.props.split(' ').includes('nav'));
	if (nav) {
		const d = new DOMParser().parseFromString(text(resolvePath(base, nav.href)), 'application/xhtml+xml');
		d.querySelectorAll('nav a[href]').forEach((a) => {
			const key = resolvePath(dirname(resolvePath(base, nav.href)), a.getAttribute('href') ?? '');
			if (!titles.has(key)) titles.set(key, (a.textContent ?? '').trim());
		});
	}
	const ncxItem = [...manifest.values()].find((m) => m.type === 'application/x-dtbncx+xml');
	if (ncxItem) {
		const ncxPath = resolvePath(base, ncxItem.href);
		const d = xml(text(ncxPath));
		d.querySelectorAll('navPoint').forEach((np) => {
			const src = np.querySelector('content')?.getAttribute('src') ?? '';
			const label = np.querySelector('navLabel > text')?.textContent?.trim() ?? '';
			const key = resolvePath(dirname(ncxPath), src);
			if (label && !titles.has(key)) titles.set(key, label);
		});
	}
	const chapters: EpubChapter[] = [];
	opf.querySelectorAll('spine > itemref').forEach((ref, index) => {
		const item = manifest.get(ref.getAttribute('idref') ?? '');
		if (!item || !/x?html/.test(item.type)) return;
		const path = resolvePath(base, item.href);
		if (!files[path]) return;
		const raw = strFromU8(files[path]);
		const doc = new DOMParser().parseFromString(raw, 'text/html');
		chapters.push({ title: titles.get(path) || `Chapter ${index + 1}`, content: doc.body?.innerHTML ?? raw });
	});
	const dc = (name: string) => opf.getElementsByTagNameNS('*', name)[0]?.textContent?.trim();
	return { title: dc('title') ?? 'Untitled', author: dc('creator'), chapters };
}
