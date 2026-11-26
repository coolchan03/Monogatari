import { useMemo, useState } from 'preact/hooks';
import { addRepo, installSource, listRepos, publisherChangeOffers, publisherKey, RECOMMENDED_REPOS, refreshRepo, removeRepo, uninstallSource, updatesAvailable, installFromCode, type RepoRecord } from '../../core/repos';
import { getInstalled, useStore } from '../../core/store';
import type { InstalledSource, SourceEntry } from '../../core/types';
import { Empty, ErrorBox, Page, SearchBox, Spinner, Tabs, toast } from '../components';

export function ReposPage() {
	const [repos, loading] = useStore(['repos'], listRepos, [] as RepoRecord[]);
	const [installed] = useStore(['sources'], getInstalled, [] as InstalledSource[]);
	const [url, setUrl] = useState('');
	const [busy, setBusy] = useState('');
	const [error, setError] = useState('');
	const [kind, setKind] = useState<'0' | '1' | '2'>('0');
	const [q, setQ] = useState('');
	const [lang, setLang] = useState('');
	const [showNsfw, setShowNsfw] = useState(false);

	const have = new Map(installed.map((s) => [s.id, s]));
	const updates = useMemo(() => updatesAvailable(installed, repos), [installed, repos]);
	const offers = useMemo(() => publisherChangeOffers(installed, repos), [installed, repos]);

	const all = useMemo(() => {
		const best = new Map<number, SourceEntry>();
		for (const r of repos) for (const e of r.entries ?? []) if (!best.has(e.id)) best.set(e.id, e);
		return [...best.values()];
	}, [repos]);
	const langs = useMemo(() => [...new Set(all.map((e) => e.lang))].sort(), [all]);
	const list = all
		.filter((e) => String(e.itemType) === kind && (!lang || e.lang === lang) && (showNsfw || !e.isNsfw) && (!q || e.name.toLowerCase().includes(q.toLowerCase())))
		.sort((a, b) => a.name.localeCompare(b.name));

	const run = async (label: string, fn: () => Promise<unknown>, ok?: string) => {
		setBusy(label);
		setError('');
		try {
			await fn();
			if (ok) toast(ok);
		} catch (e: any) {
			setError(e?.message ?? String(e));
		} finally {
			setBusy('');
		}
	};

	return (
		<Page title="Source repositories">
			<p class="muted">
				Repositories list Mangayomi JavaScript extensions. Only add repositories you trust: an extension runs code from that repository (inside a sandbox with no access to your library, but it can make network requests).
			</p>
			<form
				class="toolbar"
				onSubmit={(e) => {
					e.preventDefault();
					if (url.trim()) void run('add', () => addRepo(url), 'Repository added').then(() => setUrl(''));
				}}
			>
				<input class="input grow" placeholder="https://…/index.json" value={url} onInput={(e) => setUrl((e.target as HTMLInputElement).value)} aria-label="Repository URL" />
				<button class="btn primary" disabled={!!busy}>
					{busy === 'add' ? 'Adding…' : 'Add'}
				</button>
				<label class="btn">
					Install .js file
					<input
						type="file"
						accept=".js,text/javascript"
						hidden
						onChange={async (e) => {
							const f = (e.target as HTMLInputElement).files?.[0];
							if (!f) return;
							const name = prompt('Name for this source', f.name.replace(/\.js$/, ''));
							if (!name) return;
							const base = prompt('Website base URL (https://…)', '') ?? '';
							const type = prompt('Type: 0 = manga, 2 = novel, 1 = anime', '0') ?? '0';
							await run('file', async () => installFromCode(await f.text(), { name, baseUrl: base, itemType: ([0, 1, 2].includes(Number(type)) ? Number(type) : 0) as 0 | 1 | 2 }), 'Source installed');
						}}
					/>
				</label>
			</form>
			{error && <ErrorBox error={error} />}
			<div class="chips">
				{RECOMMENDED_REPOS.filter((r) => !repos.some((x) => x.url === r.url)).map((r) => (
					<button key={r.url} class="chip btn small" disabled={!!busy} onClick={() => void run('add', () => addRepo(r.url), `${r.name} added`)}>
						+ {r.name}
					</button>
				))}
			</div>

			{repos.length > 0 && (
				<>
					<h2>Your repositories</h2>
					<ul class="list">
						{repos.map((r) => (
							<li key={r.url} class="row item">
								<div class="grow">
									<div class="ellipsis">{r.url}</div>
									<div class={r.error ? 'error-text' : 'muted'}>{r.error ? r.error : `${r.count ?? 0} sources · ${r.runnable ?? 0} runnable (JavaScript)`}</div>
								</div>
								<button class="btn small" disabled={!!busy} onClick={() => void run(r.url, () => refreshRepo(r.url), 'Refreshed')}>
									Refresh
								</button>
								<button class="btn small danger" onClick={() => void removeRepo(r.url)}>
									Remove
								</button>
							</li>
						))}
					</ul>
				</>
			)}

			{updates.length > 0 && (
				<div class="banner">
					<span class="grow">{updates.length} extension update(s) available</span>
					<button
						class="btn primary small"
						disabled={!!busy}
						onClick={() =>
							void run(
								'update',
								async () => {
									for (const e of updates) await installSource(e);
								},
								'Extensions updated',
							)
						}
					>
						Update all
					</button>
				</div>
			)}

			{offers.map(({ installed: inst, candidate }) => (
				<div class="banner" key={'offer' + inst.id}>
					<span class="grow">
						{candidate.name} v{candidate.version} is offered by a different publisher ({publisherKey(candidate.repoUrl)}); you installed it from {publisherKey(inst.entry.repoUrl)}.
					</span>
					<button
						class="btn small"
						disabled={!!busy}
						onClick={() => {
							if (confirm(`Replace ${inst.entry.name} with code from ${publisherKey(candidate.repoUrl)}? That publisher will be able to run code in this app.`))
								void run('o' + inst.id, () => installSource(candidate, { approvePublisherChange: true }), 'Updated');
						}}
					>
						Review and switch
					</button>
				</div>
			))}

			<h2>Available sources</h2>
			{loading ? (
				<Spinner />
			) : all.length === 0 ? (
				<Empty title="No sources yet">Add a repository above, or tap one of the suggestions.</Empty>
			) : (
				<>
					<Tabs value={kind} onChange={setKind} items={[{ id: '0', label: 'Manga' }, { id: '2', label: 'Novels' }, { id: '1', label: 'Anime' }]} />
					<div class="toolbar">
						<SearchBox placeholder="Filter sources" value={q} onInput={(e) => setQ((e.target as HTMLInputElement).value)} />
						<select class="input" value={lang} onChange={(e) => setLang((e.target as HTMLSelectElement).value)} aria-label="Language">
							<option value="">All languages</option>
							{langs.map((l) => (
								<option value={l}>{l || '—'}</option>
							))}
						</select>
						<label class="check">
							<input type="checkbox" checked={showNsfw} onChange={(e) => setShowNsfw((e.target as HTMLInputElement).checked)} />
							<span>18+</span>
						</label>
					</div>
					<ul class="list">
						{list.map((e) => {
							const inst = have.get(e.id);
							const outdated = inst && updates.some((u) => u.id === e.id);
							return (
								<li key={e.id} class="row item">
									<div class="grow">
										<div class="source-name">{e.name}</div>
										<div class="muted">
											{e.lang || 'multi'} · v{e.version}
											{e.isNsfw ? ' · 18+' : ''}
											{e.hasCloudflare ? ' · Cloudflare' : ''}
											{!e.isJs ? ' · Dart (not supported)' : ''}
										</div>
									</div>
									{inst ? (
										<>
											{outdated && (
												<button class="btn small primary" disabled={!!busy} onClick={() => void run('i' + e.id, () => installSource(updates.find((u) => u.id === e.id)!), 'Updated')}>
													Update
												</button>
											)}
											<button class="btn small danger" onClick={() => void uninstallSource(e.id)}>
												Uninstall
											</button>
										</>
									) : (
										<button class="btn small primary" disabled={!e.isJs || !!busy} onClick={() => void run('i' + e.id, () => installSource(e), `${e.name} installed`)}>
											{busy === 'i' + e.id ? '…' : 'Install'}
										</button>
									)}
								</li>
							);
						})}
					</ul>
				</>
			)}
		</Page>
	);
}
