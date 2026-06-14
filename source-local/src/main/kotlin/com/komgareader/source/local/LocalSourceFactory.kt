package com.komgareader.source.local

import android.content.Context
import android.net.Uri
import com.komgareader.domain.source.SourceId

object LocalSourceFactory {
    /** Cheap — no scan here (lazy on first browse); safe from SourceRegistration.build/sourceIdOf. */
    fun create(context: Context, name: String, rootTreeUri: String): LocalSource = LocalSource(
        id = SourceId.LOCAL,
        name = name,
        scanner = LocalFolderScanner(context.applicationContext, Uri.parse(rootTreeUri)),
        cache = LocalFileCache(context.applicationContext),
    )
}
