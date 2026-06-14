package com.komgareader.source.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * SAF tree walk over the picked root: subfolders (one level) become series folders,
 * files inside them are books; loose root files become single-volume series. Also
 * resolves a relativePath back to its content Uri for reading.
 */
class LocalFolderScanner(private val context: Context, private val rootTreeUri: Uri) {

    private val root: DocumentFile? get() = DocumentFile.fromTreeUri(context, rootTreeUri)

    fun scan(): List<ScannedEntry> {
        val r = root ?: return emptyList()
        val out = mutableListOf<ScannedEntry>()
        r.listFiles().forEach { child ->
            if (child.isDirectory) {
                val folderName = child.name ?: return@forEach
                out += ScannedEntry(folderName, isDirectory = true)
                child.listFiles().forEach { f ->
                    if (!f.isDirectory) {
                        val name = f.name ?: return@forEach
                        out += ScannedEntry("$folderName/$name", isDirectory = false, sizeBytes = f.length())
                    }
                }
            } else {
                val name = child.name ?: return@forEach
                out += ScannedEntry(name, isDirectory = false, sizeBytes = child.length())
            }
        }
        return out
    }

    fun uriOf(relativePath: String): Uri? {
        var node: DocumentFile = root ?: return null
        for (seg in relativePath.split('/')) {
            node = node.findFile(seg) ?: return null
        }
        return node.uri
    }

    /**
     * O(1) content-URI for a child relative path, built from the tree document id (no `findFile`
     * walk) — for bulk use (download-table sync). The SAF child document id is
     * `<treeDocId>/<relativePath>`. Returns a readable document URI for the granted tree.
     */
    fun documentUri(relativePath: String): String? = runCatching {
        val treeDocId = DocumentsContract.getTreeDocumentId(rootTreeUri)
        val childDocId = "$treeDocId/$relativePath"
        DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, childDocId).toString()
    }.getOrNull()
}
