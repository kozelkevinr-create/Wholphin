package com.github.damontecres.wholphin.services.youtube

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Something listed in search results: a video, channel, or playlist
 */
sealed interface YouTubeEntry {
    val url: String
    val title: String
    val thumbnailUrl: String?
}

/**
 * A video listed in search results or a feed
 */
@Serializable
data class YouTubeVideo(
    override val url: String,
    override val title: String,
    val channel: String?,
    override val thumbnailUrl: String?,
    val durationSeconds: Long,
    val viewCount: Long,
) : YouTubeEntry

data class YouTubeChannel(
    override val url: String,
    override val title: String,
    override val thumbnailUrl: String?,
    val subscribers: Long,
) : YouTubeEntry

data class YouTubePlaylist(
    override val url: String,
    override val title: String,
    override val thumbnailUrl: String?,
    val uploader: String?,
    val videoCount: Long,
) : YouTubeEntry

/**
 * The videos in a channel or playlist
 */
data class YouTubeListing(
    val title: String,
    val videos: List<YouTubeVideo>,
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

        suspend fun search(query: String): List<YouTubeEntry> =
            extractor {
                val service = ServiceList.YouTube
                val handler = service.searchQHFactory.fromQuery(query, emptyList(), "")
                SearchInfo
                    .getInfo(service, handler)
                    .relatedItems
                    .mapNotNull {
                        when (it) {
                            is StreamInfoItem -> it.toVideo()
                            is ChannelInfoItem -> it.toChannel()
                            is PlaylistInfoItem -> it.toPlaylist()
                            else -> null
                        }
                    }
            }

        /**
         * The (first page of) videos uploaded by a channel
         */
        suspend fun channelVideos(url: String): YouTubeListing =
            extractor {
                val service = ServiceList.YouTube
                val info = ChannelInfo.getInfo(service, url)
                val videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
                val videos =
                    videosTab
                        ?.let { ChannelTabInfo.getInfo(service, it) }
                        ?.relatedItems
                        .orEmpty()
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toVideo() }
                YouTubeListing(info.name, videos)
            }

        /**
         * The (first page of) videos in a playlist
         */
        suspend fun playlistVideos(url: String): YouTubeListing =
            extractor {
                val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
                YouTubeListing(
                    info.name,
                    info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() },
                )
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
                        .filter {
                            // H.264 only: AV1/VP9 fall back to software decoding on most TV sticks
                            it.isUrl && it.height() <= maxHeight && it.format?.suffix == "mp4" &&
                                it.codec?.startsWith("avc1") != false
                        }.maxWithOrNull(compareBy({ it.height() }, { if (it.fps <= 30) 1 else 0 }))
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

        private fun ChannelInfoItem.toChannel() =
            YouTubeChannel(
                url = url,
                title = name,
                thumbnailUrl = thumbnails.firstOrNull()?.url,
                subscribers = subscriberCount,
            )

        private fun PlaylistInfoItem.toPlaylist() =
            YouTubePlaylist(
                url = url,
                title = name,
                thumbnailUrl = thumbnails.firstOrNull()?.url,
                uploader = uploaderName,
                videoCount = streamCount,
            )

        private fun VideoStream.height(): Int = resolution?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    }
