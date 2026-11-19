package ar.com.tpp.phone.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProvSecretTest {
    @Test
    fun passwordWhenLinphoneStillHasIt() = assertEquals("pw", provSecret("pw", "abc"))

    @Test
    fun ha1OnceLinphoneDroppedThePassword() {
        assertEquals("abc", provSecret(null, "abc"))
        assertEquals("abc", provSecret("", "abc"))
    }

    @Test
    fun nothingToSend() = assertNull(provSecret(null, null))
}
