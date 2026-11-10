const mangayomiSources = [
  {
    "id": 4720001001,
    "name": "SkyMangas",
    "lang": "es",
    "baseUrl": "https://skymangas.com",
    "apiUrl": "https://api.skymangas.com/api/v1",
    "iconUrl": "https://www.google.com/s2/favicons?sz=128&domain=https://skymangas.com",
    "typeSource": "single",
    "itemType": 0,
    "version": "0.1.0",
    "pkgPath": "manga/src/es/skymangas.js"
  }
];

// SkyMangas serves a public JSON API (api.skymangas.com/api/v1). Endpoints used, all verified live
// on 2026-11-10: manhuas/trending, manhuas/latest, manhuas/search, manhuas/<slug>,
// chapters/manhua/<id>, chapters/<chapterId>/pages. Page image URLs carry a short-lived token, so
// they are requested fresh every time pages are loaded.
class DefaultExtension extends MProvider {
  constructor() {
    super();
    this.client = new Client();
  }

  get apiUrl() {
    return this.source.apiUrl || "https://api.skymangas.com/api/v1";
  }

  get apiOrigin() {
    return this.apiUrl.replace(/\/api\/v1\/?$/, "");
  }

  getHeaders() {
    return {
      Origin: this.source.baseUrl,
      Referer: `${this.source.baseUrl}/`,
      Accept: "application/json",
    };
  }

  async fetchJson(path) {
    const res = await this.client.get(`${this.apiUrl}/${path}`, this.getHeaders());
    const json = JSON.parse(res.body);
    if (!json || json.success === false) {
      throw new Error((json && json.message) || "SkyMangas API error");
    }
    return json;
  }

  absolute(path) {
    if (!path) return "";
    if (/^https?:\/\//i.test(path)) return path;
    return `${this.apiOrigin}${path.startsWith("/") ? "" : "/"}${path}`;
  }

  // The API is not consistent about camelCase vs snake_case between endpoints.
  cover(item) {
    return this.absolute(
      item.coverThumbUrl || item.cover_thumb_url || item.coverUrl || item.cover_url,
    );
  }

  toList(items) {
    return (items || [])
      // "catalog_only" titles are listed on the site but have no chapters to read.
      .filter((i) => i && i.slug && i.title && i.availability !== "catalog_only")
      .map((i) => ({
        name: i.title,
        imageUrl: this.cover(i),
        link: `/${i.slug}`,
      }));
  }

  // These list endpoints have no reliable total, so any non-empty page may have a next one.
  listResult(items) {
    const list = this.toList(items);
    return { list, hasNextPage: list.length > 0 };
  }

  async getPopular(page) {
    const json = await this.fetchJson(`manhuas/trending?page=${page}&perPage=20`);
    return this.listResult(json.data);
  }

  async getLatestUpdates(page) {
    const json = await this.fetchJson(`manhuas/latest?page=${page}&perPage=20`);
    return this.listResult(json.data);
  }

  async search(query, page, filters) {
    const q = (query || "").trim();
    if (!q) return this.getLatestUpdates(page);
    const json = await this.fetchJson(
      // availability=readable hides catalog-only titles (most of the index has no chapters).
      `manhuas/search?q=${encodeURIComponent(q)}&page=${page}&perPage=20&availability=readable`,
    );
    const list = this.toList(json.data);
    const meta = json.meta || {};
    const hasNextPage =
      meta.totalPages !== undefined ? (meta.page || page) < meta.totalPages : list.length > 0;
    return { list, hasNextPage };
  }

  statusCode(statusId) {
    // Only the value observed for a currently releasing title is mapped; others stay "unknown".
    return statusId === 1 ? 0 : 5;
  }

  async getDetail(url) {
    const slug = url.replace(/^\/+/, "").split("/")[0];
    const json = await this.fetchJson(`manhuas/${encodeURIComponent(slug)}`);
    const m = json.data;
    if (!m || !m.id) throw new Error("Manga not found");

    const genre = [];
    for (const g of m.genres || []) if (g && g.name) genre.push(g.name);
    for (const t of m.tags || []) if (t && t.name) genre.push(t.name);
    if (m.originationName) genre.unshift(m.originationName);

    const chapters = await this.fetchAllChapters(m.id, slug);

    return {
      name: m.title,
      imageUrl: this.absolute(m.coverUrl || m.coverThumbUrl),
      description: m.isReadable === false
        ? `(Sin capítulos disponibles en SkyMangas)\n\n${m.synopsis || ""}`
        : m.synopsis || "",
      author: m.author || "",
      artist: m.artist || "",
      genre,
      status: this.statusCode(m.statusId),
      chapters,
    };
  }

  async fetchAllChapters(mangaId) {
    const all = [];
    let page = 1;
    let totalPages = 1;
    do {
      const json = await this.fetchJson(
        `chapters/manhua/${mangaId}?page=${page}&perPage=100&sort=desc`,
      );
      const rows = json.data || [];
      for (const c of rows) all.push(c);
      totalPages = (json.meta && json.meta.totalPages) || 1;
      page++;
      // Hard stop so a misbehaving API can never loop the app.
    } while (page <= totalPages && page <= 200);

    // One row per chapter id; the API never repeats ids, but be defensive.
    const seen = new Set();
    const out = [];
    for (const c of all) {
      if (seen.has(c.id)) continue;
      seen.add(c.id);
      const number = c.chapterNumber;
      const label = number !== null && number !== undefined ? `Capítulo ${number}` : "Capítulo";
      const published = c.publishedAt ? Date.parse(c.publishedAt) : NaN;
      out.push({
        name: c.title ? `${label}: ${c.title}` : label,
        // chapters/<id>/pages is what getPageList needs
        url: `chapters/${c.id}`,
        dateUpload: String(isNaN(published) ? 0 : published),
        scanlator: c.groupNames || "",
      });
    }
    return out;
  }

  async getPageList(url) {
    const id = url.replace(/^\/+/, "").split("/").pop();
    const json = await this.fetchJson(`chapters/${encodeURIComponent(id)}/pages`);
    const pages = (json.data || [])
      .slice()
      .sort((a, b) => (a.pageNumber || 0) - (b.pageNumber || 0));
    const headers = { Referer: `${this.source.baseUrl}/` };
    const result = [];
    for (const p of pages) {
      const src = p.imageUrl || p.imageWebp;
      if (!src) continue;
      result.push({ url: this.absolute(src), headers });
    }
    return result;
  }

  getFilterList() {
    return [];
  }

  getSourcePreferences() {
    return [];
  }
}
