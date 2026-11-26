import { useCallback, useEffect, useMemo, useRef, useState } from 'preact/hooks';
import { getExtension } from '../../core/extensions';
import { mediaUrl } from '../../core/http';
import { sanitizeHtml } from '../../core/sanitize';
import { replaceSettings, updateSettings, useSettings, type ReaderMode } from '../../core/settings';
import { addBookmark, getDownload, getHistoryFor, getInstalledSource, makeTitle, markRead, saveProgress } from '../../core/store';
import { loadDetails, type Chapter, type Details } from '../../core/titles';
import type { InstalledSource, JsPage, JsVideo, Title } from '../../core/types';
import { ErrorBox, Spinner, toast } from '../components';
import { back, go, readHref, titleHref } from '../router';
import Hls from 'hls.js';

interface Ctx {
	src: InstalledSource;
	title: Title;
	details: Details | null;
	chapter: Chapter | null;
	chapterUrl: string;
	chapterName: string;
}

export function ReaderPage({ sourceId, titleUrl, chapterUrl, startPage }: { sourceId: number; titleUrl: string; chapterUrl: string; startPage: number }) {
	const [ctx, setCtx] = useState<Ctx | null>(null);
	const [error, setError] = useState('');

	useEffect(() => {
		let alive = true;
		void (async () => {
			try {
				const src = await getInstalledSource(sourceId);
				if (!src) throw new Error('This source is not installed.');
				let details: Details | null = null;
				try {
					details = await loadDetails(sourceId, titleUrl);
				} catch (e) {
					if (!(await getDownload(`${sourceId}:${titleUrl}`, chapterUrl))) throw e; // offline copies still open
				}
				const chapter = details?.chapters.find((c) => c.url === chapterUrl) ?? null;
				const dl = await getDownload(`${sourceId}:${titleUrl}`, chapterUrl);
				const title = makeTitle(sourceId, titleUrl, details?.title ?? (await getHistoryFor(`${sourceId}:${titleUrl}`))?.title ?? titleUrl, details?.cover, src.entry.itemType);
				if (alive) setCtx({ src, title, details, chapter, chapterUrl, chapterName: chapter?.name ?? dl?.chapterName ?? '' });
			} catch (e: any) {
				if (alive) setError(e?.message ?? String(e));
			}
		})();
		return () => {
			alive = false;
		};
	}, [sourceId, titleUrl, chapterUrl]);

	if (error) return <div class="page"><ErrorBox error={error} onRetry={() => location.reload()} /><a class="btn" href={titleHref(sourceId, titleUrl)}>Back to title</a></div>;
	if (!ctx) return <Spinner label="Opening…" />;
	const type = ctx.src.entry.itemType;
	return type === 2 ? <NovelReader ctx={ctx} startPage={startPage} /> : type === 1 ? <VideoReader ctx={ctx} startPage={startPage} /> : <MangaReader ctx={ctx} startPage={startPage} />;
}

function useNav(ctx: Ctx) {
	const prev = ctx.chapter && ctx.details ? ctx.details.chapters[ctx.chapter.index - 1] : undefined;
	const next = ctx.chapter && ctx.details ? ctx.details.chapters[ctx.chapter.index + 1] : undefined;
	const open = (c?: Chapter) => c && go(readHref(ctx.title.sourceId, ctx.title.url, c.url), true);
	return { prev, next, open };
}

function useProgress(ctx: Ctx) {
	const timer = useRef<number>();
	return useCallback(
		(page: number, pageCount: number, finished = false) => {
			clearTimeout(timer.current);
			timer.current = window.setTimeout(() => {
				void saveProgress(ctx.title, { chapterUrl: ctx.chapterUrl, chapterName: ctx.chapterName, chapterIndex: ctx.chapter?.index ?? -1, page, pageCount });
				if (finished) void markRead(ctx.title.key, ctx.chapterUrl);
			}, 400);
		},
		[ctx],
	);
}

// ---------------------------------------------------------------- manga

type PageItem = { url: string; headers?: Record<string, string> | null; blob?: boolean };

function MangaReader({ ctx, startPage }: { ctx: Ctx; startPage: number }) {
	const s = useSettings();
	const { prev, next, open } = useNav(ctx);
	const [pages, setPages] = useState<PageItem[] | null>(null);
	const [error, setError] = useState('');
	const [idx, setIdx] = useState(startPage);
	const [ui, setUi] = useState(true);
	const [panel, setPanel] = useState(false);
	const save = useProgress(ctx);
	const mode = s.readerMode;

	useEffect(() => {
		let alive = true;
		const urls: string[] = [];
		void (async () => {
			try {
				const dl = await getDownload(ctx.title.key, ctx.chapterUrl);
				if (dl?.pages?.length) {
					const items = dl.pages.map((b) => {
						const u = URL.createObjectURL(b);
						urls.push(u);
						return { url: u, blob: true };
					});
					if (alive) setPages(items);
					return;
				}
				const ext = await getExtension(ctx.title.sourceId);
				const list: JsPage[] = await ext.getPageList(ctx.chapterUrl);
				if (!list.length) throw new Error(ext.lastNetworkProblem ? `No pages (${ext.lastNetworkProblem})` : 'The source returned no pages for this chapter.');
				const hdr = await ext.getHeaders();
				if (alive) setPages(list.map((p) => ({ url: p.url, headers: { ...hdr, ...(p.headers ?? {}) } })));
			} catch (e: any) {
				if (alive) setError(e?.message ?? String(e));
			}
		})();
		return () => {
			alive = false;
			urls.forEach(URL.revokeObjectURL);
		};
	}, [ctx.chapterUrl]);

	const count = pages?.length ?? 0;
	const go1 = useCallback(
		(delta: number) => {
			setIdx((i) => {
				const n = i + delta;
				if (n < 0) {
					if (prev) open(prev);
					return i;
				}
				if (n >= count) {
					save(count - 1, count, true);
					if (next) open(next);
					else toast('End of the last chapter');
					return i;
				}
				return n;
			});
		},
		[count, prev, next],
	);

	useEffect(() => {
		if (!pages) return;
		save(idx, count, idx >= count - 1);
		// preload ahead
		for (let k = 1; k <= s.preloadPages; k++) {
			const p = pages[idx + k];
			if (p && !p.blob) {
				const im = new Image();
				im.referrerPolicy = 'no-referrer';
				im.src = mediaUrl(p.url, p.headers);
			}
		}
	}, [idx, pages]);

	useEffect(() => {
		const f = (e: KeyboardEvent) => {
			if ((e.target as HTMLElement)?.tagName === 'INPUT') return;
			const rtl = mode === 'paged-rtl';
			if (e.key === 'ArrowRight') mode !== 'webtoon' && go1(rtl ? -1 : 1);
			else if (e.key === 'ArrowLeft') mode !== 'webtoon' && go1(rtl ? 1 : -1);
			else if (e.key === ' ' && mode !== 'webtoon') (e.preventDefault(), go1(e.shiftKey ? -1 : 1));
			else if (e.key === 'Escape') back();
			else if (e.key === 'm') setUi((u) => !u);
		};
		addEventListener('keydown', f);
		return () => removeEventListener('keydown', f);
	}, [mode, go1]);

	if (error) return <div class="page"><ErrorBox error={error} onRetry={() => location.reload()} /><a class="btn" href={titleHref(ctx.title.sourceId, ctx.title.url)}>Back to title</a></div>;
	if (!pages) return <Spinner label="Loading pages…" />;

	const bg = s.readerBackground === 'white' ? '#fff' : s.readerBackground === 'dark' ? '#16181c' : '#000';
	return (
		<div class="reader" style={{ background: bg }}>
			{ui && (
				<header class="reader-bar top">
					<button class="btn small" onClick={back} aria-label="Back">←</button>
					<div class="grow ellipsis">
						<strong>{ctx.title.title}</strong>
						<div class="muted ellipsis">{ctx.chapterName}</div>
					</div>
					<button
						class="btn small"
						aria-label="Bookmark this page"
						onClick={() => {
							void addBookmark({ titleKey: ctx.title.key, title: ctx.title.title, cover: ctx.title.cover, sourceId: ctx.title.sourceId, titleUrl: ctx.title.url, chapterUrl: ctx.chapterUrl, chapterName: ctx.chapterName, page: idx }).then(() => toast('Bookmarked'));
						}}
					>
						🔖
					</button>
					<button class="btn small" onClick={() => setPanel(!panel)} aria-label="Reader settings">⚙</button>
				</header>
			)}
			{panel && <ReaderPanel onClose={() => setPanel(false)} />}
			{mode === 'webtoon' ? (
				<Webtoon pages={pages} start={idx} onPage={setIdx} onToggle={() => setUi((u) => !u)} fit={s.readerFit} next={next} open={open} />
			) : (
				<div
					class="pager"
					onClick={(e) => {
						const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
						const x = (e.clientX - r.left) / r.width;
						const rtl = mode === 'paged-rtl';
						if (x < 0.3) go1(rtl ? 1 : -1);
						else if (x > 0.7) go1(rtl ? -1 : 1);
						else setUi((u) => !u);
					}}
					onTouchStart={(e) => ((e.currentTarget as any)._x = e.touches[0].clientX)}
					onTouchEnd={(e) => {
						const dx = e.changedTouches[0].clientX - ((e.currentTarget as any)._x ?? 0);
						if (Math.abs(dx) > 70) go1((dx < 0 ? 1 : -1) * (mode === 'paged-rtl' ? -1 : 1));
					}}
				>
					<PageImage key={idx} p={pages[idx]} fit={s.readerFit} />
				</div>
			)}
			{ui && (
				<footer class="reader-bar bottom">
					<button class="btn small" disabled={!prev} onClick={() => open(prev)}>⏮</button>
					<span class="muted">{mode === 'webtoon' ? `${idx + 1}` : idx + 1} / {count}</span>
					<input
						type="range"
						min={0}
						max={Math.max(0, count - 1)}
						value={idx}
						dir={mode === 'paged-rtl' ? 'rtl' : 'ltr'}
						class="grow"
						aria-label="Page"
						onInput={(e) => setIdx(Number((e.target as HTMLInputElement).value))}
					/>
					<button class="btn small" disabled={!next} onClick={() => open(next)}>⏭</button>
				</footer>
			)}
		</div>
	);
}

function PageImage({ p, fit }: { p: PageItem; fit: string }) {
	const [state, setState] = useState<'loading' | 'ok' | 'err'>('loading');
	const [n, setN] = useState(0);
	return (
		<div class={'page-wrap fit-' + fit}>
			{state === 'loading' && <div class="spinner" />}
			{state === 'err' && (
				<button class="btn" onClick={(e) => (e.stopPropagation(), setState('loading'), setN(n + 1))}>
					Failed to load · Retry
				</button>
			)}
			<img
				key={n}
				src={p.blob ? p.url : mediaUrl(p.url, p.headers)}
				referrerpolicy="no-referrer"
				alt=""
				draggable={false}
				style={{ display: state === 'ok' ? 'block' : 'none' }}
				onLoad={() => setState('ok')}
				onError={() => setState('err')}
			/>
		</div>
	);
}

function Webtoon({ pages, start, onPage, onToggle, fit, next, open }: { pages: PageItem[]; start: number; onPage: (i: number) => void; onToggle: () => void; fit: string; next?: Chapter; open: (c?: Chapter) => void }) {
	const root = useRef<HTMLDivElement>(null);
	useEffect(() => {
		const el = root.current?.children[start] as HTMLElement | undefined;
		el?.scrollIntoView();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);
	useEffect(() => {
		const el = root.current;
		if (!el) return;
		const io = new IntersectionObserver(
			(es) => {
				for (const e of es) if (e.isIntersecting) onPage(Number((e.target as HTMLElement).dataset.i));
			},
			{ root: el, threshold: 0.5 },
		);
		Array.from(el.children).forEach((c) => (c as HTMLElement).dataset.i && io.observe(c));
		return () => io.disconnect();
	}, [pages]);
	return (
		<div class="webtoon" ref={root} onClick={onToggle}>
			{pages.map((p, i) => (
				<div key={i} data-i={i}>
					<PageImage p={p} fit={fit === 'height' ? 'width' : fit} />
				</div>
			))}
			<div class="center">
				{next ? (
					<button class="btn primary" onClick={(e) => (e.stopPropagation(), open(next))}>
						Next chapter
					</button>
				) : (
					<span class="muted">The end</span>
				)}
			</div>
		</div>
	);
}

function ReaderPanel({ onClose }: { onClose: () => void }) {
	const s = useSettings();
	const modes: [ReaderMode, string][] = [
		['paged-rtl', 'Right to left'],
		['paged-ltr', 'Left to right'],
		['webtoon', 'Webtoon (scroll)'],
	];
	return (
		<div class="panel" onClick={(e) => e.stopPropagation()}>
			<div class="row">
				<strong class="grow">Reader</strong>
				<button class="btn small" onClick={onClose}>✕</button>
			</div>
			<label class="field">
				<span>Mode</span>
				<select class="input" value={s.readerMode} onChange={(e) => updateSettings({ readerMode: (e.target as HTMLSelectElement).value as ReaderMode })}>
					{modes.map(([v, l]) => (
						<option value={v}>{l}</option>
					))}
				</select>
			</label>
			<label class="field">
				<span>Fit</span>
				<select class="input" value={s.readerFit} onChange={(e) => updateSettings({ readerFit: (e.target as HTMLSelectElement).value as any })}>
					<option value="contain">Fit screen</option>
					<option value="width">Fit width</option>
					<option value="height">Fit height</option>
				</select>
			</label>
			<label class="field">
				<span>Background</span>
				<select class="input" value={s.readerBackground} onChange={(e) => updateSettings({ readerBackground: (e.target as HTMLSelectElement).value as any })}>
					<option value="black">Black</option>
					<option value="dark">Dark grey</option>
					<option value="white">White</option>
				</select>
			</label>
			<div class="muted">Keys: ← → pages · Space next · M menu · Esc back</div>
		</div>
	);
}
void replaceSettings;

// ---------------------------------------------------------------- novel

function NovelReader({ ctx, startPage }: { ctx: Ctx; startPage: number }) {
	const s = useSettings();
	const { prev, next, open } = useNav(ctx);
	const [html, setHtml] = useState<string | null>(null);
	const [error, setError] = useState('');
	const [ui, setUi] = useState(true);
	const [panel, setPanel] = useState(false);
	const box = useRef<HTMLDivElement>(null);
	const save = useProgress(ctx);

	useEffect(() => {
		let alive = true;
		void (async () => {
			try {
				const dl = await getDownload(ctx.title.key, ctx.chapterUrl);
				let raw = dl?.html;
				if (raw == null) {
					const ext = await getExtension(ctx.title.sourceId);
					raw = await ext.getHtmlContent(ctx.chapterName, ctx.chapterUrl);
					if (!raw.trim()) throw new Error('The source returned an empty chapter.');
				}
				if (alive) setHtml(sanitizeHtml(raw));
			} catch (e: any) {
				if (alive) setError(e?.message ?? String(e));
			}
		})();
		return () => {
			alive = false;
		};
	}, [ctx.chapterUrl]);

	useEffect(() => {
		const el = box.current;
		if (!el || html == null) return;
		if (startPage) el.scrollTop = (startPage / 1000) * (el.scrollHeight - el.clientHeight);
		let pct = startPage;
		save(pct, 1000, false); // opening a chapter already counts as history
		const f = () => {
			const max = el.scrollHeight - el.clientHeight;
			pct = max > 0 ? Math.round((el.scrollTop / max) * 1000) : 1000;
			save(pct, 1000, pct >= 985);
		};
		el.addEventListener('scroll', f, { passive: true });
		return () => el.removeEventListener('scroll', f);
	}, [html]);

	if (error) return <div class="page"><ErrorBox error={error} onRetry={() => location.reload()} /><a class="btn" href={titleHref(ctx.title.sourceId, ctx.title.url)}>Back to title</a></div>;
	if (html == null) return <Spinner label="Loading chapter…" />;
	return (
		<div class={'reader novel theme-' + s.novelTheme}>
			{ui && (
				<header class="reader-bar top">
					<button class="btn small" onClick={back} aria-label="Back">←</button>
					<div class="grow ellipsis">
						<strong>{ctx.title.title}</strong>
						<div class="muted ellipsis">{ctx.chapterName}</div>
					</div>
					<button class="btn small" onClick={() => setPanel(!panel)} aria-label="Text settings">Aa</button>
				</header>
			)}
			{panel && (
				<div class="panel" onClick={(e) => e.stopPropagation()}>
					<label class="field">
						<span>Font size {s.novelFontSize}px</span>
						<input type="range" min={12} max={34} value={s.novelFontSize} onInput={(e) => updateSettings({ novelFontSize: Number((e.target as HTMLInputElement).value) })} />
					</label>
					<label class="field">
						<span>Line height {s.novelLineHeight.toFixed(1)}</span>
						<input type="range" min={12} max={26} value={s.novelLineHeight * 10} onInput={(e) => updateSettings({ novelLineHeight: Number((e.target as HTMLInputElement).value) / 10 })} />
					</label>
					<label class="field">
						<span>Font</span>
						<select class="input" value={s.novelFont} onChange={(e) => updateSettings({ novelFont: (e.target as HTMLSelectElement).value as any })}>
							<option value="serif">Serif</option>
							<option value="sans">Sans-serif</option>
						</select>
					</label>
					<label class="field">
						<span>Theme</span>
						<select class="input" value={s.novelTheme} onChange={(e) => updateSettings({ novelTheme: (e.target as HTMLSelectElement).value as any })}>
							<option value="dark">Dark</option>
							<option value="sepia">Sepia</option>
							<option value="light">Light</option>
						</select>
					</label>
				</div>
			)}
			<div class="novel-scroll" ref={box} onClick={() => setUi((u) => !u)}>
				<article
					class="novel-text"
					style={{ fontSize: s.novelFontSize + 'px', lineHeight: s.novelLineHeight, fontFamily: s.novelFont === 'serif' ? 'Georgia, "Times New Roman", serif' : 'system-ui, sans-serif' }}
					dangerouslySetInnerHTML={{ __html: html }}
				/>
				<div class="row center wrap" onClick={(e) => e.stopPropagation()}>
					<button class="btn" disabled={!prev} onClick={() => open(prev)}>← Previous</button>
					<button class="btn primary" disabled={!next} onClick={() => { void markRead(ctx.title.key, ctx.chapterUrl); open(next); }}>Next →</button>
				</div>
			</div>
		</div>
	);
}

// ---------------------------------------------------------------- video

function VideoReader({ ctx, startPage }: { ctx: Ctx; startPage: number }) {
	const { prev, next, open } = useNav(ctx);
	const [videos, setVideos] = useState<JsVideo[] | null>(null);
	const [sel, setSel] = useState(0);
	const [error, setError] = useState('');
	const ref = useRef<HTMLVideoElement>(null);
	const save = useProgress(ctx);

	useEffect(() => {
		let alive = true;
		void (async () => {
			try {
				const ext = await getExtension(ctx.title.sourceId);
				const list = await ext.getVideoList(ctx.chapterUrl);
				if (!list.length) throw new Error(ext.lastNetworkProblem ? `No streams (${ext.lastNetworkProblem})` : 'The source returned no playable streams.');
				if (alive) setVideos(list);
			} catch (e: any) {
				if (alive) setError(e?.message ?? String(e));
			}
		})();
		return () => {
			alive = false;
		};
	}, [ctx.chapterUrl]);

	const v = videos?.[sel];
	useEffect(() => {
		const el = ref.current;
		if (!el || !v) return;
		const url = mediaUrl(v.url, v.headers);
		let hls: Hls | null = null;
		const seek = () => startPage > 0 && (el.currentTime = startPage);
		if (/\.m3u8(\?|$)/i.test(v.url) && Hls.isSupported()) {
			hls = new Hls({ enableWorker: true });
			hls.loadSource(url);
			hls.attachMedia(el);
		} else el.src = url;
		el.addEventListener('loadedmetadata', seek, { once: true });
		return () => {
			hls?.destroy();
			el.removeAttribute('src');
			el.load();
		};
	}, [v?.url]);

	if (error) return <div class="page"><ErrorBox error={error} onRetry={() => location.reload()} /><a class="btn" href={titleHref(ctx.title.sourceId, ctx.title.url)}>Back to title</a></div>;
	if (!videos || !v) return <Spinner label="Finding streams…" />;
	return (
		<div class="reader video">
			<header class="reader-bar top static">
				<button class="btn small" onClick={back} aria-label="Back">←</button>
				<div class="grow ellipsis">
					<strong>{ctx.title.title}</strong>
					<div class="muted ellipsis">{ctx.chapterName}</div>
				</div>
			</header>
			<video
				ref={ref}
				controls
				autoplay
				playsInline
				onTimeUpdate={(e) => {
					const el = e.currentTarget;
					if (el.duration) save(Math.floor(el.currentTime), Math.floor(el.duration), el.currentTime / el.duration > 0.9);
				}}
				onEnded={() => next && open(next)}
			>
				{v.subtitles.filter((t) => t.file).map((t, i) => (
					<track key={i} kind="subtitles" src={mediaUrl(t.file!, v.headers)} label={t.label || `Subtitle ${i + 1}`} default={i === 0} />
				))}
			</video>
			<div class="row wrap">
				<select class="input" value={sel} onChange={(e) => setSel(Number((e.target as HTMLSelectElement).value))} aria-label="Stream">
					{videos.map((x, i) => (
						<option value={i}>{x.quality || `Stream ${i + 1}`}</option>
					))}
				</select>
				<button class="btn" disabled={!prev} onClick={() => open(prev)}>⏮ Previous</button>
				<button class="btn" disabled={!next} onClick={() => open(next)}>Next ⏭</button>
			</div>
			<div class="muted">Streams that need special request headers play best in the Windows app.</div>
		</div>
	);
}
void useMemo;
