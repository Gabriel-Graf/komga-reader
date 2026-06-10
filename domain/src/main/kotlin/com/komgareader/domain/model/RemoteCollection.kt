package com.komgareader.domain.model

/** Eine vom Server gehaltene Collection/Read-List (innerhalb EINER Quelle). */
data class RemoteCollection(
    val remoteId: String,
    val name: String,
    val memberRemoteIds: List<String>,
    val updatedAt: Long,   // UTC epoch millis (GMT), niemals zonenbehaftet
)
