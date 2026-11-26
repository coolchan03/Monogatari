import { useEffect, useMemo, useState } from 'preact/hooks';
import { cachedDetails, loadDetails, type Chapter, type Details } from '../../core/titles';
import {
	getCategories,
	getFavourite,
	getHistoryFor,
	getReadSet,
	makeTitle,
	markRead,
	removeFavourite,
	saveProgress,
	setFavourite,
	getDownloads,
	useStore,
} from '../../core/store';
import { downloadChapter, useDownloadProgress } from '../../core/download';
import type { Category, DownloadedChapter, Favourite, HistoryEntry } from '../../core/types';
import { Cover, ErrorBox, Modal, Spinner, timeAgo, toast } from '../components';
import { readHref } from '../router';

export function DetailsPage({ sourceId, url }: { sourceId: number; url: string }) {
	const [d, setD] = useState<Details | undefined>(cachedDetails(sourceId, url));
	const [error, setError] = useState('');
	const [loading, setLoading] = useState(!d);
	const [fav] = useStore(['favourites'], () => getFavourite(`${sourceId}:${url}`), undefined as Favourite | undefined);
	const [hist] = useStore(['history'], () => getHistoryFor(`${sourceId}:${url}`), undefined as HistoryEntry | undefined);
	const [read] = useStore(['read'], () => getReadSet(`${sourceId}:${url}`), new Set<string>());
	const [cats] = useStore(['categories'], getCategories, [] as Category[]);
	const [dl] = useStore(['downloads'], getDownloads, [] as DownloadedChapter[]);
	const [desc, setDesc] = useState(false);
	const [newestFirst, setNewestFirst] = useState(true);
	const [picker, setPicker] = useState(false);
	const progress = useDownloadProgress();
	const [filter, setFilter] = useState('');

	const refresh = async (force = false) => {
		setLoading(true);
		setError('');
		try {
			const det = await loadDetails(sourceId, url, { force, fallback: fav ? { title: fav.title, cover: fav.cover } : undefined });
			setD(det);
		} catch (e: any) {
			setError(e?.message ?? String(e));
		} finally {
			setLoading(false);
		}
	};
	useEffect(() => {
		void refresh();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [sourceId, url]);

	// An Android backup stores the chapter id (a hash), not its URL; resolve it once the chapters are known.
	useEffect(() => {
		if (!d || !hist || hist.chapterUrl || !hist.chapterUid) return;
		const ch = d.chapters.find((c) => c.uid === hist.chapterUid);
		if (ch) void saveProgress(makeTitle(sourceId, url, d.title, d.cover, d.itemType), { chapterUrl: ch.url, chapterName: ch.name, chapterIndex: ch.index, page: hist.page, pageCount: hist.pageCount });
	}, [d, hist]);

	const chapters = useMemo(() => {
		if (!d) return [];
		let list = d.chapters;
		if (filter.trim()) list = list.filter((c) => c.name.toLowerCase().includes(filter.trim().toLowerCase()));
		return newestFirst ? [...list].reverse() : list;
	}, [d, newestFirst, filter]);

	if (!d) return loading ? <Spinner label="Loading…" /> : <ErrorBox error={error} onRetry={() => void refresh(true)} />;

	const title = makeTitle(sourceId, url, d.title, d.cover, d.itemType);
	const downloaded = new Set(dl.filter((x) => x.titleKey === title.key).map((x) => x.chapterUrl));
	const resume = hist?.chapterUrl ? d.chapters.find((c) => c.url === hist.chapterUrl) : undefined;
	const firstUnread = d.chapters.find((c) => !read.has(c.url));
	const target: { ch: Chapter; page: number; label: string } | undefined = resume
		? { ch: resume, page: hist!.page, label: `Continue · ${resume.name}` }
		: firstUnread
			? { ch: firstUnread, page: 0, label: read.size ? `Next · ${firstUnread.name}` : 'Start' }
			: d.chapters[0]
				? { ch: d.chapters[0], page: 0, label: 'Read again' }
				: undefined;
	const verb = d.itemType === 1 ? 'Watch' : d.itemType === 2 ? 'Read' : 'Read';

	return (
		<article class="details">
			<div class="details-head">
				<div class="details-cover">
					<Cover url={d.cover} title={d.title} />
				</div>
				<div class="details-info">
					<h1>{d.title}</h1>
					{d.authors.length > 0 && <div class="muted">{d.authors.join(', ')}</div>}
					<div class="muted">
						{[d.status, d.itemType === 2 ? 'Novel' : d.itemType === 1 ? 'Anime' : 'Manga', `${d.chapters.length} ${d.itemType === 1 ? 'episodes' : 'chapters'}`].filter(Boolean).join(' · ')}
					</div>
					<div class="row wrap">
						{target && (
							<a class="btn primary" href={readHref(sourceId, url, target.ch.url, target.page)}>
								{verb === 'Watch' ? '▶ ' : ''}
								{target.label}
							</a>
						)}
						<button
							class={'btn' + (fav ? ' active' : '')}
							onClick={() => {
								if (fav) void removeFavourite(title.key).then(() => toast('Removed from library'));
								else if (cats.length) setPicker(true);
								else void setFavourite(title, []).then(() => toast('Added to library'));
							}}
						>
							{fav ? '♥ In library' : '♡ Add to library'}
						</button>
						{fav && cats.length > 0 && (
							<button class="btn" onClick={() => setPicker(true)}>
								Categories
							</button>
						)}
						<button class="btn" onClick={() => void refresh(true)} disabled={loading}>
							{loading ? 'Refreshing…' : 'Refresh'}
						</button>
						{d.publicUrl && /^https?:/.test(d.publicUrl) && (
							<a class="btn" href={d.publicUrl} target="_blank" rel="noreferrer noopener">
								Open website
							</a>
						)}
					</div>
				</div>
			</div>
			{error && <ErrorBox error={error} onRetry={() => void refresh(true)} />}
			{d.description && (
				<div class="desc">
					<p class={desc ? '' : 'clamp'}>{d.description}</p>
					<button class="link" onClick={() => setDesc(!desc)}>
						{desc ? 'Less' : 'More'}
					</button>
				</div>
			)}
			{d.genres.length > 0 && (
				<div class="chips">
					{d.genres.map((g) => (
						<span class="chip" key={g}>
							{g}
						</span>
					))}
				</div>
			)}
			<div class="toolbar">
				<h2 class="grow">{d.itemType === 1 ? 'Episodes' : 'Chapters'}</h2>
				<input class="input" placeholder="Filter" value={filter} onInput={(e) => setFilter((e.target as HTMLInputElement).value)} aria-label="Filter chapters" />
				<button class="btn small" onClick={() => setNewestFirst(!newestFirst)}>
					{newestFirst ? '↓ Newest' : '↑ Oldest'}
				</button>
				{d.itemType !== 1 && (
					<button
						class="btn small"
						onClick={() => {
							const todo = d.chapters.filter((c) => !downloaded.has(c.url) && !read.has(c.url)).slice(0, 10);
							if (!todo.length) return toast('Nothing to download');
							todo.forEach((c) => void downloadChapter(title, c).catch((e) => toast(`Download failed: ${e.message}`)));
							toast(`Downloading ${todo.length} chapters…`);
						}}
					>
						⇩ Next 10 unread
					</button>
				)}
			</div>
			<ul class="list chapters">
				{chapters.map((c) => {
					const p = progress.get(`${title.key}|${c.url}`);
					return (
						<li key={c.url} class={'row chapter' + (read.has(c.url) ? ' read' : '')}>
							<a class="grow" href={readHref(sourceId, url, c.url)}>
								<div class="chapter-name">{c.name || `Chapter ${c.index + 1}`}</div>
								<div class="muted">
									{[c.scanlator, c.dateUpload ? timeAgo(c.dateUpload) : ''].filter(Boolean).join(' · ')}
									{hist?.chapterUrl === c.url ? ' · in progress' : ''}
								</div>
							</a>
							{d.itemType !== 1 &&
								(downloaded.has(c.url) ? (
									<span class="muted" title="Saved for offline reading">
										✓ offline
									</span>
								) : p != null ? (
									<span class="muted">{Math.round(p * 100)}%</span>
								) : (
									<button class="btn small" aria-label="Download for offline reading" onClick={() => void downloadChapter(title, c).catch((e) => toast(`Download failed: ${e.message}`))}>
										⇩
									</button>
								))}
							<button class="btn small" aria-label={read.has(c.url) ? 'Mark unread' : 'Mark read'} onClick={() => void markRead(title.key, c.url, !read.has(c.url))}>
								{read.has(c.url) ? '●' : '○'}
							</button>
						</li>
					);
				})}
			</ul>
			{picker && (
				<CategoryPicker
					cats={cats}
					selected={fav?.categoryIds ?? []}
					onClose={() => setPicker(false)}
					onSave={(ids) => {
						void setFavourite(title, ids).then(() => toast('Library updated'));
						setPicker(false);
					}}
				/>
			)}
		</article>
	);
}

function CategoryPicker({ cats, selected, onSave, onClose }: { cats: Category[]; selected: number[]; onSave: (ids: number[]) => void; onClose: () => void }) {
	const [ids, setIds] = useState(selected);
	return (
		<Modal title="Add to categories" onClose={onClose}>
			<ul class="list">
				{cats.map((c) => (
					<li key={c.id}>
						<label class="check">
							<input type="checkbox" checked={ids.includes(c.id)} onChange={(e) => setIds((x) => ((e.target as HTMLInputElement).checked ? [...x, c.id] : x.filter((i) => i !== c.id)))} />
							<span>{c.name}</span>
						</label>
					</li>
				))}
			</ul>
			<div class="row end">
				<button class="btn" onClick={onClose}>
					Cancel
				</button>
				<button class="btn primary" onClick={() => onSave(ids)}>
					Save
				</button>
			</div>
		</Modal>
	);
}
