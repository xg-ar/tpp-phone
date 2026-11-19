// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import ar.com.tpp.phone.logic.numberVariants
import java.util.concurrent.ConcurrentHashMap

/**
 * Name of a phone number in the phone's address book. The agenda never leaves the phone:
 * nothing here is sent anywhere or logged. Call off the main thread.
 */
class ContactNames(context: Context) {
    private val context = context.applicationContext

    // Only answers found with permission are cached; a missing name is cached as "".
    private val cache = ConcurrentHashMap<String, String>()

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    fun nameFor(ani: String): String? {
        if (!hasPermission()) return null
        cache[ani]?.let { return it.ifEmpty { null } }
        val name = lookup(ani)
        cache[ani] = name.orEmpty()
        return name
    }

    private fun lookup(ani: String): String? {
        for (variant in numberVariants(ani)) {
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(variant))
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            } catch (e: SecurityException) {
                return null
            }
        }
        return null
    }
}
