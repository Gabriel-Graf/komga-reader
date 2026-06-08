package com.komgareader.app.data.coil

/** Coil-Model für ein quellen-geladenes Bild. pageNumber ist 1-basiert. */
data class SourceImage(val sourceId: Long, val bookRemoteId: String, val pageNumber: Int)
