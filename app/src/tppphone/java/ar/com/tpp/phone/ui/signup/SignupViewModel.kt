// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.signup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.logic.RequestGate
import ar.com.tpp.phone.prov.ActivationResult
import ar.com.tpp.phone.prov.InstallId
import ar.com.tpp.phone.prov.ProvClient
import ar.com.tpp.phone.work.ReRegisterWorker
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R

/**
 * Runs a signup screen's request outside its view, so a fold, rotation or tap during the
 * request can't drop prov's answer: an activated line is applied here, before the screen sees it.
 */
class SignupViewModel(application: Application) : AndroidViewModel(application) {
    private val gate = RequestGate()

    val client = ProvClient(application.getString(R.string.tpp_prov_url))
    val installId: String get() = InstallId.get(getApplication())

    val busy = MutableLiveData(false)

    /** The number a code was last requested for (survives a rotation with the request). */
    var sentTo: String? = null

    /** The current WhatsApp verification; a resend replaces it. */
    var verificationId: String? = null

    /** When the help wait screen started asking prov (survives a rotation). */
    var helpWaitStartedAtMs = 0L

    /** WhatsApp was already opened with the help key (only once, not again after a rotation). */
    var helpChatOpened = false

    /** One-time result for the screen: a StartResult or an ActivationResult. Set back to null once handled. */
    val result = MutableLiveData<Any?>()

    /** Holds the gate (e.g. while a confirmation dialog is open); false if a request is already running. */
    fun begin(): Boolean {
        if (!gate.tryStart()) return false
        busy.value = true
        return true
    }

    fun cancel() {
        gate.finish()
        busy.value = false
    }

    /** Runs [request] after a successful [begin]. */
    fun launch(request: (ProvClient) -> Any) {
        viewModelScope.launch {
            var answer = withContext(Dispatchers.IO) { request(client) }
            if (answer is ActivationResult.Activated && !applyLine(answer)) answer = ActivationResult.Failed
            gate.finish()
            busy.value = false
            result.value = answer
        }
    }

    private suspend fun applyLine(activated: ActivationResult.Activated): Boolean = suspendCoroutine { cont ->
        coreContext.postOnCoreThread { core ->
            val ok = TppAccount.applyLine(core, activated.config)
            if (ok) ReRegisterWorker.schedule(getApplication())
            cont.resume(ok)
        }
    }
}
