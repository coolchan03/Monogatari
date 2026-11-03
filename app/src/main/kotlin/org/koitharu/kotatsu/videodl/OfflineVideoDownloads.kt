package org.koitharu.kotatsu.videodl

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.videodl.OfflineStreamPolicy.HlsKind
import org.koitharu.kotatsu.videodl.OfflineStreamPolicy.Protocol
import org.koitharu.kotatsu.videodl.OfflineStreamPolicy.Reason
import java.util.concurrent.TimeUnit

/** What the player menus pass in when the user taps "Download for offline". */
data class OfflineVideoRequest(
	val title: String,
	val sourceName: String,
	val chapterId: Long,
	val chapterUrl: String,
	/** MediaType name: VIDEO or AUDIO. */
	val mediaType: String,
	val streamUrl: String,
	val audioUrl: String? = null,
	val mimeType: String? = null,
	val headers: Map<String, String> = emptyMap(),
)

/** Checks a stream against [OfflineStreamPolicy] (fetching its manifest when needed) and queues the download. */
@OptIn(UnstableApi::class)
object OfflineVideoDownloads {

	sealed interface Outcome {
		data object Started : Outcome
		data object AlreadySaved : Outcome
		data object StorageFull : Outcome
		data class Rejected(val reason: Reason) : Outcome
	}

	private const val MAX_MANIFEST_BYTES = 2_000_000L

	private val client by lazy {
		OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
	}

	private class Plan(
		val reason: Reason? = null,
		val cacheUrl: String = "",
		val keys: List<OfflineStreamPolicy.DashKey> = emptyList(),
	)

	suspend fun enqueue(context: Context, request: OfflineVideoRequest): Outcome {
		val app = context.applicationContext
		val video = OfflineStreamPolicy.classify(request.streamUrl, request.mimeType)
		val protocol = video.protocol ?: return Outcome.Rejected(video.reason ?: Reason.UNSUPPORTED_FORMAT)
		if (request.audioUrl != null) {
			// A separate audio track must be a plain file; streaming audio parts are not supported.
			val audio = OfflineStreamPolicy.classify(request.audioUrl, null)
			if (audio.protocol == null) return Outcome.Rejected(audio.reason ?: Reason.UNSUPPORTED_FORMAT)
			if (audio.protocol != Protocol.PROGRESSIVE) return Outcome.Rejected(Reason.UNSUPPORTED_FORMAT)
		}
		val id = OfflineVideoStore.entryId(request.streamUrl, request.audioUrl)
		// Created on the caller's (main) thread so Media3 listeners run there.
		val manager = OfflineVideoManager.manager(app)
		val existing = OfflineVideoStore.get(app, id)
		if (existing?.completed == true) return Outcome.AlreadySaved
		if (existing != null) {
			// Already queued or running, unless the entry is a leftover without a Media3 download.
			val running = withContext(Dispatchers.IO) { runCatching { manager.downloadIndex.getDownload(id) }.getOrNull() }
			if (running != null) return Outcome.Started
		}
		if (withContext(Dispatchers.IO) { OfflineVideoManager.isFull(app) }) return Outcome.StorageFull
		val chosen = withContext(Dispatchers.IO) { plan(protocol, request.streamUrl, request.headers) }
		if (chosen.reason != null) return Outcome.Rejected(chosen.reason)

		OfflineVideoStore.put(
			app,
			OfflineVideoStore.Entry(
				id = id,
				title = request.title,
				sourceName = request.sourceName,
				chapterId = request.chapterId,
				chapterUrl = request.chapterUrl,
				mediaType = request.mediaType,
				streamUrl = request.streamUrl,
				audioUrl = request.audioUrl,
				mimeType = OfflineVideoPlayback.mimeFor(protocol, request.mimeType),
				protocol = protocol.name,
				createdAt = System.currentTimeMillis(),
				completed = false,
				cacheUrl = chosen.cacheUrl.takeIf { it != request.streamUrl },
				streamKeys = chosen.keys.map { it.encode() },
				headers = request.headers,
			),
		)
		send(app, buildRequest(id, chosen.cacheUrl, protocol, chosen.keys))
		request.audioUrl?.let { send(app, buildRequest(OfflineVideoStore.audioPartId(id), it, Protocol.PROGRESSIVE, emptyList())) }
		return Outcome.Started
	}

	fun message(context: Context, outcome: Outcome, title: String): String = when (outcome) {
		Outcome.Started -> context.getString(R.string.offline_video_started, title)
		Outcome.AlreadySaved -> context.getString(R.string.offline_video_already_saved)
		Outcome.StorageFull -> context.getString(R.string.offline_video_storage_full)
		is Outcome.Rejected -> context.getString(
			when (outcome.reason) {
				Reason.DRM -> R.string.offline_video_refused_drm
				Reason.LIVE -> R.string.offline_video_refused_live
				Reason.NOT_HTTP, Reason.NO_URL -> R.string.offline_video_refused_not_http
				Reason.UNSUPPORTED_FORMAT -> R.string.offline_video_refused_unsupported
			},
		)
	}

	private fun buildRequest(
		id: String,
		url: String,
		protocol: Protocol,
		keys: List<OfflineStreamPolicy.DashKey>,
	): DownloadRequest = DownloadRequest.Builder(id, Uri.parse(url))
		.setMimeType(
			when (protocol) {
				Protocol.HLS -> MimeTypes.APPLICATION_M3U8
				Protocol.DASH -> MimeTypes.APPLICATION_MPD
				Protocol.PROGRESSIVE -> null
			},
		)
		.setStreamKeys(keys.map { StreamKey(it.period, it.adaptationSet, it.representation) })
		.build()

	private fun send(context: Context, request: DownloadRequest) {
		DownloadService.sendAddDownload(context, OfflineDownloadService::class.java, request, true)
	}

	/** Looks inside HLS and DASH manifests so live and protected streams are refused before any download starts. */
	private fun plan(protocol: Protocol, url: String, headers: Map<String, String>): Plan = when (protocol) {
		Protocol.PROGRESSIVE -> Plan(cacheUrl = url)
		Protocol.DASH -> planDash(url, headers)
		Protocol.HLS -> planHls(url, headers)
	}

	private fun planDash(url: String, headers: Map<String, String>): Plan {
		val manifest = fetchText(url, headers) ?: return Plan(cacheUrl = url)
		OfflineStreamPolicy.inspectDash(manifest)?.let { return Plan(reason = it) }
		return Plan(cacheUrl = url, keys = OfflineStreamPolicy.dashStreamKeys(manifest))
	}

	private fun planHls(url: String, headers: Map<String, String>): Plan {
		val playlist = fetchText(url, headers) ?: return Plan(cacheUrl = url)
		return when (OfflineStreamPolicy.inspectHls(playlist)) {
			HlsKind.LIVE -> Plan(reason = Reason.LIVE)
			HlsKind.DRM -> Plan(reason = Reason.DRM)
			HlsKind.VOD -> Plan(cacheUrl = url)
			HlsKind.MASTER -> planHlsVariant(url, playlist, headers)
		}
	}

	/** Saves one variant (the best) instead of every quality in a master playlist. */
	private fun planHlsVariant(url: String, master: String, headers: Map<String, String>): Plan {
		if (OfflineStreamPolicy.hasSeparateHlsAudio(master)) return Plan(reason = Reason.UNSUPPORTED_FORMAT)
		val variant = OfflineStreamPolicy.bestHlsVariant(master)
			?.let { OfflineStreamPolicy.resolve(url, it) } ?: return Plan(reason = Reason.UNSUPPORTED_FORMAT)
		val playlist = fetchText(variant, headers) ?: return Plan(cacheUrl = variant)
		return when (OfflineStreamPolicy.inspectHls(playlist)) {
			HlsKind.LIVE -> Plan(reason = Reason.LIVE)
			HlsKind.DRM -> Plan(reason = Reason.DRM)
			HlsKind.MASTER -> Plan(reason = Reason.UNSUPPORTED_FORMAT)
			HlsKind.VOD -> Plan(cacheUrl = variant)
		}
	}

	private fun fetchText(url: String, headers: Map<String, String>): String? = runCatching {
		val builder = Request.Builder().url(url)
		headers.forEach { (name, value) -> builder.header(name, value) }
		client.newCall(builder.build()).execute().use { response ->
			if (!response.isSuccessful) return@use null
			val source = response.body?.source() ?: return@use null
			source.request(MAX_MANIFEST_BYTES)
			source.buffer.readUtf8()
		}
	}.getOrNull()
}
