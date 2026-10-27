package com.github.damontecres.wholphin.services.youtube

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A video listed in search results or a feed
 */
@Serializable
data class YouTubeVideo(
    val url: String,
    val title: String,
    val channel: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val viewCount: Long,
)

/**
 * What's needed to play a video. Either a single [videoUrl] with audio included, or separate
 * [videoUrl] & [audioUrl] streams to merge, or a [hlsUrl] (such as for live streams)
 */
data class YouTubePlayable(
    val title: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val hlsUrl: String?,
)

/**
 * Searches & resolves YouTube videos without ads using NewPipe Extractor
 */
@Singleton
class YouTubeService
    @Inject
    constructor(
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        private val initialized by lazy {
            NewPipe.init(NewPipeDownloader(okHttpClient))
            true
        }

        private suspend fun <T> extractor(block: () -> T): T =
            withContext(WholphinDispatchers.IO) {
                check(initialized)
                block()
            }

        suspend fun search(query: String): List<YouTubeVideo> =
            extractor {
                val service = ServiceList.YouTube
                val handler =
                    service.searchQHFactory.fromQuery(
                        query,
                        listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                        "",
                    )
                SearchInfo
                    .getInfo(service, handler)
                    .relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo() }
            }

        suspend fun resolve(
            url: String,
            maxHeight: Int = 1080,
        ): YouTubePlayable =
            extractor {
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val hls = info.hlsUrl?.takeIf { it.isNotBlank() }
                // Video only streams have the higher resolutions, but need to be merged with an audio stream
                val video =
                    info.videoOnlyStreams
                        .filter { it.isUrl && it.height() <= maxHeight && it.format?.suffix == "mp4" }
                        .maxByOrNull { it.height() }
                val audio =
                    info.audioStreams
                        .filter { it.isUrl && it.format?.suffix == "m4a" }
                        .maxByOrNull { it.averageBitrate }
                if (video != null && audio != null) {
                    Timber.d("Resolved %s + %s", video.resolution, audio.averageBitrate)
                    YouTubePlayable(info.name, video.content, audio.content, null)
                } else if (hls != null) {
                    YouTubePlayable(info.name, null, null, hls)
                } else {
                    // Fallback to streams that already include audio, typically lower resolution
                    val combined =
                        info.videoStreams
                            .filter { it.isUrl }
                            .maxByOrNull { it.height() }
                            ?: throw IllegalStateException("No playable streams found")
                    YouTubePlayable(info.name, combined.content, null, null)
                }
            }

        private fun StreamInfoItem.toVideo() =
            YouTubeVideo(
                url = url,
                title = name,
                channel = uploaderName,
                thumbnailUrl = thumbnails.firstOrNull()?.url,
                durationSeconds = duration,
                viewCount = viewCount,
            )

        private fun VideoStream.height(): Int = resolution?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    }
