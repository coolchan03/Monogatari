import { useState } from 'preact/hooks';
import { getInstalled, setSourceFlags, useStore } from '../../core/store';
import type { InstalledSource } from '../../core/types';
import { getSettings } from '../../core/settings';
import { Empty, Page, SearchBox, Spinner, Tabs } from '../components';
import { go, href } from '../router';

const KINDS = [
	{ id: '0', label: 'Manga' },
	{ id: '2', label: 'Novels' },
	{ id: '1', label: 'Anime' },
] as const;

export function ExplorePage() {
	const [sources, loading] = useStore(['sources'], getInstalled, [] as InstalledSource[]);
	const [kind, setKind] = useState<'0' | '1' | '2'>('0');
	const [q, setQ] = useState('');
	const nsfw = getSettings().showNsfw;
	const list = sources
		.filter((s) => s.enabled !== false && String(s.entry.itemType) === kind && (nsfw || !s.entry.isNsfw))
		.sort((a, b) => Number(!!b.pinned) - Number(!!a.pinned) || a.entry.name.localeCompare(b.entry.name));
	return (
		<Page
			title="Explore"
			actions={
				<a class="btn primary" href="#/repos">
					Source repositories
				</a>
			}
		>
			<form
				class="toolbar"
				onSubmit={(e) => {
					e.preventDefault();
					if (q.trim()) go(href('search') + '?q=' + encodeURIComponent(q.trim()));
				}}
			>
				<SearchBox placeholder="Search all installed sources" value={q} onInput={(e) => setQ((e.target as HTMLInputElement).value)} />
				<button class="btn primary">Search</button>
			</form>
			<Tabs value={kind} onChange={setKind} items={KINDS.map((k) => ({ id: k.id, label: `${k.label} (${sources.filter((s) => String(s.entry.itemType) === k.id).length})` }))} />
			{loading ? (
				<Spinner />
			) : sources.length === 0 ? (
				<Empty title="No sources installed">
					Add a Mangayomi repository and install a few extensions to start reading. <a href="#/repos">Open source repositories</a>.
				</Empty>
			) : list.length === 0 ? (
				<Empty title="Nothing here">No installed {KINDS.find((k) => k.id === kind)!.label.toLowerCase()} sources.</Empty>
			) : (
				<ul class="list">
					{list.map((s) => (
						<li key={s.id} class="row item">
							<a class="grow source" href={href('source', s.id)}>
								<span class="source-name">{s.entry.name}</span>
								<span class="muted">
									{s.entry.lang || 'multi'} · v{s.entry.version}
									{s.entry.isNsfw ? ' · 18+' : ''}
								</span>
							</a>
							<button class="btn small" title={s.pinned ? 'Unpin' : 'Pin to top'} onClick={() => void setSourceFlags(s.id, { pinned: !s.pinned })}>
								{s.pinned ? '★' : '☆'}
							</button>
							<a class="btn small" href={href('source-settings', s.id)}>
								Settings
							</a>
						</li>
					))}
				</ul>
			)}
		</Page>
	);
}
