import { db } from './db';
import { execute } from './http';
import type { InstalledSource, ItemType, SourceEntry } from './types';

export interface RepoRecord {
	url: string;
	addedAt: number;
	fetchedAt?: number;
	count?: number;
	runnable?: number;
	entries?: SourceEntry[];
	error?: string;
}

/** Same recommended repositories as the Android app (sourcerepo/data/RecommendedRepos.kt). */
export const RECOMMENDED_REPOS: { name: string; url: string; type: 'manga' | 'anime' | 'novel' }[] = [
	{ name: 'Mangayomi (official)', type: 'manga', url: 'https://raw.githubusercontent.com/kodjodevf/mangayomi-extensions/refs/heads/main/index.json' },
	{ name: 'm2k3a', type: 'manga', url: 'https://m2k3a.github.io/mangayomi-extensions/index.json' },
	{ name: 'Swakshan', type: 'manga', url: 'https://raw.githubusercontent.com/Swakshan/mangayomi-swak-extensions/refs/heads/main/index.json' },
	{ name: 'Sugoi modules', type: 'manga', url: 'https://raw.githubusercontent.com/Schnitzel5/sugoi-modules/refs/heads/main/index.json' },
	{ name: 'Monogatari replacements', type: 'manga', url: 'https://raw.githubusercontent.com/coolchan03/Monogatari/devel/extensions/index.json' },
	{ name: 'm2k3a — anime', type: 'anime', url: 'https://raw.githubusercontent.com/m2k3a/mangayomi-extensions/refs/heads/main/anime_index.json' },
	{ name: 'Swakshan — anime', type: 'anime', url: 'https://raw.githubusercontent.com/Swakshan/mangayomi-swak-extensions/refs/heads/main/anime_index.json' },
	{ name: 'Sugoi modules — anime', type: 'anime', url: 'https://raw.githubusercontent.com/Schnitzel5/sugoi-modules/refs/heads/main/anime_index.json' },
	{ name: 'Kegareta sauces — anime', type: 'anime', url: 'https://raw.githubusercontent.com/gato404/kegareta-sauces/refs/heads/main/anime_index.json' },
	{ name: 'Sugoi modules — novels', type: 'novel', url: 'https://raw.githubusercontent.com/Schnitzel5/sugoi-modules/refs/heads/main/novel_index.json' },
	{ name: 'm2k3a — novels', type: 'novel', url: 'https://raw.githubusercontent.com/m2k3a/mangayomi-extensions/refs/heads/main/novel_index.json' },
	{ name: 'Mangayomi — novels', type: 'novel', url: 'https://raw.githubusercontent.com/kodjodevf/mangayomi-extensions/refs/heads/main/novel_index.json' },
	{ name: 'Swakshan — novels', type: 'novel', url: 'https://raw.githubusercontent.com/Swakshan/mangayomi-swak-extensions/refs/heads/main/novel_index.json' },
];

/** Dotted-number comparison: "0.1.10" > "0.1.9". */
export function compareVersions(a: string, b: string): number {
	const pa = a.trim().split(/[.-]/).map((x) => parseInt(x, 10) || 0);
	const pb = b.trim().split(/[.-]/).map((x) => parseInt(x, 10) || 0);
	for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
		const c = (pa[i] ?? 0) - (pb[i] ?? 0);
		if (c) return c;
	}
	return 0;
}

const s = (v: unknown): string => (typeof v === 'string' ? v : v == null ? '' : String(v));

function shouldHide(status: string, checkedAt: string): boolean {
	if (status.toLowerCase() !== 'broken' || !checkedAt) return false;
	const t = Date.parse(checkedAt);
	if (Number.isNaN(t)) return false;
	return t >= Date.now() - 7 * 86_400_000 && t <= Date.now() + 5 * 60_000;
}

/** Parses Mangayomi repo indexes. Entries without a numeric id and an https sourceCodeUrl are Mihon-style APK entries and are skipped. */
export function parseIndex(text: string, repoUrl: string): SourceEntry[] {
	let root: unknown;
	try {
		root = JSON.parse(text);
	} catch {
		throw new Error('Repo index is not valid JSON');
	}
	if (!Array.isArray(root)) throw new Error('Mangayomi index must be a JSON array');
	const out: SourceEntry[] = [];
	for (const o of root as any[]) {
		if (!o || typeof o !== 'object') continue;
		if (shouldHide(s(o.status), s(o.checkedAt))) continue;
		const id = Number(o.id);
		const codeUrl = s(o.sourceCodeUrl);
		if (!Number.isFinite(id) || !/^https:\/\//i.test(codeUrl) || !s(o.name).trim()) continue;
		const lang = Number(o.sourceCodeLanguage);
		const isJs = lang === 1 || (Number.isNaN(lang) && /\.js$/i.test(codeUrl)) || (lang !== 0 && /\.js$/i.test(codeUrl));
		const itemType = ([0, 1, 2].includes(Number(o.itemType)) ? Number(o.itemType) : 0) as ItemType;
		out.push({
			id,
			name: s(o.name),
			baseUrl: s(o.baseUrl),
			lang: s(o.lang),
			version: s(o.version),
			itemType,
			isJs,
			sourceCodeUrl: codeUrl,
			iconUrl: s(o.iconUrl),
			apiUrl: s(o.apiUrl),
			dateFormat: s(o.dateFormat),
			dateFormatLocale: s(o.dateFormatLocale),
			isNsfw: o.isNsfw === true || o.isNsfw === 'true',
			hasCloudflare: o.hasCloudflare === true || o.hasCloudflare === 'true',
			additionalParams: s(o.additionalParams),
			notes: s(o.notes),
			repoUrl,
			sha256: s(o.sha256) || undefined,
			author: s(o.author || o.developer) || undefined,
		});
	}
	return out;
}

export async function listRepos(): Promise<RepoRecord[]> {
	return (await db.getAll<RepoRecord>('repos')).sort((a, b) => a.addedAt - b.addedAt);
}

export async function refreshRepo(url: string): Promise<RepoRecord> {
	const prev = await db.get<RepoRecord>('repos', url);
	const rec: RepoRecord = { ...(prev ?? { url, addedAt: Date.now() }) };
	try {
		const res = await execute({ method: 'GET', url, headers: { accept: 'application/json' } });
		if (res.statusCode >= 400) throw new Error(`HTTP ${res.statusCode}`);
		const entries = parseIndex(res.body, url);
		rec.entries = entries;
		rec.count = entries.length;
		rec.runnable = entries.filter((e) => e.isJs).length;
		rec.fetchedAt = Date.now();
		rec.error = undefined;
	} catch (e: any) {
		rec.error = e?.message ?? String(e);
	}
	await db.put('repos', rec);
	return rec;
}

export async function addRepo(url: string): Promise<RepoRecord> {
	url = url.trim();
	if (!/^https:\/\//i.test(url)) throw new Error('Repository URLs must start with https://');
	const rec = await refreshRepo(url);
	if (rec.error) {
		await db.delete('repos', url);
		throw new Error(rec.error);
	}
	return rec;
}

export const removeRepo = (url: string) => db.delete('repos', url);

async function sha256Hex(text: string): Promise<string> {
	const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
	return Array.from(new Uint8Array(buf), (b) => b.toString(16).padStart(2, '0')).join('');
}

const GITHUB_HOSTS = new Set(['raw.githubusercontent.com', 'github.com', 'gist.githubusercontent.com']);

/**
 * Stable identity of a repository index URL (same rules as the Android JsPublisherPolicy): lowercase host plus the repository
 * path. For GitHub hosts only owner/repo count, so a branch or index file rename is not a new publisher.
 */
export function publisherKey(repoUrl: string): string {
	let u: URL;
	try {
		u = new URL(repoUrl.trim());
	} catch {
		return repoUrl.trim().toLowerCase();
	}
	const host = u.hostname.toLowerCase().replace(/\.$/, '');
	let segs = u.pathname.split('/').filter(Boolean);
	if (GITHUB_HOSTS.has(host)) segs = segs.slice(0, 2).map((x) => x.toLowerCase());
	else while (segs.length && segs[segs.length - 1].includes('.')) segs.pop();
	return host + segs.map((x) => '/' + x).join('');
}

/** A blank installed repo (legacy or local-file install) has no known publisher and adopts the next one. */
export function isSamePublisher(installedRepoUrl: string, candidateRepoUrl: string): boolean {
	if (!installedRepoUrl) return true;
	if (!candidateRepoUrl) return false;
	return publisherKey(installedRepoUrl) === publisherKey(candidateRepoUrl);
}

export class PublisherChangeError extends Error {
	constructor(
		readonly sourceId: number,
		readonly installedPublisher: string,
		readonly candidatePublisher: string,
	) {
		super(`Extension ${sourceId} is published by ${installedPublisher}; refusing code from ${candidatePublisher} without approval`);
		this.name = 'PublisherChangeError';
	}
}

export async function installSource(entry: SourceEntry, opts: { approvePublisherChange?: boolean } = {}): Promise<InstalledSource> {
	if (!entry.isJs) throw new Error('Only JavaScript extensions can run here (Dart extensions need Mangayomi itself).');
	const res = await execute({ method: 'GET', url: entry.sourceCodeUrl, headers: {} });
	if (res.statusCode >= 400) throw new Error(`Download failed: HTTP ${res.statusCode}`);
	const code = res.body;
	if (!code.includes('DefaultExtension')) throw new Error('This file is not a Mangayomi extension');
	if (entry.sha256) {
		if (!/^[a-f0-9]{64}$/i.test(entry.sha256)) throw new Error('Invalid SHA-256 supplied by the source index');
		if ((await sha256Hex(code)).toLowerCase() !== entry.sha256.toLowerCase()) throw new Error('Source file SHA-256 does not match its index');
	}
	const installed = await db.get<InstalledSource>('sources', entry.id);
	if (installed && !opts.approvePublisherChange && !isSamePublisher(installed.entry.repoUrl, entry.repoUrl)) {
		throw new PublisherChangeError(entry.id, publisherKey(installed.entry.repoUrl), publisherKey(entry.repoUrl));
	}
	if (installed && compareVersions(entry.version, installed.entry.version) < 0) throw new Error('Refusing extension version downgrade');
	const rec: InstalledSource = { id: entry.id, entry, code, installedAt: Date.now(), pinned: installed?.pinned, enabled: installed?.enabled ?? true };
	await db.put('sources', rec);
	return rec;
}

export async function uninstallSource(id: number) {
	await db.delete('sources', id);
}

export const installedSources = () => db.getAll<InstalledSource>('sources');

export async function installFromCode(code: string, meta: Partial<SourceEntry> & { name: string }): Promise<InstalledSource> {
	if (!code.includes('DefaultExtension')) throw new Error('This file is not a Mangayomi extension');
	const id = meta.id ?? Math.floor(Math.random() * 1e9) + 9e9;
	const entry: SourceEntry = {
		id,
		name: meta.name,
		baseUrl: meta.baseUrl ?? '',
		lang: meta.lang ?? '',
		version: meta.version ?? '0.0.1',
		itemType: meta.itemType ?? 0,
		isJs: true,
		sourceCodeUrl: '',
		iconUrl: '',
		apiUrl: meta.apiUrl ?? '',
		dateFormat: meta.dateFormat ?? '',
		dateFormatLocale: meta.dateFormatLocale ?? '',
		isNsfw: meta.isNsfw ?? false,
		hasCloudflare: false,
		additionalParams: '',
		notes: 'Installed from a local file',
		repoUrl: '',
	};
	const rec: InstalledSource = { id, entry, code, installedAt: Date.now(), enabled: true };
	await db.put('sources', rec);
	return rec;
}

/** Newest runnable entry per installed source from the SAME publisher that is newer than what is installed. */
export function updatesAvailable(installed: InstalledSource[], repos: RepoRecord[]): SourceEntry[] {
	const out: SourceEntry[] = [];
	for (const inst of installed) {
		let best: SourceEntry | undefined;
		for (const r of repos)
			for (const e of r.entries ?? [])
				if (
					e.id === inst.id &&
					e.isJs &&
					isSamePublisher(inst.entry.repoUrl, e.repoUrl) &&
					compareVersions(e.version, inst.entry.version) > 0 &&
					(!best || compareVersions(e.version, best.version) > 0)
				)
					best = e;
		if (best) out.push(best);
	}
	return out;
}

/** Newer versions of installed sources offered only by a different publisher; each needs explicit approval. */
export function publisherChangeOffers(installed: InstalledSource[], repos: RepoRecord[]): Array<{ installed: InstalledSource; candidate: SourceEntry }> {
	const out: Array<{ installed: InstalledSource; candidate: SourceEntry }> = [];
	for (const inst of installed) {
		if (!inst.entry.repoUrl) continue;
		let best: SourceEntry | undefined;
		for (const r of repos)
			for (const e of r.entries ?? [])
				if (
					e.id === inst.id &&
					e.isJs &&
					!isSamePublisher(inst.entry.repoUrl, e.repoUrl) &&
					compareVersions(e.version, inst.entry.version) > 0 &&
					(!best || compareVersions(e.version, best.version) > 0)
				)
					best = e;
		if (best) out.push({ installed: inst, candidate: best });
	}
	return out;
}
