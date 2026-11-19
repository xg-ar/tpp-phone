// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.signup

import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ar.com.tpp.phone.logic.displayNumber
import ar.com.tpp.phone.logic.extractCode
import ar.com.tpp.phone.logic.offersHelp
import ar.com.tpp.phone.prov.ActivationResult
import ar.com.tpp.phone.prov.StartResult
import org.linphone.R
import org.linphone.databinding.TppCodeFragmentBinding

class CodeFragment : Fragment() {
    companion object {
        private const val CODE_LENGTH = 6
        private const val STATE_LAST_PASTED = "lastPasted"
    }

    private lateinit var binding: TppCodeFragmentBinding
    private lateinit var e164: String
    private val vm: SignupViewModel by viewModels()
    private var lastPasted: String? = null

    private val showFallbacks = Runnable { showFallbacks() }

    // Android lets an app read the clipboard only while its window has focus.
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus) pasteFromClipboard()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = TppCodeFragmentBinding.inflate(inflater, container, false)
        val args = requireArguments()
        e164 = args.getString(ARG_E164)!!
        if (vm.verificationId == null) vm.verificationId = args.getString(ARG_VERIFICATION_ID)
        lastPasted = savedInstanceState?.getString(STATE_LAST_PASTED)

        binding.header.title = getString(R.string.tpp_code_header)
        blockBackWhileBusy(vm, binding.header.back)
        binding.body.text = getString(R.string.tpp_code_body, displayNumber(e164))
        binding.confirm.setOnClickListener { confirm(binding.code.text.toString()) }
        binding.resend.setOnClickListener { resend() }
        binding.help.setOnClickListener { askForHelp(binding.help, R.id.codeFragment, R.id.action_code_to_helpWait) }
        vm.busy.observe(viewLifecycleOwner) { busy ->
            binding.code.isEnabled = !busy
            binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
            updateButtons()
        }
        vm.result.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            vm.result.value = null
            when (result) {
                is ActivationResult.Activated -> navigateFrom(R.id.codeFragment, R.id.action_code_to_home)
                is ActivationResult -> showProblem(result)
                is StartResult -> onResent(result)
            }
        }
        if (savedInstanceState == null) {
            binding.root.postDelayed(showFallbacks, args.getInt(ARG_RESEND_AFTER, 30) * 1000L)
        } else {
            showFallbacks()
        }
        return binding.root
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // Added after Android restores the field: a restored code must not be confirmed again.
        binding.code.doAfterTextChanged {
            if (binding.code.normalizeCode(CODE_LENGTH)) return@doAfterTextChanged
            updateButtons()
            if (it.toString().length == CODE_LENGTH) confirm(it.toString())
        }
        updateButtons()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
    }

    override fun onDestroyView() {
        binding.root.removeCallbacks(showFallbacks)
        binding.root.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_LAST_PASTED, lastPasted)
    }

    /** "Copiar código" in the WhatsApp message: paste it once when the relative comes back. */
    private fun pasteFromClipboard() {
        if (vm.busy.value == true || binding.code.text.isNotEmpty()) return
        val clip = requireContext().getSystemService<ClipboardManager>()?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val code = extractCode(clip.getItemAt(0).coerceToText(requireContext()).toString(), CODE_LENGTH) ?: return
        if (code == lastPasted) return
        lastPasted = code
        binding.code.setText(code) // completes the code, which confirms it
    }

    private fun updateButtons() {
        val busy = vm.busy.value == true
        binding.confirm.setUsable(!busy && binding.code.text.length == CODE_LENGTH)
        binding.resend.setUsable(!busy)
    }

    private fun confirm(code: String) {
        val vid = vm.verificationId ?: return
        if (!vm.begin()) return
        showMessage(getString(R.string.tpp_code_activating))
        val installId = vm.installId
        vm.launch { it.confirm(vid, code, installId) }
    }

    private fun showProblem(problem: ActivationResult) {
        when (problem) {
            is ActivationResult.WrongCode -> if (problem.attemptsLeft > 0) {
                binding.code.text.clear()
                showMessage(resources.getQuantityString(R.plurals.tpp_code_wrong, problem.attemptsLeft, problem.attemptsLeft))
            } else {
                expired()
            }
            ActivationResult.Expired -> expired()
            // The same code works again: "Confirmar" stays usable with the digits in place.
            ActivationResult.ActivationFailed -> showMessage(getString(R.string.tpp_signup_error_activation))
            ActivationResult.RateLimited -> showMessage(getString(R.string.tpp_signup_error_rate_limited))
            ActivationResult.InvalidNumber -> showMessage(getString(R.string.tpp_signup_error_invalid_number))
            else -> showMessage(getString(R.string.tpp_signup_error_network))
        }
        if (offersHelp(problem)) showFallbacks()
    }

    private fun expired() {
        binding.code.text.clear()
        showMessage(getString(R.string.tpp_code_expired))
    }

    private fun resend() {
        if (!vm.begin()) return
        binding.message.visibility = View.GONE
        val installId = vm.installId
        vm.launch { it.startVerification(e164, installId) }
    }

    private fun onResent(result: StartResult) {
        when (result) {
            is StartResult.Sent -> {
                vm.verificationId = result.verificationId
                binding.code.text.clear()
                showMessage(getString(R.string.tpp_code_resent))
                showFallbacks(false)
                binding.root.postDelayed(showFallbacks, result.resendAfterSec * 1000L)
            }
            StartResult.RateLimited -> showMessage(getString(R.string.tpp_signup_error_rate_limited))
            StartResult.InvalidNumber -> showMessage(getString(R.string.tpp_signup_error_invalid_number))
            StartResult.Failed -> showMessage(getString(R.string.tpp_signup_error_network))
        }
        if (offersHelp(result)) showFallbacks()
    }

    private fun showMessage(text: String) {
        binding.message.text = text
        binding.message.visibility = View.VISIBLE
    }

    /** "Reenviar código" and "Activar por WhatsApp", each with a line saying what it is for. */
    private fun showFallbacks(show: Boolean = true) {
        val visibility = if (show) View.VISIBLE else View.GONE
        for (v in listOf(binding.fallbackIntro, binding.resend, binding.fallbackOr, binding.help)) v.visibility = visibility
    }
}
