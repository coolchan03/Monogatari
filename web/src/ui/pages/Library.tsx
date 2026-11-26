import { useMemo, useState } from 'preact/hooks';
import { createCategory, deleteCategory, getCategories, getFavourites, renameCategory, useStore } from '../../core/store';
import type { Category, Favourite } from '../../core/types';
import { Empty, Grid, Page, SearchBox, Spinner, Tabs, TitleCard, toast, Modal } from '../components';
import { go } from '../router';

type Sort = 'added' | 'title' | 'new';

export function LibraryPage() {
	const [favs, loading] = useStore(['favourites'], getFavourites, [] as Favourite[]);
	const [cats] = useStore(['categories'], getCategories, [] as Category[]);
	const [cat, setCat] = useState<number | 'all'>('all');
	const [sort, setSort] = useState<Sort>('added');
	const [q, setQ] = useState('');
	const [manage, setManage] = useState(false);
	const [kind, setKind] = useState<'all' | '0' | '1' | '2'>('all');

	const shown = useMemo(() => {
		let list = favs.filter((f) => (cat === 'all' ? true : f.categoryIds.includes(cat)));
		if (kind !== 'all') list = list.filter((f) => String(f.itemType) === kind);
		if (q.trim()) list = list.filter((f) => f.title.toLowerCase().includes(q.trim().toLowerCase()));
		const s = [...list];
		if (sort === 'title') s.sort((a, b) => a.title.localeCompare(b.title));
		else if (sort === 'new') s.sort((a, b) => (b.newChapters ?? 0) - (a.newChapters ?? 0) || b.addedAt - a.addedAt);
		else s.sort((a, b) => b.addedAt - a.addedAt);
		return s;
	}, [favs, cat, sort, q, kind]);

	return (
		<Page
			title="Library"
			actions={
				<>
					<a class="btn" href="#/updates">
						Check for updates
					</a>
					<button class="btn" onClick={() => setManage(true)}>
						Categories
					</button>
				</>
			}
		>
			<div class="toolbar">
				<SearchBox placeholder="Filter library" value={q} onInput={(e) => setQ((e.target as HTMLInputElement).value)} />
				<select class="input" value={sort} onChange={(e) => setSort((e.target as HTMLSelectElement).value as Sort)} aria-label="Sort">
					<option value="added">Recently added</option>
					<option value="title">Title</option>
					<option value="new">New chapters</option>
				</select>
				<select class="input" value={kind} onChange={(e) => setKind((e.target as HTMLSelectElement).value as any)} aria-label="Type">
					<option value="all">All media</option>
					<option value="0">Manga</option>
					<option value="2">Novels</option>
					<option value="1">Anime</option>
				</select>
			</div>
			{cats.length > 0 && (
				<Tabs
					value={String(cat)}
					onChange={(v) => setCat(v === 'all' ? 'all' : Number(v))}
					items={[{ id: 'all', label: `All (${favs.length})` }, ...cats.map((c) => ({ id: String(c.id), label: `${c.name} (${favs.filter((f) => f.categoryIds.includes(c.id)).length})` }))]}
				/>
			)}
			{loading ? (
				<Spinner />
			) : shown.length === 0 ? (
				<Empty title={favs.length ? 'Nothing matches' : 'Your library is empty'}>
					{favs.length ? 'Try another filter.' : (
						<>
							Browse a source from <a href="#/explore">Explore</a> and tap “Add to library”, or restore a backup in <a href="#/settings">Settings</a>.
						</>
					)}
				</Empty>
			) : (
				<Grid>
					{shown.map((f) => (
						<TitleCard key={f.key} t={f} badge={f.newChapters ? `+${f.newChapters}` : undefined} />
					))}
				</Grid>
			)}
			{manage && <CategoryManager cats={cats} onClose={() => setManage(false)} />}
		</Page>
	);
}

function CategoryManager({ cats, onClose }: { cats: Category[]; onClose: () => void }) {
	const [name, setName] = useState('');
	return (
		<Modal title="Categories" onClose={onClose}>
			<ul class="list">
				{cats.map((c) => (
					<li key={c.id} class="row">
						<span class="grow">{c.name}</span>
						<button
							class="btn small"
							onClick={() => {
								const n = prompt('Rename category', c.name);
								if (n) void renameCategory(c.id, n.trim());
							}}
						>
							Rename
						</button>
						<button
							class="btn small danger"
							onClick={() => {
								if (confirm(`Delete “${c.name}”? Titles stay in your library.`)) void deleteCategory(c.id);
							}}
						>
							Delete
						</button>
					</li>
				))}
				{cats.length === 0 && <li class="muted">No categories yet.</li>}
			</ul>
			<form
				class="row"
				onSubmit={(e) => {
					e.preventDefault();
					if (!name.trim()) return;
					void createCategory(name).then(() => toast('Category created'));
					setName('');
				}}
			>
				<input class="input grow" placeholder="New category" value={name} onInput={(e) => setName((e.target as HTMLInputElement).value)} />
				<button class="btn primary">Add</button>
			</form>
			<div class="row end">
				<button class="btn" onClick={onClose}>
					Done
				</button>
			</div>
		</Modal>
	);
}
void go;
