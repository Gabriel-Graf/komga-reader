package com.komgareader.domain.model

/**
 * How a set novel bookmark is drawn on the page (three Kindle-like modes):
 * - [UNDERLINE]: a line just below the bookmarked word.
 * - [MARGIN]: a simple vertical line at the row's left margin.
 * - [FLAG]: a vertical stroke at the word's start carrying the bookmark number.
 */
enum class BookmarkMarkerStyle { UNDERLINE, MARGIN, FLAG }
