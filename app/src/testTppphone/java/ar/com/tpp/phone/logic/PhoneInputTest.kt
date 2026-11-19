package ar.com.tpp.phone.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneInputTest {
    @Test
    fun argentinaVariants() {
        val want = "5491162406120"
        for (s in listOf(
            "1162406120", "11 6240-6120", "01162406120", "11 15 6240 6120",
            "011 15-6240-6120", "+54 9 11 6240 6120", "91162406120", "5491162406120"
        )) {
            assertEquals(s, want, toE164("AR", s))
        }
    }

    @Test
    fun argentinaInterior() = assertEquals("5493515123456", toE164("AR", "0351 15 512-3456"))

    @Test
    fun bolivia() = assertEquals("59171234567", toE164("BO", "71234567"))

    @Test
    fun paraguay() = assertEquals("595981123456", toE164("PY", "0981 123456"))

    @Test
    fun internationalPrefixWins() = assertEquals("59171234567", toE164("AR", "+591 71234567"))

    @Test
    fun rejectsGarbage() {
        assertNull(toE164("AR", "123"))
        assertNull(toE164("AR", ""))
        assertNull(toE164("AR", "abc"))
    }

    @Test
    fun displaysTheWhatsAppNumber() {
        assertEquals("+54 9 11 5328-2884", displayNumber("5491153282884"))
        assertEquals("+591 71234567", displayNumber("59171234567"))
    }
}
