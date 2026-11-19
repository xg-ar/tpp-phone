// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.signup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import android.widget.EditText
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import ar.com.tpp.phone.logic.extractCode
import ar.com.tpp.phone.logic.helpUrl
import ar.com.tpp.phone.prov.HelpKeyResult
import ar.com.tpp.phone.prov.InstallId
import ar.com.tpp.phone.prov.ProvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.R
import org.linphone.core.tools.Log

// Navigation arguments shared by the signup screens.
const val ARG_E164 = "e164"
const val ARG_PREFILL_E164 = "prefillE164"
const val ARG_VERIFICATION_ID = "verificationId"
const val ARG_RESEND_AFTER = "resendAfterSec"
const val ARG_HELP_KEY = "helpKey"

/**
 * Opens the WhatsApp chat with "TPP Phone" and the help key, if any. With a key the bot links it
 * to the chat's number and this phone activates by itself; without one the bot asks the relative
 * to start from the app.
 */
fun Fragment.openHelpChat(key: String? = null) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, helpUrl(getString(R.string.tpp_whatsapp_help_url), key).toUri()))
    } catch (e: ActivityNotFoundException) {
        Log.e("[TPP Signup] No app can open the WhatsApp link: $e")
    }
}

/**
 * "Activar por WhatsApp": asks prov for a help key and goes to the waiting
 * screen, which opens WhatsApp. If prov can't be reached, opens the chat without a key.
 */
fun Fragment.askForHelp(button: View, from: Int, toWait: Int) {
    button.setUsable(false)
    val client = ProvClient(getString(R.string.tpp_prov_url))
    val installId = InstallId.get(requireContext().applicationContext)
    viewLifecycleOwner.lifecycleScope.launch {
        val result = withContext(Dispatchers.IO) { client.newHelpKey(installId) }
        button.setUsable(true)
        if (result is HelpKeyResult.Created) {
            navigateFrom(from, toWait, bundleOf(ARG_HELP_KEY to result.key))
        } else {
            openHelpChat()
        }
    }
}

/** A text button that looks off while it can't be used. */
fun View.setUsable(usable: Boolean) {
    isEnabled = usable
    alpha = if (usable) 1f else 0.4f
}

/** Navigates only if this fragment is still the current screen (a late result must not crash). */
fun Fragment.navigateFrom(destination: Int, action: Int, args: android.os.Bundle? = null) {
    val nav = findNavController()
    if (nav.currentDestination?.id == destination) nav.navigate(action, args)
}

/**
 * Back (system and the given buttons) does nothing while a request runs: leaving would drop
 * prov's answer. Call from onCreateView.
 */
fun Fragment.blockBackWhileBusy(vm: SignupViewModel, vararg backButtons: View) {
    val callback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {}
    }
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    vm.busy.observe(viewLifecycleOwner) { callback.isEnabled = it }
    for (button in backButtons) {
        button.setOnClickListener { if (vm.busy.value != true) findNavController().popBackStack() }
    }
}

/**
 * Keeps a code field to its digits, so a pasted "3402 7880" (or the whole WhatsApp message)
 * becomes "34027880". Returns true if it rewrote the text: the watcher then runs again.
 */
fun EditText.normalizeCode(length: Int): Boolean {
    val raw = text.toString()
    val code = extractCode(raw, length) ?: raw.filter { it.isDigit() }.take(length)
    if (code == raw) return false
    setText(code)
    setSelection(code.length)
    return true
}
