package ar.com.tpp.phone.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class LegalTextTest {
    private val bundled = "<p><b>1. Prestador</b><br>copia de la app</p>"

    @Test
    fun usesTheTextFromProv() {
        val remote = "<!-- terms -->\n<p><b>1. Prestador</b><br>texto nuevo de prov</p>"
        assertEquals(remote, pickLegalText(remote, bundled))
    }

    @Test
    fun keepsTheBundledCopyWithoutAnAnswer() = assertEquals(bundled, pickLegalText(null, bundled))

    @Test
    fun keepsTheBundledCopyWhenEmpty() = assertEquals(bundled, pickLegalText("  \n", bundled))

    @Test
    fun keepsTheBundledCopyForAFullPage() {
        // A captive portal or an error page from a proxy, never a fragment from prov.
        val page = "<!doctype html><html><body><p>Iniciá sesión en la red WiFi</p></body></html>"
        assertEquals(bundled, pickLegalText(page, bundled))
    }

    @Test
    fun keepsTheBundledCopyWithoutParagraphs() = assertEquals(bundled, pickLegalText("not found", bundled))

    @Test
    fun keepsTheBundledCopyWhenTooLong() {
        val huge = "<p>" + "x".repeat(MAX_LEGAL_TEXT_CHARS) + "</p>"
        assertEquals(bundled, pickLegalText(huge, bundled))
    }
}
