// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

import java.net.URLEncoder

// The WhatsApp support bot's keyword: with a help key it links the chat's number to this phone.
private const val WATI_KEYWORD = "TPP Phone"

/**
 * WhatsApp support chat, prefilled with the bot's keyword and, when prov gave one, the help key
 * that lets this phone activate by itself once the message arrives.
 */
fun helpUrl(baseUrl: String, key: String? = null): String {
    val text = if (key == null) WATI_KEYWORD else "$WATI_KEYWORD $key"
    // URLEncoder writes spaces as '+', which wa.me would show as is.
    return "$baseUrl?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
