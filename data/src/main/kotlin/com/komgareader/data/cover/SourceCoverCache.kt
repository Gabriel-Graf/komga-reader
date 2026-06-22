package com.komgareader.data.cover

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Stable cache-file key for a source cover, keyed by the work itself (no downloaded file needed). */
fun sourceCoverKey(sourceId: Long, remoteId: String, isSeries: Boolean): String =
    sha256Hex("$sourceId|$remoteId|${if (isSeries) "series" else "book"}")

/**
 * Persistent cache of raw cover bytes fetched from a source ([com.komgareader.domain.source.BrowsableSource.coverBytes],
 * Seam A) while online, keyed by (sourceId, remoteId, isSeries) — independent of any downloaded file.
 * Lets covers for works that are NOT downloaded (e.g. collection members, which are series that need not
 * be downloaded) still render offline, so cover tiles never go blank once seen online. Populated by the
 * collection-cover prewarm and write-through in the Coil cover fetcher; pruned to the still-wanted keys
 * via [coverPrunePlan].
 *
 * Distinct from [LocalCoverStore], which renders a cover from a downloaded file (signature-keyed); this
 * one persists the source's own thumbnail bytes for works that have no local file.
 */
@Singleton
class SourceCoverCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File by lazy { File(context.filesDir, "source-covers").apply { mkdirs() } }

    private fun fileFor(sourceId: Long, remoteId: String, isSeries: Boolean): File =
        File(dir, "${sourceCoverKey(sourceId, remoteId, isSeries)}.png")

    /** Cached bytes for this cover, or `null` on a cold miss. */
    suspend fun get(sourceId: Long, remoteId: String, isSeries: Boolean): ByteArray? =
        withContext(Dispatchers.IO) {
            val f = fileFor(sourceId, remoteId, isSeries)
            if (f.isFile && f.length() > 0L) runCatching { f.readBytes() }.getOrNull() else null
        }

    /** True if a non-empty cover is already cached (lets the prewarm skip a redundant network fetch). */
    suspend fun has(sourceId: Long, remoteId: String, isSeries: Boolean): Boolean =
        withContext(Dispatchers.IO) { fileFor(sourceId, remoteId, isSeries).let { it.isFile && it.length() > 0L } }

    /** Persists [bytes] for this cover if non-empty and not already cached (write-through, idempotent). */
    suspend fun putIfAbsent(sourceId: Long, remoteId: String, isSeries: Boolean, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        withContext(Dispatchers.IO) {
            val f = fileFor(sourceId, remoteId, isSeries)
            if (f.isFile && f.length() > 0L) return@withContext
            runCatching {
                File(dir, "${f.name}.tmp").let { tmp -> tmp.writeBytes(bytes); tmp.renameTo(f) }
            }
        }
    }

    /** Deletes cached covers whose key is not in [keepKeys] (prune to the current members). */
    suspend fun keepOnly(keepKeys: Set<String>) {
        withContext(Dispatchers.IO) {
            val existing = dir.list()?.toSet().orEmpty()
            coverPrunePlan(existing, keepKeys).forEach { runCatching { File(dir, it).delete() } }
        }
    }
}
