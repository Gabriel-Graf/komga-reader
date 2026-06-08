package com.komgareader.app.data.coil

/**
 * Coil-Model für ein quellen-geladenes **Cover** (Titelbild). [isSeries] unterscheidet
 * Serien- von Buch-/Kapitel-Cover; [remoteId] ist die quellen-interne Serien- bzw. Buch-ID.
 */
data class SourceCover(val sourceId: Long, val remoteId: String, val isSeries: Boolean)
