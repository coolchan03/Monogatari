package org.koitharu.kotatsu.core.media

import java.net.URLEncoder

/** Visible official-site discovery for providers without a stable, permitted content API. */
internal object OfficialMediaSites {
    enum class Site { STORYWEAVER, FICTIONPRESS, FANFICTION, BILIBILI }

    fun searchUrl(site: Site, query: String): String {
        val term = query.trim().take(120)
        val encoded = URLEncoder.encode(term, "UTF-8")
        return when (site) {
            Site.STORYWEAVER -> if (term.isEmpty()) "https://storyweaver.org.in/"
                else "https://storyweaver.org.in/search?query=" + encoded
            Site.FICTIONPRESS -> if (term.isEmpty()) "https://www.fictionpress.com/"
                else "https://www.fictionpress.com/search/?keywords=" + encoded
            Site.FANFICTION -> if (term.isEmpty()) "https://www.fanfiction.net/"
                else "https://www.fanfiction.net/search/?keywords=" + encoded
            Site.BILIBILI -> if (term.isEmpty()) "https://www.bilibili.com/"
                else "https://search.bilibili.com/all?keyword=" + encoded
        }
    }
}
