package com.komgareader.domain.source

/** Baut deterministisch [PageRef]s aus einer Seitenzahl — ohne Netzabruf, quellen-neutral.
 *  `url` bleibt leer; Bytes liefert die Quelle über [BrowsableSource.openPage]. */
fun buildPageRefs(bookRemoteId: String, pageCount: Int): List<PageRef> =
    (1..pageCount).map { n -> PageRef(index = n - 1, bookRemoteId = bookRemoteId, pageNumber = n, url = "") }
