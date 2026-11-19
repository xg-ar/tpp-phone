// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

import ar.com.tpp.phone.prov.LineStatus

enum class UnauthorizedAction { SHOW_LINE_MOVED, RETRY_LATER, NOTHING }

// Flexisip caches each credential for 30 s, so a just-activated line may get a 401 twice.
private const val MAX_RETRIES = 2

/** What to do when Flexisip answers the REGISTER with 401. */
fun onUnauthorized(status: LineStatus, retriesDone: Int): UnauthorizedAction = when {
    status == LineStatus.REPLACED -> UnauthorizedAction.SHOW_LINE_MOVED
    status == LineStatus.ACTIVE && retriesDone < MAX_RETRIES -> UnauthorizedAction.RETRY_LATER
    else -> UnauthorizedAction.NOTHING
}
