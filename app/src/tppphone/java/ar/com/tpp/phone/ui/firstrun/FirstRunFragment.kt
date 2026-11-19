// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.firstrun

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.linphone.BuildConfig
import org.linphone.R
import org.linphone.databinding.TppFirstRunFragmentBinding

class FirstRunFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TppFirstRunFragmentBinding.inflate(inflater, container, false)
        binding.activate.setOnClickListener { findNavController().navigate(R.id.action_firstRun_to_number) }
        if (BuildConfig.DEBUG) {
            // Hidden entry to load a test account by hand.
            binding.logo.setOnLongClickListener {
                findNavController().navigate(R.id.action_firstRun_to_testAccount)
                true
            }
        }
        return binding.root
    }
}
