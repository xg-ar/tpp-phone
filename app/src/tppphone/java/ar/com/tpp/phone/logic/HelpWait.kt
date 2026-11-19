// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

// Same as prov's help key lifetime: if the WhatsApp bot is down, the app stops asking.
const val HELP_WAIT_MS = 10 * 60_000L

fun helpWaitOver(startedAtMs: Long, nowMs: Long): Boolean = nowMs - startedAtMs >= HELP_WAIT_MS
