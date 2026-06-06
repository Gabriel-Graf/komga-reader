package com.komgareader.source.komga

import com.komgareader.source.komga.dto.BookDto
import com.komgareader.source.komga.dto.KomgaPage
import com.komgareader.source.komga.dto.PageDto
import com.komgareader.source.komga.dto.ReadProgressUpdateDto
import com.komgareader.source.komga.dto.SeriesDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Komga-REST-Endpunkte (v1). Pfade ohne führenden Slash → relativ zur baseUrl `.../api/v1/`. */
interface KomgaApi {

    @GET("series")
    suspend fun listSeries(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("search") search: String? = null,
    ): KomgaPage<SeriesDto>

    @GET("series/{id}")
    suspend fun getSeries(@Path("id") seriesId: String): SeriesDto

    @GET("series/{id}/books")
    suspend fun listBooks(
        @Path("id") seriesId: String,
        @Query("unpaged") unpaged: Boolean = true,
    ): KomgaPage<BookDto>

    @GET("books/{id}")
    suspend fun getBook(@Path("id") bookId: String): BookDto

    @GET("books/{id}/pages")
    suspend fun listPages(@Path("id") bookId: String): List<PageDto>

    @GET("books/{id}/pages/{number}")
    @Streaming
    suspend fun getPage(
        @Path("id") bookId: String,
        @Path("number") pageNumber: Int,
    ): ResponseBody

    @GET("books/{id}/file")
    @Streaming
    suspend fun getFile(@Path("id") bookId: String): ResponseBody

    @PATCH("books/{id}/read-progress")
    suspend fun updateProgress(
        @Path("id") bookId: String,
        @Body body: ReadProgressUpdateDto,
    )
}
