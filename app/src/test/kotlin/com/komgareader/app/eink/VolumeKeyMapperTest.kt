package com.komgareader.app.eink

import android.view.KeyEvent
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.eink.PressKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VolumeKeyMapperTest {
    @Test fun `volume up short turns page back`() =
        assertEquals(ButtonEvent(HardwareButton.PAGE_PREV, PressKind.SHORT),
            volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_UP, longPress = false))

    @Test fun `volume down short turns page forward`() =
        assertEquals(ButtonEvent(HardwareButton.PAGE_NEXT, PressKind.SHORT),
            volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_DOWN, longPress = false))

    @Test fun `volume up long is a long VOLUME_UP event`() =
        assertEquals(ButtonEvent(HardwareButton.VOLUME_UP, PressKind.LONG),
            volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_UP, longPress = true))

    @Test fun `volume down long is a long VOLUME_DOWN event`() =
        assertEquals(ButtonEvent(HardwareButton.VOLUME_DOWN, PressKind.LONG),
            volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_DOWN, longPress = true))

    @Test fun `other keys are ignored`() =
        assertNull(volumeButtonEvent(KeyEvent.KEYCODE_A, longPress = false))
}
