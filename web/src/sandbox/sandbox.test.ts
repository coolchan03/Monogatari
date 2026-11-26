import { describe, expect, it } from 'vitest';
import { select } from './selector';
import { parseDate } from './dates';
import { unpack } from './unpacker';
import * as C from './crypto';

const doc = (html: string) => new DOMParser().parseFromString(html, 'text/html');

describe('jsoup selector dialect', () => {
	const d = doc(`<div id=a><p class=x>Hello <b>World</b></p><p>Other</p><ul><li>1</li><li>2</li><li>3</li></ul></div>`);
	it('handles native CSS', () => expect(select(d, 'p.x').length).toBe(1));
	it(':contains is case-insensitive over all text', () => expect(select(d, 'p:contains(hello world)').length).toBe(1));
	it(':containsOwn only looks at own text', () => {
		expect(select(d, 'p:containsOwn(World)').length).toBe(0);
		expect(select(d, 'p:containsOwn(Hello)').length).toBe(1);
	});
	it(':eq uses the sibling index', () => expect(select(d, 'li:eq(1)')[0].textContent).toBe('2'));
	it('supports combinators after jsoup pseudos', () => expect(select(d, 'div:has(p) > ul li:gt(0)').map((e) => e.textContent)).toEqual(['2', '3']));
	it('returns [] for invalid selectors', () => expect(select(d, '!!!')).toEqual([]));
});

describe('parseDate', () => {
	const now = Date.UTC(2026, 10, 26, 12);
	it('parses epoch seconds', () => expect(parseDate('1700000000', '', '', now)).toBe(1700000000000));
	it('parses relative phrases', () => expect(parseDate('3 days ago', '', '', now)).toBe(now - 3 * 86400000));
	it('parses an explicit pattern', () => {
		const t = parseDate('March 5, 2024', 'MMMM d, yyyy', 'en', now)!;
		expect(new Date(t).getFullYear()).toBe(2024);
		expect(new Date(t).getMonth()).toBe(2);
		expect(new Date(t).getDate()).toBe(5);
	});
	it('learns common numeric formats', () => {
		const t = parseDate('2024-03-05', '', '', now)!;
		expect(new Date(t).getDate()).toBe(5);
	});
	it('parses ISO', () => expect(parseDate('2024-03-05T10:00:00Z', '', '', now)).toBe(Date.UTC(2024, 2, 5, 10)));
});

describe('helpers', () => {
	it('unpacks Dean Edwards packed scripts', () => {
		const packed = "eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\\\b'+c.toString(a)+'\\\\b','g'),k[c]);return p}('0 1=\"2\";',3,3,'var|x|hi'.split('|'),0,{}))";
		expect(unpack(packed)).toBe('var x="hi";');
	});
	it('round-trips CryptoJS style AES', () => {
		const enc = C.encryptAesCryptoJs('secret text', 'pass');
		expect(enc).not.toBe('');
		expect(C.decryptAesCryptoJs(enc, 'pass')).toBe('secret text');
	});
	it('cbc round trip', () => {
		const e = C.cryptoHandler('hello', '1234567890123456', '12345678901234567890123456789012', true);
		expect(C.cryptoHandler(e, '1234567890123456', '12345678901234567890123456789012', false)).toBe('hello');
	});
	it('deobfuscates JSFuck digits', () => expect(C.deobfuscateJsPassword('[!+[]+!+[]]+[!+[]+!+[]+!+[]]')).toBe('23'));
});
