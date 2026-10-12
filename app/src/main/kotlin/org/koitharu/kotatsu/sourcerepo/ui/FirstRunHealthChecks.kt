package org.koitharu.kotatsu.sourcerepo.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A bounded offline preflight, deliberately separate from the full network source sweep.
 * It cannot certify websites, CAPTCHA, reading, or video playback.
 */
@Singleton
class FirstRunHealthChecks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsStore: JsSourceStore,
) {
    data class Report(
        val storageWritable: Boolean,
        val storageFreeBytes: Long,
        val online: Boolean,
        val onWifi: Boolean,
        val builtInSources: Int,
        val installedJsSources: Int,
    ) {
        fun summary(): String = buildString {
            appendLine("Storage: " + if (storageWritable) "writable" else "needs attention")
            appendLine("Free app storage: ${storageFreeBytes / (1024L * 1024L)} MB")
            appendLine("Network: " + if (online) "connected" else "offline or not validated")
            appendLine("Connection: " + if (onWifi) "Wi-Fi" else "not Wi-Fi")
            appendLine("Source registry: $builtInSources built-in, $installedJsSources installed JS")
            append("Reader support: EPUB, PDF, text and HTML are included in the app.")
        }
    }

    fun run(): Report {
        val dir = context.filesDir
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return Report(
            storageWritable = dir.isDirectory && dir.canWrite(),
            storageFreeBytes = dir.usableSpace,
            online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            onWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            builtInSources = MangaParserSource.entries.size,
            installedJsSources = jsStore.list().size,
        )
    }
}
