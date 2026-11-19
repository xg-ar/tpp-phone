// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

/**
 * Ways the phone's address book may have stored [ani] (the caller's number as the platform sends it).
 * No duplicates, most likely first. The real ANI format is checked in the end-to-end test.
 */
fun numberVariants(ani: String): List<String> {
    val digits = ani.trim().removePrefix("+")
    if (digits.isEmpty()) return emptyList()
    val variants = mutableListOf(digits, "+$digits")
    if (digits.startsWith("54")) {
        val rest = digits.removePrefix("54")
        variants += listOf("0$rest", "+54$rest")
    } else {
        variants += listOf("+54$digits", "0$digits", "54$digits")
    }
    return variants.distinct()
}
