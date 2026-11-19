package ar.com.tpp.phone.logic

import ar.com.tpp.phone.prov.VoiceMessage
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMessageRowTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val now = ZonedDateTime.of(2026, 10, 24, 21, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val at = ZonedDateTime.of(2026, 10, 24, 18, 42, 0, 0, zone).toInstant().toEpochMilli()

    private fun msg(caller: String = "5491155551234", dur: Int = 65, heard: Boolean = false, days: Int = 5) =
        VoiceMessage("id", at, dur, caller, List(64) { 0 }, heard, days)

    private fun row(m: VoiceMessage, name: String? = null) = voiceMessageRow(m, name, now, zone)

    @Test
    fun durationAndWhen() {
        val r = row(msg())
        assertEquals("1:05", r.durationText)
        assertEquals("Hoy, 18:42", r.whenText)
        assertEquals("0:09", row(msg(dur = 9)).durationText)
    }

    @Test
    fun expiryTexts() {
        assertEquals("Se borra en 5 días", row(msg(days = 5)).expiryText)
        assertEquals("Se borra mañana", row(msg(days = 1)).expiryText)
        assertEquals("Se borra hoy", row(msg(days = 0)).expiryText)
    }

    @Test
    fun titles() {
        val named = row(msg(), "Juan")
        assertEquals("Juan", named.title)
        assertEquals("5491155551234", named.subtitle)
        val plain = row(msg())
        assertEquals("5491155551234", plain.title)
        assertNull(plain.subtitle)
        val hidden = row(msg(caller = ""), "Juan")
        assertEquals("Número oculto", hidden.title)
        assertNull(hidden.subtitle)
    }

    @Test
    fun unheardIsNotHeard() {
        assertTrue(row(msg(heard = false)).unheard)
        assertFalse(row(msg(heard = true)).unheard)
    }
}
