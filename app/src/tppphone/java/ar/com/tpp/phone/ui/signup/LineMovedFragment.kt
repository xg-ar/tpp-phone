// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.databinding.TppLineMovedFragmentBinding

/** The old account stays until a new activation replaces it. */
class LineMovedFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TppLineMovedFragmentBinding.inflate(inflater, container, false)
        binding.header.title = getString(R.string.tpp_line_moved_header)
        binding.header.back.setOnClickListener { findNavController().popBackStack() }
        val e164 = arguments?.getString(ARG_PREFILL_E164)
        binding.activate.setOnClickListener {
            findNavController().navigate(R.id.action_lineMoved_to_number, bundleOf(ARG_PREFILL_E164 to e164))
        }
        return binding.root
    }
}
