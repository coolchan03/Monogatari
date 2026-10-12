package org.koitharu.kotatsu.builtin.newpipe

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NewPipePageCollectorTest {
    @Test fun combinesAndDeduplicatesPages() = runBlocking {
        val output = NewPipePageCollector.collect(
            first = listOf("a", "b"),
            next = 2,
            key = { it },
            fetch = { page: Int ->
                when (page) {
                    2 -> listOf("b", "c") to 3
                    3 -> listOf("c", "d") to null
                    else -> emptyList<String>() to null
                }
            },
        )
        assertEquals(listOf("a", "b", "c", "d"), output)
    }

    @Test fun duplicateOnlyPageTerminatesLoop() = runBlocking {
        var attempts = 0
        val output = NewPipePageCollector.collect(
            first = listOf("a"),
            next = 1,
            key = { it },
            fetch = { page: Int ->
                attempts++
                listOf("a") to (page + 1)
            },
        )
        assertEquals(listOf("a"), output)
        assertEquals(1, attempts)
    }

    @Test fun stopsAtPageLimit() = runBlocking {
        var fetched = 0
        val output = NewPipePageCollector.collect(
            first = listOf("item0"),
            next = 1,
            key = { it },
            fetch = { page: Int ->
                fetched++
                listOf("item$page") to (page + 1)
            },
        )
        assertEquals(NewPipePageCollector.MAX_PAGES, output.size)
        assertEquals(NewPipePageCollector.MAX_PAGES - 1, fetched)
    }
}
