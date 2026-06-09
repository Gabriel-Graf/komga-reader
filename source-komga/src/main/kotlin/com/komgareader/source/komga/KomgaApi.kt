package com.komgareader.source.komga

import com.komgareader.source.komga.dto.BookDto
import com.komgareader.source.komga.dto.CollectionCreationDto
import com.komgareader.source.komga.dto.CollectionDto
import com.komgareader.source.komga.dto.CollectionUpdateDto
import com.komgareader.source.komga.dto.KomgaPage
import com.komgareader.source.komga.dto.KomgaUserDto
import com.komgareader.source.komga.dto.LibraryDto
import com.komgareader.source.komga.dto.PageDto
import com.komgareader.source.komga.dto.ReadListCreationDto
import com.komgareader.source.komga.dto.ReadListDto
import com.komgareader.source.komga.dto.ReadListUpdateDto
import com.komgareader.source.komga.dto.ReadProgressUpdateDto
import com.komgareader.source.komga.dto.SeriesDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
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
        @Query("library_id") libraryIds: List<String>? = null,
    ): KomgaPage<SeriesDto>

    @GET("libraries")
    suspend fun listLibraries(): List<LibraryDto>

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

    @GET("series/{id}/thumbnail")
    @Streaming
    suspend fun getSeriesThumbnail(@Path("id") seriesId: String): ResponseBody

    @GET("books/{id}/thumbnail")
    @Streaming
    suspend fun getBookThumbnail(@Path("id") bookId: String): ResponseBody

    @PATCH("books/{id}/read-progress")
    suspend fun updateProgress(
        @Path("id") bookId: String,
        @Body body: ReadProgressUpdateDto,
    )

    @DELETE("books/{id}/read-progress")
    suspend fun deleteProgress(@Path("id") bookId: String)

    @GET("users/me")
    suspend fun getMe(): KomgaUserDto

    @GET("collections")
    suspend fun listCollections(
        @Query("unpaged") unpaged: Boolean = true,
    ): KomgaPage<CollectionDto>

    @POST("collections")
    suspend fun createCollection(@Body body: CollectionCreationDto): CollectionDto

    @PATCH("collections/{id}")
    suspend fun updateCollection(@Path("id") id: String, @Body body: CollectionUpdateDto)

    @DELETE("collections/{id}")
    suspend fun deleteCollection(@Path("id") id: String)

    @GET("readlists")
    suspend fun listReadLists(
        @Query("unpaged") unpaged: Boolean = true,
    ): KomgaPage<ReadListDto>

    @POST("readlists")
    suspend fun createReadList(@Body body: ReadListCreationDto): ReadListDto

    @PATCH("readlists/{id}")
    suspend fun updateReadList(@Path("id") id: String, @Body body: ReadListUpdateDto)

    @DELETE("readlists/{id}")
    suspend fun deleteReadList(@Path("id") id: String)
}
