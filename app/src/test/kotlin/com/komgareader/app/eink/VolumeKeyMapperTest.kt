package com.komgareader.app.eink

import android.view.KeyEvent
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.HardwareButton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VolumeKeyMapperTest {
    @Test fun `volume up turns page back`() =
        assertEquals(ButtonEvent(HardwareButton.PAGE_PREV), volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_UP))

    @Test fun `volume down turns page forward`() =
        assertEquals(ButtonEvent(HardwareButton.PAGE_NEXT), volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_DOWN))

    @Test fun `other keys are ignored`() =
        assertNull(volumeButtonEvent(KeyEvent.KEYCODE_A))
}
