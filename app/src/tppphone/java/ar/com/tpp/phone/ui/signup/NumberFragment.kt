// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.signup

import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.logic.Country
import ar.com.tpp.phone.logic.defaultRegion
import ar.com.tpp.phone.logic.displayNumber
import ar.com.tpp.phone.logic.offersHelp
import ar.com.tpp.phone.logic.orderedCountries
import ar.com.tpp.phone.logic.toE164
import ar.com.tpp.phone.prov.StartResult
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.databinding.TppNumberFragmentBinding

class NumberFragment : Fragment() {
    companion object {
        private const val STATE_REGION = "region"
    }

    private lateinit var binding: TppNumberFragmentBinding
    private lateinit var countries: List<Country>
    private lateinit var country: Country
    private val vm: SignupViewModel by viewModels()
    private var dialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = TppNumberFragmentBinding.inflate(inflater, container, false)
        binding.header.title = getString(R.string.tpp_number_header)
        blockBackWhileBusy(vm, binding.header.back)

        val spanish = Locale.forLanguageTag("es-AR")
        countries = orderedCountries { Locale("", it).getDisplayCountry(spanish) }
        val prefill = arguments?.getString(ARG_PREFILL_E164)
        val region = savedInstanceState?.getString(STATE_REGION) ?: initialRegion(prefill)
        select(countries.firstOrNull { it.region == region } ?: countries.first())
        if (savedInstanceState == null && prefill != null) binding.number.setText(nationalPart(prefill))

        binding.country.setOnClickListener {
            CountryPickerDialog.show(requireContext(), countries) {
                select(it)
                refresh()
            }
        }
        binding.number.doAfterTextChanged { refresh() }
        binding.send.setOnClickListener {
            val e164 = currentE164() ?: return@setOnClickListener
            // Taken at the first tap: a double tap must not open two dialogs or send two codes.
            if (vm.begin()) checkOtherLine(e164)
        }
        binding.help.setOnClickListener { askForHelp(binding.help, R.id.numberFragment, R.id.action_number_to_helpWait) }
        vm.busy.observe(viewLifecycleOwner) { busy ->
            binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
            updateButtons()
        }
        vm.result.observe(viewLifecycleOwner) { result ->
            if (result is StartResult) {
                vm.result.value = null
                onStartResult(result)
            }
        }
        refresh()
        return binding.root
    }

    override fun onDestroyView() {
        // A dialog doesn't survive a rotation; release the request it was holding.
        dialog?.let {
            it.dismiss()
            vm.cancel()
        }
        dialog = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::country.isInitialized) outState.putString(STATE_REGION, country.region)
    }

    private fun initialRegion(prefill: String?): String {
        if (prefill != null) {
            val util = PhoneNumberUtil.getInstance()
            runCatching { util.getRegionCodeForNumber(util.parse("+$prefill", null)) }.getOrNull()?.let { return it }
        }
        val tm = requireContext().getSystemService(TelephonyManager::class.java)
        return defaultRegion(tm?.simCountryIso, tm?.networkCountryIso)
    }

    private fun nationalPart(e164: String): String {
        val util = PhoneNumberUtil.getInstance()
        return runCatching { util.getNationalSignificantNumber(util.parse("+$e164", null)) }.getOrDefault(e164)
    }

    private fun select(c: Country) {
        country = c
        binding.country.text = "${c.flag} +${c.callingCode}"
    }

    private fun currentE164(): String? = toE164(country.region, binding.number.text.toString())

    private fun refresh() {
        updateButtons()
        binding.error.visibility = View.GONE
        showHelp(false)
    }

    private fun showHelp(show: Boolean) {
        binding.help.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateButtons() {
        val usable = currentE164() != null && vm.busy.value != true
        binding.send.setUsable(usable)
    }

    private fun ask(message: String, yes: Int, no: Int, onYes: () -> Unit) {
        dialog = AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(yes) { _, _ ->
                dialog = null
                onYes()
            }
            .setNegativeButton(no) { _, _ ->
                dialog = null
                vm.cancel()
            }
            .setOnCancelListener {
                dialog = null
                vm.cancel()
            }
            .show()
    }

    /** Spec §4.2 case 5: one line per app, so changing it needs a yes. */
    private fun checkOtherLine(e164: String) {
        coreContext.postOnCoreThread { core ->
            val current = TppAccount.username(core)
            binding.root.post {
                if (view == null) return@post vm.cancel()
                if (current == null || current == e164) {
                    confirmNumber(e164)
                } else {
                    ask(
                        getString(R.string.tpp_number_replace_line, displayNumber(current), displayNumber(e164)),
                        R.string.tpp_number_replace_yes,
                        R.string.tpp_cancel
                    ) { confirmNumber(e164) }
                }
            }
        }
    }

    private fun confirmNumber(e164: String) {
        ask(getString(R.string.tpp_number_confirm, displayNumber(e164)), R.string.tpp_number_confirm_yes, R.string.tpp_number_confirm_fix) {
            vm.sentTo = e164
            val installId = vm.installId
            vm.launch { it.startVerification(e164, installId) }
        }
    }

    private fun onStartResult(result: StartResult) {
        when (result) {
            is StartResult.Sent -> navigateFrom(
                R.id.numberFragment,
                R.id.action_number_to_code,
                bundleOf(ARG_E164 to (vm.sentTo ?: currentE164()), ARG_VERIFICATION_ID to result.verificationId, ARG_RESEND_AFTER to result.resendAfterSec)
            )
            StartResult.InvalidNumber -> showError(R.string.tpp_signup_error_invalid_number, offersHelp(result))
            StartResult.RateLimited -> showError(R.string.tpp_signup_error_rate_limited, offersHelp(result))
            StartResult.Failed -> showError(R.string.tpp_signup_error_network, offersHelp(result))
        }
    }

    private fun showError(text: Int, help: Boolean) {
        binding.error.setText(text)
        binding.error.visibility = View.VISIBLE
        showHelp(help)
    }
}
