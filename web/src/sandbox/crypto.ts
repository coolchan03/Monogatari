import { cbc, gcm } from '@noble/ciphers/aes';
import { md5 } from '@noble/hashes/legacy';
import { randomBytes } from '@noble/hashes/utils';

const enc = new TextEncoder();
const dec = new TextDecoder();

export function b64decode(s: string): Uint8Array {
	const bin = atob(s.replace(/\s+/g, '').replace(/-/g, '+').replace(/_/g, '/'));
	const out = new Uint8Array(bin.length);
	for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
	return out;
}

export function b64encode(bytes: Uint8Array): string {
	let s = '';
	for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
	return btoa(s);
}

function hexDecode(s: string): Uint8Array {
	const clean = s.trim().toLowerCase();
	if (clean.length % 2 || /[^0-9a-f]/.test(clean)) throw new Error('bad hex');
	const out = new Uint8Array(clean.length / 2);
	for (let i = 0; i < out.length; i++) out[i] = parseInt(clean.substr(i * 2, 2), 16);
	return out;
}

function concat(...parts: Uint8Array[]): Uint8Array {
	const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
	let o = 0;
	for (const p of parts) {
		out.set(p, o);
		o += p.length;
	}
	return out;
}

/** AES/CBC/PKCS7 with UTF-8 key and IV; on any failure the input comes back, like upstream. */
export function cryptoHandler(text: string, iv: string, key: string, encrypt: boolean): string {
	try {
		const cipher = cbc(enc.encode(key), enc.encode(iv));
		if (encrypt) return b64encode(cipher.encrypt(enc.encode(text)));
		return dec.decode(cipher.decrypt(b64decode(text)));
	} catch {
		return text;
	}
}

/** OpenSSL EVP_BytesToKey with MD5 and one iteration, as used by CryptoJS. */
function evpBytesToKey(password: Uint8Array, salt: Uint8Array): { key: Uint8Array; iv: Uint8Array } {
	const out = new Uint8Array(48);
	let generated = 0;
	let block: Uint8Array = new Uint8Array(0);
	while (generated < out.length) {
		block = md5(concat(block, password, salt));
		const n = Math.min(block.length, out.length - generated);
		out.set(block.subarray(0, n), generated);
		generated += n;
	}
	return { key: out.slice(0, 32), iv: out.slice(32, 48) };
}

export function decryptAesCryptoJs(encrypted: string, passphrase: string): string {
	try {
		const data = b64decode(encrypted.trim());
		if (data.length <= 16 || dec.decode(data.subarray(0, 8)) !== 'Salted__') throw new Error('Missing salt header');
		const { key, iv } = evpBytesToKey(enc.encode(passphrase), data.subarray(8, 16));
		return dec.decode(cbc(key, iv).decrypt(data.subarray(16)));
	} catch {
		return '';
	}
}

export function encryptAesCryptoJs(plain: string, passphrase: string): string {
	try {
		const salt = randomBytes(8);
		const { key, iv } = evpBytesToKey(enc.encode(passphrase), salt);
		const body = cbc(key, iv).encrypt(enc.encode(plain));
		return b64encode(concat(enc.encode('Salted__'), salt, body));
	} catch {
		return '';
	}
}

/** AES-GCM, base64 ciphertext, hex key/IV/tag. An empty tag means it is appended to the ciphertext. */
export function decryptAesGcm(encrypted: string, keyHex: string, ivHex: string, tagHex: string): string {
	try {
		const body = b64decode(encrypted.trim());
		const key = hexDecode(keyHex);
		if (![16, 24, 32].includes(key.length)) throw new Error('Bad key size');
		const tag = tagHex.trim() ? hexDecode(tagHex) : new Uint8Array(0);
		return dec.decode(gcm(key, hexDecode(ivHex)).decrypt(concat(body, tag)));
	} catch {
		return encrypted;
	}
}

/** Decodes JSFuck-style digit passwords such as `[+!+[]+!+[]]+(...)`. Port of Aniyomi's Deobfuscator. */
export function deobfuscateJsPassword(input: string): string {
	let result = '';
	let idx = 0;
	const matching = (opening: number): number => {
		const open = input[opening];
		const close = open === '[' ? ']' : ')';
		let depth = 0;
		for (let i = opening; i < input.length; i++) {
			if (input[i] === open) depth++;
			else if (input[i] === close) depth--;
			if (depth === 0) return i;
			if (depth < 0) return -1;
		}
		return -1;
	};
	while (idx < input.length) {
		const chr = input[idx];
		if (chr !== '[' && chr !== '(') {
			idx++;
			continue;
		}
		const closing = matching(idx);
		if (closing < 0) break;
		if (chr === '[') {
			const group = input.slice(idx, closing);
			const count = (group.match(/!\+\[\]/g) ?? []).length;
			if (count === 0 && (group.match(/\+\[\]/g) ?? []).length === 1) result += '0';
			else if (count >= 1 && count <= 9) result += String(count);
			else result += '-';
		} else {
			result += '.';
			if (input[closing + 1] === '[') {
				const skip = matching(closing + 1);
				if (skip < 0) break;
				idx = skip + 1;
				continue;
			}
		}
		idx = closing + 1;
	}
	return result;
}
