package ar.com.tpp.phone.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class InmateDialNumberTest {
    @Test
    fun formatsBuenosAires() {
        assertEquals("9 11 2345 6789", inmateDialNumber("5491123456789"))
    }

    @Test
    fun formatsInterior() {
        assertEquals("9 221 456 7890", inmateDialNumber("5492214567890"))
    }

    @Test
    fun formatsForeignNumber() {
        assertEquals("9 59899123456", inmateDialNumber("59899123456"))
    }

    @Test
    fun stripsNonDigits() {
        assertEquals("9 11 2345 6789", inmateDialNumber("+54 9 11 2345-6789"))
    }
}
