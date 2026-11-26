import { useEffect } from 'preact/hooks';
import { href, useRoute } from './router';
import { Toasts } from './components';
import { applyTheme } from '../core/settings';
import { LibraryPage } from './pages/Library';
import { HistoryPage } from './pages/History';
import { ExplorePage } from './pages/Explore';
import { BrowsePage } from './pages/Browse';
import { DetailsPage } from './pages/Details';
import { ReaderPage } from './pages/Reader';
import { ReposPage } from './pages/Repos';
import { SettingsPage } from './pages/Settings';
import { SourceSettingsPage } from './pages/SourceSettings';
import { DownloadsPage } from './pages/Downloads';
import { BookmarksPage } from './pages/Bookmarks';
import { SearchPage } from './pages/Search';
import { UpdatesPage } from './pages/Updates';
import { requestPersistence } from '../core/db';

const NAV = [
	{ id: '', label: 'Library', icon: '▤' },
	{ id: 'history', label: 'History', icon: '↺' },
	{ id: 'explore', label: 'Explore', icon: '⌕' },
	{ id: 'downloads', label: 'Offline', icon: '⇩' },
	{ id: 'settings', label: 'Settings', icon: '⚙' },
];

function Body() {
	const { path, query } = useRoute();
	const [a, b, c, d] = path;
	switch (a) {
		case undefined:
			return <LibraryPage />;
		case 'history':
			return <HistoryPage />;
		case 'explore':
			return <ExplorePage />;
		case 'search':
			return <SearchPage query={query.get('q') ?? ''} />;
		case 'source':
			return <BrowsePage sourceId={Number(b)} initialQuery={query.get('q') ?? ''} />;
		case 'title':
			return <DetailsPage key={b + c} sourceId={Number(b)} url={c} />;
		case 'read':
			return <ReaderPage key={b + c + d} sourceId={Number(b)} titleUrl={c} chapterUrl={d} startPage={Number(query.get('p') ?? 0)} />;
		case 'repos':
			return <ReposPage />;
		case 'downloads':
			return <DownloadsPage />;
		case 'bookmarks':
			return <BookmarksPage />;
		case 'updates':
			return <UpdatesPage />;
		case 'settings':
			return <SettingsPage />;
		case 'source-settings':
			return <SourceSettingsPage sourceId={Number(b)} />;
		default:
			return <LibraryPage />;
	}
}

export function App() {
	const { path } = useRoute();
	const reading = path[0] === 'read';
	useEffect(() => {
		applyTheme();
		void requestPersistence();
	}, []);
	const current = path[0] ?? '';
	return (
		<div class={'shell' + (reading ? ' reading' : '')}>
			{!reading && (
				<nav class="nav" aria-label="Main">
					<a class="brand" href="#/">
						<img src="./icons/icon-192.png" alt="" width="32" height="32" />
						<span>Monogatari</span>
					</a>
					{NAV.map((n) => (
						<a key={n.id} href={href(n.id).replace(/\/$/, '') || '#/'} class={'nav-item' + (current === n.id ? ' active' : '')} aria-current={current === n.id ? 'page' : undefined}>
							<span class="nav-icon" aria-hidden="true">
								{n.icon}
							</span>
							<span class="nav-label">{n.label}</span>
						</a>
					))}
				</nav>
			)}
			<main class="main">
				<Body />
			</main>
			<Toasts />
		</div>
	);
}
