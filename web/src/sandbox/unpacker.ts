/** Unpacker for Dean Edwards' `eval(function(p,a,c,k,e,d){...})` packer, used by many video hosts. */

const PACKED_ARGS = /}\s*\(\s*'((?:[^'\\]|\\.)*)'\s*,\s*(\d+|\[\])\s*,\s*(\d+)\s*,\s*'((?:[^'\\]|\\.)*)'\s*\.split\(\s*'\|'\s*\)/gs;
const ALPHABET_62 = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
const ALPHABET_95 = ' !"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~';

const unescapeJs = (s: string) => s.replace(/\\\\/g, '\u0000').replace(/\\'/g, "'").replace(/\u0000/g, '\\');

function unbase(word: string, radix: number): number {
	if (radix <= 36) {
		const n = parseInt(word, radix);
		return Number.isNaN(n) ? -1 : n;
	}
	const alphabet = radix > 62 ? ALPHABET_95 : ALPHABET_62;
	let value = 0;
	for (const c of word) {
		const d = alphabet.indexOf(c);
		if (d < 0 || d >= radix) return -1;
		value = value * radix + d;
	}
	return value;
}

function unpackMatch(m: RegExpMatchArray): string | null {
	const payload = unescapeJs(m[1]);
	const radix = parseInt(m[2], 10) || 62;
	if (Number.isNaN(parseInt(m[3], 10))) return null;
	const symbols = unescapeJs(m[4]).split('|');
	return payload.replace(/\b\w+\b/g, (word) => {
		const index = unbase(word, radix);
		return (index >= 0 && symbols[index]) || word;
	});
}

export function unpack(packed: string): string | null {
	const m = Array.from(packed.matchAll(PACKED_ARGS))[0];
	return m ? unpackMatch(m) : null;
}

export function unpackAndCombine(script: string): string | null {
	const parts = Array.from(script.matchAll(PACKED_ARGS))
		.map(unpackMatch)
		.filter((x): x is string => x != null);
	return parts.length ? parts.join(' ') : null;
}
