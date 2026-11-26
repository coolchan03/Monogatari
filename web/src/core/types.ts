export type ItemType = 0 | 1 | 2; // manga, anime, novel (Mangayomi itemType codes)
export const ITEM_MANGA: ItemType = 0;
export const ITEM_ANIME: ItemType = 1;
export const ITEM_NOVEL: ItemType = 2;

export interface SourceEntry {
	id: number;
	name: string;
	baseUrl: string;
	lang: string;
	version: string;
	itemType: ItemType;
	isJs: boolean;
	sourceCodeUrl: string;
	iconUrl: string;
	apiUrl: string;
	dateFormat: string;
	dateFormatLocale: string;
	isNsfw: boolean;
	hasCloudflare: boolean;
	additionalParams: string;
	notes: string;
	repoUrl: string;
	sha256?: string;
	author?: string;
}

export interface InstalledSource {
	id: number;
	entry: SourceEntry;
	code: string;
	installedAt: number;
	pinned?: boolean;
	enabled?: boolean;
}

export interface JsChapter {
	name: string;
	url: string;
	dateUpload?: number | null;
	scanlator?: string | null;
}

export interface JsManga {
	name?: string | null;
	link?: string | null;
	imageUrl?: string | null;
	description?: string | null;
	author?: string | null;
	artist?: string | null;
	status: number;
	genre: string[];
	chapters: JsChapter[];
}

export interface JsPages {
	list: JsManga[];
	hasNextPage: boolean;
}

export interface JsPage {
	url: string;
	headers?: Record<string, string> | null;
}

export interface JsTrack {
	file?: string | null;
	label?: string | null;
}

export interface JsVideo {
	url: string;
	quality: string;
	originalUrl: string;
	headers?: Record<string, string> | null;
	subtitles: JsTrack[];
	audios: JsTrack[];
}

/** A manga/novel/anime title as the app stores it. */
export interface Title {
	key: string; // `${sourceId}:${url}`
	sourceId: number;
	url: string;
	title: string;
	cover?: string;
	itemType: ItemType;
}

export interface Favourite extends Title {
	categoryIds: number[];
	addedAt: number;
	lastChapterCount?: number;
	newChapters?: number;
}

export interface Category {
	id: number;
	name: string;
	order: number;
}

export interface HistoryEntry extends Title {
	chapterUrl: string;
	chapterName: string;
	chapterIndex: number; // reading order index
	/** Set by backup import: Android chapter id (hash of the chapter URL), resolved to a URL when the chapters load. */
	chapterUid?: string;
	page: number;
	pageCount: number;
	updatedAt: number;
}

export interface DownloadedChapter {
	key: string; // `${titleKey}|${chapterUrl}`
	titleKey: string;
	chapterUrl: string;
	chapterName: string;
	itemType: ItemType;
	pages?: Blob[];
	html?: string;
	savedAt: number;
}

export interface Bookmark {
	key: string;
	titleKey: string;
	title: string;
	cover?: string;
	sourceId: number;
	titleUrl: string;
	chapterUrl: string;
	chapterName: string;
	page: number;
	createdAt: number;
}

export interface ReadChapter {
	key: string; // `${titleKey}|${chapterUrl}`
	titleKey: string;
	chapterUrl: string;
}

export const titleKey = (sourceId: number, url: string) => `${sourceId}:${url}`;
