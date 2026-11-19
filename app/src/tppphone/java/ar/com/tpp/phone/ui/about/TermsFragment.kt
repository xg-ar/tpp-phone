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

/** TPP Phone terms: loaded from prov, with the copy in res/raw as fallback; same plain-text screen as the privacy policy. */
class TermsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TppPrivacyFragmentBinding.inflate(inflater, container, false)
        binding.header.title = getString(R.string.tpp_terms_title)
        binding.header.back.setOnClickListener { findNavController().popBackStack() }

        val terms = resources.openRawResource(R.raw.tpp_terms).bufferedReader().use { it.readText() }
        binding.text.text = HtmlCompat.fromHtml(terms, HtmlCompat.FROM_HTML_MODE_LEGACY).trim()
        // the current text lives in prov; the bundled copy stays if it does not arrive.
        val prov = ProvClient(getString(R.string.tpp_prov_url))
        viewLifecycleOwner.lifecycleScope.launch {
            val remote = withContext(Dispatchers.IO) { prov.legalFragment("terminos") }
            val chosen = pickLegalText(remote, terms)
            if (chosen !== terms) binding.text.text = HtmlCompat.fromHtml(chosen, HtmlCompat.FROM_HTML_MODE_LEGACY).trim()
        }
        return binding.root
    }
}
