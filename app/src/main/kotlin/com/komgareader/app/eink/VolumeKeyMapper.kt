package com.komgareader.app.eink

import android.view.KeyEvent
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.eink.PressKind

/**
 * Pure mapping of a volume key + press duration to a [ButtonEvent].
 * Short presses keep the existing page-turn semantics (emitted as PAGE_PREV/PAGE_NEXT so the
 * reader's page-turn collector is unchanged). Long presses are emitted as the raw VOLUME_* button
 * with [PressKind.LONG] for the shortcut layer (Home / refresh). Returns null for unrelated keys.
 */
fun volumeButtonEvent(keyCode: Int, longPress: Boolean): ButtonEvent? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP ->
        if (longPress) ButtonEvent(HardwareButton.VOLUME_UP, PressKind.LONG)
        else ButtonEvent(HardwareButton.PAGE_PREV, PressKind.SHORT)
    KeyEvent.KEYCODE_VOLUME_DOWN ->
        if (longPress) ButtonEvent(HardwareButton.VOLUME_DOWN, PressKind.LONG)
        else ButtonEvent(HardwareButton.PAGE_NEXT, PressKind.SHORT)
    else -> null
}
