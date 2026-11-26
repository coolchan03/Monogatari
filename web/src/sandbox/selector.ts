/**
 * Mangayomi's selector dialect is modelled on Jsoup's. The browser's native CSS engine covers almost all of it;
 * this module adds the Jsoup-only pseudo selectors (:contains, :containsOwn, :matches, :eq, :lt, :gt, ...).
 */

const JSOUP_PSEUDO = /:(contains|containsOwn|containsData|containsWholeText|containsWholeOwnText|matches|matchesOwn|matchesWholeText|matchesWholeOwnText|eq|lt|gt|even|odd)\b/;
const PSEUDO_CALL = /:(containsOwn|containsData|containsWholeText|containsWholeOwnText|contains|matchesOwn|matchesWholeText|matchesWholeOwnText|matches|eq|lt|gt)\(((?:[^()\\]|\\.|\([^()]*\))*)\)/g;

type Filter = { kind: string; arg: string };
type Compound = { combinator: ' ' | '>' | '+' | '~'; css: string; filters: Filter[] };

export function normalizeText(s: string): string {
	return s.replace(/\s+/g, ' ').trim();
}

export function ownText(el: Element): string {
	let out = '';
	el.childNodes.forEach((n) => {
		if (n.nodeType === 3) out += n.textContent ?? '';
	});
	return normalizeText(out);
}

/** Like Jsoup's Element.text(): whitespace-normalised text of the element and its descendants. */
export function elementText(el: Element): string {
	return normalizeText(el.textContent ?? '');
}

export function select(root: ParentNode, selector: string): Element[] {
	selector = selector.trim();
	if (!selector) return [];
	try {
		if (!JSOUP_PSEUDO.test(selector)) return Array.from(root.querySelectorAll(selector));
		return selectAdvanced(root, selector);
	} catch {
		// An invalid selector yields "nothing found", like upstream's try/catch.
		return [];
	}
}

export function selectFirst(root: ParentNode, selector: string): Element | null {
	return select(root, selector)[0] ?? null;
}

function splitTopLevel(input: string, isSep: (ch: string) => boolean): string[] {
	const parts: string[] = [];
	let depth = 0;
	let quote = '';
	let start = 0;
	for (let i = 0; i < input.length; i++) {
		const ch = input[i];
		if (ch === '\\') {
			i++;
			continue;
		}
		if (quote) {
			if (ch === quote) quote = '';
			continue;
		}
		if (ch === '"' || ch === "'") quote = ch;
		else if (ch === '(' || ch === '[') depth++;
		else if (ch === ')' || ch === ']') depth--;
		else if (depth === 0 && isSep(ch)) {
			parts.push(input.slice(start, i));
			start = i + 1;
		}
	}
	parts.push(input.slice(start));
	return parts;
}

function parseGroup(group: string): Compound[] {
	// Normalise combinators so that "a > b" and "a>b" tokenise the same way.
	const tokens: Compound[] = [];
	let depth = 0;
	let quote = '';
	let cur = '';
	let pending: Compound['combinator'] = ' ';
	const flush = () => {
		if (cur.trim()) tokens.push(buildCompound(pending, cur.trim()));
		cur = '';
		pending = ' ';
	};
	for (let i = 0; i < group.length; i++) {
		const ch = group[i];
		if (ch === '\\') {
			cur += ch + (group[++i] ?? '');
			continue;
		}
		if (quote) {
			cur += ch;
			if (ch === quote) quote = '';
			continue;
		}
		if (ch === '"' || ch === "'") {
			quote = ch;
			cur += ch;
		} else if (ch === '(' || ch === '[') {
			depth++;
			cur += ch;
		} else if (ch === ')' || ch === ']') {
			depth--;
			cur += ch;
		} else if (depth === 0 && (ch === '>' || ch === '+' || ch === '~')) {
			flush();
			pending = ch;
		} else if (depth === 0 && /\s/.test(ch)) {
			if (cur.trim()) {
				const comb = pending;
				flush();
				pending = ' ';
				void comb;
			}
		} else cur += ch;
	}
	flush();
	return tokens;
}

function buildCompound(combinator: Compound['combinator'], text: string): Compound {
	const filters: Filter[] = [];
	let css = text.replace(PSEUDO_CALL, (_m, kind: string, arg: string) => {
		filters.push({ kind, arg: unquote(arg) });
		return '';
	});
	css = css.replace(/:(even|odd)\b/g, (_m, kind: string) => {
		filters.push({ kind, arg: '' });
		return '';
	});
	return { combinator, css: css || '*', filters };
}

function unquote(s: string): string {
	s = s.trim();
	if (s.length >= 2 && (s[0] === '"' || s[0] === "'") && s[s.length - 1] === s[0]) s = s.slice(1, -1);
	return s.replace(/\\(.)/g, '$1');
}

function siblingIndex(el: Element): number {
	let i = 0;
	let p = el.previousElementSibling;
	while (p) {
		i++;
		p = p.previousElementSibling;
	}
	return i;
}

function passes(el: Element, filters: Filter[]): boolean {
	for (const f of filters) {
		switch (f.kind) {
			case 'contains':
				if (!elementText(el).toLowerCase().includes(f.arg.toLowerCase())) return false;
				break;
			case 'containsOwn':
				if (!ownText(el).toLowerCase().includes(f.arg.toLowerCase())) return false;
				break;
			case 'containsData':
				if (!Array.from(el.childNodes).some((n) => n.nodeType === 3 && (n.textContent ?? '').toLowerCase().includes(f.arg.toLowerCase()))) return false;
				break;
			case 'containsWholeText':
				if (!(el.textContent ?? '').includes(f.arg)) return false;
				break;
			case 'containsWholeOwnText':
				if (!Array.from(el.childNodes).some((n) => n.nodeType === 3 && (n.textContent ?? '').includes(f.arg))) return false;
				break;
			case 'matches':
				if (!new RegExp(f.arg).test(elementText(el))) return false;
				break;
			case 'matchesOwn':
				if (!new RegExp(f.arg).test(ownText(el))) return false;
				break;
			case 'matchesWholeText':
				if (!new RegExp(f.arg).test(el.textContent ?? '')) return false;
				break;
			case 'matchesWholeOwnText':
				if (!new RegExp(f.arg).test(Array.from(el.childNodes).filter((n) => n.nodeType === 3).map((n) => n.textContent).join(''))) return false;
				break;
			case 'eq':
				if (siblingIndex(el) !== Number(f.arg)) return false;
				break;
			case 'lt':
				if (!(siblingIndex(el) < Number(f.arg))) return false;
				break;
			case 'gt':
				if (!(siblingIndex(el) > Number(f.arg))) return false;
				break;
			case 'even':
				if (siblingIndex(el) % 2 !== 0) return false;
				break;
			case 'odd':
				if (siblingIndex(el) % 2 !== 1) return false;
				break;
		}
	}
	return true;
}

function selectAdvanced(root: ParentNode, selector: string): Element[] {
	const found = new Set<Element>();
	for (const group of splitTopLevel(selector, (c) => c === ',')) {
		const compounds = parseGroup(group);
		if (compounds.length === 0) continue;
		let current: Element[] = [];
		compounds.forEach((c, index) => {
			let candidates: Element[];
			if (index === 0) {
				candidates = Array.from(root.querySelectorAll(c.css));
			} else {
				const next = new Set<Element>();
				for (const el of current) {
					if (c.combinator === ' ') el.querySelectorAll(c.css).forEach((e) => next.add(e));
					else if (c.combinator === '>') Array.from(el.children).forEach((e) => e.matches(c.css) && next.add(e));
					else if (c.combinator === '+') {
						const sib = el.nextElementSibling;
						if (sib?.matches(c.css)) next.add(sib);
					} else {
						let sib = el.nextElementSibling;
						while (sib) {
							if (sib.matches(c.css)) next.add(sib);
							sib = sib.nextElementSibling;
						}
					}
				}
				candidates = Array.from(next);
			}
			current = candidates.filter((e) => passes(e, c.filters));
		});
		current.forEach((e) => found.add(e));
	}
	const list = Array.from(found);
	if (list.length > 1) {
		list.sort((a, b) => (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1));
	}
	return list;
}
