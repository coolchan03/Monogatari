import { clearHistory, getHistory, removeHistory, useStore } from '../../core/store';
import type { HistoryEntry } from '../../core/types';
import { Cover, Empty, Page, Spinner, timeAgo } from '../components';
import { readHref, titleHref } from '../router';

export function HistoryPage() {
	const [items, loading] = useStore(['history'], getHistory, [] as HistoryEntry[]);
	return (
		<Page
			title="History"
			actions={
				items.length > 0 && (
					<button
						class="btn danger"
						onClick={() => {
							if (confirm('Clear all reading history?')) void clearHistory();
						}}
					>
						Clear
					</button>
				)
			}
		>
			{loading ? (
				<Spinner />
			) : items.length === 0 ? (
				<Empty title="No history yet">Titles you read, listen to or watch show up here.</Empty>
			) : (
				<ul class="list">
					{items.map((h) => (
						<li key={h.key} class="row item">
							<a href={titleHref(h.sourceId, h.url)} class="thumb">
								<Cover url={h.cover} title={h.title} />
							</a>
							<div class="grow">
								<a href={titleHref(h.sourceId, h.url)} class="item-title">
									{h.title}
								</a>
								<div class="muted">
									{h.chapterName || 'Chapter'} {h.pageCount > 0 && h.itemType === 0 ? `· page ${h.page + 1}/${h.pageCount}` : ''} · {timeAgo(h.updatedAt)}
								</div>
							</div>
							{h.chapterUrl && (
								<a class="btn primary small" href={readHref(h.sourceId, h.url, h.chapterUrl, h.page)}>
									Continue
								</a>
							)}
							<button class="btn small" aria-label="Remove from history" onClick={() => void removeHistory(h.key)}>
								✕
							</button>
						</li>
					))}
				</ul>
			)}
		</Page>
	);
}
