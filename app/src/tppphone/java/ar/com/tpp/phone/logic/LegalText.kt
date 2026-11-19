// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

/** Upper bound for a legal text from prov; anything larger is not ours. */
const val MAX_LEGAL_TEXT_CHARS = 200_000

/**
 * the terms and the privacy policy come from prov so they can change without a new
 * build; the copy bundled in res/raw is the fallback. Only an HTML fragment with paragraphs
 * is accepted: a full page (captive portal, proxy error) or an empty answer keeps the copy.
 */
fun pickLegalText(remote: String?, bundled: String): String {
    val text = remote?.trim().orEmpty()
    val usable = text.isNotEmpty() &&
        text.length <= MAX_LEGAL_TEXT_CHARS &&
        text.contains("<p", ignoreCase = true) &&
        !text.contains("<html", ignoreCase = true) &&
        !text.contains("<!doctype", ignoreCase = true)
    return if (usable) remote!! else bundled
}
