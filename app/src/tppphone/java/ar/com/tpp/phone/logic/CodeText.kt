// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

/**
 * The [length]-digit code in a pasted text: the code alone, with a space or dash in the middle
 * ("3402 7880"), or the whole WhatsApp message. Longer digit runs (phone numbers) never match.
 */
fun extractCode(text: String, length: Int): String? {
    val pattern = Regex("(?<!\\d)(?:\\d[ -]?){${length - 1}}\\d(?!\\d)")
    return pattern.find(text)?.value?.filter { it.isDigit() }
}
