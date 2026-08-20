package dev.trove.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.trove.app.TroveApplication
import java.io.File
import kotlinx.coroutines.flow.first

/**
 * Background feed sync: imports an OPML payload when provided, then fetches
 * all feeds and expires old unsaved items. Runs via WorkManager so it
 * survives the app going to the background (or being swiped away).
 */
class FeedSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TroveApplication
        val feedRepo = app.feedRepository
        val settingsRepo = app.settingsRepository

        return try {
            val imported = readOpmlFile()?.let { feedRepo.importOpml(it) } ?: 0

            val settings = settingsRepo.settings.first()
            val added = feedRepo.fetchAllFeeds()
            val expired = app.repository.cleanupFeedItems(settings.feedRetention)

            Result.success(workDataOf(KEY_IMPORTED to imported, KEY_ADDED to added, KEY_EXPIRED to expired))
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** Reads (and deletes) the staged OPML file, if any. */
    private fun readOpmlFile(): String? {
        val path = inputData.getString(KEY_OPML_FILE) ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            file.delete()
            content
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val KEY_OPML_FILE = "opml_file"
        const val KEY_IMPORTED = "imported"
        const val KEY_ADDED = "added"
        const val KEY_EXPIRED = "expired"

        const val SYNC_WORK = "pouch-feed-sync"
        const val OPML_WORK = "pouch-opml-import"
    }
}
