package com.komgareader.app.data

import com.komgareader.domain.repository.ServerConfig
import com.komgareader.source.komga.KomgaSource
import com.komgareader.source.komga.KomgaSourceFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Baut aus einer persistierten [ServerConfig] eine [KomgaSource]. Null = nicht verbunden. */
@Singleton
class KomgaSourceProvider @Inject constructor() {
    fun from(config: ServerConfig?): KomgaSource? = config?.let {
        KomgaSourceFactory.create(
            name = it.name,
            baseUrl = it.baseUrl,
            apiKey = it.apiKey,
            username = it.username,
            password = it.password,
        )
    }
}
