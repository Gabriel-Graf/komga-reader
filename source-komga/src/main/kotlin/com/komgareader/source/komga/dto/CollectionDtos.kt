package com.komgareader.source.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class CollectionDto(
    val id: String,
    val name: String,
    val ordered: Boolean = false,
    val seriesIds: List<String> = emptyList(),
    val lastModifiedDate: String = "",
)

@Serializable
data class CollectionCreationDto(
    val name: String,
    val ordered: Boolean = true,
    val seriesIds: List<String>,
)

@Serializable
data class CollectionUpdateDto(
    val name: String? = null,
    val ordered: Boolean? = null,
    val seriesIds: List<String>? = null,
)

@Serializable
data class ReadListDto(
    val id: String,
    val name: String,
    val summary: String = "",
    val ordered: Boolean = true,
    val bookIds: List<String> = emptyList(),
    val lastModifiedDate: String = "",
)

@Serializable
data class ReadListCreationDto(
    val name: String,
    val summary: String = "",
    val ordered: Boolean = true,
    val bookIds: List<String>,
)

@Serializable
data class ReadListUpdateDto(
    val name: String? = null,
    val summary: String? = null,
    val ordered: Boolean? = null,
    val bookIds: List<String>? = null,
)

/** Ausschnitt aus GET /users/me — nur die Rollen für die Schreib-Capability. */
@Serializable
data class KomgaUserDto(val roles: List<String> = emptyList())
