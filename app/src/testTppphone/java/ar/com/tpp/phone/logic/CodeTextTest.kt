package ar.com.tpp.phone.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeTextTest {
    @Test
    fun plainCode() = assertEquals("34027880", extractCode("34027880", 8))

    @Test
    fun codeWithASpaceOrDash() {
        assertEquals("34027880", extractCode("3402 7880", 8))
        assertEquals("34027880", extractCode("3402-7880", 8))
        assertEquals("123456", extractCode("123 456", 6))
    }

    @Test
    fun codeInsideTheBotMessage() = assertEquals(
        "34027880",
        extractCode("✅ Tu código de soporte de TPP Phone para el número +5491153282884 es: *3402 7880*\n\nEn la app…", 8)
    )

    @Test
    fun phoneNumbersAreNotCodes() {
        assertNull(extractCode("+5491153282884", 8))
        assertNull(extractCode("+5491153282884", 6))
    }

    @Test
    fun tooShortOrNothing() {
        assertNull(extractCode("3402 788", 8))
        assertNull(extractCode("", 6))
        assertNull(extractCode("hola", 6))
    }
}
