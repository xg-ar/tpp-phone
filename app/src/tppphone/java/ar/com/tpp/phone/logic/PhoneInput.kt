// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber

private val MOBILE_TYPES = setOf(PhoneNumberType.MOBILE, PhoneNumberType.FIXED_LINE_OR_MOBILE)

/**
 * What the relative typed, as E.164 digits without '+', or null if it is not a valid mobile
 * (same rule as prov/numbers.py). Argentine numbers always get the mobile 9: relatives type
 * them with 0, 15 or no 9 at all, and libphonenumber only turns the 15 into the 9.
 */
fun toE164(region: String, input: String): String? {
    val util = PhoneNumberUtil.getInstance()
    val number = try {
        util.parse(input, region)
    } catch (e: NumberParseException) {
        return null
    }
    val candidates = buildList {
        if (number.countryCode == 54 && !number.nationalNumber.toString().startsWith("9")) {
            add(PhoneNumber().setCountryCode(54).setNationalNumber("9${number.nationalNumber}".toLong()))
        }
        add(number)
    }
    val valid = candidates.firstOrNull { util.isValidNumber(it) && util.getNumberType(it) in MOBILE_TYPES }
        ?: return null
    return util.format(valid, PhoneNumberUtil.PhoneNumberFormat.E164).removePrefix("+")
}

/** The number as the relative knows it from WhatsApp, e.g. "+54 9 11 5328-2884". */
fun displayNumber(e164: String): String {
    val util = PhoneNumberUtil.getInstance()
    return try {
        util.format(util.parse("+$e164", null), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    } catch (e: NumberParseException) {
        "+$e164"
    }
}
