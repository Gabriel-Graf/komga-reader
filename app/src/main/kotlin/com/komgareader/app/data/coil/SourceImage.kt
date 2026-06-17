package com.komgareader.app.data.coil

/** Coil-Model für ein quellen-geladenes Bild. pageNumber ist 1-basiert. */
data class SourceImage(
    val sourceId: Long,
    override val bookRemoteId: String,
    val pageNumber: Int,
) : ReaderPageImage {
    override val pageKey: Int get() = pageNumber
}
