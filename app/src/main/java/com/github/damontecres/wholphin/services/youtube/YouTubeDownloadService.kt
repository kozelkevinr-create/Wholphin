package com.github.damontecres.wholphin.services.youtube

import android.content.Context
import androidx.core.content.edit
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks a download server (yt-dlp behind a small HTTP API) to save YouTube videos & playlists.
 *
 * The server's URL & token are entered in the app and stored on the device.
 */
@Singleton
class YouTubeDownloadService
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        var serverUrl: String
            get() = prefs.getString(KEY_URL, null).orEmpty()
            set(value) = prefs.edit { putString(KEY_URL, value.trim().trimEnd('/')) }

        var token: String
            get() = prefs.getString(KEY_TOKEN, null).orEmpty()
            set(value) = prefs.edit { putString(KEY_TOKEN, value.trim()) }

        val isConfigured: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()

        /**
         * Queue a download, returns the server's job id
         */
        suspend fun download(
            url: String,
            playlist: Boolean,
        ): String =
            withContext(WholphinDispatchers.IO) {
                check(isConfigured) { "Download server is not configured" }
                val body =
                    JSONObject()
                        .put("url", url)
                        .put("playlist", playlist)
                        .toString()
                        .toRequestBody("application/json".toMediaType())
                val request =
                    Request
                        .Builder()
                        .url("$serverUrl/download")
                        .header("Authorization", "Bearer $token")
                        .post(body)
                        .build()
                okHttpClient.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (response.code != 202) {
                        throw IllegalStateException("Download server returned ${response.code}: ${text.take(200)}")
                    }
                    JSONObject(text).optString("id")
                }
            }

        /**
         * Recent downloads, newest first
         */
        suspend fun jobs(): List<DownloadJob> =
            withContext(WholphinDispatchers.IO) {
                check(isConfigured) { "Download server is not configured" }
                val request =
                    Request
                        .Builder()
                        .url("$serverUrl/jobs")
                        .header("Authorization", "Bearer $token")
                        .build()
                okHttpClient.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download server returned ${response.code}")
                    }
                    val array = JSONObject(text).optJSONArray("jobs")
                    List(array?.length() ?: 0) { i ->
                        val job = array!!.getJSONObject(i)
                        DownloadJob(
                            id = job.optString("id"),
                            url = job.optString("url"),
                            playlist = job.optBoolean("playlist"),
                            status = job.optString("status"),
                            title = job.optString("title").takeIf { it.isNotBlank() },
                            progress = job.optString("progress").takeIf { it.isNotBlank() },
                            error = job.optString("error").takeIf { it.isNotBlank() },
                        )
                    }
                }
            }

        companion object {
            private const val PREFS_NAME = "youtube_downloads"
            private const val KEY_URL = "server_url"
            private const val KEY_TOKEN = "token"

            private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

            /**
             * Whether [text] looks like a link to YouTube
             */
            fun isYouTubeUrl(text: String): Boolean =
                runCatching {
                    val uri = java.net.URI(text.trim())
                    uri.scheme == "https" && uri.host in YOUTUBE_HOSTS
                }.getOrDefault(false)
        }
    }

/**
 * A download on the server
 *
 * @param status one of queued, running, done, failed
 */
data class DownloadJob(
    val id: String,
    val url: String,
    val playlist: Boolean,
    val status: String,
    val title: String?,
    val progress: String?,
    val error: String?,
)
