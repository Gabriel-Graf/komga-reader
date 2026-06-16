package com.komgareader.app.eink

import android.view.KeyEvent
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.HardwareButton

/**
 * Pure mapping of a volume key to a page-turn [ButtonEvent]: Volume Up = previous page, Volume Down =
 * next page. Returns null for unrelated keys. (The Onyx Boox firmware intercepts volume keys in its
 * own policy layer before the app, so a held press collapses to a tap and is unusable for shortcuts —
 * verified on device; see MainActivity. Volume keys therefore only page-turn.)
 */
fun volumeButtonEvent(keyCode: Int): ButtonEvent? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP -> ButtonEvent(HardwareButton.PAGE_PREV)
    KeyEvent.KEYCODE_VOLUME_DOWN -> ButtonEvent(HardwareButton.PAGE_NEXT)
    else -> null
}
