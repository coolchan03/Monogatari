import { getDownloads, getInstalled, removeDownload, useStore } from '../../core/store';
import type { DownloadedChapter, InstalledSource } from '../../core/types';
import { zipPages } from '../../core/backup';
import { Empty, Page, Spinner, toast } from '../components';
import { readHref } from '../router';

export function DownloadsPage() {
	const [items, loading] = useStore(['downloads'], getDownloads, [] as DownloadedChapter[]);
	const [sources] = useStore(['sources'], getInstalled, [] as InstalledSource[]);
	const groups = new Map<string, DownloadedChapter[]>();
	for (const d of items) groups.set(d.titleKey, [...(groups.get(d.titleKey) ?? []), d]);
	return (
		<Page title="Offline">
			<p class="muted">Chapters saved on this device open without a network connection. Browsers may clear this storage if the device runs low on space.</p>
			{loading ? (
				<Spinner />
			) : items.length === 0 ? (
				<Empty title="Nothing saved">Use the ⇩ button on a chapter to save it for offline reading.</Empty>
			) : (
				[...groups.entries()].map(([k, chs]) => {
					const [sid, ...rest] = k.split(':');
					const titleUrl = rest.join(':');
					const name = sources.find((s) => String(s.id) === sid)?.entry.name ?? sid;
					return (
						<section key={k}>
							<h2 class="ellipsis">{titleUrl}</h2>
							<div class="muted">{name}</div>
							<ul class="list">
								{chs.sort((a, b) => a.savedAt - b.savedAt).map((c) => (
									<li class="row item" key={c.key}>
										<a class="grow" href={readHref(Number(sid), titleUrl, c.chapterUrl)}>
											{c.chapterName || c.chapterUrl}
											<div class="muted">{c.itemType === 2 ? 'Text' : `${c.pages?.length ?? 0} pages`}</div>
										</a>
										{c.pages && (
											<button
												class="btn small"
												onClick={async () => {
													const blob = await zipPages(c.pages!);
													const a = document.createElement('a');
													a.href = URL.createObjectURL(blob);
													a.download = (c.chapterName || 'chapter').replace(/[^\w.-]+/g, '_') + '.cbz';
													a.click();
													setTimeout(() => URL.revokeObjectURL(a.href), 5000);
												}}
											>
												Export .cbz
											</button>
										)}
										<button class="btn small danger" onClick={() => void removeDownload(c.key).then(() => toast('Removed'))}>
											Delete
										</button>
									</li>
								))}
							</ul>
						</section>
					);
				})
			)}
		</Page>
	);
}
