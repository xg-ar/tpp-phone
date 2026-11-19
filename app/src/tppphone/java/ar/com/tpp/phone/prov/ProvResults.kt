// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.prov

import java.time.OffsetDateTime
import org.json.JSONException
import org.json.JSONObject

/** What prov hands over once a number is verified. */
data class LineConfig(val sipUser: String, val password: String, val domain: String, val dialNumber: String) {
    // Never let the SIP password reach a log by accident.
    override fun toString() = "LineConfig(sipUser=$sipUser, domain=$domain, dialNumber=$dialNumber)"
}

sealed interface StartResult {
    data class Sent(val verificationId: String, val resendAfterSec: Int) : StartResult

    data object InvalidNumber : StartResult

    data object RateLimited : StartResult

    /** No network, a 5xx or a body we don't understand. */
    data object Failed : StartResult
}

sealed interface ActivationResult {
    data class Activated(val config: LineConfig) : ActivationResult

    data class WrongCode(val attemptsLeft: Int) : ActivationResult

    /** 410: the code or help key expired, or is not this phone's. */
    data object Expired : ActivationResult

    /** 503 activacion_fallida: the telephony platform failed; the same code still works for a retry. */
    data object ActivationFailed : ActivationResult

    data object InvalidNumber : ActivationResult

    data object RateLimited : ActivationResult

    data object Failed : ActivationResult

    /** Help key: the WhatsApp message has not reached the bot yet (202). */
    data object Pending : ActivationResult
}

sealed interface HelpKeyResult {
    data class Created(val key: String) : HelpKeyResult

    data object RateLimited : HelpKeyResult

    data object Failed : HelpKeyResult
}

enum class LineStatus { ACTIVE, REPLACED, UNAUTHORIZED, UNKNOWN }

/** "Probalo" echo test call (prov POST /v1/prueba-llamada). */
enum class TestCallResult {
    SCHEDULED,

    /** 429 en_curso: a test for this line is already on its way (one at a time). */
    IN_PROGRESS,

    /** 429 linea: the line used today's tests. */
    LIMIT_DAY,

    /** 429 global (or no reason): many tests right now. */
    BUSY,
    UNAVAILABLE,
    UNAUTHORIZED,
    FAILED
}

// http is -1 when the request never got an answer. Decisions use the "error" field, not only
// the status: FastAPI's own validation errors are also 422, without it.

private fun json(body: String?): JSONObject? = try {
    body?.let { JSONObject(it) }
} catch (e: Exception) {
    null
}

private fun JSONObject.error(): String = optString("error")

fun parseStart(http: Int, body: String?): StartResult {
    val o = json(body) ?: return StartResult.Failed
    return when {
        http == 200 && o.optString("id_verificacion").isNotEmpty() ->
            StartResult.Sent(o.getString("id_verificacion"), o.optInt("reenviar_en", 30))
        o.error() == "numero_invalido" -> StartResult.InvalidNumber
        o.error() == "limite" -> StartResult.RateLimited
        else -> StartResult.Failed
    }
}

fun parseActivation(http: Int, body: String?): ActivationResult {
    val o = json(body) ?: return ActivationResult.Failed
    if (http == 200) {
        val fields = listOf("usuario_sip", "contrasena", "dominio", "numero_para_interno").map { o.optString(it) }
        if (fields.any { it.isEmpty() }) return ActivationResult.Failed
        return ActivationResult.Activated(LineConfig(fields[0], fields[1], fields[2], fields[3]))
    }
    return when (o.error()) {
        "codigo_incorrecto" -> ActivationResult.WrongCode(o.optInt("intentos_restantes", 0))
        "codigo_vencido" -> ActivationResult.Expired
        "activacion_fallida" -> ActivationResult.ActivationFailed
        "numero_invalido" -> ActivationResult.InvalidNumber
        "limite" -> ActivationResult.RateLimited
        else -> ActivationResult.Failed
    }
}

fun parseLineStatus(http: Int, body: String?): LineStatus {
    if (http == 401) return LineStatus.UNAUTHORIZED
    if (http != 200) return LineStatus.UNKNOWN
    return when (json(body)?.optString("estado")) {
        "activa" -> LineStatus.ACTIVE
        "reemplazada" -> LineStatus.REPLACED
        else -> LineStatus.UNKNOWN
    }
}

fun parseHelpKey(http: Int, body: String?): HelpKeyResult {
    val o = json(body) ?: return HelpKeyResult.Failed
    return when {
        http == 200 && o.optString("clave").isNotEmpty() -> HelpKeyResult.Created(o.getString("clave"))
        o.error() == "limite" -> HelpKeyResult.RateLimited
        else -> HelpKeyResult.Failed
    }
}

fun parseClaim(http: Int, body: String?): ActivationResult =
    if (http == 202) ActivationResult.Pending else parseActivation(http, body)

fun parseTestCall(http: Int, body: String?): TestCallResult = when (http) {
    202 -> TestCallResult.SCHEDULED
    429 -> when (json(body)?.optString("motivo")) {
        "en_curso" -> TestCallResult.IN_PROGRESS
        "linea" -> TestCallResult.LIMIT_DAY
        else -> TestCallResult.BUSY
    }
    503 -> TestCallResult.UNAVAILABLE
    401 -> TestCallResult.UNAUTHORIZED
    else -> TestCallResult.FAILED
}

/** "Eliminar mi cuenta" (prov POST /v1/linea/baja). UNAUTHORIZED: the line is not this phone's anymore. */
enum class DeleteResult { DELETED, UNAUTHORIZED, FAILED }

fun parseDeleteAccount(code: Int): DeleteResult = when (code) {
    200 -> DeleteResult.DELETED
    401 -> DeleteResult.UNAUTHORIZED
    else -> DeleteResult.FAILED
}

/** One voice message as listed by prov (GET /v1/mensajes). */
data class VoiceMessage(
    val id: String,
    val dateEpochMs: Long,
    val durationSec: Int,
    val caller: String,
    val waveform: List<Int>,
    val heard: Boolean,
    val daysLeft: Int
)

sealed class MessagesResult {
    data class Ok(val list: List<VoiceMessage>) : MessagesResult()

    /** 401: the credential is not the active one. */
    object Unauthorized : MessagesResult()

    object Offline : MessagesResult()

    object Failed : MessagesResult()
}

const val WAVEFORM_SIZE = 64

fun parseMessages(http: Int, body: String?): MessagesResult {
    if (http == -1) return MessagesResult.Offline
    if (http == 401) return MessagesResult.Unauthorized
    if (http != 200 || body == null) return MessagesResult.Failed
    return try {
        val arr = JSONObject(body).getJSONArray("mensajes")
        MessagesResult.Ok(
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val wave = o.getJSONArray("onda")
                VoiceMessage(
                    id = o.getString("id"),
                    dateEpochMs = OffsetDateTime.parse(o.getString("fecha")).toInstant().toEpochMilli(),
                    durationSec = o.getInt("duracion_s"),
                    caller = o.optString("llamante", ""),
                    // Pad with zeros or cut, so the bubble always has 64 bars.
                    waveform = List(WAVEFORM_SIZE) { j -> if (j < wave.length()) wave.getInt(j).coerceIn(0, 31) else 0 },
                    heard = o.getBoolean("escuchado"),
                    daysLeft = o.getInt("dias_restantes")
                )
            }
        )
    } catch (e: JSONException) {
        MessagesResult.Failed
    } catch (e: java.time.format.DateTimeParseException) {
        MessagesResult.Failed
    }
}
