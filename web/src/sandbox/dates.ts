/**
 * Mangayomi's `parseDates(values, dateFormat, locale)`: turns chapter date strings into epoch-millisecond strings.
 * Handles epoch numbers, ISO-8601, "today/yesterday", "3 days ago" phrases (several languages), an explicit
 * Java-style pattern and a set of common patterns. Unparseable values fall back to the time of the call.
 */

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

const TODAY = ['today', 'hoy', "aujourd'hui", 'oggi', 'heute', 'bugün', 'hari ini', 'اليوم', '今天', 'hôm nay', 'just now'];
const YESTERDAY = ['yesterday', 'ayer', 'hier', 'ieri', 'gestern', 'dün', 'kemarin', 'أمس', '昨天', 'hôm qua'];
const TWO_DAYS_AGO = ['يومين', 'anteayer', 'avant-hier', "l'altro ieri", 'vorgestern', '前天', 'hôm kia'];
const AGO_SUFFIXES = ['ago', 'atrás', 'önce', 'قبل', ' fa', 'vor', 'trước', '前', 'lalu', 'назад'];
const AGO_PREFIXES = ['hace', 'il y a', 'vor', 'há'];

const UNITS: [string[], number][] = [
	[['second', 'segundo', 'seconde', 'sekunde', 'detik', 'saniye', 'secondi', 'giây', 'วินาที', '秒', 'sec', 's'], 1000],
	[['minute', 'minuto', 'menit', 'dakika', 'minuti', 'phút', 'นาที', 'دقيقة', 'دقائق', '分钟', 'min', 'm'], MINUTE],
	[['hour', 'hora', 'heure', 'stunde', 'jam', 'saat', 'ore', 'giờ', 'ชั่วโมง', 'ساعة', '小时', 'hr', 'h'], HOUR],
	[['day', 'día', 'dia', 'jour', 'tag', 'hari', 'gün', 'giorn', 'ngày', 'วัน', 'يوم', 'أيام', '天', 'd'], DAY],
	[['week', 'semana', 'semaine', 'woche', 'settimana', 'minggu', 'hafta', 'tuần', 'สัปดาห์', '周', 'w'], 7 * DAY],
	[['month', 'mes', 'mois', 'monat', 'mese', 'bulan', 'ay ', 'tháng', 'เดือน', '月', 'mo'], 30 * DAY],
	[['year', 'año', 'ans', 'ano', 'jahr', 'anno', 'tahun', 'yıl', 'năm', 'ปี', '年', 'y'], 365 * DAY],
];

const NUMERIC_FORMATS = [
	'yyyy-MM-dd HH:mm:ss', 'yyyy-MM-dd HH:mm', 'yyyy-MM-dd', 'yyyy/MM/dd', 'yyyy.MM.dd',
	'dd/MM/yyyy', 'MM/dd/yyyy', 'dd-MM-yyyy', 'MM-dd-yyyy', 'dd.MM.yyyy',
	'dd/MM/yy', 'MM/dd/yy', 'dd.MM.yy', 'yyyyMMdd',
];
const TEXTUAL_FORMATS = [
	'MMMM d, yyyy', 'MMMM dd, yyyy', 'MMM d, yyyy', 'MMM dd, yyyy', 'MMM. d, yyyy',
	'd MMMM yyyy', 'dd MMMM yyyy', 'd MMM yyyy', 'dd MMM yyyy', 'MMMM d yyyy', 'MMM d yyyy',
	'EEEE, MMMM d, yyyy', 'EEE, d MMM yyyy', 'dd MMM, yyyy', 'd MMM, yyyy', 'MMMM yyyy',
];
const COMMON_LOCALES = ['es', 'fr', 'pt', 'de', 'it', 'id', 'tr', 'ru', 'vi'];

const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

function localeNames(locale: string, style: 'long' | 'short', kind: 'month' | 'weekday'): string[] {
	const out: string[] = [];
	try {
		const f = new Intl.DateTimeFormat(locale || 'en', { [kind]: style, timeZone: 'UTC' } as Intl.DateTimeFormatOptions);
		const count = kind === 'month' ? 12 : 7;
		for (let i = 0; i < count; i++) {
			out.push(f.format(new Date(Date.UTC(2021, kind === 'month' ? i : 0, kind === 'month' ? 15 : 3 + i))).toLowerCase());
		}
	} catch {
		/* unknown locale */
	}
	return out;
}

interface Compiled {
	re: RegExp;
	fields: string[];
	months: string[];
	monthsShort: string[];
}

const cache = new Map<string, Compiled | null>();

function compile(pattern: string, locale: string): Compiled | null {
	const key = pattern + '|' + locale;
	if (cache.has(key)) return cache.get(key)!;
	const months = localeNames(locale, 'long', 'month');
	const monthsShort = localeNames(locale, 'short', 'month').map((s) => s.replace(/\.$/, ''));
	const weekdays = [...localeNames(locale, 'long', 'weekday'), ...localeNames(locale, 'short', 'weekday')].map((s) => s.replace(/\.$/, ''));
	const fields: string[] = [];
	let re = '^\\s*';
	let ok = true;
	for (let i = 0; i < pattern.length; ) {
		const ch = pattern[i];
		if (ch === "'") {
			const end = pattern.indexOf("'", i + 1);
			if (end < 0) {
				ok = false;
				break;
			}
			re += end === i + 1 ? "'" : escapeRe(pattern.slice(i + 1, end));
			i = end + 1;
			continue;
		}
		if (/[A-Za-z]/.test(ch)) {
			let j = i;
			while (pattern[j] === ch) j++;
			const n = j - i;
			i = j;
			switch (ch) {
				case 'y':
				case 'u':
					fields.push('y');
					re += n === 2 ? '(\\d{2})' : '(\\d{4})';
					break;
				case 'M':
				case 'L':
					if (n >= 4) {
						fields.push('Mname');
						re += `(${months.map(escapeRe).join('|') || '[^\\s\\d]+'})`;
					} else if (n === 3) {
						fields.push('Mshort');
						re += `(${[...monthsShort, ...months].map(escapeRe).join('|') || '[^\\s\\d]+'})\\.?`;
					} else {
						fields.push('M');
						re += n === 2 ? '(\\d{2})' : '(\\d{1,2})';
					}
					break;
				case 'd':
					fields.push('d');
					re += n === 2 ? '(\\d{2})' : '(\\d{1,2})';
					break;
				case 'H':
				case 'k':
					fields.push('H');
					re += n === 2 ? '(\\d{2})' : '(\\d{1,2})';
					break;
				case 'h':
				case 'K':
					fields.push('h');
					re += n === 2 ? '(\\d{2})' : '(\\d{1,2})';
					break;
				case 'm':
					fields.push('m');
					re += n === 2 ? '(\\d{2})' : '(\\d{1,2})';
					break;
				case 's':
					fields.push('s');
					re += n === 2 ? '(\\d{2})' : '(\\d{1,2})';
					break;
				case 'S':
					fields.push('S');
					re += `(\\d{${n}})`;
					break;
				case 'a':
					fields.push('a');
					re += '([AaPp]\\.?[Mm]\\.?)';
					break;
				case 'E':
					fields.push('E');
					re += `(?:${weekdays.map(escapeRe).join('|') || '[^\\s\\d]+'})\\.?`;
					fields.pop();
					break;
				case 'Z':
				case 'X':
				case 'z':
					fields.push('z');
					re += '(Z|[+-]\\d{2}:?\\d{2}|[A-Z]{2,5})';
					break;
				default:
					ok = false;
			}
			if (!ok) break;
			continue;
		}
		re += /\s/.test(ch) ? '\\s+' : escapeRe(ch);
		i++;
	}
	const compiled = ok ? { re: new RegExp(re + '\\s*[^\\w]*$', 'i'), fields, months, monthsShort } : null;
	cache.set(key, compiled);
	return compiled;
}

function parsePattern(value: string, pattern: string, locale: string): number | null {
	const c = compile(pattern, locale);
	if (!c) return null;
	const m = c.re.exec(value.toLowerCase().replace(/\s+/g, ' '));
	if (!m) return null;
	let y = new Date().getFullYear();
	let mo = 0;
	let d = 1;
	let H = 0;
	let mi = 0;
	let s = 0;
	let h12: number | null = null;
	let pm: boolean | null = null;
	let offset: number | null = null;
	for (let i = 0; i < c.fields.length; i++) {
		const v = m[i + 1];
		switch (c.fields[i]) {
			case 'y':
				y = v.length === 2 ? 2000 + parseInt(v, 10) : parseInt(v, 10);
				break;
			case 'M':
				mo = parseInt(v, 10) - 1;
				break;
			case 'Mname':
			case 'Mshort': {
				const lv = v.toLowerCase().replace(/\.$/, '');
				let idx = c.months.indexOf(lv);
				if (idx < 0) idx = c.monthsShort.indexOf(lv);
				if (idx < 0) return null;
				mo = idx;
				break;
			}
			case 'd':
				d = parseInt(v, 10);
				break;
			case 'H':
				H = parseInt(v, 10);
				break;
			case 'h':
				h12 = parseInt(v, 10);
				break;
			case 'm':
				mi = parseInt(v, 10);
				break;
			case 's':
				s = parseInt(v, 10);
				break;
			case 'a':
				pm = v.toLowerCase().startsWith('p');
				break;
			case 'z': {
				if (v.toLowerCase() === 'z') offset = 0;
				else if (/^[+-]/.test(v)) {
					const sign = v[0] === '-' ? -1 : 1;
					const digits = v.slice(1).replace(':', '');
					offset = sign * (parseInt(digits.slice(0, 2), 10) * 60 + parseInt(digits.slice(2), 10));
				}
				break;
			}
		}
	}
	if (h12 != null) H = (h12 % 12) + (pm ? 12 : 0);
	if (mo < 0 || mo > 11 || d < 1 || d > 31 || H > 24 || mi > 59 || s > 59) return null;
	const probe = new Date(Date.UTC(y, mo, d));
	if (probe.getUTCMonth() !== mo) return null; // strict (non-lenient) like SimpleDateFormat
	if (offset != null) return Date.UTC(y, mo, d, H, mi, s) - offset * MINUTE;
	return new Date(y, mo, d, H, mi, s).getTime();
}

function startOfDay(now: number, daysAgo: number): number {
	const d = new Date(now);
	d.setDate(d.getDate() - daysAgo);
	d.setHours(0, 0, 0, 0);
	return d.getTime();
}

function parseRelative(lower: string, now: number): number | null {
	const amount = /\d+/.exec(lower)?.[0];
	if (!amount) return null;
	const unit = UNITS.find(([words]) =>
		words.some((w) => (w.length <= 2 ? new RegExp(`\\d\\s*${escapeRe(w)}\\b`).test(lower) : lower.includes(w))),
	);
	if (!unit) return null;
	const result = now - parseInt(amount, 10) * unit[1];
	return result > 0 ? result : null;
}

function parseIso(value: string): number | null {
	const t = Date.parse(value);
	return Number.isNaN(t) ? null : t;
}

export function parseDate(raw: string, dateFormat: string, locale: string, now = Date.now(), learn?: (f: string, l: string) => void): number | null {
	const value = raw.trim();
	if (!value) return null;
	const lower = value.toLowerCase();
	if (/^\d+$/.test(value)) {
		if (value.length === 10) return parseInt(value, 10) * 1000;
		if (value.length >= 12 && value.length <= 14) return parseInt(value, 10);
	}
	if (value.includes('T')) {
		const iso = parseIso(value);
		if (iso != null) return iso;
	}
	if (TODAY.some((w) => lower.startsWith(w))) return startOfDay(now, 0);
	if (YESTERDAY.some((w) => lower.startsWith(w))) return startOfDay(now, 1);
	if (TWO_DAYS_AGO.some((w) => lower.startsWith(w))) return startOfDay(now, 2);
	if (AGO_SUFFIXES.some((w) => lower.includes(w)) || AGO_PREFIXES.some((w) => lower.startsWith(w))) {
		const rel = parseRelative(lower, now);
		if (rel != null) return rel;
	}
	const cleaned = value.replace(/(\d+)(st|nd|rd|th)\b/gi, '$1');
	const loc = locale.replace('_', '-') || 'en';
	if (dateFormat.trim()) {
		const t = parsePattern(cleaned, dateFormat, loc);
		if (t != null) return t;
	}
	const hasLetters = /\p{L}/u.test(cleaned);
	const candidates = hasLetters ? TEXTUAL_FORMATS : NUMERIC_FORMATS;
	const locales = Array.from(new Set([...(locale ? [loc] : []), 'en', ...(hasLetters ? COMMON_LOCALES : [])]));
	for (const l of locales) {
		for (const p of candidates) {
			const t = parsePattern(cleaned, p, l);
			if (t != null) {
				learn?.(p, l);
				return t;
			}
		}
	}
	return parseRelative(lower, now) ?? parseIso(value);
}

export function parseDates(values: unknown[], dateFormat: string, locale: string): string[] {
	const fallback = Date.now();
	let format = dateFormat;
	let loc = locale;
	return values
		.map((v) => String(v ?? '').trim())
		.filter((v) => v)
		.map((v) =>
			String(
				parseDate(v, format, loc, fallback, (f, l) => {
					format = f;
					loc = l;
				}) ?? fallback,
			),
		);
}
