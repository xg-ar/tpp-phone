// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.account.provSecret
import ar.com.tpp.phone.prov.ProvClient
import ar.com.tpp.phone.prov.TestCallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R

/**
 * "Probalo": confirms, then asks prov for an echo call to this line in 1 minute.
 * [link] is off from the first tap: a quick double tap must not open two dialogs.
 */
fun Fragment.askForTestCall(link: View) {
    link.isEnabled = false
    AlertDialog.Builder(requireContext())
        .setMessage(R.string.tpp_probalo_dialog)
        .setPositiveButton(R.string.tpp_probalo_call_me) { _, _ -> requestTestCall(link) }
        .setNegativeButton(R.string.tpp_cancel) { _, _ -> link.isEnabled = true }
        .setOnCancelListener { link.isEnabled = true }
        .show()
}

private fun Fragment.requestTestCall(link: View) {
    // The dialog is not tied to the screen: it may outlive it (incoming call, line moved).
    if (!isAdded || view == null) return
    val client = ProvClient(getString(R.string.tpp_prov_url))
    coreContext.postOnCoreThread { core ->
        val account = TppAccount.current(core)
        val user = account?.params?.identityAddress?.username
        val auth = account?.findAuthInfo()
        val password = provSecret(auth?.password, auth?.ha1)
        link.post {
            if (view == null) return@post
            if (user == null || password == null) return@post showTestCallResult(link, TestCallResult.FAILED)
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { client.requestTestCall(user, password) }
                showTestCallResult(link, result)
            }
        }
    }
}

private fun Fragment.showTestCallResult(link: View, result: TestCallResult) {
    link.isEnabled = true
    val text = when (result) {
        TestCallResult.SCHEDULED -> R.string.tpp_probalo_scheduled
        TestCallResult.IN_PROGRESS -> R.string.tpp_probalo_in_progress
        TestCallResult.LIMIT_DAY -> R.string.tpp_probalo_limit
        TestCallResult.BUSY -> R.string.tpp_probalo_busy
        else -> R.string.tpp_probalo_failed
    }
    Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
}
