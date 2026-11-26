import { useEffect, useState } from 'preact/hooks';
import { getExtension } from '../../core/extensions';
import { getInstalled, absoluteUrl } from '../../core/store';
import { getSettings } from '../../core/settings';
import type { InstalledSource, JsManga } from '../../core/types';
import { Empty, Grid, Page, Spinner, TitleCard } from '../components';

interface Result {
	src: InstalledSource;
	items?: JsManga[];
	error?: string;
	done: boolean;
}

/** Global search: every installed source answers independently and results stream in as they arrive. */
export function SearchPage({ query }: { query: string }) {
	const [results, setResults] = useState<Result[]>([]);
	const [pending, setPending] = useState(true);
	useEffect(() => {
		let alive = true;
		setResults([]);
		setPending(true);
		void (async () => {
			const nsfw = getSettings().showNsfw;
			const all = (await getInstalled()).filter((s) => s.enabled !== false && (nsfw || !s.entry.isNsfw));
			if (!alive) return;
			setResults(all.map((src) => ({ src, done: false })));
			let next = 0;
			const worker = async () => {
				while (alive && next < all.length) {
					const src = all[next++];
					let r: Result;
					try {
						const ext = await getExtension(src.id);
						const res = await ext.search(query, 1);
						r = { src, items: res.list.slice(0, 18), done: true };
					} catch (e: any) {
						r = { src, error: e?.message ?? String(e), done: true };
					}
					if (alive) setResults((prev) => prev.map((x) => (x.src.id === src.id ? r : x)));
				}
			};
			await Promise.all([worker(), worker(), worker()]);
			if (alive) setPending(false);
		})();
		return () => {
			alive = false;
		};
	}, [query]);

	const shown = results.filter((r) => r.items?.length);
	return (
		<Page title={`Search: ${query}`}>
			{pending && <Spinner label={`Searching ${results.filter((r) => r.done).length}/${results.length} sources…`} />}
			{!pending && shown.length === 0 && <Empty title="No results">Nothing matched in the installed sources.</Empty>}
			{shown.map((r) => (
				<section key={r.src.id}>
					<h2>
						<a href={`#/source/${r.src.id}?q=${encodeURIComponent(query)}`}>{r.src.entry.name}</a>
					</h2>
					<Grid>
						{r.items!.map((m) => (
							<TitleCard key={m.link} t={{ sourceId: r.src.id, url: m.link!, title: m.name ?? m.link!, cover: m.imageUrl ? absoluteUrl(m.imageUrl, r.src.entry.baseUrl) : undefined }} />
						))}
					</Grid>
				</section>
			))}
			{results.filter((r) => r.error).length > 0 && (
				<details>
					<summary class="muted">{results.filter((r) => r.error).length} source(s) failed</summary>
					<ul>
						{results.filter((r) => r.error).map((r) => (
							<li key={r.src.id}>
								<strong>{r.src.entry.name}</strong>: {r.error}
							</li>
						))}
					</ul>
				</details>
			)}
		</Page>
	);
}
