package app.beansontoast.brume.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.beansontoast.brume.core.sync.SyncFile
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The Storage Access Framework side of folder sync, copied from pastille's SyncFolder (2026-10-29).
 * The storage app (Google Drive, a local folder, Nextcloud…) does any uploading through its own
 * DocumentsProvider; Brume only reads and writes one document, and holds no network permission.
 */
object SyncFolder {

    /**
     * Finds or creates [SyncFile.NAME] in the folder [tree]. Throws when the folder can't be listed:
     * a failed listing is never read as "no file", which would create a duplicate.
     */
    fun resolve(context: Context, tree: Uri): Uri {
        val resolver = context.contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val children = resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId), columns, null, null, null)?.use { c ->
            val found = mutableListOf<SyncFile.Child>()
            while (c.moveToNext()) {
                val id: String = c.getString(0) ?: continue
                val modified: Long? = if (c.isNull(2)) null else c.getLong(2).takeIf { it > 0 }
                found += SyncFile.Child(id, c.getString(1), modified, c.getString(3) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
            found
        } ?: throw IOException("Folder listing unavailable")
        return when (val choice = SyncFile.choose(children)) {
            is SyncFile.Choice.Use -> DocumentsContract.buildDocumentUriUsingTree(tree, choice.documentId)
            SyncFile.Choice.Create -> DocumentsContract.createDocument(
                resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parentId), SyncFile.MIME, SyncFile.NAME,
            ) ?: throw FileNotFoundException("Could not create ${SyncFile.NAME}")
        }
    }

    /** The folder's name as the storage app shows it, or null when it doesn't say. */
    fun folderName(context: Context, tree: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun read(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: throw FileNotFoundException(uri.toString())

    /** "wt": truncate, so a shorter file never keeps the tail of the longer one it replaces. */
    fun write(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) } ?: throw FileNotFoundException(uri.toString())
    }
}
