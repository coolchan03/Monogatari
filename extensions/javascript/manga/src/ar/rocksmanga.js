const mangayomiSources = [
  {
    "id": 4720001002,
    "name": "Rocks Manga",
    "lang": "ar",
    "baseUrl": "https://rocksmanga.com",
    "apiUrl": "https://api.rocksmanga.com",
    "iconUrl": "https://www.google.com/s2/favicons?sz=128&domain=https://rocksmanga.com",
    "typeSource": "single",
    "itemType": 0,
    "version": "0.1.0",
    "pkgPath": "manga/src/ar/rocksmanga.js"
  }
];

// rocksmanga.com is a Next.js app whose data comes from a public JSON API on api.rocksmanga.com.
// Endpoints used, verified live on 2026-11-10:
//   GET /manga?page=N[&search=Q]       -> { items[], total, page, pageSize }
//   GET /manga/<slug>                  -> { manga }
//   GET /manga/<slug>/chapters         -> { chapters[] }
//   GET /manga/<slug>/chapter/<n>      -> { chapter: { pages[] } }
// The list endpoint ignores every sort parameter tried (sort, orderBy, order, ...), so "popular"
// and "latest" both use the site's default order, which is most recently updated first.
class DefaultExtension extends MProvider {
  constructor() {
    super();
    this.client = new Client();
  }

  get apiUrl() {
    return (this.source.apiUrl || "https://api.rocksmanga.com").replace(/\/+$/, "");
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
    if (res.statusCode && res.statusCode >= 400) {
      throw new Error(`Rocks Manga returned HTTP ${res.statusCode}`);
    }
    return JSON.parse(res.body);
  }

  absolute(path) {
    if (!path) return "";
    if (/^https?:\/\//i.test(path)) return path;
    return `${this.apiUrl}${path.startsWith("/") ? "" : "/"}${path}`;
  }

  toList(json) {
    const items = (json && json.items) || [];
    const list = items
      .filter((i) => i && i.slug && i.title)
      .map((i) => ({
        name: i.title,
        imageUrl: this.absolute(i.cover_url),
        link: `/${i.slug}`,
      }));
    const total = json && json.total;
    const page = (json && json.page) || 1;
    const size = (json && json.pageSize) || 20;
    const hasNextPage = typeof total === "number" ? page * size < total : list.length > 0;
    return { list, hasNextPage };
  }

  async getPopular(page) {
    return this.toList(await this.fetchJson(`manga?page=${page}`));
  }

  async getLatestUpdates(page) {
    return this.toList(await this.fetchJson(`manga?page=${page}`));
  }

  async search(query, page, filters) {
    const q = (query || "").trim();
    if (!q) return this.getLatestUpdates(page);
    return this.toList(await this.fetchJson(`manga?page=${page}&search=${encodeURIComponent(q)}`));
  }

  statusCode(status) {
    return (
      {
        RELEASING: 0,
        ONGOING: 0,
        COMPLETED: 1,
        FINISHED: 1,
        HIATUS: 2,
        ON_HIATUS: 2,
        CANCELLED: 3,
        CANCELED: 3,
      }[String(status || "").toUpperCase()] ?? 5
    );
  }

  async getDetail(url) {
    const slug = url.replace(/^\/+/, "").split("/")[0];
    const json = await this.fetchJson(`manga/${encodeURIComponent(slug)}`);
    const m = json && json.manga;
    if (!m || !m.slug) throw new Error("Manga not found");

    const names = (arr) =>
      (arr || []).map((x) => (typeof x === "string" ? x : x && (x.name || x.title))).filter(Boolean);
    const genre = names(m.genres);
    if (m.type) genre.unshift(String(m.type));

    const chaptersJson = await this.fetchJson(`manga/${encodeURIComponent(slug)}/chapters`);
    const rows = (chaptersJson && chaptersJson.chapters) || [];
    const seen = new Set();
    const chapters = [];
    for (const c of rows) {
      if (c.number === undefined || c.number === null || seen.has(String(c.number))) continue;
      seen.add(String(c.number));
      const time = c.created_at ? Date.parse(c.created_at) : NaN;
      chapters.push({
        name: c.title ? `الفصل ${c.number}: ${c.title}` : `الفصل ${c.number}`,
        // /manga/<slug>/chapter/<number> is what getPageList calls
        url: `${slug}/chapter/${c.number}`,
        dateUpload: String(isNaN(time) ? 0 : time),
        scanlator: (c.uploader && (c.uploader.display_name || c.uploader.username)) || "",
      });
    }
    // Newest first, the order Mangayomi expects.
    chapters.sort((a, b) => parseFloat(b.url.split("/").pop()) - parseFloat(a.url.split("/").pop()));

    return {
      name: m.title,
      imageUrl: this.absolute(m.cover_url),
      description: m.synopsis || "",
      author: names(m.authors).join(", "),
      artist: names(m.artists).join(", "),
      genre,
      status: this.statusCode(m.status),
      chapters,
    };
  }

  async getPageList(url) {
    const [slug, , number] = url.replace(/^\/+/, "").split("/");
    const json = await this.fetchJson(
      `manga/${encodeURIComponent(slug)}/chapter/${encodeURIComponent(number)}`,
    );
    const pages = (json && json.chapter && json.chapter.pages) || [];
    const headers = { Referer: `${this.source.baseUrl}/` };
    return pages.map((p) => ({ url: this.absolute(typeof p === "string" ? p : p.url), headers }));
  }

  getFilterList() {
    return [];
  }

  getSourcePreferences() {
    return [];
  }
}
