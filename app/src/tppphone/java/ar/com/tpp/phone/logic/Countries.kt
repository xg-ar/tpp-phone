// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.text.Collator
import java.util.Locale

data class Country(val region: String, val name: String, val callingCode: Int, val flag: String)

// Where today's Acrobits customers are, most first.
private val PRIORITY = listOf("AR", "BO", "PY", "PE", "CL", "CO", "BR", "UY")

private const val DEFAULT_REGION = "AR"

/** Two regional indicator symbols: Android draws them as the country's flag. */
fun flagEmoji(region: String): String = region.uppercase()
    .map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }
    .joinToString("")

fun orderedCountries(displayName: (String) -> String): List<Country> {
    val util = PhoneNumberUtil.getInstance()
    val all = util.supportedRegions.map { Country(it, displayName(it), util.getCountryCodeForRegion(it), flagEmoji(it)) }
    val first = PRIORITY.mapNotNull { region -> all.firstOrNull { it.region == region } }
    val collator = Collator.getInstance(Locale.forLanguageTag("es-AR"))
    val rest = all.filter { it.region !in PRIORITY }.sortedWith(compareBy(collator) { it.name })
    return first + rest
}

/** The SIM's country, else the network's, else Argentina. */
fun defaultRegion(simIso: String?, networkIso: String?): String {
    val supported = PhoneNumberUtil.getInstance().supportedRegions
    return listOf(simIso, networkIso)
        .map { it.orEmpty().uppercase() }
        .firstOrNull { it in supported } ?: DEFAULT_REGION
}
