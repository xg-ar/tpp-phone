package ar.com.tpp.phone.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePulseTest {
    @Test
    fun pulsesWhenReadyVisibleAndAnimationsOn() {
        assertTrue(shouldPulse(Status.READY, visible = true, animationsEnabled = true))
    }

    @Test
    fun stillWhenNotReady() {
        assertFalse(shouldPulse(Status.CONNECTING, visible = true, animationsEnabled = true))
        assertFalse(shouldPulse(Status.PROBLEM, visible = true, animationsEnabled = true))
        assertFalse(shouldPulse(null, visible = true, animationsEnabled = true))
    }

    @Test
    fun stillWhenScreenNotVisible() {
        assertFalse(shouldPulse(Status.READY, visible = false, animationsEnabled = true))
    }

    @Test
    fun stillWhenUserTurnedAnimationsOff() {
        assertFalse(shouldPulse(Status.READY, visible = true, animationsEnabled = false))
    }
}
