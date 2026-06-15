package com.komgareader.app.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IconAnimationPlanTest {
    @Test fun `lcd spin is continuous`() {
        val p = iconAnimationPlan(einkMode = false, IconAnimation.SpinClockwise)
        assertTrue(p.continuous); assertEquals(800, p.cycleMillis)
    }
    @Test fun `eink spin is a single fast turn`() {
        val p = iconAnimationPlan(einkMode = true, IconAnimation.SpinClockwise)
        assertFalse(p.continuous); assertEquals(400, p.cycleMillis)
    }
    @Test fun `lcd bob is continuous and slow`() {
        val p = iconAnimationPlan(einkMode = false, IconAnimation.BobVertical)
        assertTrue(p.continuous); assertEquals(1200, p.cycleMillis)
    }
    @Test fun `eink bob is a single bounded cycle`() {
        val p = iconAnimationPlan(einkMode = true, IconAnimation.BobVertical)
        assertFalse(p.continuous); assertEquals(600, p.cycleMillis)
    }
}
