package com.komgareader.source.komga

import okhttp3.Interceptor
import okhttp3.Response

/** Hängt den Komga-API-Key an jede Anfrage (`X-API-Key`). */
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-API-Key", apiKey)
            .build()
        return chain.proceed(request)
    }
}
