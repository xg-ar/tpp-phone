// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.prov

import android.content.Context
import java.io.File
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

// The last list is kept in SharedPreferences, in prov's own format, so it shows without network.

/** SharedPreferences file with the last list (and the SIP user it belongs to). */
const val VOICE_PREFS = "tpp_voice_messages"

/** Folder under cacheDir with the downloaded audios. */
const val VOICE_AUDIO_DIR = "voz"

/** prov keeps each message 30 days. */
private const val KEEP_MS = 30L * 86_400_000L
private const val DAY_MS = 86_400_000L

fun messagesToCache(list: List<VoiceMessage>): String {
    val arr = JSONArray()
    list.forEach { m ->
        arr.put(
            JSONObject()
                .put("id", m.id)
                .put("fecha", Instant.ofEpochMilli(m.dateEpochMs).toString())
                .put("duracion_s", m.durationSec)
                .put("llamante", m.caller)
                .put("onda", JSONArray(m.waveform))
                .put("escuchado", m.heard)
                .put("dias_restantes", m.daysLeft)
        )
    }
    return JSONObject().put("mensajes", arr).toString()
}

fun messagesFromCache(json: String?): List<VoiceMessage> =
    (parseMessages(200, json) as? MessagesResult.Ok)?.list ?: emptyList()

/**
 * The cached list as it is [nowMs]: messages past their 30 days are dropped, and the days left are
 * whole days until expiry, floor((date + 30 d - now) / 1 d), the same as prov's dias_restantes.
 */
fun freshCachedMessages(list: List<VoiceMessage>, nowMs: Long): List<VoiceMessage> =
    list.filter { it.dateEpochMs + KEEP_MS >= nowMs }
        .map { it.copy(daysLeft = maxOf(0L, Math.floorDiv(it.dateEpochMs + KEEP_MS - nowMs, DAY_MS)).toInt()) }

/** Deletes the downloaded audios (cacheDir/voz). */
fun deleteVoiceAudio(cacheDir: File) {
    File(cacheDir, VOICE_AUDIO_DIR).deleteRecursively()
}

/** "Eliminar mi cuenta": nothing of the line's voice messages stays on the phone. Off the main thread. */
fun clearVoiceMessages(context: Context) {
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    deleteVoiceAudio(context.cacheDir)
}

/** Audio cache files (cacheDir/voz) whose message is not in the list anymore; "<id>.webm" or "<id>.webm.tmp". */
fun staleAudioFiles(fileNames: List<String>, keepIds: Set<String>): List<String> =
    fileNames.filter { it.substringBefore('.') !in keepIds }

fun markHeard(list: List<VoiceMessage>, id: String): List<VoiceMessage> =
    list.map { if (it.id == id && !it.heard) it.copy(heard = true) else it }

fun without(list: List<VoiceMessage>, id: String): List<VoiceMessage> = list.filter { it.id != id }
