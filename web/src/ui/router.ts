import { useEffect, useState } from 'preact/hooks';

export interface Route {
	path: string[];
	query: URLSearchParams;
}

function parse(): Route {
	const raw = location.hash.replace(/^#/, '') || '/';
	const [p, q = ''] = raw.split('?');
	return { path: p.split('/').filter(Boolean).map(decodeURIComponent), query: new URLSearchParams(q) };
}

export function useRoute(): Route {
	const [route, setRoute] = useState(parse);
	useEffect(() => {
		const f = () => setRoute(parse());
		addEventListener('hashchange', f);
		return () => removeEventListener('hashchange', f);
	}, []);
	return route;
}

export const href = (...parts: (string | number)[]) => '#/' + parts.map((p) => encodeURIComponent(String(p))).join('/');

export const go = (url: string, replace = false) => {
	if (replace) location.replace(url);
	else location.hash = url.replace(/^#/, '');
};

export const back = () => (history.length > 1 ? history.back() : go('#/'));

export const titleHref = (sourceId: number, url: string) => href('title', sourceId, url);
export const readHref = (sourceId: number, titleUrl: string, chapterUrl: string, page = 0) =>
	href('read', sourceId, titleUrl, chapterUrl) + (page ? `?p=${page}` : '');
