// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.testaccount

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.work.ReRegisterWorker
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.databinding.TppTestAccountFragmentBinding

/** Debug builds only (reached from a long press on the first-run title). */
class TestAccountFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TppTestAccountFragmentBinding.inflate(inflater, container, false)
        binding.useAccount.setOnClickListener {
            val username = binding.username.text.toString().trim()
            val password = binding.password.text.toString()
            if (username.isEmpty() || password.isEmpty()) return@setOnClickListener
            coreContext.postOnCoreThread { core ->
                TppAccount.create(core, username, password)
                ReRegisterWorker.schedule(requireContext().applicationContext)
                binding.root.post { findNavController().navigate(R.id.action_testAccount_to_home) }
            }
        }
        return binding.root
    }
}
