package com.komgareader.domain.usecase

import com.komgareader.domain.model.NovelBookmark

/** Outcome of tapping a word: either set a new bookmark (with this number) or remove an existing one (by id). */
sealed interface ToggleResult {
    data class Set(val number: Int) : ToggleResult
    data class Remove(val id: Long) : ToggleResult
}

/** Next free bookmark number: monotonic max+1, never reusing gaps. Empty → 1. */
fun nextBookmarkNumber(existing: List<Int>): Int = (existing.maxOrNull() ?: 0) + 1

/**
 * Toggle the bookmark at [xpointer] against [existing]: if a bookmark already
 * has this xpointer, remove it; otherwise set a new one with the next number.
 */
fun toggleBookmark(existing: List<NovelBookmark>, xpointer: String): ToggleResult {
    val match = existing.firstOrNull { it.xpointer == xpointer }
    return if (match != null) ToggleResult.Remove(match.id)
    else ToggleResult.Set(nextBookmarkNumber(existing.map { it.number }))
}
