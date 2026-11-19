// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

import java.time.Instant
import java.time.ZoneId

data class ReceivedCallRow(
    val title: String,
    val whenText: String,
    val detail: String,
    val missed: Boolean
)

// Fixed table: JDK and Android disagree on Spanish abbreviations ("oct" vs "oct.").
private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** "Hoy, 18:42" / "Ayer, 09:05" / "3 oct, 10:00": shared by the calls and voice messages lists. */
fun whenText(epochMs: Long, nowEpochMs: Long, zone: ZoneId): String {
    val start = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
    val time = "%02d:%02d".format(start.hour, start.minute)
    val day = when (start.toLocalDate()) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> {
            val dayMonth = "${start.dayOfMonth} ${MONTHS[start.monthValue - 1]}"
            if (start.year == today.year) dayMonth else "$dayMonth ${start.year}"
        }
    }
    return "$day, $time"
}

fun receivedCallRow(
    displayName: String?,
    username: String,
    startEpochMs: Long,
    durationSec: Int,
    answered: Boolean,
    nowEpochMs: Long,
    zone: ZoneId
): ReceivedCallRow {
    val minutes = maxOf(1, (durationSec + 59) / 60)
    return ReceivedCallRow(
        title = displayName?.takeIf { it.isNotBlank() } ?: username,
        whenText = whenText(startEpochMs, nowEpochMs, zone),
        detail = if (answered) "$minutes min" else "Perdida",
        missed = !answered
    )
}
