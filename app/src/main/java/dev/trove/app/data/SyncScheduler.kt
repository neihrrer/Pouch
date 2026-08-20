package dev.trove.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.trove.app.TroveApplication
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Schedules feed syncing: periodic refresh (per the feed interval setting),
 * an optional one-time sync on app start, and one-time OPML imports.
 */
object SyncScheduler {

    private const val OPML_IMPORT_FILE = "opml-import.xml"

    fun schedule(context: Context, interval: FeedFetchInterval, syncOnStart: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (interval == FeedFetchInterval.OFF) {
            workManager.cancelUniqueWork(FeedSyncWorker.SYNC_WORK)
        } else {
            val request = PeriodicWorkRequestBuilder<FeedSyncWorker>(interval.millis, TimeUnit.MILLISECONDS)
                .setConstraints(constraints(context))
                .build()
            workManager.enqueueUniquePeriodicWork(
                FeedSyncWorker.SYNC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
        if (syncOnStart) {
            val oneTime = OneTimeWorkRequestBuilder<FeedSyncWorker>()
                .setConstraints(constraints(context))
                .build()
            workManager.enqueueUniqueWork(
                "pouch-feed-sync-start",
                ExistingWorkPolicy.KEEP,
                oneTime,
            )
        }
    }

    fun importOpml(context: Context, opml: String) {
        // WorkManager Data is hard-limited to 10 KB (Data$Companion throws
        // IllegalStateException when serializing more) — an OPML file with
        // many feeds easily exceeds that, and build() runs on the main thread
        // in HomeViewModel. Stage the content in a temp file and pass only
        // the path; the worker reads (and deletes) it.
        val file = File(context.cacheDir, OPML_IMPORT_FILE)
        file.writeText(opml)
        val request = OneTimeWorkRequestBuilder<FeedSyncWorker>()
            .setConstraints(constraints(context))
            .setInputData(Data.Builder().putString(FeedSyncWorker.KEY_OPML_FILE, file.absolutePath).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FeedSyncWorker.OPML_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun constraints(context: Context): Constraints {
        val settings = kotlinx.coroutines.runBlocking {
            (context.applicationContext as TroveApplication).settingsRepository.settings.first()
        }
        return Constraints.Builder()
            .setRequiredNetworkType(if (settings.syncOnlyWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(settings.syncOnlyCharging)
            .build()
    }
}
