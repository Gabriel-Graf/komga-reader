package com.komgareader.app.di

import javax.inject.Qualifier

/**
 * Qualifier für den app-weit lebenden [kotlinx.coroutines.CoroutineScope]
 * (siehe [AppModule.applicationScope]). Unterscheidet ihn von anderen
 * injizierbaren Scopes und macht die Injektionsstelle selbsterklärend.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
