package com.komgareader.domain.model

/**
 * What the device standby/screensaver shows (E-Ink devices only — see
 * [com.komgareader.domain.eink.EinkController.setScreenSaverImage]).
 *
 * - [OFF]: the app does not manage the screensaver (the device keeps whatever is set).
 * - [CUSTOM]: a fixed user-picked image.
 * - [BOOK_COVER]: the cover of the book currently being read, refreshed each time a book opens.
 */
enum class ScreenSaverMode { OFF, CUSTOM, BOOK_COVER }
