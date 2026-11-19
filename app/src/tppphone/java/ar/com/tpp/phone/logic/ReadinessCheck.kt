// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

enum class RegState { OK, CONNECTING, FAILED, NONE }

enum class Status { READY, CONNECTING, PROBLEM }

/** Everything that must be true for a call to ring; drives the home screen traffic light. */
data class Readiness(
    val registered: RegState,
    val internet: Boolean,
    val notifications: Boolean,
    val microphone: Boolean,
    val fullScreenIntent: Boolean,
    val batteryUnrestricted: Boolean
)

/** Without internet the account cannot register either: report only the root cause. */
private fun Readiness.accountProblem() =
    internet && (registered == RegState.FAILED || registered == RegState.NONE)

// Battery optimization is only recommended: high-priority FCM pushes reach an optimized app
// (seen on a Samsung). Revisit per brand with the device matrix.
fun Readiness.problemCount(): Int = listOf(
    !internet,
    accountProblem(),
    !notifications,
    !microphone,
    !fullScreenIntent
).count { it }

fun Readiness.status(): Status = when {
    problemCount() > 0 -> Status.PROBLEM
    registered == RegState.OK -> Status.READY
    else -> Status.CONNECTING
}
