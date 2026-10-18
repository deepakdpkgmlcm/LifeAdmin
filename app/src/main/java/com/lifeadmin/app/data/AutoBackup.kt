package com.lifeadmin.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Saves a backup file into a folder the person chose (e.g. Google Drive or Downloads) once a week. */
object AutoBackup {
    private const val WORK = "auto_backup"
    private const val PREFIX = "lifeadmin-auto-"
    private const val KEEP = 5

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (AppSettings(context).autoBackupFolder.isBlank()) {
            wm.cancelUniqueWork(WORK)
            return
        }
        wm.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
        )
    }

    /** Writes one backup now. Returns true on success. */
    suspend fun runNow(context: Context): Boolean {
        val settings = AppSettings(context)
        val folder = settings.autoBackupFolder.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return false
        return runCatching {
            val db = AppDatabase.get(context)
            val json = Backup.toJson(db.itemDao().getAll(), db.historyDao().getAll())
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            val file = DocumentsContract.createDocument(resolver, parent, "application/json", "$PREFIX${LocalDate.now()}.json")
                ?: error("Couldn't create the file")
            resolver.openOutputStream(file)?.use { it.write(json.toByteArray()) } ?: error("Couldn't write")
            prune(context, folder)
            settings.lastAutoBackup = System.currentTimeMillis()
        }.isSuccess
    }

    /** Keeps the newest few automatic backups and removes older ones. */
    private fun prune(context: Context, folder: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val found = mutableListOf<Pair<String, String>>() // name, documentId
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                if (name.startsWith(PREFIX)) found += name to c.getString(1)
            }
        }
        found.sortedByDescending { it.first }.drop(KEEP).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id)) }
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (AutoBackup.runNow(applicationContext)) Result.success() else Result.retry()
}
