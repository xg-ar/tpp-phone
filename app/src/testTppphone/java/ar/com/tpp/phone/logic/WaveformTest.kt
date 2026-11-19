package ar.com.tpp.phone.logic

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformTest {
    @Test
    fun playedBarsFollowsProgress() {
        assertEquals(0, playedBars(0f, 64))
        assertEquals(32, playedBars(0.5f, 64))
        assertEquals(64, playedBars(1f, 64))
    }

    @Test
    fun playedBarsClampsProgress() {
        assertEquals(64, playedBars(1.7f, 64))
        assertEquals(0, playedBars(-0.3f, 64))
        assertEquals(0, playedBars(Float.NaN, 64))
    }

    @Test
    fun seekFractionClampsToWidth() {
        assertEquals(0f, seekFraction(-5f, 100f), 0f)
        assertEquals(0.5f, seekFraction(50f, 100f), 0f)
        assertEquals(1f, seekFraction(150f, 100f), 0f)
    }

    @Test
    fun seekFractionWithNoWidthIsZero() {
        assertEquals(0f, seekFraction(10f, 0f), 0f)
    }

    @Test
    fun progressOfPosition() {
        assertEquals(0f, playbackProgress(0, 10_000), 0f)
        assertEquals(0.25f, playbackProgress(2_500, 10_000), 0f)
        assertEquals(1f, playbackProgress(12_000, 10_000), 0f)
        assertEquals(0f, playbackProgress(500, 0), 0f)
    }

    @Test
    fun elapsedTextWhilePlaying() {
        assertEquals("0:00", elapsedText(0.0f, 75))
        assertEquals("0:37", elapsedText(0.5f, 75))
        assertEquals("1:15", elapsedText(1f, 75))
    }

    @Test
    fun shortNotificationOnlyLowersTheVolume() {
        assertFalse(pausesOnFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertFalse(pausesOnFocusChange(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test
    fun callOrOtherAudioPauses() {
        assertTrue(pausesOnFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertTrue(pausesOnFocusChange(AudioManager.AUDIOFOCUS_LOSS))
    }
}
