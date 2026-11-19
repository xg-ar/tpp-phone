// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.about

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.databinding.TppAboutUsFragmentBinding

/** "Sobre nosotros": company, shop and, in small print, the mandatory license notices. */
class AboutUsFragment : Fragment() {
    companion object {
        private const val SHOP_URL = "https://prepago.tpp.com.ar"

        // stable prov link that redirects to the public source repository.
        private const val SOURCE_PATH = "/tpp-phone/codigo-fuente"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TppAboutUsFragmentBinding.inflate(inflater, container, false)
        binding.header.title = getString(R.string.tpp_about_us)
        binding.header.back.setOnClickListener { findNavController().popBackStack() }

        binding.shop.label = getString(R.string.tpp_about_us_shop)
        binding.shop.isLast = true
        binding.shop.row.setOnClickListener { openUrl(SHOP_URL) }

        binding.phones.text = HtmlCompat.fromHtml(getString(R.string.tpp_about_us_phones), HtmlCompat.FROM_HTML_MODE_COMPACT)
        binding.phones.movementMethod = LinkMovementMethod.getInstance()

        binding.source.setOnClickListener { openUrl(getString(R.string.tpp_prov_url).trimEnd('/') + SOURCE_PATH) }
        binding.terms.setOnClickListener { findNavController().navigate(R.id.action_aboutUs_to_terms) }
        binding.licenses.setOnClickListener { findNavController().navigate(R.id.action_aboutUs_to_licenses) }
        return binding.root
    }

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
