package com.komgareader.source.opds

/**
 * Ein einzelner Atom-Eintrag aus einem OPDS-Feed.
 * [coverHref] ist null wenn kein Cover-Link vorhanden.
 * [acquisitionHref] ist null wenn kein Acquisition-Link vorhanden.
 */
data class OpdsEntry(
    val id: String,
    val title: String,
    val coverHref: String?,
    val acquisitionHref: String?,
    val acquisitionType: String?,
)
