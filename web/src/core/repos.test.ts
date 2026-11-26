import { describe, expect, it, vi } from 'vitest';

vi.hoisted(() => {
	// settings.ts reads matchMedia at import time, which jsdom lacks
	(globalThis as any).matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
});

import { compareVersions, isSamePublisher, publisherChangeOffers, publisherKey, updatesAvailable } from './repos';
import type { InstalledSource, SourceEntry } from './types';
import type { RepoRecord } from './repos';

const entry = (over: Partial<SourceEntry>): SourceEntry => ({
	id: 1, name: 'S', baseUrl: 'https://s.example', lang: 'en', version: '1.0.0', itemType: 0, isJs: true, sourceCodeUrl: '', iconUrl: '', apiUrl: '',
	dateFormat: '', dateFormatLocale: '', isNsfw: false, hasCloudflare: false, additionalParams: '', notes: '', repoUrl: '', ...over,
});
const inst = (e: SourceEntry): InstalledSource => ({ id: e.id, entry: e, code: 'DefaultExtension', installedAt: 0 });
const repo = (url: string, entries: SourceEntry[]) => ({ url, addedAt: 0, entries }) as unknown as RepoRecord;

describe('publisher identity', () => {
	it('ignores branch and index file name on GitHub hosts', () => {
		expect(publisherKey('https://raw.githubusercontent.com/Owner/Repo/main/index.json')).toBe(publisherKey('https://raw.githubusercontent.com/owner/repo/master/index.min.json'));
	});
	it('distinguishes owners and hosts, and drops a trailing file name elsewhere', () => {
		expect(isSamePublisher('https://raw.githubusercontent.com/a/r/main/i.json', 'https://raw.githubusercontent.com/b/r/main/i.json')).toBe(false);
		expect(publisherKey('https://example.com/repo/index.json')).toBe('example.com/repo');
		expect(isSamePublisher('https://example.com/repo/index.json', 'https://evil.example.com/repo/index.json')).toBe(false);
	});
	it('treats blank installed provenance as unpinned but a blank candidate as foreign', () => {
		expect(isSamePublisher('', 'https://x.example/r/')).toBe(true);
		expect(isSamePublisher('https://x.example/r/', '')).toBe(false);
	});
});

describe('updates respect the installed publisher', () => {
	const mine = 'https://raw.githubusercontent.com/me/ext/main/index.json';
	const other = 'https://raw.githubusercontent.com/other/fork/main/index.json';
	const installed = [inst(entry({ version: '1.0.0', repoUrl: mine }))];
	it('does not update silently from another publisher, even at a higher version', () => {
		const repos = [repo(other, [entry({ version: '9.0.0', repoUrl: other })])];
		expect(updatesAvailable(installed, repos)).toEqual([]);
		expect(publisherChangeOffers(installed, repos).map((o) => o.candidate.version)).toEqual(['9.0.0']);
	});
	it('updates from the same publisher and ignores the foreign newer one for the silent path', () => {
		const repos = [repo(other, [entry({ version: '9.0.0', repoUrl: other })]), repo(mine, [entry({ version: '1.1.0', repoUrl: mine })])];
		expect(updatesAvailable(installed, repos).map((e) => e.version)).toEqual(['1.1.0']);
	});
	it('compareVersions orders numerically', () => expect(compareVersions('1.10.0', '1.9.0')).toBeGreaterThan(0));
});
