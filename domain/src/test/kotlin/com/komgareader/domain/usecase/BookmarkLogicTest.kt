package com.komgareader.domain.usecase

import com.komgareader.domain.model.NovelBookmark
import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarkLogicTest {

    private fun bm(number: Int, xp: String) = NovelBookmark(
        id = number.toLong(), sourceId = 1, bookId = "b", xpointer = xp,
        number = number, label = null, snippet = "s", createdAt = 0,
    )

    @Test fun nextNumber_empty_is_one() {
        assertEquals(1, nextBookmarkNumber(emptyList()))
    }

    @Test fun nextNumber_is_max_plus_one() {
        assertEquals(3, nextBookmarkNumber(listOf(1, 2)))
    }

    @Test fun nextNumber_does_not_reuse_gaps() {
        assertEquals(3, nextBookmarkNumber(listOf(2)))
    }

    @Test fun toggle_sets_when_absent() {
        val res = toggleBookmark(emptyList(), "/p[1]/text()[1].0")
        assertEquals(ToggleResult.Set(1), res)
    }

    @Test fun toggle_sets_second_with_next_number() {
        val res = toggleBookmark(listOf(bm(1, "/a")), "/b")
        assertEquals(ToggleResult.Set(2), res)
    }

    @Test fun toggle_removes_when_present() {
        val existing = bm(1, "/a")
        val res = toggleBookmark(listOf(existing), "/a")
        assertEquals(ToggleResult.Remove(1L), res)
    }
}
