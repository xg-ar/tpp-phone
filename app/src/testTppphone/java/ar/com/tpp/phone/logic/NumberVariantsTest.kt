// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberVariantsTest {
    @Test
    fun localNumberGetsArgentinaPrefixes() {
        val v = numberVariants("1141526000")
        assertTrue(v.containsAll(listOf("1141526000", "+541141526000", "01141526000", "541141526000")))
    }

    @Test
    fun argentinaNumberGetsLocalAndPlusForms() {
        val v = numberVariants("541141526000")
        assertTrue(v.containsAll(listOf("541141526000", "01141526000", "+541141526000", "+541141526000")))
        assertEquals("541141526000", v.first())
    }

    @Test
    fun emptyGivesEmptyList() {
        assertTrue(numberVariants("").isEmpty())
    }

    @Test
    fun noDuplicates() {
        for (ani in listOf("1141526000", "541141526000", "+541141526000")) {
            val v = numberVariants(ani)
            assertEquals(v.distinct(), v)
        }
    }
}
