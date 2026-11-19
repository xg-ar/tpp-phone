// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.account.provSecret
import ar.com.tpp.phone.prov.DeleteResult
import ar.com.tpp.phone.prov.ProvClient
import ar.com.tpp.phone.prov.clearVoiceMessages
import ar.com.tpp.phone.work.ReRegisterWorker
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R

/** "Eliminar mi cuenta": confirms, then asks prov to drop this line. [link] is off while waiting. */
fun Fragment.askToDeleteAccount(link: View) {
    link.isEnabled = false
    AlertDialog.Builder(requireContext())
        .setMessage(R.string.tpp_delete_dialog)
        .setPositiveButton(R.string.tpp_delete_confirm) { _, _ -> deleteAccount(link) }
        .setNegativeButton(R.string.tpp_cancel) { _, _ -> link.isEnabled = true }
        .setOnCancelListener { link.isEnabled = true }
        .show()
}

// Outlives the fragment: prov may finish the deletion after the user left the screen, and
// the phone must then drop its account anyway.
private val deleteScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

private fun Fragment.deleteAccount(link: View) {
    if (!isAdded || view == null) return
    val client = ProvClient(getString(R.string.tpp_prov_url))
    val appContext = requireContext().applicationContext
    val activityRef = WeakReference(requireActivity())
    val progress = showProgress()
    coreContext.postOnCoreThread { core ->
        val account = TppAccount.current(core)
        val user = account?.params?.identityAddress?.username
        val auth = account?.findAuthInfo()
        val password = provSecret(auth?.password, auth?.ha1)
        if (user == null || password == null) {
            Handler(Looper.getMainLooper()).post { finishDelete(appContext, activityRef, link, progress, DeleteResult.FAILED) }
            return@postOnCoreThread
        }
        deleteScope.launch {
            val result = client.deleteAccount(user, password)
            // The line's voice messages (cached list and audios) go too. Still on IO, before the restart.
            if (result != DeleteResult.FAILED) clearVoiceMessages(appContext)
            // DELETED, or the line is not this phone's anymore: nothing to keep either way.
            // The screen is rebuilt only after the account is gone, or it would still find it.
            coreContext.postOnCoreThread { c ->
                try {
                    if (result != DeleteResult.FAILED) {
                        c.clearAccounts()
                        c.clearAllAuthInfo()
                        ReRegisterWorker.cancel(appContext)
                    }
                } finally {
                    Handler(Looper.getMainLooper()).post { finishDelete(appContext, activityRef, link, progress, result) }
                }
            }
        }
    }
}

private fun Fragment.showProgress(): AlertDialog {
    val ctx = requireContext()
    val pad = (20 * ctx.resources.displayMetrics.density).toInt()
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(pad, pad, pad, pad)
        addView(ProgressBar(ctx))
        addView(
            TextView(ctx).apply {
                setText(R.string.tpp_delete_in_progress)
                setPadding(pad, 0, 0, 0)
            }
        )
    }
    return AlertDialog.Builder(ctx).setView(box).setCancelable(false).show()
}

private fun finishDelete(
    appContext: Context,
    activityRef: WeakReference<out Activity>,
    link: View,
    progress: AlertDialog,
    result: DeleteResult
) {
    val activity = activityRef.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    if (activity != null) {
        try {
            progress.dismiss()
        } catch (e: IllegalArgumentException) {
            // Window already gone.
        }
    }
    if (result == DeleteResult.FAILED) {
        link.isEnabled = true
        Toast.makeText(appContext, R.string.tpp_delete_failed, Toast.LENGTH_LONG).show()
        return
    }
    Toast.makeText(appContext, R.string.tpp_delete_done, Toast.LENGTH_LONG).show()
    // Fresh task: recreate() would restore the saved back stack (home, about) instead of the welcome screen.
    restartToWelcome(appContext, activity)
}
