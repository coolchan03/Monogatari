import { useEffect, useState } from 'preact/hooks';
import { db } from '../../core/db';
import { loadDetails } from '../../core/titles';
import { getFavourites } from '../../core/store';
import { Page, Spinner } from '../components';
import { titleHref } from '../router';

/** Checks every library title for new chapters (the web equivalent of the Android tracker). */
export function UpdatesPage() {
	const [log, setLog] = useState<{ title: string; sourceId: number; url: string; added?: number; error?: string }[]>([]);
	const [done, setDone] = useState(false);
	const [progress, setProgress] = useState([0, 0]);
	useEffect(() => {
		let alive = true;
		void (async () => {
			const favs = await getFavourites();
			setProgress([0, favs.length]);
			let n = 0;
			let i = 0;
			const worker = async () => {
				while (alive && i < favs.length) {
					const f = favs[i++];
					try {
						const d = await loadDetails(f.sourceId, f.url, { force: true });
						const count = d.chapters.length;
						const added = f.lastChapterCount != null ? Math.max(0, count - f.lastChapterCount) : 0;
						await db.put('favourites', { ...f, lastChapterCount: count, newChapters: added > 0 ? (f.newChapters ?? 0) + added : f.newChapters });
						if (added > 0 && alive) setLog((l) => [...l, { title: f.title, sourceId: f.sourceId, url: f.url, added }]);
					} catch (e: any) {
						if (alive) setLog((l) => [...l, { title: f.title, sourceId: f.sourceId, url: f.url, error: e?.message ?? String(e) }]);
					}
					if (alive) setProgress([++n, favs.length]);
				}
			};
			await Promise.all([worker(), worker(), worker()]);
			if (alive) setDone(true);
		})();
		return () => {
			alive = false;
		};
	}, []);
	return (
		<Page title="Library updates">
			{!done && <Spinner label={`Checking ${progress[0]}/${progress[1]}…`} />}
			{done && log.every((l) => l.error) && <p class="muted">No new chapters.</p>}
			<ul class="list">
				{log.map((l, i) => (
					<li class="row item" key={i}>
						<a class="grow" href={titleHref(l.sourceId, l.url)}>
							{l.title}
						</a>
						<span class={l.error ? 'error-text' : 'badge-inline'}>{l.error ? l.error : `+${l.added} new`}</span>
					</li>
				))}
			</ul>
		</Page>
	);
}
