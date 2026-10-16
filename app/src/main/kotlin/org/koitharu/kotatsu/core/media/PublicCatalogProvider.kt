package org.koitharu.kotatsu.core.media

/** Public book catalogs shared by global and Books search. */
internal enum class PublicCatalogProvider(val label: String) {
    GUTENBERG("Gutenberg"),
    OPEN_LIBRARY("Open Library"),
    INTERNET_ARCHIVE("Archive"),
    WIKISOURCE("Wikisource"),
    GOOGLE_BOOKS("Google Books"),
    STANDARD_EBOOKS("Standard Ebooks"),
    FADED_PAGE("Faded Page"),
    GUTENBERG_AUSTRALIA("Gutenberg Australia"),
    GLOBAL_GREY("Global Grey"),
    BAEN_FREE("Baen Free Library"),
    STORYWEAVER("StoryWeaver");
    suspend fun search(catalog: PublicBookCatalog, term: String): List<PublicBookCatalog.Book> = when (this) {
        GUTENBERG -> catalog.searchGutenberg(term)
        OPEN_LIBRARY -> catalog.searchOpenLibrary(term)
        INTERNET_ARCHIVE -> catalog.searchInternetArchive(term)
        WIKISOURCE -> catalog.searchWikisource(term)
        GOOGLE_BOOKS -> catalog.searchGoogleBooks(term)
        STANDARD_EBOOKS -> catalog.searchStandardEbooks(term)
        FADED_PAGE -> catalog.searchFadedPage(term)
        GUTENBERG_AUSTRALIA -> catalog.searchGutenbergAustralia(term)
        GLOBAL_GREY -> catalog.searchGlobalGrey(term)
        BAEN_FREE -> catalog.searchBaenFreeLibrary(term)
        STORYWEAVER -> catalog.searchStoryWeaver(term)
    }

    companion object {
        fun fromLabel(value: String): PublicCatalogProvider? =
            entries.firstOrNull { it.label == value }
    }
}
