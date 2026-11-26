import { getBookmarks, removeBookmark, useStore } from '../../core/store';
import type { Bookmark } from '../../core/types';
import { Cover, Empty, Page, Spinner, timeAgo } from '../components';
import { readHref } from '../router';

export function BookmarksPage() {
	const [items, loading] = useStore(['bookmarks'], getBookmarks, [] as Bookmark[]);
	return (
		<Page title="Bookmarks">
			{loading ? (
				<Spinner />
			) : items.length === 0 ? (
				<Empty title="No bookmarks">Tap 🔖 in the reader to keep your place.</Empty>
			) : (
				<ul class="list">
					{items.map((b) => (
						<li class="row item" key={b.key}>
							<div class="thumb">
								<Cover url={b.cover} title={b.title} />
							</div>
							<div class="grow">
								<div class="item-title">{b.title}</div>
								<div class="muted">
									{b.chapterName} · page {b.page + 1} · {timeAgo(b.createdAt)}
								</div>
							</div>
							{b.chapterUrl && (
								<a class="btn small primary" href={readHref(b.sourceId, b.titleUrl, b.chapterUrl, b.page)}>
									Open
								</a>
							)}
							<button class="btn small" aria-label="Remove bookmark" onClick={() => void removeBookmark(b.key)}>
								✕
							</button>
						</li>
					))}
				</ul>
			)}
		</Page>
	);
}
