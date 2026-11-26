import { useEffect, useState } from 'preact/hooks';

export type ThemeMode = 'system' | 'light' | 'dark' | 'amoled';
export type ReaderMode = 'paged-ltr' | 'paged-rtl' | 'webtoon';

export interface Settings {
	theme: ThemeMode;
	accent: string;
	readerMode: ReaderMode;
	readerFit: 'width' | 'height' | 'contain';
	readerBackground: 'black' | 'dark' | 'white';
	novelFontSize: number;
	novelFont: 'serif' | 'sans';
	novelTheme: 'dark' | 'sepia' | 'light';
	novelLineHeight: number;
	gridSize: number;
	showNsfw: boolean;
	proxyUrl: string;
	proxyMode: 'auto' | 'always' | 'never';
	proxyImages: boolean;
	preloadPages: number;
	lang: string;
}

export const DEFAULT_SETTINGS: Settings = {
	theme: 'system',
	accent: '#1976D2',
	readerMode: 'paged-rtl',
	readerFit: 'contain',
	readerBackground: 'black',
	novelFontSize: 18,
	novelFont: 'serif',
	novelTheme: 'dark',
	novelLineHeight: 1.7,
	gridSize: 150,
	showNsfw: false,
	proxyUrl: '',
	proxyMode: 'auto',
	proxyImages: false,
	preloadPages: 3,
	lang: '',
};

const KEY = 'monogatari.settings.v1';
let current: Settings = load();
const subs = new Set<() => void>();

function load(): Settings {
	try {
		return { ...DEFAULT_SETTINGS, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') };
	} catch {
		return { ...DEFAULT_SETTINGS };
	}
}

export function getSettings(): Settings {
	return current;
}

export function updateSettings(patch: Partial<Settings>) {
	current = { ...current, ...patch };
	try {
		localStorage.setItem(KEY, JSON.stringify(current));
	} catch {
		/* storage unavailable */
	}
	applyTheme();
	subs.forEach((f) => f());
}

export function replaceSettings(next: Partial<Settings>) {
	updateSettings({ ...DEFAULT_SETTINGS, ...next });
}

export function useSettings(): Settings {
	const [, tick] = useState(0);
	useEffect(() => {
		const f = () => tick((n) => n + 1);
		subs.add(f);
		return () => void subs.delete(f);
	}, []);
	return current;
}

export function applyTheme() {
	const root = document.documentElement;
	const dark = current.theme === 'dark' || current.theme === 'amoled' || (current.theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
	root.dataset.theme = dark ? (current.theme === 'amoled' ? 'amoled' : 'dark') : 'light';
	root.style.setProperty('--accent', current.accent);
	root.style.setProperty('--grid', current.gridSize + 'px');
	document.querySelector('meta[name=theme-color]')?.setAttribute('content', current.accent);
}

matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', applyTheme);
