// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ar.com.tpp.phone.ui.askToDeleteAccount
import org.linphone.BuildConfig
import org.linphone.R
import org.linphone.databinding.TppAboutFragmentBinding

class AboutFragment : Fragment() {
    companion object {
        private const val SUPPORT_EMAIL = "prepago@tpp.com.ar"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TppAboutFragmentBinding.inflate(inflater, container, false)
        binding.header.title = getString(R.string.tpp_about_title)
        binding.header.back.setOnClickListener { findNavController().popBackStack() }

        var version = getString(R.string.tpp_about_version, BuildConfig.VERSION_NAME)
        if (BuildConfig.DEBUG) version += "\n" + getString(R.string.linphone_app_version)
        binding.versionText = version

        binding.check.label = getString(R.string.tpp_about_check)
        binding.check.row.setOnClickListener { findNavController().navigate(R.id.action_about_to_problem) }

        binding.email.label = getString(R.string.tpp_about_email)
        binding.email.row.setOnClickListener { writeToSupport() }

        binding.privacy.label = getString(R.string.tpp_about_privacy)
        binding.privacy.row.setOnClickListener { findNavController().navigate(R.id.action_about_to_privacy) }

        binding.aboutUs.label = getString(R.string.tpp_about_us)
        binding.aboutUs.isLast = true
        binding.aboutUs.row.setOnClickListener { findNavController().navigate(R.id.action_about_to_aboutUs) }

        binding.deleteAccount.setOnClickListener { askToDeleteAccount(it) }
        return binding.root
    }

    /** "Ayuda vía email": the support address, with the subject filled in. */
    private fun writeToSupport() {
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.tpp_about_email_subject))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), getString(R.string.tpp_about_email_none, SUPPORT_EMAIL), Toast.LENGTH_LONG).show()
        }
    }
}
