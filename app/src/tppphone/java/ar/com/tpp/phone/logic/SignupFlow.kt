// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

import ar.com.tpp.phone.prov.ActivationResult
import ar.com.tpp.phone.prov.StartResult

/** One signup request at a time: a double tap must not send two codes (or burn one). */
class RequestGate {
    private var busy = false

    @Synchronized
    fun tryStart(): Boolean {
        if (busy) return false
        busy = true
        return true
    }

    @Synchronized
    fun finish() {
        busy = false
    }
}

/** The WhatsApp help button shows whenever the relative can't fix the problem by retyping. */
fun offersHelp(result: StartResult): Boolean = result is StartResult.Failed || result is StartResult.RateLimited

fun offersHelp(result: ActivationResult): Boolean = when (result) {
    is ActivationResult.Activated, is ActivationResult.WrongCode, ActivationResult.InvalidNumber -> false
    else -> true
}
