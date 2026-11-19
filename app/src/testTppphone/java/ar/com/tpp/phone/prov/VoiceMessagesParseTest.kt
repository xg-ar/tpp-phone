package ar.com.tpp.phone.prov

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMessagesParseTest {
    private val wave = (0 until 64).joinToString(",") { (it % 32).toString() }
    private val two = """{"mensajes":[
        {"id":"a1","fecha":"2026-11-09T12:00:00Z","duracion_s":65,"llamante":"5491155551234","onda":[$wave],"escuchado":false,"dias_restantes":5},
        {"id":"b2","fecha":"2026-11-08T03:30:00Z","duracion_s":12,"llamante":"","onda":[$wave],"escuchado":true,"dias_restantes":0}]}"""

    @Test
    fun okWithTwoMessages() {
        val r = parseMessages(200, two) as MessagesResult.Ok
        assertEquals(2, r.list.size)
        val m = r.list[0]
        assertEquals("a1", m.id)
        assertEquals(1794225600000L, m.dateEpochMs)
        assertEquals(65, m.durationSec)
        assertEquals("5491155551234", m.caller)
        assertEquals(64, m.waveform.size)
        assertEquals(false, m.heard)
        assertEquals(5, m.daysLeft)
        assertEquals("", r.list[1].caller)
        assertTrue(r.list[1].heard)
    }

    @Test
    fun unauthorized() = assertEquals(MessagesResult.Unauthorized, parseMessages(401, null))

    @Test
    fun offline() = assertEquals(MessagesResult.Offline, parseMessages(-1, null))

    @Test
    fun serverErrorAndBrokenJson() {
        assertEquals(MessagesResult.Failed, parseMessages(500, "{}"))
        assertEquals(MessagesResult.Failed, parseMessages(200, "not json"))
        assertEquals(MessagesResult.Failed, parseMessages(200, null))
    }

    @Test
    fun waveformIsPaddedOrCutTo64() {
        val short = """{"mensajes":[{"id":"a","fecha":"2026-11-09T12:00:00Z","duracion_s":5,"llamante":"1","onda":[1,2,3],"escuchado":false,"dias_restantes":3}]}"""
        val long = short.replace("[1,2,3]", "[" + (0 until 100).joinToString(",") { "7" } + "]")
        val a = (parseMessages(200, short) as MessagesResult.Ok).list[0].waveform
        val b = (parseMessages(200, long) as MessagesResult.Ok).list[0].waveform
        assertEquals(64, a.size)
        assertEquals(listOf(1, 2, 3, 0), a.take(4))
        assertEquals(64, b.size)
    }

    private fun dateOf(f: String): Long {
        val j = """{"mensajes":[{"id":"a","fecha":"$f","duracion_s":5,"llamante":"1","onda":[],"escuchado":false,"dias_restantes":3}]}"""
        return (parseMessages(200, j) as MessagesResult.Ok).list[0].dateEpochMs
    }

    @Test
    fun dateFormats() {
        assertEquals(1794225600000L, dateOf("2026-11-09T12:00:00Z"))
        assertEquals(1794225600000L, dateOf("2026-11-09T12:00:00+00:00"))
        assertEquals(1794225600123L, dateOf("2026-11-09T12:00:00.123456+00:00"))
    }

    @Test
    fun waveformValuesAreClamped() {
        val j = """{"mensajes":[{"id":"a","fecha":"2026-11-09T12:00:00Z","duracion_s":5,"llamante":"1","onda":[-4,40,7],"escuchado":false,"dias_restantes":3}]}"""
        assertEquals(listOf(0, 31, 7), (parseMessages(200, j) as MessagesResult.Ok).list[0].waveform.take(3))
    }
}
