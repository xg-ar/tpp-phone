// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.databinding.TppLicensesFragmentBinding

/** "Licencias": TPP Phone's own notice, then assets/licenses (NOTICE.txt first, then the full texts). */
class LicensesFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TppLicensesFragmentBinding.inflate(inflater, container, false)
        binding.header.title = getString(R.string.tpp_licenses_title)
        binding.header.back.setOnClickListener { findNavController().popBackStack() }

        val assets = requireContext().assets
        val files = assets.list("licenses").orEmpty().sortedBy { if (it == "NOTICE.txt") "" else it }
        val separator = "\n\n" + "-".repeat(40) + "\n\n"
        val texts = files.map { name -> assets.open("licenses/$name").bufferedReader().use { it.readText() } }
        binding.text.text = (listOf(getString(R.string.tpp_about_us_legal)) + texts).joinToString(separator)
        return binding.root
    }
}
