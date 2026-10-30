package com.github.damontecres.wholphin.services.youtube

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.SubtitlesStream
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
 * Where to continue from to get the next page of a search, channel, or playlist
 */
class YouTubeCursor internal constructor(
    internal val kind: Kind,
    internal val key: Any,
    internal val page: Page,
) {
    internal enum class Kind { SEARCH, CHANNEL, PLAYLIST }
}

/**
 * A page of results and, if there is more, how to get it
 */
data class YouTubePageResult<T>(
    val items: List<T>,
    val cursor: YouTubeCursor?,
)

/**
 * The videos in a channel or playlist
 */
data class YouTubeListing(
    val title: String,
    val videos: List<YouTubeVideo>,
    val cursor: YouTubeCursor? = null,
)

/**
 * A caption track for a video
 */
data class YouTubeSubtitle(
    val id: String,
    val url: String,
    val mimeType: String,
    val language: String,
    val label: String,
)

/**
 * What's needed to play a video. Either a single [videoUrl] with audio included, or separate
 * [videoUrl] & [audioUrl] streams to merge, or a [hlsUrl] (such as for live streams)
 *
 * @param heights the resolutions available for the video, highest first
 * @param height the resolution that was chosen, if known
 */
data class YouTubePlayable(
    val title: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val hlsUrl: String?,
    val subtitles: List<YouTubeSubtitle> = emptyList(),
    val heights: List<Int> = emptyList(),
    val height: Int = 0,
)

/**
 * Searches YouTube and finds playable streams using NewPipe Extractor
 */
@Singleton
class YouTubeService
    @Inject
    constructor(
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
        private val settings: YouTubeSettings,
    ) {
        private val initialized by lazy {
            NewPipe.init(NewPipeDownloader(okHttpClient))
            true
        }

        private suspend fun <T> extractor(block: () -> T): T =
            withContext(WholphinDispatchers.IO) {
                check(initialized)
                val region = settings.current.region
                NewPipe.setupLocalization(
                    Localization(YouTubeSettings.languageForRegion(region), region),
                    ContentCountry(region),
                )
                block()
            }

        suspend fun search(query: String): YouTubePageResult<YouTubeEntry> =
            extractor {
                val service = ServiceList.YouTube
                val handler = service.searchQHFactory.fromQuery(query, emptyList(), "")
                val info = SearchInfo.getInfo(service, handler)
                YouTubePageResult(
                    info.relatedItems.mapNotNull { it.toEntry() },
                    info.nextPage?.let { YouTubeCursor(YouTubeCursor.Kind.SEARCH, query, it) },
                )
            }

        /**
         * The first page of videos uploaded by a channel
         */
        suspend fun channelVideos(url: String): YouTubeListing =
            extractor {
                val service = ServiceList.YouTube
                val info = ChannelInfo.getInfo(service, url)
                val videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
                val tabInfo = videosTab?.let { ChannelTabInfo.getInfo(service, it) }
                YouTubeListing(
                    info.name,
                    tabInfo?.relatedItems.orEmpty().filterIsInstance<StreamInfoItem>().map { it.toVideo() },
                    tabInfo?.nextPage?.let { YouTubeCursor(YouTubeCursor.Kind.CHANNEL, videosTab, it) },
                )
            }

        /**
         * The first page of videos in a playlist
         */
        suspend fun playlistVideos(url: String): YouTubeListing =
            extractor {
                val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
                YouTubeListing(
                    info.name,
                    info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() },
                    info.nextPage?.let { YouTubeCursor(YouTubeCursor.Kind.PLAYLIST, url, it) },
                )
            }

        /**
         * Get the page after the one the [cursor] came from
         */
        suspend fun more(cursor: YouTubeCursor): YouTubePageResult<YouTubeEntry> =
            extractor {
                val service = ServiceList.YouTube
                val page =
                    when (cursor.kind) {
                        YouTubeCursor.Kind.SEARCH -> {
                            val handler = service.searchQHFactory.fromQuery(cursor.key as String, emptyList(), "")
                            SearchInfo.getMoreItems(service, handler, cursor.page)
                        }

                        YouTubeCursor.Kind.CHANNEL -> {
                            ChannelTabInfo.getMoreItems(service, cursor.key as ListLinkHandler, cursor.page)
                        }

                        YouTubeCursor.Kind.PLAYLIST -> {
                            PlaylistInfo.getMoreItems(service, cursor.key as String, cursor.page)
                        }
                    }
                YouTubePageResult(
                    page.items.mapNotNull { (it as InfoItem).toEntry() },
                    page.nextPage?.takeIf { page.hasNextPage() }?.let { YouTubeCursor(cursor.kind, cursor.key, it) },
                )
            }

        /**
         * Find the streams to play a video.
         *
         * @param maxHeight highest resolution to use, defaults to the user's setting
         */
        suspend fun resolve(
            url: String,
            maxHeight: Int = settings.current.maxHeight,
        ): YouTubePlayable =
            extractor {
                val prefs = settings.current
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val hls = info.hlsUrl?.takeIf { it.isNotBlank() }
                // H.264 only: AV1/VP9 fall back to software decoding on most TV sticks
                val videoStreams =
                    info.videoOnlyStreams.filter {
                        it.isUrl && it.format?.suffix == "mp4" && it.codec?.startsWith("avc1") != false
                    }
                val heights = videoStreams.map { it.height() }.filter { it > 0 }.distinct().sortedDescending()
                // Video only streams have the higher resolutions, but need to be merged with an audio stream
                val video =
                    videoStreams
                        .filter { it.height() <= maxHeight }
                        .let { candidates ->
                            if (prefs.allow60fps) candidates else candidates.filter { it.fps <= 30 }.ifEmpty { candidates }
                        }.maxWithOrNull(compareBy({ it.height() }, { it.fps }))
                val audio =
                    info.audioStreams
                        .filter { it.isUrl && it.format?.suffix == "m4a" }
                        .let { preferAudioLanguage(it, prefs.audioLanguage) }
                        .maxByOrNull { it.averageBitrate }
                val subtitles = info.subtitles.toSubtitles(prefs.captionLanguage)
                if (video != null && audio != null) {
                    Timber.d("Resolved %s + %s", video.resolution, audio.averageBitrate)
                    YouTubePlayable(info.name, video.content, audio.content, null, subtitles, heights, video.height())
                } else if (hls != null) {
                    YouTubePlayable(info.name, null, null, hls, subtitles)
                } else {
                    // Fallback to streams that already include audio, typically lower resolution
                    val combined =
                        info.videoStreams
                            .filter { it.isUrl }
                            .maxByOrNull { it.height() }
                            ?: throw IllegalStateException("No playable streams found")
                    YouTubePlayable(info.name, combined.content, null, null, subtitles, heights, combined.height())
                }
            }

        /**
         * YouTube serves auto-dubbed tracks next to the original. Use the requested [language] if there is one,
         * otherwise the original, then English, then anything
         */
        private fun preferAudioLanguage(
            streams: List<AudioStream>,
            language: String,
        ): List<AudioStream> =
            streams
                .filter { language.isNotBlank() && it.audioLocale?.language == language }
                .ifEmpty { streams.filter { it.audioTrackType == AudioTrackType.ORIGINAL } }
                .ifEmpty { streams.filter { it.audioTrackType == null && it.audioLocale == null } }
                .ifEmpty { streams.filter { it.audioLocale?.language == "en" } }
                .ifEmpty { streams }

        /**
         * Caption tracks in a format the player understands. Auto generated captions are only
         * kept for English and the user's preferred language, since YouTube offers dozens of translations
         */
        private fun List<SubtitlesStream>.toSubtitles(preferred: String): List<YouTubeSubtitle> =
            filter { it.isUrl && it.format?.mimeType in SUBTITLE_MIME_TYPES }
                .filter { !it.isAutoGenerated || it.locale?.language in setOf("en", preferred) }
                .distinctBy { it.languageTag to it.isAutoGenerated }
                .mapIndexed { index, it ->
                    YouTubeSubtitle(
                        id = "yt_sub_$index",
                        url = it.content,
                        mimeType = it.format!!.mimeType,
                        language = it.languageTag,
                        label = it.displayLanguageName + if (it.isAutoGenerated) " (auto)" else "",
                    )
                }

        private fun InfoItem.toEntry(): YouTubeEntry? =
            when (this) {
                is StreamInfoItem -> toVideo()
                is ChannelInfoItem -> toChannel()
                is PlaylistInfoItem -> toPlaylist()
                else -> null
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

        companion object {
            private val SUBTITLE_MIME_TYPES = setOf("text/vtt", "application/ttml+xml", "application/x-subrip")
        }
    }
