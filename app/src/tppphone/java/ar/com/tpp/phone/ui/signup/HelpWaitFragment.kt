// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ar.com.tpp.phone.logic.displayNumber
import ar.com.tpp.phone.logic.helpWaitOver
import ar.com.tpp.phone.prov.ActivationResult
import org.linphone.R
import org.linphone.databinding.TppHelpWaitFragmentBinding

/**
 * Waits for the relative's WhatsApp to reach the bot: asks prov every few seconds
 * while on screen, and activates the chat's number as soon as the bot has linked the key.
 */
class HelpWaitFragment : Fragment() {
    companion object {
        private const val POLL_MS = 3_000L
        private const val RETRY_OFFLINE_MS = 6_000L

        // Opening WhatsApp while this screen's enter transition runs lost the prefilled chat
        // (WhatsApp showed its chat list, 2026-11-01): wait until the screen is settled.
        private const val OPEN_CHAT_DELAY_MS = 600L
    }

    private lateinit var binding: TppHelpWaitFragmentBinding
    private lateinit var key: String
    private val vm: SignupViewModel by viewModels()
    private var stopped = false

    private val poll = Runnable { claim() }

    private val openChat = Runnable {
        if (isResumed && !vm.helpChatOpened) {
            vm.helpChatOpened = true
            openHelpChat(key)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = TppHelpWaitFragmentBinding.inflate(inflater, container, false)
        key = requireArguments().getString(ARG_HELP_KEY)!!
        if (vm.helpWaitStartedAtMs == 0L) vm.helpWaitStartedAtMs = System.currentTimeMillis()
        binding.header.title = getString(R.string.tpp_help_wait_header)
        blockBackWhileBusy(vm, binding.header.back, binding.cancel)
        binding.reopen.setOnClickListener { openHelpChat(key) }
        binding.retry.setOnClickListener {
            stopped = false
            showWaiting()
            claim()
        }
        binding.again.setOnClickListener {
            askForHelp(binding.again, R.id.helpWaitFragment, R.id.action_helpWait_to_helpWait)
        }
        vm.result.observe(viewLifecycleOwner) { result ->
            if (result !is ActivationResult) return@observe
            vm.result.value = null
            onResult(result)
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // First time only: the message with the key is what the relative has to send.
        if (!vm.helpChatOpened) binding.root.postDelayed(openChat, OPEN_CHAT_DELAY_MS)
        if (!stopped) claim()
    }

    override fun onPause() {
        binding.root.removeCallbacks(openChat)
        binding.root.removeCallbacks(poll)
        super.onPause()
    }

    private fun claim() {
        binding.root.removeCallbacks(poll)
        // If the WhatsApp bot is down the message never arrives: stop asking after 10 minutes, like the key.
        if (helpWaitOver(vm.helpWaitStartedAtMs, System.currentTimeMillis())) {
            return stop(getString(R.string.tpp_help_wait_expired), again = true)
        }
        if (!vm.begin()) return
        val installId = vm.installId
        vm.launch { it.claimHelpKey(key, installId) }
    }

    private fun onResult(result: ActivationResult) {
        when (result) {
            ActivationResult.Pending -> later(POLL_MS)
            is ActivationResult.Activated -> {
                val number = displayNumber(result.config.sipUser)
                Toast.makeText(requireContext(), getString(R.string.tpp_help_wait_done, number), Toast.LENGTH_LONG).show()
                navigateFrom(R.id.helpWaitFragment, R.id.action_helpWait_to_home)
            }
            ActivationResult.Failed -> {
                // Offline for a moment (the relative was in WhatsApp): keep trying.
                showMessage(getString(R.string.tpp_help_wait_offline))
                later(RETRY_OFFLINE_MS)
            }
            ActivationResult.Expired -> stop(getString(R.string.tpp_help_wait_expired), again = true)
            ActivationResult.ActivationFailed -> stop(getString(R.string.tpp_signup_error_activation), again = false)
            ActivationResult.RateLimited -> stop(getString(R.string.tpp_signup_error_rate_limited), again = false)
            else -> stop(getString(R.string.tpp_signup_error_network), again = false)
        }
    }

    private fun later(delayMs: Long) {
        if (isResumed) binding.root.postDelayed(poll, delayMs)
    }

    private fun showWaiting() {
        binding.progress.visibility = View.VISIBLE
        binding.message.visibility = View.GONE
        binding.retry.visibility = View.GONE
        binding.again.visibility = View.GONE
    }

    private fun showMessage(text: String) {
        binding.message.text = text
        binding.message.visibility = View.VISIBLE
    }

    /** Stops polling; "Reintentar" claims the same key again, "Pedir ayuda de nuevo" gets a new one. */
    private fun stop(text: String, again: Boolean) {
        stopped = true
        binding.progress.visibility = View.GONE
        showMessage(text)
        binding.retry.visibility = if (again) View.GONE else View.VISIBLE
        binding.again.visibility = if (again) View.VISIBLE else View.GONE
    }
}
