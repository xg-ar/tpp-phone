// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import ar.com.tpp.phone.logic.shouldPulse
import ar.com.tpp.phone.prov.VoiceMessage
import ar.com.tpp.phone.ui.LivePulseAnimator
import ar.com.tpp.phone.ui.restartIfNoAccount
import ar.com.tpp.phone.ui.signup.ARG_PREFILL_E164
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.databinding.TppHomeFragmentBinding

class HomeFragment : Fragment() {
    private companion object {
        const val MESSAGES_POLL_MS = 30_000L
    }

    private lateinit var binding: TppHomeFragmentBinding
    private val viewModel: HomeViewModel by viewModels()
    private val adapter = ReceivedCallsAdapter()
    private val voiceAdapter = VoiceMessagesAdapter(
        onPlayPause = { viewModel.playOrPause(it) },
        onSeek = { m, f -> viewModel.seek(m, f) },
        onLongPress = { askToDelete(it) }
    )
    private val contactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.contactsAsked() }
    private var pulse: LivePulseAnimator? = null
    private var polling: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = TppHomeFragmentBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        binding.calls.adapter = adapter
        binding.voiceMessages.adapter = voiceAdapter
        // Progress ticks rebind one row: no change animation flicker.
        binding.voiceMessages.itemAnimator = null
        pulse = LivePulseAnimator(binding.statusHalo, binding.statusIcon)

        binding.footer.text = HtmlCompat.fromHtml(getString(R.string.tpp_home_footer), HtmlCompat.FROM_HTML_MODE_COMPACT)
        binding.footer.movementMethod = LinkMovementMethod.getInstance()

        binding.contactsLink.setOnClickListener { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }
        binding.help.setOnClickListener { findNavController().navigate(R.id.action_home_to_about) }
        // Always opens the checks list, also when everything is fine.
        binding.statusCard.setOnClickListener { findNavController().navigate(R.id.action_home_to_problem) }

        viewModel.calls.observe(viewLifecycleOwner) { adapter.submitList(it) }
        viewModel.messageItems.observe(viewLifecycleOwner) { voiceAdapter.submitList(it) }
        viewModel.player.state.observe(viewLifecycleOwner) {
            voiceAdapter.setPlayerState(it)
            // A 10-minute message must not stop when the screen times out (onStop pauses it).
            binding.root.keepScreenOn = it.playing
        }
        viewModel.downloading.observe(viewLifecycleOwner) { voiceAdapter.setDownloading(it) }
        viewModel.offline.observe(viewLifecycleOwner) { offline ->
            if (offline != true) return@observe
            viewModel.offline.value = false
            Toast.makeText(requireContext(), R.string.tpp_voice_offline, Toast.LENGTH_SHORT).show()
        }
        viewModel.status.observe(viewLifecycleOwner) { updatePulse() }
        viewModel.lineMovedFor.observe(viewLifecycleOwner) { user ->
            if (user == null) return@observe
            viewModel.lineMovedFor.value = null
            findNavController().navigate(R.id.action_home_to_lineMoved, bundleOf(ARG_PREFILL_E164 to user))
        }
        viewModel.problemCount.observe(viewLifecycleOwner) { count ->
            binding.problemBody = resources.getQuantityString(R.plurals.tpp_status_problem_body, count, count)
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        restartIfNoAccount(requireActivity())
        viewModel.refresh()
        viewModel.updateContactsLink()
        updatePulse()
        // Right away, then every 30 s while visible: in foreground the push reaches Linphone's service, not us.
        polling?.cancel()
        polling = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                viewModel.refreshMessages()
                delay(MESSAGES_POLL_MS)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        polling?.cancel()
        polling = null
        updatePulse()
    }

    override fun onStop() {
        // Screen turned off by hand, call screen or another screen of the app: no audio in the background.
        viewModel.pausePlayback()
        super.onStop()
    }

    override fun onDestroyView() {
        pulse?.stop()
        pulse = null
        super.onDestroyView()
    }

    private fun askToDelete(message: VoiceMessage) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.tpp_voice_delete_dialog)
            .setPositiveButton(R.string.tpp_voice_delete_confirm) { _, _ -> viewModel.delete(message.id) }
            .setNegativeButton(R.string.tpp_cancel, null)
            .show()
    }

    private fun updatePulse() {
        pulse?.update(shouldPulse(viewModel.status.value, isResumed, ValueAnimator.areAnimatorsEnabled()))
    }
}
