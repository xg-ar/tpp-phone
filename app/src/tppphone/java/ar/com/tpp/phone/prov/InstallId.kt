// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.prov

import android.content.Context
import java.util.UUID

/** Random id of this installation, for prov's limits and audit. Not the IMEI. */
object InstallId {
    private const val PREFS = "tpp_install"
    private const val KEY = "id"

    @Synchronized
    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, id).apply()
        return id
    }
}
