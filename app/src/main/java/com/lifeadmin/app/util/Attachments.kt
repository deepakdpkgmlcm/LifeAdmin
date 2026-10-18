package com.lifeadmin.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Photos and files kept with an item, stored privately inside the app. */
object Attachments {
    const val MAX_BYTES = 25L * 1024 * 1024

    fun authority(context: Context) = context.packageName + ".files"

    private fun dir(context: Context) = File(context.filesDir, "attachments").apply { mkdirs() }

    fun file(context: Context, a: Attachment) = File(dir(context), a.fileName)

    /** Copies [uri] into the app and links it to the item. Returns null if it can't be read or is too large. */
    suspend fun add(context: Context, itemId: Long, uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "Attachment"
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        }
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: name.substringAfterLast('.', "bin")
        if (name == "Attachment" || name.startsWith("scan_")) {
            name = if (mime.startsWith("image/")) "Photo.$ext" else "File.$ext"
        }
        val fileName = UUID.randomUUID().toString() + "." + ext
        val target = File(dir(context), fileName)
        val ok = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) error("too large")
                        out.write(buf, 0, n)
                    }
                }
            } ?: error("unreadable")
        }.isSuccess
        if (!ok) {
            target.delete()
            return@withContext null
        }
        val a = Attachment(itemId = itemId, name = name, mimeType = mime, fileName = fileName)
        a.copy(id = AppDatabase.get(context).attachmentDao().insert(a))
    }

    /** Opens the file in whatever app handles it (gallery, PDF viewer...). */
    fun open(context: Context, a: Attachment): Boolean {
        val uri = FileProvider.getUriForFile(context, authority(context), file(context, a))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, a.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    suspend fun delete(context: Context, a: Attachment) = withContext(Dispatchers.IO) {
        file(context, a).delete()
        AppDatabase.get(context).attachmentDao().deleteById(a.id)
    }

    /** Removes files whose item no longer exists (after a delete that wasn't undone), and old camera shots. */
    suspend fun cleanUp(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val ids = db.itemDao().getAll().map { it.id }.toSet()
        db.attachmentDao().getAll().filter { it.itemId !in ids }.forEach { delete(context, it) }
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        File(context.cacheDir, "scans").listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.delete() }
    }
}
