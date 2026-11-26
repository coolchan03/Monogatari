/** Tiny promise wrapper over IndexedDB. One object store per kind of record; `kv` holds settings and small blobs. */

const DB_NAME = 'monogatari';
const DB_VERSION = 1;

export const STORES = ['kv', 'repos', 'sources', 'favourites', 'categories', 'history', 'read', 'bookmarks', 'downloads', 'prefs'] as const;
export type StoreName = (typeof STORES)[number];

const KEY_PATHS: Record<StoreName, string | null> = {
	kv: null,
	repos: 'url',
	sources: 'id',
	favourites: 'key',
	categories: 'id',
	history: 'key',
	read: 'key',
	bookmarks: 'key',
	downloads: 'key',
	prefs: null,
};

let dbPromise: Promise<IDBDatabase> | null = null;
const listeners = new Map<StoreName, Set<() => void>>();

function open(): Promise<IDBDatabase> {
	if (!dbPromise) {
		dbPromise = new Promise((resolve, reject) => {
			const req = indexedDB.open(DB_NAME, DB_VERSION);
			req.onupgradeneeded = () => {
				const db = req.result;
				for (const name of STORES) {
					if (!db.objectStoreNames.contains(name)) {
						const kp = KEY_PATHS[name];
						db.createObjectStore(name, kp ? { keyPath: kp } : undefined);
					}
				}
			};
			req.onsuccess = () => resolve(req.result);
			req.onerror = () => reject(req.error);
		});
	}
	return dbPromise;
}

function wrap<T>(req: IDBRequest<T>): Promise<T> {
	return new Promise((resolve, reject) => {
		req.onsuccess = () => resolve(req.result);
		req.onerror = () => reject(req.error);
	});
}

async function tx<T>(store: StoreName, mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
	const db = await open();
	const t = db.transaction(store, mode);
	const result = await wrap(fn(t.objectStore(store)));
	if (mode === 'readwrite') {
		await new Promise<void>((resolve, reject) => {
			t.oncomplete = () => resolve();
			t.onerror = () => reject(t.error);
			t.onabort = () => reject(t.error);
		});
	}
	return result;
}

function notify(store: StoreName) {
	listeners.get(store)?.forEach((fn) => fn());
}

export function subscribe(stores: StoreName[], fn: () => void): () => void {
	for (const s of stores) {
		if (!listeners.has(s)) listeners.set(s, new Set());
		listeners.get(s)!.add(fn);
	}
	return () => stores.forEach((s) => listeners.get(s)?.delete(fn));
}

export const db = {
	get: <T>(store: StoreName, key: IDBValidKey): Promise<T | undefined> => tx(store, 'readonly', (s) => s.get(key)),
	getAll: <T>(store: StoreName): Promise<T[]> => tx(store, 'readonly', (s) => s.getAll()),
	async put<T>(store: StoreName, value: T, key?: IDBValidKey): Promise<void> {
		await tx(store, 'readwrite', (s) => (KEY_PATHS[store] ? s.put(value) : s.put(value, key)));
		notify(store);
	},
	async delete(store: StoreName, key: IDBValidKey): Promise<void> {
		await tx(store, 'readwrite', (s) => s.delete(key));
		notify(store);
	},
	async clear(store: StoreName): Promise<void> {
		await tx(store, 'readwrite', (s) => s.clear());
		notify(store);
	},
	async putMany<T>(store: StoreName, values: T[]): Promise<void> {
		const d = await open();
		const t = d.transaction(store, 'readwrite');
		const os = t.objectStore(store);
		for (const v of values) os.put(v);
		await new Promise<void>((resolve, reject) => {
			t.oncomplete = () => resolve();
			t.onerror = () => reject(t.error);
			t.onabort = () => reject(t.error);
		});
		notify(store);
	},
	/** All records of an out-of-line-key store whose string key starts with `prefix`. */
	async entriesWithPrefix<T>(store: StoreName, prefix: string): Promise<[string, T][]> {
		const d = await open();
		return new Promise((resolve, reject) => {
			const out: [string, T][] = [];
			const range = IDBKeyRange.bound(prefix, prefix + '\uffff');
			const cur = d.transaction(store, 'readonly').objectStore(store).openCursor(range);
			cur.onsuccess = () => {
				const c = cur.result;
				if (c) {
					out.push([String(c.key), c.value as T]);
					c.continue();
				} else resolve(out);
			};
			cur.onerror = () => reject(cur.error);
		});
	},
	count: (store: StoreName): Promise<number> => tx(store, 'readonly', (s) => s.count()),
};

export async function kvGet<T>(key: string, fallback: T): Promise<T> {
	const v = await db.get<T>('kv', key);
	return v === undefined ? fallback : v;
}

export const kvSet = <T>(key: string, value: T) => db.put('kv', value, key);

/** Request durable storage so the browser does not evict the library under pressure. */
export async function requestPersistence(): Promise<boolean> {
	try {
		return (await navigator.storage?.persist?.()) ?? false;
	} catch {
		return false;
	}
}
