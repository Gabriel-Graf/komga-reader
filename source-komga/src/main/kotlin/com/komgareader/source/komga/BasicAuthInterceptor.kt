package com.komgareader.source.komga

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

/** Hängt HTTP-Basic-Auth-Credentials an jede Anfrage (`Authorization: Basic ...`). */
class BasicAuthInterceptor(private val username: String, private val password: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", Credentials.basic(username, password))
            .build()
        return chain.proceed(request)
    }
}
