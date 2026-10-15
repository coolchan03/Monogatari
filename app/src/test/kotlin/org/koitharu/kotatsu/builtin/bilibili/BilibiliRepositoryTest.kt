package org.koitharu.kotatsu.builtin.bilibili

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.SortOrder

class BilibiliRepositoryTest {
    @Test fun safeVideoSearchAndOffsitePlaybackLink() {
        val data = JSONObject("""{"code":0,"data":{"result":[
            {"bvid":"BV1xx411c7mD","title":"<em class=\"keyword\">Anime</em> trailer",
             "pic":"//i0.hdslb.com/bfs/archive/preview.jpg","author":"Studio",
             "typename":"Animation","description":"Official preview","is_pay":0},
            {"bvid":"BV1xx411c7mA","title":"Paid","is_pay":1},
            {"bvid":"../../bad","title":"Bad id"}]}}""")
        val rows = BilibiliRepository.parseVideos(data)
        assertEquals(1, rows.size)
        assertEquals("Anime trailer", rows.single().title)
        assertTrue(rows.single().thumbnail!!.startsWith("https://i0.hdslb.com/"))
        assertEquals("Studio", rows.single().author)
    }

    @Test fun encodesSearchQueryAndRejectsUnavailableVideoSources() {
        val url = BilibiliRepository.searchUrl("One & Two", SortOrder.NEWEST, 2)
        assertTrue(url.contains("One%20%26%20Two"))
        assertTrue(url.contains("order=pubdate"))
        assertTrue(url.contains("page=2"))
        assertTrue(BilibiliRepository.searchUrl("", SortOrder.RELEVANCE, 1).contains("/ranking/"))
        assertTrue(BilibiliRepository.parseVideos(JSONObject("""{"code":0,"data":{}}""")).isEmpty())
    }

    @Test(expected = java.io.IOException::class)
    fun reportsProviderApiErrors() {
        BilibiliRepository.parseVideos(JSONObject("""{"code":-403,"message":"Access denied"}"""))
    }
}
