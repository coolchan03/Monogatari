import { useCallback, useEffect, useRef, useState } from 'preact/hooks';
import { getExtension, type Extension } from '../../core/extensions';
import { absoluteUrl, getInstalledSource } from '../../core/store';
import type { InstalledSource, JsManga } from '../../core/types';
import { ErrorBox, Grid, Page, SearchBox, Spinner, Tabs, TitleCard, Modal } from '../components';
import { FilterEditor } from '../filters';

type Mode = 'popular' | 'latest' | 'search';

export function BrowsePage({ sourceId, initialQuery }: { sourceId: number; initialQuery: string }) {
	const [src, setSrc] = useState<InstalledSource | null | undefined>(undefined);
	const [mode, setMode] = useState<Mode>(initialQuery ? 'search' : 'popular');
	const [query, setQuery] = useState(initialQuery);
	const [draft, setDraft] = useState(initialQuery);
	const [items, setItems] = useState<JsManga[]>([]);
	const [page, setPage] = useState(1);
	const [more, setMore] = useState(false);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState('');
	const [latest, setLatest] = useState(true);
	const [filters, setFilters] = useState<any[]>([]);
	const [showFilters, setShowFilters] = useState(false);
	const [filterVersion, setFilterVersion] = useState(0);
	const ext = useRef<Extension | null>(null);
	const run = useRef(0);

	useEffect(() => {
		let alive = true;
		void getInstalledSource(sourceId).then(async (s) => {
			if (!alive) return;
			setSrc(s ?? null);
			if (!s) return;
			try {
				const e = await getExtension(sourceId);
				ext.current = e;
				setLatest(await e.supportsLatest());
				setFilters(await e.getFilterList());
			} catch {
				/* source without filters */
			}
		});
		return () => {
			alive = false;
		};
	}, [sourceId]);

	const load = useCallback(
		async (reset: boolean, nextPage: number) => {
			const my = ++run.current;
			setLoading(true);
			setError('');
			if (reset) setItems([]);
			try {
				const e = ext.current ?? (await getExtension(sourceId));
				ext.current = e;
				const res = mode === 'search' ? await e.search(query, nextPage, filters) : mode === 'latest' ? await e.getLatestUpdates(nextPage) : await e.getPopular(nextPage);
				if (my !== run.current) return;
				setItems((prev) => {
					const seen = new Set((reset ? [] : prev).map((x) => x.link));
					return [...(reset ? [] : prev), ...res.list.filter((x) => x.link && !seen.has(x.link))];
				});
				setPage(nextPage);
				setMore(res.hasNextPage && res.list.length > 0);
			} catch (err: any) {
				if (my === run.current) setError(err?.message ?? String(err));
			} finally {
				if (my === run.current) setLoading(false);
			}
		},
		[sourceId, mode, query, filters],
	);

	useEffect(() => {
		if (src) void load(true, 1);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [src, mode, query, filterVersion]);

	const sentinel = useRef<HTMLDivElement>(null);
	useEffect(() => {
		const el = sentinel.current;
		if (!el || !more || loading) return;
		const io = new IntersectionObserver((es) => es[0].isIntersecting && void load(false, page + 1), { rootMargin: '600px' });
		io.observe(el);
		return () => io.disconnect();
	}, [more, loading, page, load]);

	if (src === undefined) return <Spinner />;
	if (src === null) return <ErrorBox error="This source is not installed." />;
	const base = src.entry.baseUrl;
	return (
		<Page
			title={src.entry.name}
			actions={
				filters.length > 0 && (
					<button class="btn" onClick={() => setShowFilters(true)}>
						Filters
					</button>
				)
			}
		>
			<form
				class="toolbar"
				onSubmit={(e) => {
					e.preventDefault();
					setQuery(draft.trim());
					setMode(draft.trim() || filters.length ? 'search' : 'popular');
					setFilterVersion((v) => v + 1);
				}}
			>
				<SearchBox placeholder={`Search ${src.entry.name}`} value={draft} onInput={(e) => setDraft((e.target as HTMLInputElement).value)} />
				<button class="btn primary">Search</button>
			</form>
			<Tabs
				value={mode}
				onChange={(m) => {
					setMode(m);
					if (m !== 'search') setQuery('');
				}}
				items={[
					{ id: 'popular', label: 'Popular' },
					...(latest ? [{ id: 'latest' as Mode, label: 'Latest' }] : []),
					{ id: 'search', label: 'Search' },
				]}
			/>
			{error && <ErrorBox error={error} onRetry={() => void load(items.length === 0, items.length === 0 ? 1 : page + 1)} />}
			{!error && !loading && items.length === 0 && <div class="muted center">No results.</div>}
			<Grid>
				{items.map((m) => (
					<TitleCard key={m.link} t={{ sourceId, url: m.link!, title: m.name ?? m.link!, cover: m.imageUrl ? absoluteUrl(m.imageUrl, base) : undefined }} />
				))}
			</Grid>
			{loading && <Spinner />}
			<div ref={sentinel} style="height:1px" />
			{showFilters && (
				<Modal title="Filters" onClose={() => setShowFilters(false)}>
					<FilterEditor filters={filters} onChange={() => setFilters([...filters])} />
					<div class="row end">
						<button
							class="btn"
							onClick={() => {
								void ext.current?.getFilterList().then(setFilters);
							}}
						>
							Reset
						</button>
						<button
							class="btn primary"
							onClick={() => {
								setShowFilters(false);
								setMode('search');
								setFilterVersion((v) => v + 1);
							}}
						>
							Apply
						</button>
					</div>
				</Modal>
			)}
		</Page>
	);
}
