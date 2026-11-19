// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

import ar.com.tpp.phone.prov.VoiceMessage
import java.time.ZoneId

data class VoiceMessageRow(
    val title: String,
    val subtitle: String?,
    val whenText: String,
    val durationText: String,
    val expiryText: String,
    val unheard: Boolean
)

fun voiceMessageRow(m: VoiceMessage, contactName: String?, nowEpochMs: Long, zone: ZoneId): VoiceMessageRow {
    val name = contactName?.takeIf { it.isNotBlank() }
    val (title, subtitle) = when {
        m.caller.isEmpty() -> "Número oculto" to null
        name != null -> name to m.caller
        else -> m.caller to null
    }
    return VoiceMessageRow(
        title = title,
        subtitle = subtitle,
        whenText = whenText(m.dateEpochMs, nowEpochMs, zone),
        durationText = "%d:%02d".format(m.durationSec / 60, m.durationSec % 60),
        expiryText = when (m.daysLeft) {
            0 -> "Se borra hoy"
            1 -> "Se borra mañana"
            else -> "Se borra en ${m.daysLeft} días"
        },
        unheard = !m.heard
    )
}
