package org.koitharu.kotatsu.builtin

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/** Per-host minimum interval, shared by all built-in clients in this process. */
class PoliteHostInterceptor(private val intervalMs: Long) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val state = slots.computeIfAbsent(host) { HostState() }
        synchronized(state) {
            val delay = (state.nextAllowed - System.currentTimeMillis()).coerceAtLeast(0)
            if (delay > 0) Thread.sleep(delay)
            state.nextAllowed = System.currentTimeMillis() + intervalMs
        }
        return chain.proceed(chain.request())
    }

    private class HostState {
        var nextAllowed = 0L
    }

    companion object {
        private val slots = ConcurrentHashMap<String, HostState>()
        fun intervalFor(sourceId: String): Long = when (sourceId.lowercase()) {
            "ao3" -> 3_000L
            "wattpad" -> 1_000L
            "royalroad", "scribblehub" -> 1_500L
            else -> 300L
        }
    }
}
