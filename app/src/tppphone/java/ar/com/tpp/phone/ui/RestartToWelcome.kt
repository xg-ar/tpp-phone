// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import ar.com.tpp.phone.account.TppAccount
import org.linphone.LinphoneApplication.Companion.coreContext

/** Starts TppMainActivity in a fresh task: with no account it opens on the welcome screen, without any saved back stack. */
fun restartToWelcome(context: Context, old: Activity? = null) {
    context.startActivity(
        Intent(context.applicationContext, TppMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    old?.takeIf { !it.isFinishing }?.finish()
}

/** A phone without account must never sit on a screen that needs one. Returns true if a restart was started. */
fun restartIfNoAccount(activity: Activity) {
    coreContext.postOnCoreThread { core ->
        if (TppAccount.current(core) == null) activity.runOnUiThread { restartToWelcome(activity, activity) }
    }
}
