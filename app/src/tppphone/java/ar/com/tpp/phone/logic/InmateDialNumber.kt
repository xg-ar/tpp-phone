// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

/**
 * Number the inmate dials to reach this account: "9" + the WhatsApp number without 549.
 * Grouping only adds spaces (2-4-4 for Buenos Aires, 3-3-4 otherwise); the digits dialed
 * are the same whatever the real area code length is.
 */
fun inmateDialNumber(sipUsername: String): String {
    val digits = sipUsername.filter { it.isDigit() }
    if (!digits.startsWith("549")) return "9 $digits"

    val national = digits.removePrefix("549")
    return when {
        national.length == 10 && national.startsWith("11") ->
            "9 ${national.substring(0, 2)} ${national.substring(2, 6)} ${national.substring(6)}"
        national.length == 10 ->
            "9 ${national.substring(0, 3)} ${national.substring(3, 6)} ${national.substring(6)}"
        else -> "9 $national"
    }
}
