package ar.com.tpp.phone.prov

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMessagesCacheTest {
    private val a = VoiceMessage("a1", 1794225600123L, 65, "5491155551234", List(64) { it % 32 }, false, 5)
    private val b = VoiceMessage("b2", 1794100000000L, 12, "", List(64) { 0 }, true, 0)

    @Test
    fun roundTrip() {
        assertEquals(listOf(a, b), messagesFromCache(messagesToCache(listOf(a, b))))
    }

    @Test
    fun emptyAndBrokenCacheGiveEmptyList() {
        assertEquals(emptyList<VoiceMessage>(), messagesFromCache(messagesToCache(emptyList())))
        assertEquals(emptyList<VoiceMessage>(), messagesFromCache(null))
        assertEquals(emptyList<VoiceMessage>(), messagesFromCache("garbage"))
    }

    @Test
    fun staleAudioFilesAreTheOnesNotListed() {
        // A listed message's download in progress (.tmp) is kept.
        val files = listOf("a1.webm", "zz.webm", "b2.webm.tmp", "zz.webm.tmp", "other.txt")
        assertEquals(listOf("zz.webm", "zz.webm.tmp", "other.txt"), staleAudioFiles(files, setOf("a1", "b2")))
    }

    @Test
    fun staleAudioFilesWithEmptyListRemovesAll() {
        assertEquals(listOf("a1.webm"), staleAudioFiles(listOf("a1.webm"), emptySet()))
    }

    @Test
    fun markHeardOnlyTouchesThatMessage() {
        val r = markHeard(listOf(a, b.copy(heard = false)), "a1")
        assertTrue(r[0].heard)
        assertEquals(false, r[1].heard)
    }

    @Test
    fun heardFlagSurvivesTheCache() {
        val cached = messagesFromCache(messagesToCache(markHeard(listOf(a, b), "a1")))
        assertTrue(cached.first { it.id == "a1" }.heard)
        assertTrue(cached.first { it.id == "b2" }.heard)
    }

    @Test
    fun withoutRemovesById() {
        assertEquals(listOf(b), without(listOf(a, b), "a1"))
    }

    private val day = 86_400_000L
    private val now = 1794225600000L

    private fun at(ms: Long) = a.copy(id = "m$ms", dateEpochMs = ms, daysLeft = 99)

    @Test
    fun cachedListDropsExpiredMessages() {
        val fresh = at(now - 29 * day)
        val expired = at(now - 30 * day - 1)
        assertEquals(listOf(fresh.id), freshCachedMessages(listOf(fresh, expired), now).map { it.id })
    }

    @Test
    fun cachedListRecomputesWholeDaysLeft() {
        val r = freshCachedMessages(
            listOf(at(now), at(now - day / 2), at(now - 10 * day), at(now - 30 * day + 1), at(now - 30 * day)),
            now
        )
        // floor((date + 30 d - now) / 1 d); exactly 30 d old is still kept, with 0 days left.
        assertEquals(listOf(30, 29, 20, 0, 0), r.map { it.daysLeft })
    }

    @Test
    fun cachedListAheadOfThePhoneClockGivesThirtyDays() {
        // A message dated a bit ahead of the phone's clock.
        assertEquals(30, freshCachedMessages(listOf(at(now + day / 3)), now).single().daysLeft)
    }

    @Test
    fun deleteVoiceAudioRemovesTheFolder() {
        val cache = Files.createTempDirectory("tppcache").toFile()
        val voz = File(cache, VOICE_AUDIO_DIR).apply { mkdirs() }
        File(voz, "a1.webm").writeText("x")
        File(voz, "b2.webm.tmp").writeText("y")
        val other = File(cache, "other.txt").apply { writeText("z") }
        deleteVoiceAudio(cache)
        assertFalse(voz.exists())
        assertTrue(other.exists())
        deleteVoiceAudio(cache) // nothing left: no error
        cache.deleteRecursively()
    }
}
