package com.github.damontecres.wholphin.services.youtube

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a "suggested for you" list without a YouTube account.
 *
 * It mixes the newest uploads from the channels the user saved with the videos YouTube
 * suggests after the ones the user watched recently, leaving out anything already watched.
 */
@Singleton
class YouTubeSuggestions
    @Inject
    constructor(
        private val youTubeService: YouTubeService,
        private val library: YouTubeLibrary,
    ) {
        private var cached: List<YouTubeVideo> = emptyList()
        private var cachedKey: String? = null
        private var cachedAt = 0L

        /**
         * Get suggestions, reusing the last result for a while unless the saved channels or watch history changed
         */
        suspend fun get(force: Boolean = false): List<YouTubeVideo> {
            val state = library.state.value
            val seeds = state.history.distinctBy { it.channelUrl ?: it.channel }.take(RELATED_SEEDS)
            val channels = state.channels.take(MAX_CHANNELS)
            val key = (channels.map { it.url } + seeds.map { it.url }).joinToString("|")
            val fresh = System.currentTimeMillis() - cachedAt < CACHE_MS
            if (!force && key == cachedKey && fresh) return cached
            if (channels.isEmpty() && seeds.isEmpty()) return emptyList()

            val limit = Semaphore(MAX_CONCURRENT)
            val lists =
                coroutineScope {
                    val fromChannels =
                        channels.map { channel ->
                            async {
                                limit.withPermit {
                                    runCatching { youTubeService.channelVideos(channel.url).videos.take(PER_CHANNEL) }
                                        .onFailure { Timber.w(it, "Error loading uploads for ${channel.title}") }
                                        .getOrDefault(emptyList())
                                }
                            }
                        }
                    val fromHistory =
                        seeds.map { video ->
                            async {
                                limit.withPermit {
                                    runCatching { youTubeService.relatedVideos(video.url).take(PER_SEED) }
                                        .onFailure { Timber.w(it, "Error loading related videos for ${video.title}") }
                                        .getOrDefault(emptyList())
                                }
                            }
                        }
                    (fromChannels + fromHistory).awaitAll()
                }

            val watched = state.history.mapTo(mutableSetOf()) { it.url }
            val result = interleave(lists).filter { it.url !in watched }.distinctBy { it.url }.take(MAX_RESULTS)
            cached = result
            cachedKey = key
            cachedAt = System.currentTimeMillis()
            return result
        }

        /**
         * Take one from each list in turn so no single source takes over
         */
        private fun interleave(lists: List<List<YouTubeVideo>>): List<YouTubeVideo> =
            buildList {
                val longest = lists.maxOfOrNull { it.size } ?: 0
                for (index in 0 until longest) {
                    lists.forEach { list -> list.getOrNull(index)?.let(::add) }
                }
            }

        companion object {
            private const val MAX_CHANNELS = 8
            private const val RELATED_SEEDS = 3
            private const val PER_CHANNEL = 4
            private const val PER_SEED = 8
            private const val MAX_RESULTS = 40
            private const val MAX_CONCURRENT = 3
            private const val CACHE_MS = 15 * 60 * 1000L
        }
    }
