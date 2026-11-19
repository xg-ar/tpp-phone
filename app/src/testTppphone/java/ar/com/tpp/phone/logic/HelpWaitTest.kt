package ar.com.tpp.phone.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpWaitTest {
    private val start = 1_000_000L

    @Test
    fun waitsUpToTenMinutes() {
        assertFalse(helpWaitOver(start, start + 9 * 60_000L))
        assertTrue(helpWaitOver(start, start + 10 * 60_000L))
    }
}
