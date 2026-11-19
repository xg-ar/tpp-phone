// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

enum class Check { INTERNET, ACCOUNT, NOTIFICATIONS, MICROPHONE, FULL_SCREEN, BATTERY }

enum class CheckState { OK, PROBLEM, PENDING, RECOMMENDED }

data class CheckRow(val check: Check, val state: CheckState)

/** Rows of the problem screen, in display order; PROBLEM rows match problemCount(). */
fun Readiness.checkRows(): List<CheckRow> {
    fun ok(value: Boolean) = if (value) CheckState.OK else CheckState.PROBLEM
    val account = when {
        !internet || registered == RegState.CONNECTING -> CheckState.PENDING
        registered == RegState.OK -> CheckState.OK
        else -> CheckState.PROBLEM
    }
    return listOf(
        CheckRow(Check.INTERNET, ok(internet)),
        CheckRow(Check.ACCOUNT, account),
        CheckRow(Check.NOTIFICATIONS, ok(notifications)),
        CheckRow(Check.MICROPHONE, ok(microphone)),
        CheckRow(Check.FULL_SCREEN, ok(fullScreenIntent)),
        CheckRow(Check.BATTERY, if (batteryUnrestricted) CheckState.OK else CheckState.RECOMMENDED)
    )
}
