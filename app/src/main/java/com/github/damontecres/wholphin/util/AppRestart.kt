package com.github.damontecres.wholphin.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.core.content.IntentCompat
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import timber.log.Timber

/**
 * Clears cached data (images & other files in the cache directory).
 *
 * This does not touch servers, users, or saved logins which are stored in the database & datastore, not the cache.
 */
@OptIn(ExperimentalCoilApi::class)
fun clearAppCache(context: Context) {
    SingletonImageLoader.get(context).let {
        it.memoryCache?.clear()
        it.diskCache?.clear()
    }
    context.cacheDir.listFiles()?.forEach {
        if (!it.deleteRecursively()) Timber.w("Could not delete cache entry %s", it)
    }
}

/**
 * Fully kills the app process and launches it again
 */
fun hardRestart(context: Context) {
    val launchIntent =
        context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (launchIntent == null) {
        Timber.w("No launch intent found, cannot restart")
        return
    }
    // The helper runs in a separate process so it survives this process being killed
    val intent =
        Intent(context, RestartActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_LAUNCH_INTENT, launchIntent)
            .putExtra(EXTRA_PID, Process.myPid())
    context.startActivity(intent)
    if (context is Activity) context.finish()
}

private const val EXTRA_LAUNCH_INTENT = "launchIntent"
private const val EXTRA_PID = "pid"

/**
 * Runs in the `:restart` process: starts the app's launcher activity, then kills the original process
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchIntent = IntentCompat.getParcelableExtra(intent, EXTRA_LAUNCH_INTENT, Intent::class.java)
        val pid = intent.getIntExtra(EXTRA_PID, -1)
        if (pid > 0) Process.killProcess(pid)
        launchIntent?.let { startActivity(it) }
        finish()
        Runtime.getRuntime().exit(0)
    }
}
