package ar.com.tpp.phone.logic

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpLinkTest {
    private val base = "https://wa.me/5491162406120"

    @Test
    fun sendsTheWatiKeywordOnly() {
        // The WhatsApp support bot answers "TPP Phone" with a support code for the chat's own number;
        // a number typed in the app would only mislead.
        val url = helpUrl(base)
        assertTrue(url, url.startsWith("$base?text="))
        assertEquals("TPP Phone", URLDecoder.decode(url.substringAfter("?text="), "UTF-8"))
    }

    @Test
    fun spacesAreNotPlusSigns() = assertTrue(!helpUrl(base).contains("+"))

    @Test
    fun carriesTheHelpKey() {
        val text = URLDecoder.decode(helpUrl(base, "K7QF-2MXP").substringAfter("?text="), "UTF-8")
        assertEquals("TPP Phone K7QF-2MXP", text)
    }
}
