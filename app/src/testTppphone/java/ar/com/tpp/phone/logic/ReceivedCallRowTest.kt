package ar.com.tpp.phone.logic

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceivedCallRowTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val now = ZonedDateTime.of(2026, 10, 24, 21, 0, 0, 0, zone)

    private fun at(month: Int, day: Int, hour: Int, minute: Int, year: Int = 2026) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun row(
        displayName: String?,
        start: Long,
        durationSec: Int,
        answered: Boolean,
        username: String = "1000"
    ) = receivedCallRow(displayName, username, start, durationSec, answered, now.toInstant().toEpochMilli(), zone)

    @Test
    fun answeredToday() {
        val r = row("TPP - Unidad 9 La Plata", at(10, 24, 18, 42), 720, true)
        assertEquals("TPP - Unidad 9 La Plata", r.title)
        assertEquals("Hoy, 18:42", r.whenText)
        assertEquals("12 min", r.detail)
        assertFalse(r.missed)
    }

    @Test
    fun missedYesterday() {
        val r = row("TPP - Unidad 9 La Plata", at(10, 23, 20, 15), 0, false)
        assertEquals("Ayer, 20:15", r.whenText)
        assertEquals("Perdida", r.detail)
        assertTrue(r.missed)
    }

    @Test
    fun olderThisYear() {
        assertEquals("18 oct, 19:05", row("TPP - Complejo Ezeiza", at(10, 18, 19, 5), 480, true).whenText)
    }

    @Test
    fun previousYearShowsYear() {
        assertEquals("30 dic 2025, 09:05", row("x", at(12, 30, 9, 5, 2025), 60, true).whenText)
    }

    @Test
    fun noDisplayNameUsesUsername() {
        assertEquals("1000", row(null, at(10, 24, 10, 0), 60, true).title)
        assertEquals("1000", row("  ", at(10, 24, 10, 0), 60, true).title)
    }

    @Test
    fun shortCallRoundsUpToOneMinute() {
        assertEquals("1 min", row("x", at(10, 24, 10, 0), 30, true).detail)
        assertEquals("1 min", row("x", at(10, 24, 10, 0), 0, true).detail)
    }
}
