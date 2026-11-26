import type { ComponentChildren, JSX } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import { mediaUrl } from '../core/http';
import type { Title } from '../core/types';
import { titleHref } from './router';
import { rememberTitle } from '../core/titles';

export function Spinner({ label }: { label?: string }) {
	return (
		<div class="center muted" role="status">
			<div class="spinner" />
			{label && <div>{label}</div>}
		</div>
	);
}

export function Empty({ title, children }: { title: string; children?: ComponentChildren }) {
	return (
		<div class="empty">
			<div class="empty-art">物語</div>
			<h2>{title}</h2>
			<div class="muted">{children}</div>
		</div>
	);
}

export function ErrorBox({ error, onRetry }: { error: string; onRetry?: () => void }) {
	return (
		<div class="error-box" role="alert">
			<div>{error}</div>
			{onRetry && (
				<button class="btn" onClick={onRetry}>
					Retry
				</button>
			)}
		</div>
	);
}

export function Cover({ url, title, headers }: { url?: string | null; title: string; headers?: Record<string, string> | null }) {
	const [failed, setFailed] = useState(false);
	if (!url || failed) return <div class="cover placeholder">{title.slice(0, 2)}</div>;
	return <img class="cover" src={mediaUrl(url, headers)} alt="" loading="lazy" referrerpolicy="no-referrer" onError={() => setFailed(true)} />;
}

export function TitleCard({ t, badge, sub }: { t: Pick<Title, 'sourceId' | 'url' | 'title' | 'cover'>; badge?: ComponentChildren; sub?: string }) {
	rememberTitle(t.sourceId, t.url, t.title, t.cover);
	return (
		<a class="card" href={titleHref(t.sourceId, t.url)} title={t.title}>
			<div class="card-cover">
				<Cover url={t.cover} title={t.title} />
				{badge != null && badge !== false && <span class="badge">{badge}</span>}
			</div>
			<div class="card-title">{t.title}</div>
			{sub && <div class="card-sub muted">{sub}</div>}
		</a>
	);
}

export function Grid({ children }: { children: ComponentChildren }) {
	return <div class="grid">{children}</div>;
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ComponentChildren }) {
	useEffect(() => {
		const f = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
		addEventListener('keydown', f);
		return () => removeEventListener('keydown', f);
	}, [onClose]);
	return (
		<div class="modal-back" onClick={onClose}>
			<div class="modal" role="dialog" aria-label={title} onClick={(e) => e.stopPropagation()}>
				<h3>{title}</h3>
				{children}
			</div>
		</div>
	);
}

type ToastMsg = { id: number; text: string };
let toastId = 0;
const toastSubs = new Set<(t: ToastMsg) => void>();
export const toast = (text: string) => toastSubs.forEach((f) => f({ id: ++toastId, text }));

export function Toasts() {
	const [items, setItems] = useState<ToastMsg[]>([]);
	useEffect(() => {
		const f = (t: ToastMsg) => {
			setItems((x) => [...x, t]);
			setTimeout(() => setItems((x) => x.filter((i) => i.id !== t.id)), 3500);
		};
		toastSubs.add(f);
		return () => void toastSubs.delete(f);
	}, []);
	return (
		<div class="toasts" aria-live="polite">
			{items.map((t) => (
				<div class="toast" key={t.id}>
					{t.text}
				</div>
			))}
		</div>
	);
}

export function Page({ title, actions, children }: { title: string; actions?: ComponentChildren; children: ComponentChildren }) {
	return (
		<section class="page">
			<header class="page-head">
				<h1>{title}</h1>
				<div class="spacer" />
				{actions}
			</header>
			{children}
		</section>
	);
}

export function Tabs<T extends string>({ value, onChange, items }: { value: T; onChange: (v: T) => void; items: { id: T; label: string }[] }) {
	return (
		<div class="tabs" role="tablist">
			{items.map((i) => (
				<button key={i.id} role="tab" aria-selected={i.id === value} class={'tab' + (i.id === value ? ' active' : '')} onClick={() => onChange(i.id)}>
					{i.label}
				</button>
			))}
		</div>
	);
}

export function SearchBox(props: JSX.InputHTMLAttributes<HTMLInputElement>) {
	return <input class="input" type="search" {...props} />;
}

export function timeAgo(ts: number): string {
	const s = Math.max(1, Math.round((Date.now() - ts) / 1000));
	if (s < 60) return 'just now';
	const units: [number, string][] = [
		[60, 'min'],
		[3600, 'h'],
		[86400, 'd'],
		[2592000, 'mo'],
		[31536000, 'y'],
	];
	let label = `${s}s`;
	let prev = 1;
	for (const [n, u] of units) {
		if (s >= n) {
			label = `${Math.floor(s / n)}${u}`;
			prev = n;
		}
	}
	void prev;
	return label + ' ago';
}
