// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import ar.com.tpp.phone.logic.pickLegalText
import ar.com.tpp.phone.prov.ProvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.R
import org.linphone.databinding.TppPrivacyFragmentBinding

/** Privacy policy: loaded from prov, with the text-only copy in res/raw as fallback. */
class PrivacyFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TppPrivacyFragmentBinding.inflate(inflater, container, false)
        binding.header.title = getString(R.string.tpp_about_privacy)
        binding.header.back.setOnClickListener { findNavController().popBackStack() }

        val policy = resources.openRawResource(R.raw.tpp_privacy_policy).bufferedReader().use { it.readText() }
        binding.text.text = HtmlCompat.fromHtml(policy, HtmlCompat.FROM_HTML_MODE_LEGACY).trim()
        // the current text lives in prov; the bundled copy stays if it does not arrive.
        val prov = ProvClient(getString(R.string.tpp_prov_url))
        viewLifecycleOwner.lifecycleScope.launch {
            val remote = withContext(Dispatchers.IO) { prov.legalFragment("privacidad") }
            val chosen = pickLegalText(remote, policy)
            if (chosen !== policy) binding.text.text = HtmlCompat.fromHtml(chosen, HtmlCompat.FROM_HTML_MODE_LEGACY).trim()
        }
        return binding.root
    }
}
