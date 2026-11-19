// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.problem

import android.Manifest
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ar.com.tpp.phone.device.DeviceSettings
import ar.com.tpp.phone.device.buildReadiness
import ar.com.tpp.phone.logic.Check
import ar.com.tpp.phone.logic.CheckRow
import ar.com.tpp.phone.logic.CheckState
import ar.com.tpp.phone.logic.checkRows
import ar.com.tpp.phone.logic.Status
import ar.com.tpp.phone.logic.problemCount
import ar.com.tpp.phone.logic.shouldPulse
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.ui.LivePulseAnimator
import ar.com.tpp.phone.ui.askForTestCall
import ar.com.tpp.phone.ui.restartToWelcome
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log
import org.linphone.databinding.TppCheckRowBinding
import org.linphone.databinding.TppProblemFragmentBinding

/** Problem screen: one row per readiness check, each with its fix. */
class ProblemFragment : Fragment() {
    companion object {
        private const val TAG = "[TPP Problem]"
    }

    private lateinit var binding: TppProblemFragmentBinding
    private var pulse: LivePulseAnimator? = null
    private var allOk = false

    // When Android no longer shows the permission dialog (denied before), go to the app settings.
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) open(DeviceSettings.appDetails(requireContext()))
        refresh()
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) = refresh()

        override fun onNetworkReachable(core: Core, reachable: Boolean) = refresh()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = TppProblemFragmentBinding.inflate(inflater, container, false)
        binding.header.title = getString(R.string.tpp_about_check)
        binding.header.back.setOnClickListener { findNavController().popBackStack() }
        pulse = LivePulseAnimator(binding.statusHalo, binding.statusIcon)
        // Home from wherever the checks were opened (home or "Acerca de").
        binding.home.setOnClickListener { findNavController().popBackStack(R.id.homeFragment, false) }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        coreContext.postOnCoreThread { core -> core.addListener(coreListener) }
        refresh() // the user may come back from a settings screen
    }

    override fun onPause() {
        coreContext.postOnCoreThread { core -> core.removeListener(coreListener) }
        super.onPause()
        updatePulse()
    }

    override fun onDestroyView() {
        pulse?.stop()
        pulse = null
        super.onDestroyView()
    }

    private fun updatePulse() {
        val status = if (allOk) Status.READY else Status.PROBLEM
        pulse?.update(shouldPulse(status, isResumed, ValueAnimator.areAnimatorsEnabled()))
    }

    private fun refresh() {
        val context = context?.applicationContext ?: return
        coreContext.postOnCoreThread { core ->
            val readiness = buildReadiness(context, core)
            binding.root.post { if (isAdded) render(readiness.checkRows(), readiness.problemCount()) }
        }
    }

    private fun render(rows: List<CheckRow>, problems: Int) {
        allOk = problems == 0
        binding.allOk = allOk
        updatePulse()
        binding.summary = resources.getQuantityString(R.plurals.tpp_problems_count, problems, problems)
        binding.checks.removeAllViews()

        val autostart = DeviceSettings.manufacturerAutostart(requireContext())
        rows.forEachIndexed { index, row ->
            addRow(row.state, row.check, isLast = index == rows.lastIndex && autostart == null)
        }
        if (autostart != null) {
            // Cannot be detected: always shown as a recommendation on these brands.
            val rowBinding = TppCheckRowBinding.inflate(layoutInflater, binding.checks, true)
            rowBinding.state = CheckState.RECOMMENDED
            rowBinding.title = getString(R.string.tpp_check_autostart_title)
            rowBinding.body = html(R.string.tpp_check_autostart_body)
            rowBinding.action = getString(R.string.tpp_check_autostart_action)
            rowBinding.isLast = true
            rowBinding.actionButton.setOnClickListener { open(autostart) }
        }
        binding.executePendingBindings()
        if (allOk) appendProbalo()
    }

    /** "Tu celular está listo para recibir llamadas. **Probalo**": same size, bold and underlined. */
    private fun appendProbalo() {
        val body = binding.statusBody
        val link = getString(R.string.tpp_probalo_link)
        val text = SpannableStringBuilder(getString(R.string.tpp_problem_all_ok_body)).append(' ')
        val start = text.length
        text.append(link)
        text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(
            object : ClickableSpan() {
                // The link is disabled with the whole text while the dialog or the request is pending.
                override fun onClick(widget: View) = askForTestCall(body)

                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = true // keeps the text color
                }
            },
            start,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        body.text = text
        body.movementMethod = LinkMovementMethod.getInstance()
        body.highlightColor = Color.TRANSPARENT
    }

    private fun addRow(state: CheckState, check: Check, isLast: Boolean) {
        val (ok, problem, body, action) = when (check) {
            Check.INTERNET -> Texts(R.string.tpp_check_internet_ok, R.string.tpp_check_internet_problem, R.string.tpp_check_internet_body, R.string.tpp_check_internet_action)
            Check.ACCOUNT -> Texts(R.string.tpp_check_account_ok, R.string.tpp_check_account_problem, R.string.tpp_check_account_body, R.string.tpp_check_account_action)
            Check.NOTIFICATIONS -> Texts(R.string.tpp_check_notifications_ok, R.string.tpp_check_notifications_problem, R.string.tpp_check_notifications_body, R.string.tpp_check_notifications_action)
            Check.MICROPHONE -> Texts(R.string.tpp_check_microphone_ok, R.string.tpp_check_microphone_problem, R.string.tpp_check_microphone_body, R.string.tpp_check_microphone_action)
            Check.FULL_SCREEN -> Texts(R.string.tpp_check_full_screen_ok, R.string.tpp_check_full_screen_problem, R.string.tpp_check_full_screen_body, R.string.tpp_check_full_screen_action)
            Check.BATTERY -> Texts(R.string.tpp_check_battery_ok, R.string.tpp_check_battery_problem, R.string.tpp_check_battery_body, R.string.tpp_check_battery_action)
        }
        val rowBinding = TppCheckRowBinding.inflate(layoutInflater, binding.checks, true)
        rowBinding.state = state
        rowBinding.title = getString(if (state == CheckState.OK || state == CheckState.PENDING) ok else problem)
        rowBinding.body = html(body)
        rowBinding.action = getString(action)
        rowBinding.isLast = isLast
        rowBinding.actionButton.setOnClickListener { fix(check) }
    }

    private fun fix(check: Check) {
        val context = requireContext()
        Log.i("$TAG Fixing [$check]")
        when (check) {
            Check.INTERNET -> open(DeviceSettings.network())
            Check.ACCOUNT -> {
                val activity = requireActivity()
                coreContext.postOnCoreThread { core ->
                    if (TppAccount.current(core) == null) {
                        activity.runOnUiThread { restartToWelcome(activity, activity) }
                    } else {
                        core.refreshRegisters()
                        activity.runOnUiThread {
                            Toast.makeText(activity, R.string.tpp_check_account_reconnecting, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            Check.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    open(DeviceSettings.appNotifications(context)) // permission given, channel or app muted
                }
            Check.MICROPHONE -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            Check.FULL_SCREEN -> Compatibility.requestFullScreenIntentPermission(context)
            Check.BATTERY -> open(DeviceSettings.batteryUnrestricted())
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("$TAG Can't open [$intent]: $e, falling back to app details")
            startActivity(DeviceSettings.appDetails(requireContext()))
        }
    }

    // getText keeps the <b> styling of the string resource.
    private fun html(id: Int): CharSequence = getText(id)

    private data class Texts(val ok: Int, val problem: Int, val body: Int, val action: Int)
}
